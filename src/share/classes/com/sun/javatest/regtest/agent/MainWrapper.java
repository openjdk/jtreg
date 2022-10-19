/*
 * Copyright (c) 1998, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
  * This class is the wrapper for all main/othervm tests.
  */
public class MainWrapper
{

    public static String MAIN_WRAPPER = "jtreg.custom.main.wrapper";
    public static String MAIN_WRAPPER_PATH = "jtreg.custom.main.wrapper.path";

    public static void main(String[] args) {
        String moduleName;
        String className;
        String[] classArgs;

        try {
            FileReader in = new FileReader(args[0]);

            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int howMany;

            while ((howMany = in.read(buf)) > 0)
                sb.append(buf, 0, howMany);
            in.close();

            String[] fileArgs = StringArray.splitTerminator("\0", sb.toString());

            int i = 0;
            String moduleClassName = fileArgs[i++];
            int sep = moduleClassName.indexOf('/');
            moduleName = (sep == -1) ? null : moduleClassName.substring(0, sep);
            className  = (sep == -1) ? moduleClassName : moduleClassName.substring(sep + 1);
            classArgs = StringArray.splitWS(fileArgs[i++]);
        } catch (IOException e) {
            AStatus.failed(MAIN_CANT_READ_ARGS + e).exit();
            throw new IllegalStateException(); // implies exit() didn't sucees
        }

        // RUN JAVA IN ANOTHER THREADGROUP
        MainThreadGroup tg = new MainThreadGroup();
        Runnable task = new MainTask(moduleName, className, classArgs);
        Thread t;
        String customWrapperName = System.getProperty(MAIN_WRAPPER);
        String customWrapperNamePath = System.getProperty(MAIN_WRAPPER_PATH);
        if (customWrapperName == null) {
            t = new Thread(tg, task);
        } else {
            t = CustomMainWrapper.getInstance(customWrapperName, customWrapperNamePath).createThread(tg, task);
        }
        t.setName("MainThread");
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            AStatus.failed(MAIN_THREAD_INTR + Thread.currentThread().getName()).exit();
        }
//      tg.cleanup();

        if (tg.uncaughtThrowable != null) {
            handleTestException(tg.uncaughtThrowable);
        } else {
            AStatus.passed("")
                   .exit();
        }

    } // main()

    private static void handleTestException(Throwable e) {
        if (SKIP_EXCEPTION.equals(e.getClass().getName())) {
            AStatus.passed(MAIN_SKIPPED + e)
                   .exit();
        } else {
            AStatus.failed(MAIN_THREW_EXCEPT + e)
                   .exit();
        }
    }

    static class MainTask implements Runnable {
        MainTask(String moduleName, String className, String[] args) {
            this.moduleName = moduleName;
            this.className = className;
            this.args = args;
        }

        public void run() {
            try {
                ClassLoader cl;
                if (moduleName != null) {
                    Class<?> layerClass;
                    try {
                        layerClass = Class.forName("java.lang.ModuleLayer");
                    } catch (ClassNotFoundException e) {
                        layerClass = Class.forName("java.lang.reflect.Layer");
                    }
                    Method bootMethod = layerClass.getMethod("boot");
                    Object bootLayer = bootMethod.invoke(null);
                    Method findLoaderMth = layerClass.getMethod("findLoader", String.class);
                    cl = (ClassLoader) findLoaderMth.invoke(bootLayer, moduleName);
                } else {
                    cl = getClass().getClassLoader();
                }

                // RUN JAVA PROGRAM
                Class<?> c = Class.forName(className, false, cl);
                Method mainMethod = c.getMethod("main", String[].class);
                mainMethod.invoke(null, (Object) args);

            } catch (InvocationTargetException e) {
                Throwable throwable = e.getTargetException();
                throwable.printStackTrace(System.err);
                System.err.println();
                System.err.println("JavaTest Message: Test threw exception: " + throwable);
                System.err.println("JavaTest Message: shutting down test");
                System.err.println();
                handleTestException(throwable);
            } catch (ClassNotFoundException e) {
                e.printStackTrace(System.err);
                System.err.println();
                System.err.println("JavaTest Message: main() method must be in a public class named");
                System.err.println("JavaTest Message: " + className + " in file " + className + ".java");
                System.err.println();
                AStatus.error(MAIN_CANT_LOAD_TEST + e).exit();
            } catch (NoSuchMethodException e) {
                e.printStackTrace(System.err);
                System.err.println();
                System.err.println("JavaTest Message: main() method must be in a public class named");
                System.err.println("JavaTest Message: " + className + " in file " + className + ".java");
                System.err.println();
                AStatus.error(MAIN_CANT_FIND_MAIN).exit();
            } catch (IllegalAccessException e) {
                e.printStackTrace(System.err);
                System.err.println();
                System.err.println("JavaTest Message: Verify that the class defining the test is");
                System.err.println("JavaTest Message: declared public (test invoked via reflection)");
                System.err.println();
                AStatus.error(e.toString()).exit();
            }
        } // run

        private final String moduleName;
        private final String className;
        private final String[] args;
    }

    static class MainThreadGroup extends ThreadGroup
    {
        MainThreadGroup() {
            super("MainThreadGroup");
        } // MainThreadGroup()

        public void uncaughtException(Thread t, Throwable e) {
            if (e instanceof ThreadDeath)
                return;
            e.printStackTrace(System.err);
            if ((uncaughtThrowable == null) && (!cleanMode)) {
                uncaughtThrowable = e;
                uncaughtThread    = t;
            }
//          cleanup();
            AStatus.failed(MAIN_THREW_EXCEPT + e).exit();
        } // uncaughtException()

//      public void cleanup() {
//          cleanMode = true;
//          boolean someAlive = false;
//          Thread ta[] = new Thread[activeCount()];
//          enumerate(ta);
//          for (int i = 0; i < ta.length; i++) {
//              if (ta[i] != null &&
//                  ta[i].isAlive() &&
//                  ta[i] != Thread.currentThread() &&
//                  !ta[i].isDaemon())
//                  {
//                      ta[i].interrupt();
//                      someAlive = true;
//                      //Thread.currentThread().yield();
//                  }
//          }
//          if (someAlive) {
//              Thread.currentThread().yield();
//              cleanup();
//          }
//      } // cleanup()

        //----------member variables--------------------------------------------

        private final boolean cleanMode   = false;
        Throwable uncaughtThrowable = null;
        Thread    uncaughtThread    = null;

    }

    //----------member variables------------------------------------------------

    private static final String
        MAIN_CANT_READ_ARGS   = "JavaTest Error: Can't read main args file.",
        MAIN_THREAD_INTR      = "Thread interrupted: ",
        MAIN_THREW_EXCEPT     = "`main' threw exception: ",
        MAIN_CANT_LOAD_TEST   = "Can't load test: ",
        MAIN_CANT_FIND_MAIN   = "Can't find `main' method",
        MAIN_SKIPPED          = "Skipped: ";
    private static final String SKIP_EXCEPTION = "jtreg.SkippedException";

}

