/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import com.sun.javatest.regtest.agent.Alarm;

/**
 * Abstract superclass for timeout handlers.
 *
 * Instances of this class will be called when an action involving a process has timed out.
 * jtreg provides a default implementation of this class;
 * alternative implementation may be specified on the {@code jtreg} command line.
 */
public abstract class TimeoutHandler {

    /**
     * The log to which messages should be written.
     */
    protected final PrintWriter log;

    /**
     * The directory in which diagnostic information may be written.
     */
    protected final File outputDir;

    /**
     * The JDK being tested.
     */
    protected final File testJdk;

    private long timeout;

    /**
     * Creates a timeout handler.
     *
     * @param log to which messages should be written
     * @param outputDir a directory in which diagnostic information may be written
     * @param testJdk the JDK being tested
     */
    public TimeoutHandler(PrintWriter log, File outputDir, File testJdk) {
        this(log, outputDir, testJdk.toPath());
    }

    /**
     * Creates a timeout handler.
     *
     * @param log to which messages should be written
     * @param outputDir a directory in which diagnostic information may be written
     * @param testJdk the JDK being tested
     */
    public TimeoutHandler(PrintWriter log, File outputDir, Path testJdk) {
        this.log = log;
        this.outputDir = outputDir;
        this.testJdk = testJdk.toFile();
    }

    /**
     * Sets the timeout, in seconds, after which the handler itself will be interrupted.
     * A negative or zero value disables the timeout.
     *
     * @param timeout the timeout
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * Gets the timeout, in seconds, after which the handler itself will be interrupted.
     * A negative or zero value disables the timeout.
     *
     * @return the timeout
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * Initiates the timeout handler, to analyze a specified process, by calling {@link #runActions}.
     * The handler itself is subject to a secondary timeout, which can be specified with
     * {@link #setTimeout}.
     *
     * @param proc the process
     */
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

        Alarm a = (timeout <= 0)
                ? Alarm.NONE
                : Alarm.scheduleInterrupt(timeout, TimeUnit.SECONDS, log, Thread.currentThread());
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
     * Performs actions on the process to gather data that can be used to analyze the time out.
     *
     * @param process the process that has timed out
     * @param pid the pid of the process
     * @throws InterruptedException if the actions exceed the specified timeout
     */
    protected abstract void runActions(Process process, long pid) throws InterruptedException;

    /**
     * Gets the process id of the specified process.
     *
     * @param proc the process
     * @return The process id, or 0 if the process id cannot be found
     */
    public long getProcessId(Process proc) {
        try {
            try {
                Method pid = Process.class.getMethod("pid");
                return (Long) pid.invoke(proc);
            } catch (NoSuchMethodException ignore) {
                // This exception is expected on pre-JDK 9,
                // try a fallback method that only works on Unix platforms
                return getProcessIdPreJdk9(proc);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static long getProcessIdPreJdk9(Process proc)
            throws IllegalAccessException, NoSuchFieldException {
        if (proc.getClass().getName().equals("java.lang.UNIXProcess")) {
            int pid;
            Field f = proc.getClass().getDeclaredField("pid");
            boolean oldValue = f.isAccessible();
            try {
                f.setAccessible(true);
                pid = f.getInt(proc);
            } finally {
                f.setAccessible(oldValue);
            }
            return pid;
        }
        return 0;
    }
}
