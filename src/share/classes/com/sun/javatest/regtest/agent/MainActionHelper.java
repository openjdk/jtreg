/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.sun.javatest.regtest.agent.AStatus.error;
import static com.sun.javatest.regtest.agent.AStatus.failed;
import static com.sun.javatest.regtest.agent.AStatus.passed;

public class MainActionHelper extends ActionHelper {

    private final String testName;
    private Map<String, String> props;
    private Set<String> addExports;
    private Set<String> addOpens;
    private Set<String> addMods;
    private SearchPath classpath;
    private SearchPath modulepath;
    private String className;
    private List<String> classArgs;
    private int timeout;
    private float timeoutFactor;
    private String testThreadFactory;
    private String testThreadFactoryPath;
    private OutputHandler outputHandler;

    MainActionHelper(String testName) {
        this.testName = testName;
    }

    MainActionHelper properties(Map<String,String> props) {
        this.props = props;
        return this;
    }

    MainActionHelper addExports(Set<String> addExports) {
        this.addExports = addExports;
        return this;
    }

    MainActionHelper addOpens(Set<String> addOpens) {
        this.addOpens = addOpens;
        return this;
    }

    MainActionHelper addMods(Set<String> addMods) {
        this.addMods = addMods;
        return this;
    }

    MainActionHelper classpath(SearchPath classpath) {
        this.classpath = classpath;
        return this;
    }

    MainActionHelper modulepath(SearchPath modulepath) {
        this.modulepath = modulepath;
        return this;
    }

    MainActionHelper className(String className) {
        this.className = className;
        return this;
    }

    MainActionHelper classArgs(List<String> classArgs) {
        this.classArgs = classArgs;
        return this;
    }

    MainActionHelper timeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    MainActionHelper timeoutFactor(float timeoutFactor) {
        this.timeoutFactor = timeoutFactor;
        return this;
    }

    MainActionHelper testThreadFactory(String testThreadFactory) {
        this.testThreadFactory = testThreadFactory;
        return this;
    }

    MainActionHelper testThreadFactoryPath(String testThreadFactoryPath) {
        this.testThreadFactoryPath = testThreadFactoryPath;
        return this;
    }

    MainActionHelper outputHandler(OutputHandler outputHandler) {
        this.outputHandler = outputHandler;
        return this;
    }

    public AStatus runClass() {
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

        PrintStream out = outputHandler.getPrintStream(OutputHandler.OutputKind.STDOUT, true);
        PrintStream err = outputHandler.getPrintStream(OutputHandler.OutputKind.STDERR, true);

        AStatus status = passed(EXEC_PASS);
        try {
            Class<?> c;
            ClassLoader loader = ClassLoader.getSystemClassLoader();
            if (modulepath != null && !modulepath.isEmpty()) {
                loader = ModuleHelper.addModules(modulepath.asList(), addMods);
            }
            if (classpath != null && !classpath.isEmpty()) {
                List<URL> urls = new ArrayList<>();
                for (Path f : classpath.asList()) {
                    try {
                        urls.add(f.toUri().toURL());
                    } catch (MalformedURLException e) {
                    }
                }
                loader = new URLClassLoader(urls.toArray(new URL[urls.size()]), loader);
            }

            ModuleHelper.addExports(addExports, loader);
            ModuleHelper.addOpens(addOpens, loader);

            c = loader.loadClass(className);

            // Select signature for main method depending on whether the class
            // implements the TestRunner marker interface.
            Class<?>[] argTypes;
            String[] classArgsArray = classArgs.toArray(new String[classArgs.size()]);
            Object[] methodArgs;
            if (TestRunner.class.isAssignableFrom(c)) {
                // Marker interface found: use main(ClassLoader, String...)
                argTypes = new Class<?>[] { ClassLoader.class, String[].class };
                methodArgs = new Object[] { loader, classArgsArray };
            } else {
                // Normal case: marker interface not found; use standard main method
                argTypes = new Class<?>[] { String[].class };
                methodArgs = new Object[] { classArgsArray };
            }

            Method method = c.getMethod("main", argTypes);

            PrintStream realStdErr = System.err;
            AStatus stat = redirectOutput(out, err);
            if (!stat.isPassed()) {
                return stat;
            }

            AgentVMRunnable avmr = new AgentVMRunnable(method, methodArgs, err);

            // Main and Thread are same here
            // RUN JAVA IN ANOTHER THREADGROUP
            AgentVMThreadGroup tg = new AgentVMThreadGroup(err, MSG_PREFIX, timeoutFactor);
            Thread t;
            if (testThreadFactory == null) {
                t = new Thread(tg, avmr);
            } else {
                t = TestThreadFactoryHelper.loadThreadFactory(testThreadFactory, testThreadFactoryPath).newThread(avmr);
            }

            Alarm alarm = null;
            if (timeout > 0) {
                PrintWriter alarmOut = outputHandler.getPrintWriter(OutputHandler.OutputKind.LOG, true);
                alarm = Alarm.schedulePeriodicInterrupt(timeout, TimeUnit.SECONDS, alarmOut, t);
            }
            Throwable error = null;
            t.setName("AgentVMThread");
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
                if (SKIP_EXCEPTION.equals(error.getClass().getName())) {
                    status = passed(MAIN_SKIPPED + error.toString());
                } else {
                    status = failed(MAIN_THREW_EXCEPT + error.toString());
                }
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
            err.println(MSG_PREFIX + className + " in file " + className + ".java");
            err.println();
            status = error(MAIN_CANT_LOAD_TEST + e);
        } catch (NoSuchMethodException e) {
            e.printStackTrace(err);
            err.println();
            err.println(MSG_PREFIX + "main() method must be in a public class named");
            err.println(MSG_PREFIX + className + " in file " + className + ".java");
            err.println();
            status = error(MAIN_CANT_FIND_MAIN);
        } catch (ModuleHelper.Fault e) {
            if (e.getCause() != null)
                e.printStackTrace(err);
            status = error(MAIN_CANT_INIT_MODULE_EXPORTS + e.getMessage());
        } finally {
            out.close();
            err.close();
            status = saved.restore(testName, status);
        }


        return status;
    }

    private static final boolean traceCleanup = Flags.get("traceCleanup");
    private static final String MSG_PREFIX = "JavaTest Message: ";
    private static final String SKIP_EXCEPTION = "jtreg.SkippedException";

    private static final String
        //    runAgentJVM
        MAIN_THREAD_INTR      = "Thread interrupted: ",
        MAIN_THREAD_TIMEOUT   = "Timeout",
        MAIN_THREW_EXCEPT     = "`main' threw exception: ",
        MAIN_CANT_LOAD_TEST   = "Can't load test: ",
        MAIN_CANT_FIND_MAIN   = "Can't find `main' method",
        MAIN_CANT_INIT_MODULE_EXPORTS = "Can't init module exports: ",
        MAIN_SKIPPED = "Skipped: ";

    public static final String MAIN_SKIPPED_STATUS_PREFIX = MAIN_SKIPPED + SKIP_EXCEPTION;

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
        private double timeoutFactor;

        AgentVMThreadGroup(PrintStream out, String messagePrefix, double timeoutFactor) {
            super("AgentVMThreadGroup");
            this.out = out;
            this.messagePrefix = messagePrefix;
            this.timeoutFactor = timeoutFactor;
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
            final long MAX_CLEANUP_TIME_MILLIS = (long) (10 * 1000 * timeoutFactor);
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
                    ArrayList<Thread> list = new ArrayList<>(num);
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
