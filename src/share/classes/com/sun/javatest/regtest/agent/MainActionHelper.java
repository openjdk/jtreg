/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javatest.Status;
import static com.sun.javatest.regtest.agent.ActionHelper.EXEC_PASS;
import static com.sun.javatest.regtest.agent.RStatus.error;
import static com.sun.javatest.regtest.agent.RStatus.failed;
import static com.sun.javatest.regtest.agent.RStatus.passed;

public class MainActionHelper extends ActionHelper {

    public static Status runClass(
            String testName,
            Map<String, String> props,
            Set<String> addExports,
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

        Status status = passed(EXEC_PASS);
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
                ModuleHelper.addModuleExports(addExports, loader);
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

            Status stat = redirectOutput(out, err);
            if (!stat.isPassed()) {
                return stat;
            }

            // RUN JAVA IN ANOTHER THREADGROUP
            SameVMThreadGroup tg = new SameVMThreadGroup();
            SameVMRunnable svmt = new SameVMRunnable(method, methodArgs, err);
            Thread t = new Thread(tg, svmt, "SameVMThread");
            Alarm alarm = null;
            if (timeout > 0) {
                PrintWriter alarmOut = outputHandler.createOutput(OutputHandler.OutputKind.LOG);
                alarm = Alarm.schedulePeriodicInterrupt(timeout, TimeUnit.SECONDS, alarmOut, t);
            }
            Throwable error = null;
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                if (t.isInterrupted() && (tg.uncaughtThrowable == null)) {
                    error = e;
                    status = error(MAIN_THREAD_INTR + e.getMessage());
                }
            } finally {
                tg.cleanup();
                if (alarm != null) {
                    alarm.cancel();
                    if (alarm.didFire() && error == null) {
                        err.println("Test timed out. No timeout information is available in samevm mode.");
                        error = new Error("timeout");
                        status = error(MAIN_THREAD_TIMEOUT);
                    }
                }
            }

            if (((svmt.t != null) || (tg.uncaughtThrowable != null)) && (error == null)) {
                if (svmt.t == null) {
                    error = tg.uncaughtThrowable;
                } else {
                    error = svmt.t;
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
            err.println("JavaTest Message: main() method must be in a public class named");
            err.println("JavaTest Message: " + classname + " in file " + classname + ".java");
            err.println();
            status = error(MAIN_CANT_LOAD_TEST + e);
        } catch (NoSuchMethodException e) {
            e.printStackTrace(err);
            err.println();
            err.println("JavaTest Message: main() method must be in a public class named");
            err.println("JavaTest Message: " + classname + " in file " + classname + ".java");
            err.println();
            status = error(MAIN_CANT_FIND_MAIN);
        } catch (ModuleHelper.Fault e) {
            if (e.getCause() != null)
                e.printStackTrace(err);
            status = error(MAIN_CANT_INIT_MODULE_EXPORTS + e.getMessage());
        } finally {
            status = saved.restore(testName, status);
        }

        // Write test output
        out.close();
        outputHandler.createOutput(OutputHandler.OutputKind.STDOUT, out.getOutput());

        err.close();
        outputHandler.createOutput(OutputHandler.OutputKind.STDERR, err.getOutput());

        return status;
    }

    private static final String
        //    runSameJVM
        MAIN_SECMGR_BAD       = "JavaTest not running its own security manager",
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

    private static class SameVMRunnable implements Runnable
    {
        public SameVMRunnable(Method m, Object[] args, PrintStream err) {
            method    = m;
            this.args = args;
            this.err  = err;
        } // SameVMRunnable()

        public void run() {
            try {
                // RUN JAVA PROGRAM
                result = method.invoke(null, args);

                System.err.println();
                System.err.println("JavaTest Message:  Test complete.");
                System.err.println();
            } catch (InvocationTargetException e) {
                // main must have thrown an exception, so the test failed
                e.getTargetException().printStackTrace(err);
                t = e.getTargetException();
                System.err.println();
                System.err.println("JavaTest Message: Test threw exception: " + t.getClass().getName());
                System.err.println("JavaTest Message: shutting down test");
                System.err.println();
            } catch (IllegalAccessException e) {
                e.printStackTrace(err);
                t = e;
                System.err.println();
                System.err.println("JavaTest Message: Verify that the class defining the test is");
                System.err.println("JavaTest Message: declared public (test invoked via reflection)");
                System.err.println();
            }
        } // run()

        //----------member variables--------------------------------------------

        public  Object result;
        private final Method method;
        private final Object[] args;
        private final PrintStream err;

        Throwable t = null;
    }

    static class SameVMThreadGroup extends ThreadGroup
    {
        SameVMThreadGroup() {
            super("SameVMThreadGroup");
        } // SameVMThreadGroup()

        @Override
        public synchronized void uncaughtException(Thread t, Throwable e) {
            if (e instanceof ThreadDeath)
                return;
            if ((uncaughtThrowable == null) && (!cleanMode)) {
                uncaughtThrowable = e;
                uncaughtThread    = t;
            }
            cleanup();
        } // uncaughtException()

        private void cleanup() {
            cleanMode = true;

            final int CLEANUP_ROUNDS = 4;
            final long MAX_CLEANUP_TIME_MILLIS = 2 * 60 * 1000;
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

            cleanupOK = liveThreads().isEmpty();
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
                        if (t.isAlive() &&
                                t != Thread.currentThread() &&
                                ! t.isDaemon())
                            list.add(t);
                    }
                    return list;
                }
            }
        }

        //----------member variables--------------------------------------------

        private boolean cleanMode   = false;
        Throwable uncaughtThrowable = null;
        Thread    uncaughtThread    = null;
        boolean cleanupOK = false;
    }

}
