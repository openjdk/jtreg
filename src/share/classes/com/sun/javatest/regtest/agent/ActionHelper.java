/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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


import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import com.sun.javatest.Status;
import com.sun.javatest.TestResult;

import static com.sun.javatest.regtest.agent.RStatus.error;
import static com.sun.javatest.regtest.agent.RStatus.passed;

public class ActionHelper {

    // <editor-fold defaultstate="collapsed" desc=" Save State ">
    /**
     * SaveState captures important system state, such as the security manager,
     * standard IO streams and system properties, and provides a way to
     * subsequently restore that state.
     */
    static class SaveState {

        SaveState() {
            if (sysProps == null) {
                sysProps = copyProperties(System.getProperties());
            }

            // Save and setup streams for the test
            stdOut = System.out;
            stdErr = System.err;

            // Default Locale
            locale = Locale.getDefault();

            // Save security manager in case changed by test
            secMgr = System.getSecurityManager();

            // If using default security manager, allow props access, and reset dirty bit
            if (secMgr instanceof RegressionSecurityManager) {
                RegressionSecurityManager rsm = (RegressionSecurityManager) secMgr;
                rsm.setAllowPropertiesAccess(true);
                rsm.resetPropertiesModified();
            }

            securityProviders = Security.getProviders();
        }

        Status restore(String testName, Status status) {
            Status cleanupStatus = null;

            // Reset security manager, if necessary
            // Do this first, to ensure we reset permissions
            try {
                if (System.getSecurityManager() != secMgr) {
                    AccessController.doPrivileged(new PrivilegedAction<Object>() {
                        @Override
                        public Object run() {
                            System.setSecurityManager(secMgr);
                            return null;
                        }
                    });
                    //System.setSecurityManager(secMgr);
                }
            } catch (SecurityException e) {
                // If we cannot reset the security manager, we might not be able to do
                // much at all -- such as write files.  So, be very noisy to the
                // primary system error stream about this badly behaved test.
                stdErr.println();
                stdErr.println("***");
                stdErr.println("*** " + testName);
                stdErr.println("*** Cannot reset security manager after test");
                stdErr.println("*** " + e.getMessage());
                stdErr.println("***");
                stdErr.println();
                cleanupStatus = error(SAMEVM_CANT_RESET_SECMGR + ": " + e);
            }

            try {
                final Provider[] sp = Security.getProviders();
                if (!equal(securityProviders, sp)) {
                    AccessController.doPrivileged(new PrivilegedAction<Object>() {
                        @Override
                        public Object run() {
                            for (Provider p : sp) {
                                Security.removeProvider(p.getName());
                            }
                            for (Provider p : securityProviders) {
                                Security.addProvider(p);
                            }
                            return null;
                        }
                    });
                }
            } catch (SecurityException e) {
                cleanupStatus = error(SAMEVM_CANT_RESET_SECPROVS + ": " + e);
            }

            // Reset system properties, if necessary
            // The default security manager tracks whether system properties may have
            // been written: if so, we reset all the system properties, otherwise
            // we just reset important props that were written in the test setup
            boolean resetAllSysProps;
            SecurityManager sm = System.getSecurityManager();
            if (sm instanceof RegressionSecurityManager) {
                resetAllSysProps = ((RegressionSecurityManager) sm).isPropertiesModified();
            } else {
                resetAllSysProps = true;
            }
            try {
                if (resetAllSysProps) {
                    System.setProperties(newProperties(sysProps));
                    //                    System.err.println("reset properties");
                } else {
                    System.setProperty("java.class.path", (String) sysProps.get("java.class.path"));
                    //                    System.err.println("no need to reset properties");
                }
            } catch (SecurityException e) {
                if (cleanupStatus == null) {
                    cleanupStatus = error(SAMEVM_CANT_RESET_PROPS + ": " + e);
                }
            }

            // Reset output streams
            Status stat = redirectOutput(stdOut, stdErr);
            if (cleanupStatus == null && !stat.isPassed()) {
                cleanupStatus = stat;
            }

            // Reset locale
            if (locale != Locale.getDefault()) {
                Locale.setDefault(locale);
            }

            return (cleanupStatus != null ? cleanupStatus : status);
        }

