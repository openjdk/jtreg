/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.sun.javatest.regtest.agent.ActionHelper.EXEC_PASS;
import static com.sun.javatest.regtest.agent.AStatus.error;
import static com.sun.javatest.regtest.agent.AStatus.failed;
import static com.sun.javatest.regtest.agent.AStatus.passed;

public class MainActionHelper extends ActionHelper {

    public static AStatus runClass(
            String testName,
            Map<String, String> props,
            Set<String> addExports,
            Set<String> addOpens,
            SearchPath classpath,
            String classname,
            String[] classArgs,
            int timeout,
            OutputHandler outputHandler) {
        SaveState saved = new SaveState();

        Properties p = System.getProperties();
        for (Map.Entry<String, String> e : props.entrySet()) {
            String name = e.getKey();
            String value = e.getValue();
            if (name.equals("test.class.path.prefix")) {
                SearchPath cp = new SearchPath(value, System.getProperty("java.class.path"));
                p.put("java.class.path", cp.toString());
            } else {
                p.put(e.getKey(), e.getValue());
            }
        }
        System.setProperties(p);

        PrintByteArrayOutputStream out = new PrintByteArrayOutputStream();
        PrintByteArrayOutputStream err = new PrintByteArrayOutputStream();

        AStatus status = passed(EXEC_PASS);
        try {
            Class<?> c;
            ClassLoader loader;
            if (classpath != null) {
                List<URL> urls = new ArrayList<URL>();
                for (File f : new SearchPath(classpath).asList()) {
                    try {
                        urls.add(f.toURI().toURL());
                    } catch (MalformedURLException e) {
                    }
                }
                loader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
                ModuleHelper.addExports(addExports, loader);
                ModuleHelper.addOpens(addOpens, loader);
                c = loader.loadClass(classname);
            } else {
                loader = null;
                c = Class.forName(classname);
            }

            // Select signature for main method depending on whether the class
            // implements the TestRunner marker interface.
            Class<?>[] argTypes;
            Object[] methodArgs;
            if (TestRunner.class.isAssignableFrom(c)) {
                // Marker interface found: use main(ClassLoader, String...)
                argTypes = new Class<?>[]{ClassLoader.class, String[].class};
                methodArgs = new Object[]{loader, classArgs};
            } else {
                // Normal case: marker interface not found; use standard main method
                argTypes = new Class<?>[]{String[].class};
                methodArgs = new Object[]{classArgs};
            }

            Method method = c.getMethod("main", argTypes);

            PrintStream realStdErr = System.err;
            AStatus stat = redirectOutput(out, err);
            if (!stat.isPassed()) {
                return stat;
            }

            // RUN JAVA IN ANOTHER THREADGROUP
            AgentVMThreadGroup tg = new AgentVMThreadGroup(err, MSG_PREFIX);
            AgentVMRunnable avmr = new AgentVMRunnable(method, methodArgs, err);
            Thread t = new Thread(tg, avmr, "AgentVMThread");
            Alarm alarm = null;
            if (timeout > 0) {
                PrintWriter alarmOut = outputHandler.createOutput(OutputHandler.OutputKind.LOG);
                alarm = Alarm.schedulePeriodicInterrupt(timeout, TimeUnit.SECONDS, alarmOut, t);
            }
            Throwable error = null;
            t.start();
            try {
                t.join();

                if (traceCleanup) {
                    realStdErr.println("main method returned");
                }
            } catch (InterruptedException e) {
                realStdErr.println("main method interrupted");
                if (t.isInterrupted() && (tg.uncaughtThrowable == null)) {
                    error = e;
                    status = error(MAIN_THREAD_INTR + e.getMessage());
                }
            } finally {
                if (traceCleanup) {
                    realStdErr.println("cleaning threads");
                }

                tg.cleanup();
                if (traceCleanup) {
                    realStdErr.println("thread cleanup completed");
                }

                if (alarm != null) {
                    alarm.cancel();
                    if (alarm.didFire() && error == null) {
                        err.println("Test timed out. No timeout information is available in agentvm mode.");
                        error = new Error("timeout");
                        status = error(MAIN_THREAD_TIMEOUT);
                    }
                }
            }

            if (((avmr.t != null) || (tg.uncaughtThrowable != null)) && (error == null)) {
                if (avmr.t == null) {
                    error = tg.uncaughtThrowable;
                } else {
                    error = avmr.t;
                }
                status = failed(MAIN_THREW_EXCEPT + error.toString());
            }

            if (status.getReason().contains("java.lang.SecurityException: System.exit() forbidden")) {
                status = failed(UNEXPECT_SYS_EXIT);
            } else if (!tg.cleanupOK) {
                status = error(EXEC_ERROR_CLEANUP);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace(err);
            err.println();
            err.println(MSG_PREFIX + "main() method must be in a public class named");
            err.println(MSG_PREFIX + classname + " in file " + classname + ".java");
            err.println();
            status = error(MAIN_CANT_LOAD_TEST + e);
        } catch (NoSuchMethodException e) {
            e.printStackTrace(err);
            err.println();
            err.println(MSG_PREFIX + "main() method must be in a public class named");
            err.println(MSG_PREFIX + classname + " in file " + classname + ".java");
            err.println();
            status = error(MAIN_CANT_FIND_MAIN);
        } catch (ModuleHelper.Fault e) {
            if (e.getCause() != null)
                e.printStackTrace(err);
            status = error(MAIN_CANT_INIT_MODULE_EXPORTS + e.getMessage());
        } finally {
            // Write test output
            out.close();
            outputHandler.createOutput(OutputHandler.OutputKind.STDOUT, out.getOutput());

            err.close();
            outputHandler.createOutput(OutputHandler.OutputKind.STDERR, err.getOutput());

            status = saved.restore(testName, status);
        }


        return status;
    }

    private static final boolean traceCleanup = Flags.get("traceCleanup");
    private static final String MSG_PREFIX = "JavaTest Message: ";

    private static final String
        //    runAgentJVM
        MAIN_THREAD_INTR      = "Thread interrupted: ",
        MAIN_THREAD_TIMEOUT   = "Timeout",
        MAIN_THREW_EXCEPT     = "`main' threw exception: ",
        MAIN_CANT_LOAD_TEST   = "Can't load test: ",
        MAIN_CANT_FIND_MAIN   = "Can't find `main' method",
        MAIN_CANT_INIT_MODULE_EXPORTS = "Can't init module exports: ";

    /**
     * Marker interface for test driver classes, which need to be passed a class
     * loader to load the classes for the test.
     *
     * @see JUnitRunner
     */
    public interface TestRunner {
    }

    //----------internal classes------------------------------------------------

    private static class AgentVMRunnable implements Runnable
    {
        public AgentVMRunnable(Method m, Object[] args, PrintStream out) {
            method    = m;
            this.args = args;
            this.out  = out;
        } // SameVMRunnable()

        @Override
        public void run() {
            try {
                // RUN JAVA PROGRAM
                result = method.invoke(null, args);

                out.println();
                out.println(MSG_PREFIX + "Test complete.");
                out.println();
            } catch (InvocationTargetException e) {
                // main must have thrown an exception, so the test failed
                e.getTargetException().printStackTrace(out);
                t = e.getTargetException();
                out.println();
                out.println(MSG_PREFIX + "Test threw exception: " + t.getClass().getName());
                out.println(MSG_PREFIX + "shutting down test");
                out.println();
            } catch (IllegalAccessException e) {
                e.printStackTrace(out);
                t = e;
                out.println();
                out.println(MSG_PREFIX + "Verify that the class defining the test is");
                out.println(MSG_PREFIX + "declared public (test invoked via reflection)");
                out.println();
            }
        } // run()

        //----------member variables--------------------------------------------

        public  Object result;
        private final Method method;
        private final Object[] args;
        private final PrintStream out;

        Throwable t = null;
    }

    static class AgentVMThreadGroup extends ThreadGroup
    {
        AgentVMThreadGroup(PrintStream out, String messagePrefix) {
            super("AgentVMThreadGroup");
            this.out = out;
            this.messagePrefix = messagePrefix;
        }

        @Override
        public synchronized void uncaughtException(Thread t, Throwable e) {
            if (e instanceof ThreadDeath)
                return;
            if ((uncaughtThrowable == null) && (!cleaning)) {
                uncaughtThrowable = e;
                uncaughtThread    = t;
            }
            cleanup();
        }

        private void cleanup() {
            cleaning = true;

            final int CLEANUP_ROUNDS = 4;
//            final long MAX_CLEANUP_TIME_MILLIS = 2 * 60 * 1000;
            final long MAX_CLEANUP_TIME_MILLIS = 10 * 1000;
            final long CLEANUP_MILLIS_PER_ROUND = MAX_CLEANUP_TIME_MILLIS / CLEANUP_ROUNDS;
            final long NANOS_PER_MILLI = 1000L * 1000L;

            long startCleanupTime = System.nanoTime();

            for (int i = 1; i <= CLEANUP_ROUNDS; i++) {
                long deadline = startCleanupTime + i * CLEANUP_MILLIS_PER_ROUND * NANOS_PER_MILLI;
                List<Thread> liveThreads = liveThreads();
                if (liveThreads.isEmpty()) {
                    // nothing left to cleanup
                    cleanupOK = true;
                    return;
                }

                // kick the remaining live threads
                for (Thread thread : liveThreads)
                    thread.interrupt();

                // try joining as many threads as possible before
                // the round times out
                for (Thread thread : liveThreads) {
                    long millis = (deadline - System.nanoTime()) / NANOS_PER_MILLI;
                    if (millis <= 0)
                        break;
                    try {
                        thread.join(millis);
                    } catch (InterruptedException ignore) {
                    }
                }
            }

            List<Thread> remaining = liveThreads();
            if (remaining.isEmpty()) {
                // nothing left to cleanup
                cleanupOK = true;
                return;
            }

            out.println();
            out.println(messagePrefix + "Problem cleaning up the following threads:");
            printTraces(remaining);
            cleanupOK = false;
        } // cleanup()

        /**
         * Gets all the "interesting" threads in the thread group.
         * @see ThreadGroup#enumerate(Thread[])
         */
        private List<Thread> liveThreads() {
            for (int estSize = activeCount() + 1; ; estSize = estSize * 2) {
                Thread[] threads = new Thread[estSize];
                int num = enumerate(threads);
                if (num < threads.length) {
                    ArrayList<Thread> list = new ArrayList<Thread>(num);
                    for (int i = 0; i < num; i++) {
                        Thread t = threads[i];
                        if (t.isAlive()
                                && t != Thread.currentThread()
                                && !t.isDaemon()) {
                            list.add(t);
                        }
                    }
                    return list;
                }
            }
        }

        private void printTraces(List<Thread> threads) {
            final int MAX_FRAMES = 20;

            for (Thread t : threads) {
                out.println(t.getName());
                StackTraceElement[] trace = t.getStackTrace();
                for (int i = 0; i < trace.length; i++) {
                    out.println("  at " + trace[i]);
                    if (i == MAX_FRAMES) {
                        out.println("  ...");
                        break;
                    }
                }
                out.println();
            }
        }

        //----------member variables--------------------------------------------

        private final PrintStream out;
        private final String messagePrefix;

        private boolean cleaning   = false;
        Throwable uncaughtThrowable = null;
        Thread    uncaughtThread    = null;
        boolean cleanupOK = false;

    }

}
