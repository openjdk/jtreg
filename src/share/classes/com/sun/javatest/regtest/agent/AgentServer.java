/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.agent;

import com.sun.javatest.Status;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public class AgentServer implements ActionHelper.OutputHandler {

    /**
     * Main program used to invoke and run the server in child JVMs
     */
    public static void main(String... args) {
        if (traceServer)
            System.err.println("AgentServer.main");

        try {
            new AgentServer(args).run();
        } catch (Throwable e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public static final boolean traceServer = Flags.get("traceServer");

    public static final String ALLOW_SET_SECURITY_MANAGER = "-allowSetSecurityManager";
    public static final String HOST = "-host";
    public static final String PORT = "-port";

    public static final byte DO_COMPILE = 1;
    public static final byte DO_MAIN = 2;
    public static final byte OUTPUT = 3;
    public static final byte STATUS = 4;
    public static final byte KEEPALIVE = 5;
    public static final byte CLOSE = 6;

    /**
     * Send KEEPALIVE bytes periodically to a stream.
     * The bytes are written every {@code WRITE_TIMEOUT} milliseconds.
     * The client reading the stream may use {@code READ_TIMEOUT} as a
     * corresponding timeout to determine if the sending has stopped
     * sending KEEPALIVE bytes.
     */
    public static class KeepAlive {
        public static final int WRITE_TIMEOUT = 60 * 1000; // 1 minute
        public static final int READ_TIMEOUT = 2 * WRITE_TIMEOUT;

        public KeepAlive(DataOutputStream out, boolean trace) {
            this.out = out;
            this.trace = trace;
        }

        public synchronized void setEnabled(boolean on) {
            alarm.cancel();
            if (on) {
                alarm = Alarm.schedule(WRITE_TIMEOUT, TimeUnit.MILLISECONDS, null, ping);
            } else {
                alarm = Alarm.NONE;
            }
        }

        public synchronized void finished() {
            setEnabled(false);
        }

        final DataOutputStream out;

        final Runnable ping = new Runnable() {
            public void run() {
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

        Alarm alarm = Alarm.NONE;
        final PrintStream traceOut = System.err;
        final boolean trace;
    }


    public AgentServer(String... args) throws IOException {
        if (traceServer) {
            traceOut.println("Agent.Server started");
        }
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
            } else {
                throw new IllegalArgumentException(arg);
            }
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
            if (allowSetSecurityManagerFlag) {
                rsm.setAllowSetSecurityManager(true);
            }
            rsm.setAllowSetIO(true);
        }
    } // use explicit localhost to avoid VPN issues

    public void run() throws IOException {
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
        if (traceServer) {
            traceOut.println("Agent.Server.doCompile");
        }
        String testName = in.readUTF();
        Map<String, String> testProps = readMap(in);
        List<String> cmdArgs = readList(in);
        keepAlive.setEnabled(true);
        try {
            Status status = CompileActionHelper.runCompile(testName, testProps, cmdArgs, 0, this);
            writeStatus(status);
        } finally {
            keepAlive.setEnabled(false);
        }
        if (traceServer) {
            traceOut.println("Agent.Server.doCompile DONE");
        }
    }

    private void doMain() throws IOException {
        if (traceServer) {
            traceOut.println("Agent.Server.doMain");
        }
        String testName = in.readUTF();
        Map<String, String> testProps = readMap(in);
        Set<String> addExports = readSet(in);
        SearchPath classPath = new SearchPath(in.readUTF());
        String className = in.readUTF();
        List<String> classArgs = readList(in);
        if (traceServer) {
            traceOut.println("Agent.Server.doMain: " + testName);
        }
        keepAlive.setEnabled(true);
        try {
            Status status = MainActionHelper.runClass(
                    testName,
                    testProps,
                    addExports,
                    classPath,
                    className,
                    classArgs.toArray(new String[classArgs.size()]), 0, this);
            writeStatus(status);
        } finally {
            keepAlive.setEnabled(false);
        }
        if (traceServer) {
            traceOut.println("Agent.Server.doMain DONE");
        }
    }

    static List<String> readList(DataInputStream in) throws IOException {
        int n = in.readShort();
        List<String> l = new ArrayList<String>(n);
        for (int i = 0; i < n; i++)
            l.add(in.readUTF());
        return l;
    }

    static Set<String> readSet(DataInputStream in) throws IOException {
        int n = in.readShort();
        Set<String> s = new LinkedHashSet<String>(n);
        for (int i = 0; i < n; i++)
            s.add(in.readUTF());
        return s;
    }

    static Map<String, String> readMap(DataInputStream in) throws IOException {
        int n = in.readShort();
        Map<String, String> p = new HashMap<String, String>(n, 1.0f);
        for (int i = 0; i < n; i++) {
            String key = in.readUTF();
            String value = in.readUTF();
            p.put(key, value);
        }
        return p;
    }

    private void writeStatus(Status s) throws IOException {
        if (traceServer) {
            traceOut.println("Agent.Server.writeStatus: " + s);
        }
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
    private final Map<OutputKind, PrintWriter> writers = new EnumMap<OutputKind, PrintWriter>(OutputKind.class);

    public PrintWriter createOutput(final OutputKind kind) {
        PrintWriter pw = writers.get(kind);
        if (pw == null) {
            pw = new PrintWriter(new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) throws IOException {
                    if (traceServer) {
                        traceOut.println("Agent.Server.write[" + kind + "] " + new String(cbuf, off, len));
                    }
                    final int BLOCKSIZE = 4096;
                    while (len > 0) {
                        int n = len > BLOCKSIZE ? BLOCKSIZE : len;
                        synchronized (out) {
                            out.writeByte(OUTPUT);
                            out.writeUTF(kind.name);
                            out.writeUTF(new String(cbuf, off, n));
                        }
                        off += n;
                        len -= n;
                    }
                    if (traceServer) {
                        traceOut.println("Agent.Server.write[" + kind + "]--done");
                    }
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
