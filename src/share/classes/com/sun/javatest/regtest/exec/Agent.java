/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.javatest.regtest.exec;


import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.javatest.Status;
import com.sun.javatest.TestResult;
import com.sun.javatest.WorkDirectory;
import com.sun.javatest.regtest.TimeoutHandler;
import com.sun.javatest.regtest.agent.ActionHelper;
import com.sun.javatest.regtest.agent.AgentServer;
import com.sun.javatest.regtest.agent.Alarm;
import com.sun.javatest.regtest.agent.Flags;
import com.sun.javatest.regtest.agent.SearchPath;
import com.sun.javatest.regtest.config.JDK;
import com.sun.javatest.regtest.config.RegressionParameters;
import com.sun.javatest.regtest.util.ProcessUtils;
import com.sun.javatest.regtest.util.StringUtils;

import static com.sun.javatest.regtest.RStatus.createStatus;
import static com.sun.javatest.regtest.agent.AgentServer.*;

public class Agent {
    public static class Fault extends Exception {
        private static final long serialVersionUID = 0;
        Fault(Throwable e) {
            super(e);
        }
    }

    // represents a timeout that occurred while executing
    // a test action within an agentvm
    static final class ActionTimeout extends Exception {
        private static final long serialVersionUID = 7956108605006221253L;
    }

    // legacy support for logging to stderr
    // showAgent is superseded by always-on log to file
    static final boolean showAgent = Flags.get("showAgent");
    static final boolean traceAgent = Flags.get("traceAgent");