        final SecurityManager secMgr;
        final PrintStream stdOut;
        final PrintStream stdErr;
        final Locale locale;
        final Provider[] securityProviders;
        static Map<?, ?> sysProps;
    }

    private static <T> boolean equal(T[] a, T[] b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    //----------for saving/restoring properties---------------------------------

    private static Map<?, ?> copyProperties(Properties p) {
        Map<Object, Object> h = new HashMap<Object, Object>();
        for (Enumeration<?> e = p.propertyNames(); e.hasMoreElements(); ) {
            Object key = e.nextElement();
            h.put(key, p.get(key));
        }
        return h;
    }

    private static Properties newProperties(Map<?, ?> h) {
        Properties p = new Properties();
        p.putAll(h);
        return p;
    }

    // </editor-fold>

    //----------output handler--------------------------------------------------

    /**
     * OutputHandler provides an abstract way to get the streams used to record
     * the output from an action of a test.
     */
    public interface OutputHandler {
        public enum OutputKind {
            LOG(""),
            STDOUT("System.out"),
            STDERR("System.err"),
            DIRECT("direct"),
            DIRECT_LOG("direct.log");
            OutputKind(String name) { this.name = name; }
            public final String name;
        };
        PrintWriter createOutput(OutputKind kind);
        void createOutput(OutputKind kind, String output);
    }

    protected static OutputHandler getOutputHandler(final TestResult.Section section) {
        return new OutputHandler() {
            @Override
            public PrintWriter createOutput(OutputKind kind) {
                if (kind == OutputKind.LOG)
                    return section.getMessageWriter();
                else
                    return section.createOutput(kind.name);
            }

            @Override
            public void createOutput(OutputKind kind, String output) {
                PrintWriter pw = createOutput(kind);
                try {
                    pw.write(output);
                } finally {
                    pw.close();
                }
            }
        };
    }
    //----------in memory streams-----------------------------------------------

    public static class PrintByteArrayOutputStream extends PrintStream {
        public PrintByteArrayOutputStream() {
            super(new ByteArrayOutputStream());
            s = (ByteArrayOutputStream) out;
        }

        public String getOutput() {
            return s.toString();
        }

        private final ByteArrayOutputStream s;
    }

    public static class PrintStringWriter extends PrintWriter {
        public PrintStringWriter() {
            super(new StringWriter());
            w = (StringWriter) out;
        }

        public String getOutput() {
            return w.toString();
        }

        private final StringWriter w;
    }

    //----------redirect streams------------------------------------------------

    // if we wanted to allow more concurrency, we could try and acquire a lock here
    protected static Status redirectOutput(PrintStream out, PrintStream err) {
        synchronized (System.class) {
            SecurityManager sc = System.getSecurityManager();
            if (sc instanceof RegressionSecurityManager) {
                boolean prev = ((RegressionSecurityManager) sc).setAllowSetIO(true);
                System.setOut(out);
                System.setErr(err);
                ((RegressionSecurityManager) sc).setAllowSetIO(prev);
            } else {
                //return Status.error(MAIN_SECMGR_BAD);
            }
        }
        return passed("OK");
    } // redirectOutput()

    protected static final String
        EXEC_ERROR_CLEANUP    = "Error while cleaning up threads after test",
        EXEC_PASS             = "Execution successful",

        UNEXPECT_SYS_EXIT     = "Unexpected exit from test",

        SAMEVM_CANT_RESET_SECMGR   = "Cannot reset security manager",
        SAMEVM_CANT_RESET_SECPROVS = "Cannot reset security providers",
        SAMEVM_CANT_RESET_PROPS    = "Cannot reset system properties";
}
