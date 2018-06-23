/*
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javatest.regtest.util.ProcessUtils;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.javatest.Status;
import com.sun.javatest.TestResult;
import com.sun.javatest.regtest.TimeoutHandler;
import com.sun.javatest.regtest.agent.ActionHelper;
import com.sun.javatest.regtest.agent.AgentServer;
import com.sun.javatest.regtest.agent.AgentServer.KeepAlive;
import com.sun.javatest.regtest.agent.Alarm;
import com.sun.javatest.regtest.agent.Flags;
import com.sun.javatest.regtest.agent.SearchPath;
import com.sun.javatest.regtest.config.JDK;
import com.sun.javatest.regtest.util.StringUtils;

import static com.sun.javatest.regtest.agent.AgentServer.*;
import static com.sun.javatest.regtest.RStatus.createStatus;

public class Agent {
    public static class Fault extends Exception {
        private static final long serialVersionUID = 0;
        Fault(Throwable e) {
            super(e);
        }
    }

    static final boolean showAgent = Flags.get("showAgent");
    static final boolean traceAgent = Flags.get("traceAgent");

    /**
     * Start a JDK with given JVM options.
     */
    private Agent(File dir, JDK jdk, List<String> vmOpts, Map<String, String> envVars,
            File policyFile, float timeoutFactor) throws Fault {
        try {
            id = count++;
            this.jdk = jdk;
            this.scratchDir = dir;
            this.vmOpts = vmOpts;

            List<String> cmd = new ArrayList<>();
            cmd.add(jdk.getJavaProg().getPath());
            cmd.addAll(vmOpts);
            if (policyFile != null)
                cmd.add("-Djava.security.policy=" + policyFile.toURI());
            cmd.add(AgentServer.class.getName());
            if (policyFile != null)
                cmd.add(AgentServer.ALLOW_SET_SECURITY_MANAGER);

            ServerSocket ss = new ServerSocket();
            // Ensure SO_REUSEADDR is false. (It is only needed if we're
            // using a fixed port.) The default setting for SO_REUSEADDR
            // is platform-specific, and Solaris has it on by default.
            ss.setReuseAddress(false);
            ss.bind(new InetSocketAddress(/*port:*/ 0), /*backlog:*/ 1);
            cmd.add(AgentServer.PORT);
            cmd.add(String.valueOf(ss.getLocalPort()));

            if (timeoutFactor != 1.0f) {
                cmd.add(AgentServer.TIMEOUTFACTOR);
                cmd.add(String.valueOf(timeoutFactor));
            }

            show("Started " + cmd);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir);
            Map<String, String> env = pb.environment();
            env.clear();
            env.putAll(envVars);
            process = pb.start();
            copyStream("stdout", process.getInputStream(), System.out);
            copyStream("stderr", process.getErrorStream(), System.err);

            try {
                final int ACCEPT_TIMEOUT = (int) (60 * 1000 * timeoutFactor);
                    // default 60 seconds, for server to start and "phone home"
                ss.setSoTimeout(ACCEPT_TIMEOUT);
                Socket s = ss.accept();
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
            throw new Fault(e);
        }
    }

    void copyStream(final String name, final InputStream in, final PrintStream out) {
        // Read a stream from the process and echo it to the local output.
        // TODO?: allow a script to temporarily claim an agent so that output
        // can be directed to the script's .jtr file?
        Thread t = new Thread() {
            @Override
            public void run() {
                try (BufferedReader inReader = new BufferedReader(new InputStreamReader(in))) {
                    String line;
                    while ((line = inReader.readLine()) != null)
                        log(name + ": " + line, out);
                } catch (IOException e) {
                    // ignore
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    public boolean matches(File scratchDir, JDK jdk, List<String> vmOpts) {
        return scratchDir.getName().equals(this.scratchDir.getName()) && this.jdk.equals(jdk) && this.vmOpts.equals(vmOpts);
    }

    public Status doCompileAction(
            final String testName,
            final Map<String, String> testProps,
            final List<String> cmdArgs,
            int timeout,
            final TimeoutHandler timeoutHandler,
            TestResult.Section trs)
                throws Fault {
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
            final SearchPath testClassPath,
            final String testClass,
            final List<String> testArgs,
            int timeout,
            final TimeoutHandler timeoutHandler,
            TestResult.Section trs)
                throws Fault {
        trace("doMainAction: " + testName
                    + " " + testClassPath
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
                        out.writeUTF(testClassPath.toString());
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
                throws Fault {
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
            synchronized (out) {
                agentAction.send();
            }
            trace(actionName + ": request sent");
            return readResults(trs);
        } catch (IOException e) {
            trace(actionName + ":  error " + e);
            throw new Fault(e);
        } finally {
            alarm.cancel();
            keepAlive.setEnabled(true);
            if (alarm.didFire()) {
                waitForTimeoutHandler(actionName, timeoutHandler, timeoutHandlerDone);
                throw new Fault(new Exception("Agent " + id + " timed out with a timeout of "
                        + timeout + " seconds"));
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
        show("Closing...");

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
            trace("Interrupted while closing");
            log("Killing process");
            ProcessUtils.destroyForcibly(process);
        } finally {
            alarm.cancel();
            Thread.interrupted(); // clear any interrupted status
        }

        show("Closed");
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

    public int getId() {
        return id;
    }

    // 2016-12-21 13:19:46,998
    private static final SimpleDateFormat logDateFormat = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss,SSS");

    private void log(String s, PrintStream out) {
        out.println("[" + logDateFormat.format(new Date()) + "] Agent[" + getId() + "]: " + s);
    }

    private void log(String s) {
        log(s, System.err);
    }

    private void show(String s) {
        if (showAgent || traceAgent) {
            log(s);
        }
    }

    private void trace(String s) {
        if (traceAgent) {
            log(s);
        }
    }

    final JDK jdk;
    final List<String> vmOpts;
    final File scratchDir;
    final Process process;
    final DataInputStream in;
    final DataOutputStream out;
    final KeepAlive keepAlive;
    final int id;

    static int count;

    /**
     * A reusable collection of JVMs with varying VM options.
     */
    public static class Pool {
        private static Pool instance;

        public static synchronized Pool instance() {
            if (instance == null)
                instance = new Pool();
            return instance;
        }

        private Pool() {
            map = new HashMap<>();
        }

        public void setSecurityPolicy(File policyFile) {
            this.policyFile = policyFile;
        }

        public void setTimeoutFactor(float factor) {
            this.timeoutFactor = factor;
        }

        synchronized Agent getAgent(File dir, JDK jdk, List<String> vmOpts, Map<String, String> envVars)
                throws Fault {
            Queue<Agent> agents = map.get(getKey(dir, jdk, vmOpts));
            Agent a = (agents == null) ? null : agents.poll();
            if (a == null) {
                a = new Agent(dir, jdk, vmOpts, envVars, policyFile, timeoutFactor);
            }
            return a;
        }

        synchronized void save(Agent agent) {
            String key = getKey(agent.scratchDir, agent.jdk, agent.vmOpts);
            Queue<Agent> agents = map.get(key);
            if (agents == null)
                map.put(key, agents = new LinkedList<>());
            agents.add(agent);
        }

        public synchronized void flush() {
            for (Queue<Agent> agents: map.values()) {
                for (Agent agent: agents) {
                    agent.close();
                }
            }
            map.clear();
        }

        /** Close all agents associated with a specific scratch directory. */
        synchronized void close(File scratchDir) {
            for (Iterator<Queue<Agent>> mapValuesIter = map.values().iterator(); mapValuesIter.hasNext(); ) {
                Queue<Agent> agents = mapValuesIter.next();
                for (Iterator<Agent> agentIter = agents.iterator(); agentIter.hasNext(); ) {
                    Agent agent = agentIter.next();
                    if (agent.scratchDir.equals(scratchDir))
                        agentIter.remove();
                }
                if (agents.isEmpty()) {
                    mapValuesIter.remove();
                }
            }
        }

        private static String getKey(File dir, JDK jdk, List<String> vmOpts) {
            return (dir.getAbsolutePath() + " " + jdk.getAbsoluteFile() + " " + StringUtils.join(vmOpts, " "));
        }

        private final Map<String, Queue<Agent>> map;
        private File policyFile;
        private float timeoutFactor = 1.0f;
    }
}