    /**
     * Start a JDK with given JVM options.
     */
    private Agent(File dir, JDK jdk, List<String> vmOpts, Map<String, String> envVars,
            File policyFile, float timeoutFactor, Logger logger,
            String testThreadFactory, String testThreadFactoryPath) throws Fault {
        Process agentServerProcess = null;
        try {
            id = ++count;
            this.jdk = jdk;
            this.execDir = dir;
            this.vmOpts = vmOpts;
            this.logger = logger;

            List<String> cmd = new ArrayList<>();
            cmd.add(jdk.getJavaProg().toString());
            cmd.addAll(vmOpts);
            if (policyFile != null)
                cmd.add("-Djava.security.policy=" + policyFile.toURI());
            String headless = System.getProperty("java.awt.headless");
            if (headless != null)
                cmd.add("-Djava.awt.headless=" + headless);
            cmd.add(AgentServer.class.getName());

            cmd.add(AgentServer.ID);
            cmd.add(String.valueOf(id));
            cmd.add(AgentServer.LOGFILE);
            cmd.add(logger.getAgentServerLogFile(id).getPath());

            if (policyFile != null)
                cmd.add(AgentServer.ALLOW_SET_SECURITY_MANAGER);

            ServerSocket ss = new ServerSocket();
            // Ensure SO_REUSEADDR is false. (It is only needed if we're
            // using a fixed port.) The default setting for SO_REUSEADDR
            // is platform-specific, and Solaris has it on by default.
            ss.setReuseAddress(false);
            InetAddress loopbackAddr = InetAddress.getLoopbackAddress();
            ss.bind(new InetSocketAddress(loopbackAddr, /*port:*/ 0), /*backlog:*/ 1);
            final int port = ss.getLocalPort();
            cmd.add(AgentServer.PORT);
            cmd.add(String.valueOf(port));

            if (timeoutFactor != 1.0f) {
                cmd.add(AgentServer.TIMEOUTFACTOR);
                cmd.add(String.valueOf(timeoutFactor));
            }

            if (testThreadFactory != null) {
                cmd.add(AgentServer.CUSTOM_TEST_THREAD_FACTORY);
                cmd.add(testThreadFactory);
            }

            if (testThreadFactoryPath != null) {
                cmd.add(CUSTOM_TEST_THREAD_FACTORY_PATH);
                cmd.add(testThreadFactoryPath);
            }
            log("Launching " + cmd);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir);
            Map<String, String> env = pb.environment();
            env.clear();
            env.putAll(envVars);
            agentServerProcess = process = pb.start();
            agentServerPid = ProcessUtils.getProcessId(process);
            copyAgentProcessStream("stdout", process.getInputStream());
            copyAgentProcessStream("stderr", process.getErrorStream());

            try {
                final int ACCEPT_TIMEOUT = (int) (60 * 1000 * timeoutFactor);
                // default 60 seconds, for server to start and "phone home"
                ss.setSoTimeout(ACCEPT_TIMEOUT);
                log("Waiting up to " + ACCEPT_TIMEOUT + " milli seconds for a" +
                        " socket connection on port " + port +
                        (agentServerPid != -1 ? " from process " + agentServerPid : ""));
                Socket s = ss.accept();
                log("Received connection on port " + port + " from " + s);
                s.setSoTimeout((int)(KeepAlive.READ_TIMEOUT * timeoutFactor));
                in = new DataInputStream(s.getInputStream());
                out = new DataOutputStream(s.getOutputStream());
            } finally {
                ss.close();
            }

            keepAlive = new KeepAlive(out, traceAgent);
            // send keep-alive messages to server while not executing actions
            keepAlive.setEnabled(true);
        } catch (IOException e) {
            log("Agent creation failed due to " + e);
            if (agentServerProcess != null) {
                // kill the launched process
                log("killing AgentServer process");
                try {
                    ProcessUtils.destroyForcibly(agentServerProcess);
                } catch (Exception ignored) {
                    // ignore
                }
            }
            throw new Fault(e);
        }
    }

    /**
     * Reads the output written by an agent process, and copies it either to
     * the current TestResult object (when one is available) or to the agent's
     * log file, if output is found while there is no test using the agent.
     *
     * @param name the name of the stream
     * @param in   the stream
     */
    void copyAgentProcessStream(final String name, final InputStream in) {
        Thread t = new Thread() {
            @Override
            public void run() {
                try (BufferedReader inReader = new BufferedReader(new InputStreamReader(in))) {
                    String line;
                    while ((line = inReader.readLine()) != null) {
                        handleProcessStreamLine(name, line);
                    }
                } catch (IOException e) {
                    // ignore
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    /**
     * This field is set during doAction, and when set, any output received
     * from the agent process on stdout (fd1) and stderr (fd2) will be written
     * to the appropriate block in the current test result section.
     * Any data received from the agent when this is not set is written to the
     * agent log file.
     */
    private TestResult.Section currentTestResultSection;

    /**
     * A map of the currently open writers for process streams capturing
     * output written by the agent on stdout (fd0) and stderr (fd1).
     */
    private Map<String, PrintWriter> processStreamWriters = new HashMap<>();

    /**
     * Starts or stops capturing output written by the agent on stdout (fd1) and stderr (fd2)
     * into the given test result section.
     *
     * If the given section is not {code null}, output written by the agent will
     * be recorded in blocks in the given section.
     * If the given section is {@code null}, output written by the agent will
     * be recorded in the agent's log file.
     *
     * It is expected that this method will be used to set a non-null section
     * for the duration of an ac tion (see doAction), so that output written
     * by the agent during that time will be recorded in the appropriate test
     * result section.
     *
     * @param section the test result section to be used, or {@code null}
     */
    private synchronized void captureProcessStreams(TestResult.Section section) {
        currentTestResultSection = section;
        if (currentTestResultSection == null) {
            for (PrintWriter pw : processStreamWriters.values()) {
                pw.close();
            }
            processStreamWriters.clear();
        }
    }

    /**
     * Saves a line of output that was written by the agent to stdout (fd1) or stderr (fd2).
     * If there is a current test result section, the line is saved there;
     * otherwise it is written to the agent log file.
     *
     * @param name the name of the stream from which the line was read
     * @param line the line that was read
     */
    private synchronized void handleProcessStreamLine(String name, String line) {
        if (currentTestResultSection == null) {
            log(name + ": " + line);
        } else {
            processStreamWriters.computeIfAbsent(name, currentTestResultSection::createOutput)
                    .println(line);
        }
    }

    public boolean matches(File execDir, JDK jdk, List<String> vmOpts) {
        return this.execDir.getName().equals(execDir.getName())
                && this.jdk.equals(jdk)
                && this.vmOpts.equals(vmOpts);
    }

    public Status doCompileAction(
            final String testName,
            final Map<String, String> testProps,
            final List<String> cmdArgs,
            int timeout,
            final TimeoutHandler timeoutHandler,
            TestResult.Section trs)
                throws ActionTimeout, Fault {
        trace("doCompileAction " + testName + " " + cmdArgs);

        return doAction("doCompileAction",
                new AgentAction() {
                    @Override
                    public void send() throws IOException {
                            // See corresponding list in AgentServer.doCompile
                            out.writeByte(DO_COMPILE);
                            out.writeUTF(testName);
                            writeMap(testProps);
                            writeCollection(cmdArgs);
                            out.flush();
                        }
                },
                timeout,
                timeoutHandler,
                trs);
    }

    public Status doMainAction(
            final String testName,
            final Map<String, String> testProps,
            final Set<String> addExports,
            final Set<String> addOpens,
            final Set<String> addMods,
            final SearchPath testClassPath,
            final SearchPath modulePath,
            final String testClass,
            final List<String> testArgs,
            int timeout,
            final TimeoutHandler timeoutHandler,
            TestResult.Section trs)
                throws ActionTimeout, Fault {
        trace("doMainAction: " + testName
                    + " " + testClassPath
                    + " " + modulePath
                    + " " + testClass
                    + " " + testArgs);

        return doAction("doMainAction",
                new AgentAction() {
                    @Override
                    public void send() throws IOException {
                        // See corresponding list in AgentServer.doMain
                        out.writeByte(DO_MAIN);
                        out.writeUTF(testName);
                        writeMap(testProps);
                        writeCollection(addExports);
                        writeCollection(addOpens);
                        writeCollection(addMods);
                        out.writeUTF(testClassPath.toString());
                        out.writeUTF(modulePath.toString());
                        out.writeUTF(testClass);
                        writeCollection(testArgs);
                        out.flush();
                    }
                },
                timeout,
                timeoutHandler,
                trs);
    }

    interface AgentAction {
        void send() throws IOException;
    }

    private Status doAction(
            String actionName,
            AgentAction agentAction,
            int timeout,
            final TimeoutHandler timeoutHandler,
            TestResult.Section trs)
                throws ActionTimeout, Fault {
        final PrintWriter messageWriter = trs.getMessageWriter();
        // Handle the timeout here (instead of in the agent) to make it possible
        // to see the unchanged state of the Agent JVM when the timeout happens.
        Alarm alarm = Alarm.NONE;
        final CountDownLatch timeoutHandlerDone = new CountDownLatch(1);
        if (timeout > 0) {
            if (timeoutHandler == null) {
                throw new NullPointerException("TimeoutHandler is required");
            }
            trace(actionName + ": scheduling timeout handler in " + timeout + " seconds");
            alarm = Alarm.schedule(timeout, TimeUnit.SECONDS, messageWriter, new Runnable() {
                        @Override
                        public void run() {
                            invokeTimeoutHandler(timeoutHandler, timeoutHandlerDone, messageWriter);
                        }
                    });
        }
        keepAlive.setEnabled(false);
        try {
            captureProcessStreams(trs);
            synchronized (out) {
                agentAction.send();
            }
            trace(actionName + ": request sent");
            return readResults(trs);
        } catch (IOException e) {
            trace(actionName + ":  error " + e);
            throw new Fault(e);
        } finally {
            captureProcessStreams(null);
            alarm.cancel();
            keepAlive.setEnabled(true);
            if (alarm.didFire()) {
                waitForTimeoutHandler(actionName, timeoutHandler, timeoutHandlerDone);
                throw new ActionTimeout();
            }
        }
    }

    private void invokeTimeoutHandler(final TimeoutHandler timeoutHandler,
                                      final CountDownLatch timeoutHandlerDone,
                                      final PrintWriter messageWriter) {
        // Invocations from an Alarm call should be quick so that the Alarm thread pool
        // is not consumed. Because of that, we launch the timeout handling in a
        // separate Thread here. Timeout handling can take a very long time.
        Thread timeoutHandlerThread = new Thread() {
            @Override
            public void run() {
                trace("timeout handler triggered");

                timeoutHandler.handleTimeout(process);

                // close the streams to release us from readResults()
                try {
                    out.close();
                } catch (IOException ex) {
                    ex.printStackTrace(messageWriter);
                }
                try {
                    in.close();
                } catch (IOException ex) {
                    ex.printStackTrace(messageWriter);
                }
                trace("timeout handler finished");
                timeoutHandlerDone.countDown();
            }
        };
        timeoutHandlerThread.setName("Timeout Handler for Agent " + getId());
        timeoutHandlerThread.start();
    }

    private void waitForTimeoutHandler(String actionName, TimeoutHandler timeoutHandler, CountDownLatch timeoutHandlerDone) {
        trace(actionName + ":  waiting for timeout handler to complete.");
        try {
            if (timeoutHandler.getTimeout() <= 0) {
                timeoutHandlerDone.await();
            } else {
                boolean done = timeoutHandlerDone.await(timeoutHandler.getTimeout() + 10, TimeUnit.SECONDS);
                if (!done) {
                    trace(actionName + ": timeout handler did not complete within its own timeout.");
                }
            }
        } catch (InterruptedException e1) {
            trace(actionName + ":  interrupted while waiting for timeout handler to complete: " + e1);
        }
    }

    public void close() {
        log("Closing...");

        keepAlive.finished();

        try {
            out.write(CLOSE); // attempt clean shutdown
            out.close();
        } catch (IOException e) {
            trace("Killing process (" + e + ")");
            ProcessUtils.destroyForcibly(process); // force shutdown if necessary
        }

        PrintWriter pw = new PrintWriter(System.err, true);
        Alarm alarm = Alarm.schedulePeriodicInterrupt(60, TimeUnit.SECONDS, pw, Thread.currentThread());
        try {
            int rc = process.waitFor();
            if (rc != 0)
                trace("Exited, process exit code: " + rc);
        } catch (InterruptedException e) {
            log("Interrupted while closing");
            log("Killing process");
            ProcessUtils.destroyForcibly(process);
        } finally {
            alarm.cancel();
            Thread.interrupted(); // clear any interrupted status
        }

        log("Closed");
    }

    void writeCollection(Collection<String> c) throws IOException {
        out.writeShort(c.size());
        for (String s: c)
            out.writeUTF(s);
    }

    void writeOptionalString(String s) throws IOException {
        if (s == null)
            out.writeByte(0);
        else {
            out.writeByte(1);
            out.writeUTF(s);
        }
    }

    static String readOptionalString(DataInputStream in) throws IOException {
        int b = in.readByte();
        return (b == 0) ? null : in.readUTF();
    }

    void writeMap(Map<String, String> map) throws IOException {
        out.writeShort(map.size());
        for (Map.Entry<String, String> e: map.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeUTF(e.getValue());
        }
    }

    Status readResults(TestResult.Section trs) throws IOException {
        Map<String, PrintWriter> streams = new HashMap<>();
        int op;
        while ((op = in.readByte()) != -1) {
            switch (op) {
                case OUTPUT: {
                    String name = in.readUTF();
                    String data = in.readUTF();
                    trace("readResults: OUTPUT \'" + name + "\' \'" + data + "\"");
                    PrintWriter pw = streams.get(name);
                    if (pw == null) {
                        if (name.equals(ActionHelper.OutputHandler.OutputKind.LOG.name))
                            pw = trs.getMessageWriter();
                        else
                            pw = trs.createOutput(name);
                        streams.put(name, pw);
                    }
                    pw.write(data);
                    break;
                }
                case STATUS: {
                    int type = in.readByte();
                    String reason = in.readUTF();
                    trace("readResults: STATUS \'" + type + "\' \'" + reason + "\"");
                    for (PrintWriter pw: streams.values()) {
                        if (pw != trs.getMessageWriter())
                            pw.close();
                    }
                    Status status = createStatus(type, reason);
                    // any other cleanup??
                    return status;
                }
                case KEEPALIVE:
                    break;
                default:
                    // mark owner bad??
//                    do {
//                    System.err.println("Unexpected op: " + op + "'" + ((char)op) + "'");
//                    } while ((op = in.readByte()) != -1);
//                    Thread.dumpStack();
                    throw new IOException("Agent: unexpected op: " + op);
            }
        }
        // mark owner bad??
        throw new EOFException("unexpected EOF");
    }

    /**
     * Returns the id for this agent.
     * The id is just a small strictly-positive integer, allocated from 1 on up.
     * The id is used to identify the agent in logging messages.
     *
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the process id of the {@code AgentServer} with which this {@code Agent}
     * communicates or {@code -1} if the process id of the {@code AgentServer}
     * couldn't be determined.
     *
     * @return the AgentServer's process id
     */
    long getAgentServerPid() {
        return agentServerPid;
    }

    /**
     * Logs a message to the log file managed by the logger.
     *
     * @param message the message
     */
    private void log(String message) {
        logger.log(this, message);
        show(message);
    }

    private void show(String s) {
        if (showAgent || traceAgent) {
            log(s, System.err);
        }
    }

    private void trace(String s) {
        if (traceAgent) {
            log(s, System.err);
        }
    }

    // legacy show/trace support
    private void log(String message, PrintStream out) {
        out.println("[" + AgentServer.logDateFormat.format(new Date()) + "] Agent[" + getId() + "]: " + message);
    }

    final JDK jdk;
    final List<String> vmOpts;
    final File execDir;
    final Process process;
    final DataInputStream in;
    final DataOutputStream out;
    final KeepAlive keepAlive;
    final int id;
    final Logger logger;
    Instant idleStartTime;
    private final long agentServerPid;

    static int count;

    /**
     * Logger provides a directory ion which log files can be created,
     * and a writer for writing client-side logging messages.
     * The directory is the system jtData directory in the work directory;
     * The file for client-side logging is jtData/agent.trace.
     * Log messages are prefixed with a standard sort-friendly timestamp,
     * so that the log files in the directory can be merge-sorted into
     * a single log for later analysis.
     */
    public static class Logger {
        private static WeakHashMap<RegressionParameters, Logger> instances = new WeakHashMap<>();

        public static synchronized Logger instance(RegressionParameters params) {
            return instances.computeIfAbsent(params, Logger::new);
        }

        public static void close(RegressionParameters params) throws IOException {
            Logger l = instances.get(params);
            if (l != null) {
                l.close();
            }
        }

        private File agentLogFileDirectory;
        private final PrintWriter agentLogWriter;

        Logger(RegressionParameters params) {
            WorkDirectory wd = params.getWorkDirectory();
            agentLogFileDirectory = wd.getJTData();
            File logFile = new File(agentLogFileDirectory, "agent.trace");
            PrintWriter out;
            try {
                out = new PrintWriter(new FileWriter(logFile));
            } catch (IOException e) {
                System.err.println("Cannot open agent log file: " + e);
                out = new PrintWriter(System.err, true) {
                    @Override
                    public void close() {
                        flush();
                    }
                };
            }
            agentLogWriter = out;
        }

        void log(Agent agent, String message) {
            String dateInfo = AgentServer.logDateFormat.format(new Date());
            String agentInfo = (agent == null) ? "" : " Agent[" + agent.getId() + "]";
            if (message.contains("\n")) {
                String[] lines = message.split("\\R");
                int i = 0;
                for (String line : lines) {
                    agentLogWriter.printf("[%s]%s: #%d/%d %s%n",
                            dateInfo, agentInfo, ++i, lines.length, line);
                }
            } else {
                agentLogWriter.printf("[%s]%s: %s%n",
                        dateInfo, agentInfo, message);
            }
        }

        File getAgentServerLogFile(int id) {
            return new File(agentLogFileDirectory, "agentServer." + id + ".trace");
        }

        public void close() throws IOException {
            agentLogWriter.close();
        }
    }

    /**
     * A reusable collection of JVMs with varying VM options.
     * <p>
     * Pools are associated with an instance of RegressionParameters, from which
     * it gets a Logger to report activity that may be useful in case of any problems.
     */
    public static class Pool {
        /**
         * The instances.
         * It is expected that there will typically be exactly one entry in this collection.
         */
        private static WeakHashMap<RegressionParameters, Pool> instances = new WeakHashMap<>();

        private Stats stats = new Stats();

        /**
         * Returns the instance for the given RegressionParameters object.
         *
         * @param params the RegressionParameters object
         * @return the instance
         */
        public static synchronized Pool instance(RegressionParameters params) {
            return instances.computeIfAbsent(params, Pool::new);
        }

        private Pool(RegressionParameters params) {
            agentsByKey = new HashMap<>();
            allAgents = new LinkedList<>();
            logger = Logger.instance(params);
        }

        /**
         * Sets the policy file to be used for agents created for this pool.
         *
         * @param policyFile the file
         */
        public void setSecurityPolicy(File policyFile) {
            this.policyFile = policyFile;
        }

        /**
         * Sets the timeout factor to be used for agents created for this pool.
         *
         * @param factor the timeout factor
         */
        public void setTimeoutFactor(float factor) {
            this.timeoutFactor = factor;
        }

        /**
         * Sets the idle timeout for VMs in the pool.
         *
         * @param timeout the timeout
         */
        public void setIdleTimeout(Duration timeout) {
            this.idleTimeout = timeout;
            logger.log(null, "POOL: idle timeout: " + timeout);
        }

        /**
         * Sets the maximum number of VMs in the pool.
         *
         * @param size the maximum number of VMs to keep in the pool
         */
        public void setMaxPoolSize(int size) {
            this.maxPoolSize = size;
            logger.log(null, "POOL: max pool size: " + maxPoolSize);
        }

        /**
         * Sets the maximum attempts to create or obtain an agent VM
         * @param numAttempts number of attempts
         * @throws IllegalArgumentException if {@code numAttempts} is less than {@code 1}
         */
        public void setNumAgentSelectionAttempts(final int numAttempts) {
            if (numAttempts < 1) {
                throw new IllegalArgumentException("invalid value for agent selection attempts: "
                        + numAttempts);
            }
            this.numAgentSelectionAttempts = numAttempts;
            logger.log(null, "POOL: agent selection attempts: " + numAttempts);
        }

        /**
         * Obtains an agent with the desired properties.
         * If a suitable agent already exists in the pool, it will be removed from the pool and
         * returned; otherwise, a new one will be created.
         * Eventually, the agent should either be {@link #save(Agent) returned} to the pool,
         * if it can be reused, or {@link Agent#close() closed}, if it should not be reused.
         *
         *
         * @param dir     the execution directory for the agent
         * @param jdk     the JDK for the agent
         * @param vmOpts  the VM options for the agent
         * @param envVars the environment variables for the agent
         * @return the agent
         * @throws Fault if there is a problem obtaining a suitable agent
         */
        Agent getAgent(File dir,
                       JDK jdk,
                       List<String> vmOpts,
                       Map<String, String> envVars,
                       String testThreadFactory,
                       String testThreadFactoryPath)
                throws Fault {
            final int numAttempts = this.numAgentSelectionAttempts;
            assert numAttempts > 0 : "unexpected agent selection attempts: " + numAttempts;
            Agent.Fault toThrow = null;
            for (int i = 1; i <= numAttempts; i++) {
                try {
                    if (i != 1) {
                        logger.log(null, "POOL: re-attempting agent creation, attempt number " + i);
                    }
                    return doGetAgent(dir, jdk, vmOpts, envVars, testThreadFactory,
                            testThreadFactoryPath);
                } catch (Agent.Fault f) {
                    logger.log(null, "POOL: agent creation failed due to " + f.getCause());
                    // keep track of the fault and reattempt to get an agent if within limit
                    if (toThrow == null) {
                        toThrow = f;
                    } else {
                        // add the previous exception as a suppressed exception
                        // of the current one
                        if (toThrow.getCause() != null) {
                            f.addSuppressed(toThrow.getCause());
                        }
                        toThrow = f;
                    }
                    if (i == numAttempts || !(f.getCause() instanceof IOException)) {
                        // we either made enough attempts or we failed due to a non IOException.
                        // In either case we don't attempt to create an agent again and instead
                        // throw the captured failure(s)
                        throw toThrow;
                    }
                }
            }
            throw new AssertionError("should not reach here");
        }

        synchronized Agent doGetAgent(File dir,
                                    JDK jdk,
                                    List<String> vmOpts,
                                    Map<String, String> envVars,
                                    String testThreadFactory,
                                    String testThreadFactoryPath)
                throws Fault {
            logger.log(null,
                    "POOL: get agent for:\n"
                            + "   directory: " + dir + "\n"
                            + "         JDK: " + jdk + "\n"
                            + "  VM options: " + vmOpts + "\n"
            );
            Deque<Agent> agents = agentsByKey.get(getKey(dir, jdk, vmOpts));
            // reuse the most recently used agent, to increase the possibility
            // that older, less-used agents can be reclaimed.
            Agent a = (agents == null) ? null : agents.pollLast();
            if (a != null) {
                logger.log(null, "POOL: Reusing Agent[" + a.getId() + "]");
                allAgents.remove(a);
                stats.reuse(a);
            } else {
                logger.log(null, "POOL: Creating new agent");
                a = new Agent(dir, jdk, vmOpts, envVars, policyFile, timeoutFactor, logger,
                        testThreadFactory, testThreadFactoryPath);
                stats.add(a);
            }

            return a;
        }

        /**
         * Saves an agent in the pool for potential reuse.
         * The agent is assumed to have been restored to some standard state.
         *
         * @param agent the agent
         */
        synchronized void save(Agent agent) {
            logger.log(agent, "Saving agent to pool");
            String key = getKey(agent.execDir, agent.jdk, agent.vmOpts);
            agentsByKey.computeIfAbsent(key, k -> new LinkedList<>()).add(agent);
            allAgents.addLast(agent);

            Instant now = Instant.now();
            agent.idleStartTime = now;
            cleanOldEntries(now);

            stats.trackPoolSize(allAgents.size());
        }

        /**
         * Remove any old entries from the pool.
         *
         * The current policy is to remove excess agents when there are too many,
         * and to remove any agents that have been idle too long.
         * The maximum number of agents in the pool, and the maximum idle time
         * are both configurable.
         *
         * @param now the current time
         */
        private synchronized void cleanOldEntries(Instant now) {
            while (allAgents.size() > maxPoolSize) {
                Agent a = allAgents.getFirst();
                logger.log(a, "Removing excess agent from pool");
                removeAgent(a);
            }

            while (!allAgents.isEmpty()
                    && isIdleTooLong(allAgents.peekFirst(), now)) {
                Agent a = allAgents.getFirst();
                logger.log(a, "Removing idle agent from pool");
                removeAgent(a);
            }
        }

        private void removeAgent(Agent a) {
            agentsByKey.get(getKey(a)).remove(a);
            allAgents.remove(a);
            a.close();
        }

        private boolean isIdleTooLong(Agent a, Instant now) {
            return Duration.between(a.idleStartTime, now).compareTo(idleTimeout) > 0;
        }

        /**
         * Flushes any agents that may have been saved in a pool associated with
         * the given RegressionParameters object.
         *
         * @param params the RegressionParameters object
         */
        public static synchronized void flush(RegressionParameters params) {
            Pool instance = instances.get(params);
            if (instance != null) {
                instance.flush();
            }
        }

        /**
         * Flushes all agents that have been saved in this pool.
         */
        public synchronized void flush() {
            logger.log(null, "POOL: closing all agents");
            for (Agent a : allAgents) {
                a.close();
            }
            allAgents.clear();
            agentsByKey.clear();
            stats.report(new File(logger.agentLogFileDirectory, "agent.summary"), logger);
        }

        /**
         * Closes any agents with a given execution directory that may have been saved in a pool
         * associated with the given RegressionParameters object.
         *
         * @param params the RegressionParameters object
         * @param dir    the execution directory
         */
        static synchronized void close(RegressionParameters params, File dir) {
            Pool instance = instances.get(params);
            if (instance != null) {
                instance.close(dir);
            }
        }

        /**
         * Closes all agents in this pool with the given execution directory.
         * This is for use when the directory is no longer suitable for use,
         * such as when containing a file that cannot be deleted.
         *
         * @param dir the execution directory
         */
        synchronized void close(File dir) {
            logger.log(null, "POOL: closing agents using directory " + dir);
            for (Iterator<Agent> iter = allAgents.iterator(); iter.hasNext(); ) {
                Agent agent = iter.next();
                if (agent.execDir.equals(dir)) {
                    // remove from the allAgents list, currently being iterated
                    iter.remove();
                    // remove from the agentsByKey map
                    String agentKey = getKey(agent);
                    Deque<Agent> deque = agentsByKey.get(agentKey);
                    deque.remove(agent);
                    if (deque.isEmpty()) {
                        agentsByKey.remove(agentKey);
                    }
                    // close the agent
                    agent.close();
                }
            }
        }

        private static String getKey(Agent agent) {
            return getKey(agent.execDir, agent.jdk, agent.vmOpts);
        }

        private static String getKey(File dir, JDK jdk, List<String> vmOpts) {
            return (dir.getAbsolutePath() + " " + jdk.getAbsoluteHomeDirectory() + " " + StringUtils.join(vmOpts, " "));
        }

        private final Logger logger;

        /**
         * A map of the currently available agents, indexed by a key
         * derived from the agent's primary execution characteristics.
         * For each key, a deque is maintained of agents with that key.
         * The most recently used entries are "last" in the deque;
         * the oldest entries are "first" in the deque.
         */
        private final Map<String, Deque<Agent>> agentsByKey;

        /**
         * A list of all the currently available agents.
         * The most recently used entries are "last" in the deque;
         * the oldest entries are "first" in the deque.
         */
        private final Deque<Agent> allAgents;

        private File policyFile;
        private float timeoutFactor = 1.0f;
        private int maxPoolSize;
        private Duration idleTimeout;
        private int numAgentSelectionAttempts;
    }

    static class Stats {
        Set<File> allDirs = new TreeSet<>();
        Set<JDK> allJDKs = new TreeSet<>(Comparator.comparing( j -> j.getPath()));
        Set<List<String>> allVMOpts = new TreeSet<>(Comparator.comparing(Objects::toString));
        Map<Integer, Integer> useCounts = new TreeMap<>();
        Map<Integer, Integer> sizeCounts = new TreeMap<>();

        void add(Agent a) {
            allDirs.add(a.execDir);
            allJDKs.add(a.jdk);
            allVMOpts.add(a.vmOpts);

            useCounts.put(a.id, 1);
        }

        void reuse(Agent a) {
            useCounts.put(a.id, useCounts.get(a.id) + 1);
        }

        void trackPoolSize(int size) {
            sizeCounts.put(size, sizeCounts.computeIfAbsent(size, s -> 0) + 1);
        }

        void clear() {
            allDirs.clear();
            allJDKs.clear();
            allVMOpts.clear();
            useCounts.clear();
            sizeCounts.clear();
        }

        void report(File file, Logger logger) {
            try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
                report(out, "Execution Directories", allDirs);
                out.println();

                report(out, "JDKs", allJDKs);
                out.println();

                report(out, "VM Options", allVMOpts);
                out.println();


                out.format("Agent Usage:%n");
                useCounts.forEach((id, c) -> out.format("    %3d: %3d%n", id, c));
                double[] use_m_sd = getSimpleMeanStandardDeviation(useCounts.values());
                out.format("Mean:          %5.1f%n", use_m_sd[0]);
                out.format("Std Deviation: %5.1f%n", use_m_sd[1]);
                out.println();

                out.format("Pool Size:%n");
                sizeCounts.forEach((size, c) -> out.format("    %3d: %3d%n", size, c));
                double[] size_m_sd = getWeightedMeanStandardDeviation(sizeCounts);
                out.format("Mean          %5.1f%n", size_m_sd[0]);
                out.format("Std Deviation %5.1f%n", size_m_sd[1]);

            } catch (IOException e) {
                logger.log(null, "STATS: can't write stats file " + file + ": " + e);
            }
        }

        private <T> void report(PrintWriter out, String title, Set<T> set) {
            out.format("%s: %d%n", title, set.size());
            set.forEach(item -> out.format("    %s%n", item));
        }

        /**
         * Returns the mean and standard deviation of a collection of values.
         *
         * @param values the values
         * @return an array containing the mean and standard deviation
         */
        double[] getSimpleMeanStandardDeviation(Collection<Integer> values) {
            double sum = 0;
            for (Integer v : values) {
                sum += v;
            }
            double mean = sum / values.size();

            double sum2 = 0;
            for (Integer v : values) {
                double x = v - mean;
                sum2 += x * x;
            }
            double sd = Math.sqrt(sum2 / values.size());

            return new double[] { mean, sd };
        }

        /**
         * Returns the mean and standard deviation of a collection of weighted values.
         * The values are provided in a map of {@code value -> frequency}.
         *
         * @param map the map of weighted values
         * @return an array containing the mean and standard deviation
         */
        double[] getWeightedMeanStandardDeviation(Map<Integer, Integer> map) {
            long count = 0;
            double sum = 0;
            for (Map.Entry<Integer, Integer> e : map.entrySet()) {
                int value = e.getKey();
                int freq = e.getValue();
                sum += value * freq;
                count += freq;
            }
            double mean = sum / count;

            double sum2 = 0;
            for (Map.Entry<Integer, Integer> e : map.entrySet()) {
                int value = e.getKey();
                int freq = e.getValue();
                double x = value - mean;
                sum2 += x * x * freq;
            }
            double sd = Math.sqrt(sum2 / count);

            return new double[] { mean, sd };
        }
    }
}
