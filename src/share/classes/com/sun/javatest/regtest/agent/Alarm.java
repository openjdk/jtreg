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

import java.io.PrintWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Provides a lightweight way of setting up and canceling timeouts.
 */
public class Alarm  {

    /**
     * Schedule an Alarm to periodically interrupt() a specified thread.
     * The first interrupt() will happen after the time specified by {@code delay}
     * and {@code delayUnit}. Thereafter the thread will be interrupted every 100ms
     * until the Alarm is canceled.
     * @param delay run the first interrupt after this time
     * @param unit TimeUnit for {@code delay}
     * @param msgOut PrintWriter for logging
     * @param threadToInterrupt The thread to call interrupt() on
     * @return a new Alarm instance
     */
    public static Alarm schedulePeriodicInterrupt(long delay,
                                                  TimeUnit unit,
                                                  PrintWriter msgOut,
                                                  final Thread threadToInterrupt) {
        Interruptor runner = new Interruptor(delay, unit, msgOut, threadToInterrupt);
        runner.future = executor
            .scheduleWithFixedDelay(runner,
                                    TimeUnit.MILLISECONDS.convert(delay, unit),
                                    100,
                                    TimeUnit.MILLISECONDS);
        return runner;
    }

    /**
     * Schedule an Alarm to interrupt() a specified thread.
     * The interrupt() will happen after the time specified by {@code delay}
     * and {@code delayUnit}.
     * @param delay run the interrupt after this time
     * @param unit TimeUnit for {@code delay}
     * @param msgOut PrintWriter for logging
     * @param threadToInterrupt The thread to call interrupt() on
     * @return a new Alarm instance
     */
    public static Alarm scheduleInterrupt(long delay,
                                          TimeUnit unit,
                                          PrintWriter msgOut,
                                          final Thread threadToInterrupt) {
        Interruptor runner = new Interruptor(delay, unit, msgOut, threadToInterrupt);
        runner.future = executor.schedule(runner, delay, unit);
        return runner;
    }

    /**
     * Schedule an Alarm to run the specified Runnable.
     * The Runnable will be run after the time specified by {@code delay}
     * and {@code delayUnit}.
     *
     * Note: Because Alarms are serviced by just a single thread, Alarm actions must
     * be quick so that other Alarms are not blocked from running as scheduled.
     *
     * @param delay run after this time
     * @param unit TimeUnit for {@code delay}
     * @param msgOut PrintWriter for logging
     * @param r the Runnable
     * @return a new Alarm instance
     */
    public static Alarm schedule(long delay,
                                 TimeUnit unit,
                                 PrintWriter msgOut,
                                 Runnable r) {
        RunnableAlarm runner = new RunnableAlarm(delay, unit, msgOut, r);
        runner.future = executor.schedule(runner, delay, unit);
        return runner;
    }

    protected volatile boolean fired;
    protected ScheduledFuture<?> future;
    protected int count;
    protected final long delay;
    protected final TimeUnit delayUnit;
    protected final PrintWriter msgOut;

    /**
     * Internal constructor.
     * @param delay run the interrupt after this time
     * @param delayUnit TimeUnit for {@code delay}
     * @param msgOut PrintWriter for logging
     */
    protected Alarm(long delay, TimeUnit delayUnit, PrintWriter msgOut) {
        this.delay = delay;
        this.delayUnit = delayUnit;
        this.msgOut = msgOut;
    }

    /**
     * Cancel the Alarm.
     */
    public void cancel() {
        future.cancel(true);
    }

    /**
     * Check if the Alarm has fired at least once.
     * @return true if the alarm has fired, false otherwise
     */
    public boolean didFire() {
        return fired;
    }

    /**
     * Shared logic for all Alarms
     */
    protected void run() {
        if (msgOut != null) {
            if (count == 0) {
                msgOut.println(String.format("Timeout signalled after %d seconds", TimeUnit.SECONDS.convert(delay, delayUnit)));
            } else if (count % 100 == 0) {
                msgOut.println(String.format("Timeout refired %d times", count));
            }
        }
        count++;
        fired = true;
    }

    /**
     * Helper class to implement the Thread.interrupt() calls.
     */
    private static class Interruptor extends Alarm implements Runnable {
        Thread threadToInterrupt;

        public Interruptor(long delay, TimeUnit unit, PrintWriter msgOut, Thread t) {
            super(delay, unit, msgOut);
            threadToInterrupt = t;
        }

        @Override
        public void run() {
            super.run();
            threadToInterrupt.interrupt();
        }
    }

    /**
     * Helper class to run a Runnable.
     */
    private static class RunnableAlarm extends Alarm implements Runnable {
        Runnable r;

        public RunnableAlarm(long delay, TimeUnit unit, PrintWriter msgOut, Runnable r) {
            super(delay, unit, msgOut);
            this.r = r;
        }

        @Override
        public void run() {
            super.run();
            r.run();
        }
    }

    /**
     * An Alarm instance that can be used to initialize an Alarm variable.
     * This instance of Alarm will never fire.
     */
    public static final Alarm NONE = new NoAlarm(0, TimeUnit.MILLISECONDS, null);

    private static class NoAlarm extends Alarm {
        protected NoAlarm(long delay, TimeUnit delayUnit, PrintWriter msgOut) {
            super(delay, delayUnit, msgOut);
        }

        @Override
        public void cancel() {
        }

        @Override
        public boolean didFire() {
            return false;
        }
    }

    /**
     * Our own ThreadFactory to make the thread in the pool be a daemon threads.
     */
    private static class DaemonThreadFactory implements ThreadFactory {

        ThreadFactory defaultFactory = Executors.defaultThreadFactory();

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = defaultFactory.newThread(r);
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final ScheduledThreadPoolExecutor executor =
            new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory());
}
