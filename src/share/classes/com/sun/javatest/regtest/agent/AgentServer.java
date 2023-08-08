/*
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


@SuppressWarnings("removal") // Security Manager and related APIs
public final class AgentServer implements ActionHelper.OutputHandler {

    /**
     * Main program used to invoke and run the server in child JVMs
     * @param args command-line arguments, used to configure the server
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
    public static final String ID = "-id";
    public static final String LOGFILE = "-logfile";
    public static final String HOST = "-host";
    public static final String PORT = "-port";
    public static final String TIMEOUTFACTOR = "-timeoutFactor";
    public static final String CUSTOM_TEST_THREAD_FACTORY = "-testThreadFactory";
    public static final String CUSTOM_TEST_THREAD_FACTORY_PATH = "-testThreadFactoryPath";

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
            @Override
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

    private float timeoutFactor = 1.0f;
    private String testThreadFactory;
    private String testThreadFactoryPath;

    public AgentServer(String... args) throws IOException {
        if (traceServer) {
            traceOut.println("Agent.Server started");
        }
        boolean allowSetSecurityManagerFlag = false;
        // use explicit localhost to avoid VPN issues
        InetAddress host = InetAddress.getByName("localhost");
        int id = 0;
        int port = -1;
        File logFile = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals(ID)) {
                try {
                    id = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    id = 0;
                }
            } else if (arg.equals(LOGFILE)) {
                logFile = new File(args[++i]);
            } else if (arg.equals(ALLOW_SET_SECURITY_MANAGER)) {
                allowSetSecurityManagerFlag = true;
            } else if (arg.equals(PORT) && i + 1 < args.length) {
                port = Integer.valueOf(args[++i]);
            } else if (arg.equals(HOST) && i + 1 < args.length) {
                host = InetAddress.getByName(args[++i]);
            } else if (arg.equals(TIMEOUTFACTOR) && i + 1 < args.length) {
                timeoutFactor = Float.valueOf(args[++i]);
            } else if (arg.equals(CUSTOM_TEST_THREAD_FACTORY) && i + 1 < args.length) {
                testThreadFactory = args[++i];
            } else if (arg.equals(CUSTOM_TEST_THREAD_FACTORY_PATH) && i + 1 < args.length) {
                testThreadFactoryPath = args[++i];
        }   else {
                throw new IllegalArgumentException(arg);
            }
        }

        this.id = id;

        PrintWriter pw = null;
        if (logFile != null) {
            try {
                pw = new PrintWriter(new FileWriter(logFile));
            } catch (IOException e) {
                traceOut.println("Cannot open log writer: " + e);
                pw = new PrintWriter(System.err) {
                    @Override
                    public void close() {
                        flush();
                    }
                };
            }
        }
        logWriter = pw;
        log("Started");

        if (port > 0) {
            Socket s = new Socket(host, port);
            s.setSoTimeout((int)(KeepAlive.READ_TIMEOUT * timeoutFactor));
            in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            log("Listening on port " + port);
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
    }

    public void run() throws IOException {
        log("Running");
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
                        // Thread.dumpStack();
                        throw new Error("Agent.Server: unexpected op: " + op);
                }
                out.flush();
            }
        } finally {
            keepAlive.finished();
            log("Exiting");
            logWriter.close();
        }
    }

    private void doCompile() throws IOException {
        if (traceServer) {
            traceOut.println("Agent.Server.doCompile");
        }
        // See corresponding list in Agent.doCompile
        String testName = in.readUTF();
        Map<String, String> testProps = readMap(in);
        List<String> cmdArgs = readList(in);
        log(testName + ": starting compilation");
        keepAlive.setEnabled(true);
        try {
            AStatus status = CompileActionHelper.runCompile(testName, testProps, cmdArgs, 0, this);
            writeStatus(status);
        } finally {
            keepAlive.setEnabled(false);
            log(testName + ": finished compilation");
        }
        if (traceServer) {
            traceOut.println("Agent.Server.doCompile DONE");
        }
    }

    private void doMain() throws IOException {
        if (traceServer) {
            traceOut.println("Agent.Server.doMain");
        }
        // See corresponding list in Agent.doMainAction
        String testName = in.readUTF();
        Map<String, String> testProps = readMap(in);
        Set<String> addExports = readSet(in);
        Set<String> addOpens = readSet(in);
        Set<String> addMods = readSet(in);
        SearchPath classPath = new SearchPath(in.readUTF());
        SearchPath modulePath = new SearchPath(in.readUTF());
        String className = in.readUTF();
        List<String> classArgs = readList(in);
        if (traceServer) {
            traceOut.println("Agent.Server.doMain: " + testName);
        }
        log(testName + ": starting execution of " + className);
        keepAlive.setEnabled(true);
        try {
            AStatus status = new MainActionHelper(testName)
                    .properties(testProps)
                    .addExports(addExports)
                    .addOpens(addOpens)
                    .addMods(addMods)
                    .classpath(classPath)
                    .modulepath(modulePath)
                    .className(className)
                    .classArgs(classArgs)
                    .timeout(0)
                    .timeoutFactor(timeoutFactor)
                    .testThreadFactory(testThreadFactory)
                    .testThreadFactoryPath(testThreadFactoryPath)
                    .outputHandler(this)
                    .runClass();
            writeStatus(status);
        } finally {
            keepAlive.setEnabled(false);
            log(testName + ": finished execution of " + className);
        }
        if (traceServer) {
            traceOut.println("Agent.Server.doMain DONE");
        }
    }

    static List<String> readList(DataInputStream in) throws IOException {
        int n = in.readShort();
        List<String> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            l.add(in.readUTF());
        return l;
    }

    static Set<String> readSet(DataInputStream in) throws IOException {
        int n = in.readShort();
        Set<String> s = new LinkedHashSet<>(n);
        for (int i = 0; i < n; i++)
            s.add(in.readUTF());
        return s;
    }

    static Map<String, String> readMap(DataInputStream in) throws IOException {
        int n = in.readShort();
        Map<String, String> p = new HashMap<>(n, 1.0f);
        for (int i = 0; i < n; i++) {
            String key = in.readUTF();
            String value = in.readUTF();
            p.put(key, value);
        }
        return p;
    }

    private void writeStatus(AStatus s) throws IOException {
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

    // This format is also used by Agent.java in the client-side log messages.
    // The format is like this:
    //     2016-12-21 13:19:46,998
    // It is "sort-friendly" so that the lines in all the logs for a test run
    // can be merged and sorted into a single log.
    public static final SimpleDateFormat logDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");

    void log(String message) {
        logWriter.printf("[%s] AgentServer[%d]: %s%n",
                AgentServer.logDateFormat.format(new Date()),
                id,
                message);
        logWriter.flush();
    }

    private final KeepAlive keepAlive;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final PrintStream traceOut = System.err;
    private final PrintWriter logWriter;
    private final int id;
    private final Map<OutputKind, Writer> writers = new EnumMap<>(OutputKind.class);

    /**
     * Create an output stream for output to be sent back to the client via the server connection.
     * @param kind the kind of stream
     * @return the output stream
     */
    public PrintWriter getPrintWriter(OutputKind kind, boolean autoFlush) {
        return new PrintWriter(getWriter(kind), autoFlush);
    }

    private Writer getWriter(final OutputKind kind) {
        Writer w = writers.get(kind);
        if (w == null) {
            w = new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) throws IOException {
                    if (traceServer) {
                        traceOut.println("Agent.Server.write[" + kind + ",writer] " + new String(cbuf, off, len));
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
                        traceOut.println("Agent.Server.write[" + kind + ",writer]--done");
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
            };
            writers.put(kind, w);
        }
        return w;
    }

    /**
     * Create an output stream for output to be sent back to the client via the server connection,
     * and use it to write the given content.
     * @param kind the kind of stream
     * @param autoFlush whether or not to flush the stream on '\n'
     */
    public PrintStream getPrintStream(OutputKind kind, boolean autoFlush) {
        return new PrintStream(getOutputStream(kind), autoFlush);
    }

    private OutputStream getOutputStream(final OutputKind kind) {
        final Writer w = getWriter(kind);
        return new OutputStream() {
            private static final int BUFSIZE = 1024;
            private ByteBuffer byteBuffer = ByteBuffer.allocate(BUFSIZE);
            private CharBuffer charBuffer = CharBuffer.allocate(BUFSIZE);
            private CharsetDecoder decoder = Charset.forName(
                    System.getProperty("sun.stdout.encoding", System.getProperty("sun.jnu.encoding",
                        Charset.defaultCharset().name())))
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

            @Override
            public void write(byte[] bytes, int off, int len) throws IOException {
                if (traceServer) {
                    traceOut.println("Agent.Server.write[" + kind + ",stream] " + new String(bytes, off, len));
                }
                int n;
                while (len > 0 && len >= (n = byteBuffer.remaining())) {
                    byteBuffer.put(bytes, off, n);
                    decode();
                    off += n;
                    len -= n;
                }
                byteBuffer.put(bytes, off, len);
                if (traceServer) {
                    traceOut.println("Agent.Server.write[" + kind + ",stream]--done");
                }
            }

            @Override
            public void write(int b) throws IOException {
                byteBuffer.put((byte) b);
                if (!byteBuffer.hasRemaining()) {
                    decode();
                }
            }

            @Override
            public void flush() throws IOException {
                decode();
                // let any content that has been decoded into the charBuffer be written out
                // to the writer so that the writer can then flush it to underlying stream
                writeCharBuffer();
                w.flush();
            }

            @Override
            public void close() throws IOException {
                decode();
                byteBuffer.flip();
                decoder.decode(byteBuffer, charBuffer, true);
                writeCharBuffer();
                w.flush();
            }

            private void decode() throws IOException {
                byteBuffer.flip();
                CoderResult cr;
                // The decoder has been configured to replace unmappable character and
                // malformed input, so this decoder.decode() will only report either
                // UNDERFLOW or OVERFLOW.
                // We transfer the decoded content in charBuffer to the writer only when the
                // charBuffer is full. i.e. the decoder.decode() reports OVERFLOW. In the case of
                // UNDERFLOW, we keep the decoded content in the charBuffer, until either this
                // OutputStream instance is flushed or additional data is written into this
                // OutputStream and the resultant decode() operation results in an OVERFLOW
                while ((cr = decoder.decode(byteBuffer, charBuffer, false)) != CoderResult.UNDERFLOW) {
                    writeCharBuffer();
                }
                byteBuffer.compact();
            }

            private void writeCharBuffer() throws IOException {
                charBuffer.flip();
                w.write(charBuffer.toString());
                charBuffer.clear();
            }
        };
    }
}
