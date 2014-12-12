/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.sun.javatest.Status;
import com.sun.javatest.TestResult;
import com.sun.javatest.util.Timer;

import static com.sun.javatest.regtest.RStatus.*;
import java.util.TimerTask;

public class Agent {
    public static class Fault extends Exception {
        private static final long serialVersionUID = 0;
        Fault(Throwable e) {
            super(e);
        }
    }

    static final boolean showAgent = Action.show("showAgent"); // mild uugh
    static final boolean traceAgent = Action.show("traceAgent");
    static final boolean traceServer = Action.show("traceServer");

    /**
     * If true, communication with the Agent Server is via a socket, whose initial
     * port is communicated on the command line when the server is started.
     * If false, communication is via stdin/stdout.
     */
    static final boolean USE_SOCKETS = true;

    /**
     * Main program used to invoke and run the server in child JVMs
     */
    public static void main(String... args) {
        if (traceServer)
            System.err.println("Agent.main");

        try {
            new Server(args).run();
        } catch (Throwable e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * Start a JDK with given JVM options.
     */
    private Agent(File dir, JDK jdk, List<String> vmOpts, Map<String, String> envVars,
            File policyFile) throws IOException {
        ServerSocket ss = null;

        id = count++;
        this.jdk = jdk;
        this.scratchDir = dir;
        this.vmOpts = vmOpts;

        List<String> cmd = new ArrayList<String>();
        cmd.add(jdk.getJavaProg().getPath());
        cmd.addAll(vmOpts);
        if (policyFile != null)
            cmd.add("-Djava.security.policy=" + policyFile.toURI());
        cmd.add(Agent.class.getName());
        if (policyFile != null)
            cmd.add(Agent.Server.ALLOW_SET_SECURITY_MANAGER);

        if (USE_SOCKETS) {
            ss = new ServerSocket(/*port:*/ 0, /*backlog:*/ 1);
//            cmd.add(Agent.Server.HOST);
//            cmd.add(String.valueOf(ss.getInetAddress().getHostAddress()));
            cmd.add(Agent.Server.PORT);
            cmd.add(String.valueOf(ss.getLocalPort()));
        }

        if (showAgent || traceAgent)
            System.err.println("Agent[" + id + "]: Started " + cmd);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        Map<String, String> env = pb.environment();
        env.clear();
        env.putAll(envVars);
        process = pb.start();

        if (USE_SOCKETS) {
            try {
                final int ACCEPT_TIMEOUT = 60*1000; // 1 minute, for server to start and "phone home"
                ss.setSoTimeout(ACCEPT_TIMEOUT);
                Socket s = ss.accept();
                s.setSoTimeout(KeepAlive.READ_TIMEOUT);
                in = new DataInputStream(s.getInputStream());
                out = new DataOutputStream(s.getOutputStream());
                copyStream("stdout", process.getInputStream(), System.out);
                copyStream("stderr", process.getErrorStream(), System.err);
            } finally {
                ss.close();
            }
        } else {
            in = new DataInputStream(process.getInputStream());
            out = new DataOutputStream(process.getOutputStream());
            copyStream("stderr", process.getErrorStream(), System.err);
        }

        keepAlive = new KeepAlive(out, traceAgent);
        // send keep-alive messages to server while not executing actions
        keepAlive.setEnabled(true);
    }

    void copyStream(final String name, final InputStream in, final PrintStream out) {
        // Read a stream from the process and echo it to the local output.
        // TODO?: allow a script to temporarily claim an agent so that output
        // can be directed to the script's .jtr file?
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    BufferedReader inReader = new BufferedReader(new InputStreamReader(in));
                    try {
                        String line;
                        while ((line = inReader.readLine()) != null)
                            out.println("Agent[" + id + "]." + name + ": " + line);
                    } catch (IOException e) {
                        // ignore
                    } finally {
                        inReader.close();
                    }
                } catch (IOException e) {
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    public boolean matches(File scratchDir, JDK jdk, List<String> vmOpts) {
        return scratchDir.getName().equals(this.scratchDir.getName()) && this.jdk.equals(jdk) && this.vmOpts.equals(vmOpts);
    }

    public Status doCompileAction(String testName,
            Map<String, String> testProps,
            List<String> cmdArgs,
            int timeout,
            TimeoutHandler timeoutHandler,
            TestResult.Section trs)
            throws Fault {
        // Handle the timeout here (instead of in the agent) to make it possible
        // to see the unchanged state of the Agent JVM when the timeout happens.
        TimeoutTask timeoutTask = new TimeoutTask(timeoutHandler);
        if (timeout > 0) {
            timer.schedule(timeoutTask, (long)timeout * 1000);
        }
        keepAlive.setEnabled(false);
        try {
            if (traceAgent)
                System.err.println("Agent.doCompileAction " + testName + " " + cmdArgs);
            synchronized (out) {
                out.writeByte(DO_COMPILE);
                out.writeUTF(testName);
                writeProperties(testProps);
                writeList(cmdArgs);
                out.flush();
            }
            if (traceAgent)
                System.err.println("Agent.doCompileAction: request sent");
            return readResults(trs);
        } catch (IOException e) {
            if (traceAgent)
                System.err.println("Agent.doCompileAction: error " + e);
            if (timeoutTask.hasTimedOut()) {
                throw new Fault(new Exception("Agent timed out with a timeout of "
                    + timeout + " seconds"));
            }
            throw new Fault(e);
        } finally {
            timeoutTask.cancel();
            keepAlive.setEnabled(true);
        }
    }

    public Status doMainAction(
            String testName,
            Map<String, String> testProps,
            SearchPath testClassPath,
            String testClass,
            List<String> testArgs,
            int timeout,
            TimeoutHandler timeoutHandler,
            TestResult.Section trs)
            throws Fault {
        // Handle the timeout here (instead of in the agent) to make it possible
        // to see the unchanged state of the Agent JVM when the timeout happens.
        TimeoutTask timeoutTask = new TimeoutTask(timeoutHandler);
        if (timeout > 0) {
            timer.schedule(timeoutTask, (long)timeout * 1000);
        }
        keepAlive.setEnabled(false);
        try {
            if (traceAgent) {
                System.err.println("Agent.doMainAction " + testName
                        + " " + testClassPath
                        + " " + testClass
                        + " " + testArgs);
            }
            synchronized (out) {
                out.writeByte(DO_MAIN);
                out.writeUTF(testName);
                writeProperties(testProps);
                out.writeUTF(testClassPath.toString());
                out.writeUTF(testClass);
                writeList(testArgs);
                out.flush();
            }
            if (traceAgent)
                System.err.println("Agent.doMainAction: request sent");
            return readResults(trs);
        } catch (IOException e) {
            if (traceAgent)
                System.err.println("Agent.doMainAction: error " + e);
            if (timeoutTask.hasTimedOut()) {
                throw new Fault(new Exception("Agent timed out with a timeout of "
                    + timeout + " seconds"));
            }
            throw new Fault(e);
        } finally {
            timeoutTask.cancel();
            keepAlive.setEnabled(true);
        }
    }


    private class TimeoutTask extends TimerTask {

        TimeoutTask(TimeoutHandler timeoutHandler) {
            if (timeoutHandler == null) {
                throw new NullPointerException("TimeoutHandler is required");
            }
            this.timeoutHandler = timeoutHandler;
        }

        @Override
        public void run() {
            timedOut = true;
            timeoutHandler.handleTimeout(process);
            // close the streams to release us from readResults()
            try {
                out.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            try {
                in.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        public boolean hasTimedOut() {
            return timedOut;
        }

        private boolean timedOut = false;
        private final TimeoutHandler timeoutHandler;
    };

    public void close() {
        if (showAgent || traceAgent)
            System.err.println("Agent[" + id + "]: Closing...");

        keepAlive.finished();

        try {
            out.write(CLOSE); // attempt clean shutdown
            out.close();
        } catch (IOException e) {
            process.destroy(); // force shutdown if necessary
        }

        final int PROCESS_CLOSE_TIMEOUT = 60 * 1000; // 1 minute
        PrintWriter pw = new PrintWriter(System.err, true);
        Alarm alarm = new Alarm(PROCESS_CLOSE_TIMEOUT, "Agent[" + id + "]", pw);
        try {
            int rc = process.waitFor();
            if (rc != 0 || traceAgent)
                System.err.println("Agent[" + id + "]: Exited, process exit code: " + rc);
        } catch (InterruptedException e) {
            if (traceAgent)
                System.err.println("Agent[" + id + "]: Interrupted while closing");
            System.err.println("Agent[" + id + "]: Killing process");
            process.destroy();
        } finally {
            alarm.cancel();
            Thread.interrupted(); // clear any interrupted status
        }


        if (showAgent || traceAgent)
            System.err.println("Agent[" + id + "]: Closed");
    }

    void writeList(List<String> list) throws IOException {
        out.writeShort(list.size());
        for (String s: list)
            out.writeUTF(s);
    }

    static List<String> readList(DataInputStream in) throws IOException {
        int n = in.readShort();
        List<String> l = new ArrayList<String>(n);
        for (int i = 0; i < n; i++)
            l.add(in.readUTF());
        return l;
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

    void writeProperties(Map<String, String> p) throws IOException {
        out.writeShort(p.size());
        for (Map.Entry<String, String> e: p.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeUTF(e.getValue());
        }
    }

    static Map<String, String> readProperties(DataInputStream in) throws IOException {
        int n = in.readShort();
        Map<String, String> p = new HashMap<String, String>(n, 1.0f);
        for (int i = 0; i < n; i++) {
            String key = in.readUTF();
            String value = in.readUTF();
            p.put(key, value);
        }
        return p;
    }

    Status readResults(TestResult.Section trs) throws IOException {
        Map<String, PrintWriter> streams = new HashMap<String, PrintWriter>();
        int op;
        while ((op = in.readByte()) != -1) {
            switch (op) {
                case OUTPUT: {
                    String name = in.readUTF();
                    String data = in.readUTF();
                    if (traceAgent)
                        System.err.println("Agent.readResults: OUTPUT \'" + name + "\' \'" + data + "\"");
                    PrintWriter pw = streams.get(name);
                    if (pw == null) {
                        if (name.equals(Action.OutputHandler.OutputKind.LOG.name))
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
                    if (traceAgent)
                        System.err.println("Agent.readResults: STATUS \'" + type + "\' \'" + reason + "\"");
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

    final JDK jdk;
    final List<String> vmOpts;
    final File scratchDir;
    final Process process;
    final DataInputStream in;
    final DataOutputStream out;
    final KeepAlive keepAlive;
    final int id;

    static int count;
    static java.util.Timer timer = new java.util.Timer("Agent Timeouts", true);

    private static final byte DO_COMPILE = 1;
    private static final byte DO_MAIN = 2;
    private static final byte OUTPUT = 3;
    private static final byte STATUS = 4;
    private static final byte KEEPALIVE = 5;
    private static final byte CLOSE = 6;


    static class Server implements Action.OutputHandler {
        static final String ALLOW_SET_SECURITY_MANAGER = "-allowSetSecurityManager";
        static final String HOST = "-host";
        static final String PORT = "-port";

        Server(String ... args) throws IOException {
            if (traceServer)
                traceOut.println("Agent.Server started");
            boolean allowSetSecurityManagerFlag = false;
            // use explicit localhost to avoid VPN issues
            InetAddress host = InetAddress.getByName("localhost");
            int port = -1;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals(ALLOW_SET_SECURITY_MANAGER)) {
                    allowSetSecurityManagerFlag = true;
                } else if (arg.equals(PORT) && i + 1 < args.length) {
                    port = Integer.valueOf(args[++i]);
                } else if (arg.equals(HOST) && i + 1 < args.length) {
                    host = InetAddress.getByName(args[++i]);
                } else
                    throw new IllegalArgumentException(arg);
            }

            if (port > 0) {
                Socket s = new Socket(host, port);
                s.setSoTimeout(KeepAlive.READ_TIMEOUT);
                in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            } else {
                in = new DataInputStream(new BufferedInputStream(System.in));
                out = new DataOutputStream(new BufferedOutputStream(System.out));
            }
            keepAlive = new KeepAlive(out, traceServer);

            RegressionSecurityManager.install();
            SecurityManager sm = System.getSecurityManager();
            if (sm instanceof RegressionSecurityManager) {
                RegressionSecurityManager rsm = (RegressionSecurityManager) sm;
                rsm.setAllowPropertiesAccess(true);
                if (allowSetSecurityManagerFlag)
                    rsm.setAllowSetSecurityManager(true);
                rsm.setAllowSetIO(true);
            }
        }

        void run() throws IOException {
            try {
                int op;
                while ((op = in.read()) != -1) {
                    switch (op) {
                        case DO_COMPILE:
                            doCompile();
                            break;
                        case DO_MAIN:
                            doMain();
                            break;
                        case KEEPALIVE:
                            break;
                        case CLOSE:
                            return;
                        default:
    //                        Thread.dumpStack();
                            throw new Error("Agent.Server: unexpected op: " + op);
                    }
                    out.flush();
                }
            } finally {
                keepAlive.finished();
            }
        }

        private void doCompile() throws IOException {
            if (traceServer)
                traceOut.println("Agent.Server.doCompile");
            String testName = in.readUTF();
            Map<String, String> testProps = readProperties(in);
            List<String> cmdArgs = readList(in);

            keepAlive.setEnabled(true);
            try {
                Status status = CompileAction.runCompile(
                        testName,
                        testProps,
                        cmdArgs,
                        0,
                        this);

                writeStatus(status);
            } finally {
                keepAlive.setEnabled(false);
            }

            if (traceServer)
                traceOut.println("Agent.Server.doCompile DONE");
        }

        private void doMain() throws IOException {
            if (traceServer)
                traceOut.println("Agent.Server.doMain");
            String testName = in.readUTF();
            Map<String, String> testProps = readProperties(in);
            SearchPath classPath = new SearchPath(in.readUTF());
            String className = in.readUTF();
            List<String> classArgs = readList(in);

            if (traceServer)
                traceOut.println("Agent.Server.doMain: " + testName);

            keepAlive.setEnabled(true);
            try {
                Status status = MainAction.runClass(
                        testName,
                        testProps,
                        classPath,
                        className,
                        classArgs.toArray(new String[classArgs.size()]),
                        0,
                        this);
                writeStatus(status);
            } finally {
                keepAlive.setEnabled(false);
            }

            if (traceServer)
                traceOut.println("Agent.Server.doMain DONE");
        }

        private void writeStatus(Status s) throws IOException {
            if (traceServer)
                traceOut.println("Agent.Server.writeStatus: " + s);
            synchronized (out) {
                out.writeByte(STATUS);
                out.writeByte(s.getType());
                out.writeUTF(s.getReason());
            }
            writers.clear();
        }

        private final KeepAlive keepAlive;
        private final DataInputStream in;
        private final DataOutputStream out;
        private final PrintStream traceOut = System.err;
        private Map<OutputKind, PrintWriter> writers =
                new EnumMap<OutputKind, PrintWriter>(OutputKind.class);

        public PrintWriter createOutput(final OutputKind kind) {
            PrintWriter pw = writers.get(kind);
            if (pw == null) {
                pw = new PrintWriter(new Writer() {
                    @Override
                    public void write(char[] cbuf, int off, int len) throws IOException {
                        if (traceServer)
                            traceOut.println("Agent.Server.write[" + kind + "] " + new String(cbuf, off, len));
                        final int BLOCKSIZE = 4096;
                        while (len > 0) {
                            int n = (len > BLOCKSIZE ? BLOCKSIZE : len);
                            synchronized (out) {
                                out.writeByte(OUTPUT);
                                out.writeUTF(kind.name);
                                out.writeUTF(new String(cbuf, off, n));
                            }
                            off += n;
                            len -= n;
                        }
                        if (traceServer)
                            traceOut.println("Agent.Server.write[" + kind + "]--done");
                    }

                    @Override
                    public void flush() throws IOException {
                        out.flush();
                    }

                    @Override
                    public void close() throws IOException {
                        out.flush();
                    }
                });
                writers.put(kind, pw);
            }
            return pw;
        }

        public void createOutput(OutputKind kind, String output) {
            PrintWriter pw = createOutput(kind);
            pw.write(output);
            pw.close();
        }
    }

    /**
     * Send KEEPALIVE bytes periodically to a stream.
     * The bytes are written every {@code WRITE_TIMEOUT} milliseconds.
     * The client reading the stream may use {@code READ_TIMEOUT} as a
     * corresponding timeout to determine if the sending has stopped
     * sending KEEPALIVE bytes.
     */
    static class KeepAlive {
        static final int WRITE_TIMEOUT = 60 * 1000; // 1 minute
        static final int READ_TIMEOUT = 2 * WRITE_TIMEOUT;

        KeepAlive(DataOutputStream out, boolean trace) {
            this.out = out;
            this.trace = trace;
        }

        synchronized void setEnabled(boolean on) {
            if (entry != null)
                timer.cancel(entry);
            if (on) {
                entry = timer.requestDelayedCallback(ping, WRITE_TIMEOUT);
            } else {
                entry = null;
            }
        }

        synchronized void finished() {
            setEnabled(false);
            timer.finished();
        }

        final Timer timer = new Timer();
        final DataOutputStream out;

        final Timer.Timeable ping = new Timer.Timeable() {
            public void timeout() {
                try {
                    synchronized (out) {
                        if (trace)
                            traceOut.println("KeepAlive.ping");
                        out.writeByte(KEEPALIVE);
                        out.flush();
                    }
                    setEnabled(true);
                } catch (IOException e) {
                }
            }
        };

        Timer.Entry entry;
        final PrintStream traceOut = System.err;
        final boolean trace;
    }

    /**
     * A reusable collection of JVMs with varying VM options.
     */
    static class Pool {
        private static Pool instance;

        static synchronized Pool instance() {
            if (instance == null)
                instance = new Pool();
            return instance;
        }

        private Pool() {
            map = new HashMap<String, Queue<Agent>>();
        }

        void setSecurityPolicy(File policyFile) {
            this.policyFile = policyFile;
        }

        synchronized Agent getAgent(File dir, JDK jdk, List<String> vmOpts, Map<String, String> envVars) throws IOException {
            Queue<Agent> agents = map.get(getKey(dir, jdk, vmOpts));
            Agent a = (agents == null) ? null : agents.poll();
            if (a == null) {
                a = new Agent(dir, jdk, vmOpts, envVars, policyFile);
            }
            return a;
        }

        synchronized void save(Agent agent) {
            String key = getKey(agent.scratchDir, agent.jdk, agent.vmOpts);
            Queue<Agent> agents = map.get(key);
            if (agents == null)
                map.put(key, agents = new LinkedList<Agent>());
            agents.add(agent);
        }

        synchronized void flush() {
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

        private Map<String, Queue<Agent>> map;
        private File policyFile;
    }
}
