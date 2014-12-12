/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Abstract super-class for timeout handlers.
 */
public abstract class TimeoutHandler {

    protected final PrintWriter log;
    protected final File outputDir;
    protected final File testJdk;

    private static final long TIMEOUTHANDLER_TIMEOUT =
        TimeUnit.MILLISECONDS.convert(300, TimeUnit.SECONDS);

    public TimeoutHandler(PrintWriter log, File outputDir, File testJdk) {
        this.log = log;
        this.outputDir = outputDir;
        this.testJdk = testJdk;
    }

    public final void handleTimeout(Process proc) {
        log.println("Timeout information:");
        long pid = 0;
        try {
            pid = getProcessId(proc);
        } catch(Exception ex) {
            ex.printStackTrace(log);
        }
        if (pid == 0) {
            log.println("Could not find process id for the process that timed out.");
            log.println("Skipping timeout handling.");
            return;
        }

        Alarm a = new Alarm(TIMEOUTHANDLER_TIMEOUT, Thread.currentThread(), "Timeout Handler", log);
        try {
            runActions(proc, pid);
        } catch (InterruptedException ex) {
            a.cancel();
            log.println("Timeout handler interrupted: ");
            ex.printStackTrace(log);
        } finally {
            a.cancel();
        }

        log.println("--- Timeout information end.");
    }

    /**
     * Perform actions on the Process to gather data that can be used to
     * analyze the time out.
     * @param process Process that has timed out.
     * @param proc pid of the process.
     */
    protected abstract void runActions(Process process, long pid) throws InterruptedException;

    /**
     * Get the process id of the specified process.
     * @param proc
     * @return Process id
     */
    private static long getProcessId(Process proc) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchFieldException   {
        try {
            Method getPid = Process.class.getMethod("getPid");
            return (Long) getPid.invoke(proc);
        } catch (NoSuchMethodException ignore) {
            // This exception is expected on pre JDK 9,
            // try a fallback method that only works on Unix platforms
            return getProcessIdPreJdk9(proc);
        }
    }

    private static long getProcessIdPreJdk9(Process proc) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException {
        if (proc.getClass().getName().equals("java.lang.UNIXProcess")) {
            Field f = proc.getClass().getDeclaredField("pid");
            boolean oldValue = f.isAccessible();
            f.setAccessible(true);
            int pid = f.getInt(proc);
            f.setAccessible(oldValue);
            return pid;
        }
        return 0;
    }
}
