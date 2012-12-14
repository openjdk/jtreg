/*
 * Copyright 1998-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.javatest.regtest;

import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.sun.javatest.Status;

/**
  * This class is the wrapper for all main/othervm tests.
  *
  * @author Iris A Garcia
  */
public class MainWrapper
{
    public static void main(String [] args) {
        String [] mainArgs;
        try {
            FileReader in = new FileReader(args[0]);

            StringWriter out = new StringWriter();
            char [] buf = new char[1024];
            int howMany;

            while ((howMany = in.read(buf)) > 0)
                out.write(buf, 0, howMany);
            out.close();
            in.close();
            mainArgs = StringArray.splitTerminator("\0", out.toString());

            int i = 0;
            buildFN                  = mainArgs[i++];
            String stringifiedArgs   = mainArgs[i++];
            allArgs = StringArray.splitWS(stringifiedArgs);
        } catch (IOException e) {
            Status.failed(MAIN_CANT_READ_ARGS).exit();
        }

        // RUN JAVA IN ANOTHER THREADGROUP
        MainThreadGroup tg = new  MainThreadGroup();
        Thread t = new Thread(tg, new MainThread(), "MainThread");
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            Status.failed(MAIN_THREAD_INTR + Thread.currentThread().getName()).exit();
        }
//      tg.cleanup();

        if (tg.uncaughtThrowable != null)
            Status.failed(MAIN_THREW_EXCEPT + tg.uncaughtThrowable.toString()).exit();
        else
            Status.passed("").exit();

    } // main()

    static class MainThread extends Thread
    {
        public void run() {
            try {
                Class c = Class.forName(buildFN);
                Class [] argTypes = {String[].class};
                Method method = c.getMethod("main", argTypes);
                Object [] runArgs = {allArgs};

                // RUN JAVA PROGRAM
                method.invoke(null, runArgs);

            } catch (InvocationTargetException e) {
                e.getTargetException().printStackTrace();
                System.err.println();
                System.err.println("JavaTest Message: Test threw exception: " + e.getTargetException());
                System.err.println("JavaTest Message: shutting down test");
                System.err.println();
                Status.failed(MAIN_THREW_EXCEPT + e.getTargetException()).exit();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                System.err.println();
                System.err.println("JavaTest Message: main() method must be in a public class named");
                System.err.println("JavaTest Message: " + buildFN + " in file " + buildFN + ".java");
                System.err.println();
                Status.error(MAIN_CANT_LOAD_TEST + e).exit();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                System.err.println();
                System.err.println("JavaTest Message: main() method must be in a public class named");
                System.err.println("JavaTest Message: " + buildFN + " in file " + buildFN + ".java");
                System.err.println();
                Status.error(MAIN_CANT_FIND_MAIN).exit();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                System.err.println();
                System.err.println("JavaTest Message: Verify that the class defining the test is");
                System.err.println("JavaTest Message: declared public (test invoked via reflection)");
                System.err.println();
                Status.error(e.toString()).exit();
            }
        } // run
    }

    static class MainThreadGroup extends ThreadGroup
    {
        MainThreadGroup() {
            super("MainThreadGroup");
        } // MainThreadGroup()

        public void uncaughtException(Thread t, Throwable e) {
            if (e instanceof ThreadDeath)
                return;
            e.printStackTrace();
            if ((uncaughtThrowable == null) && (!cleanMode)) {
                uncaughtThrowable = e;
                uncaughtThread    = t;
            }
//          cleanup();
            Status.failed(MAIN_THREW_EXCEPT + e.toString()).exit();
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

        private boolean cleanMode   = false;
        Throwable uncaughtThrowable = null;
        Thread    uncaughtThread    = null;
    }

    //----------member variables------------------------------------------------

    private static final String
        MAIN_CANT_READ_ARGS   = "JavaTest Error: Can't read main args file.",
        MAIN_THREAD_INTR      = "Thread interrupted: ",
        MAIN_THREW_EXCEPT     = "`main' threw exception: ",
        MAIN_CANT_LOAD_TEST   = "Can't load test: ",
        MAIN_CANT_FIND_MAIN   = "Can't find `main' method";

    private static String buildFN;
    private static String [] allArgs;
}
