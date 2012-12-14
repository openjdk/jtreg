/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.javatest.util.I18NResourceBundle;
import com.sun.javatest.util.Timer;
import java.io.PrintWriter;

/**
 * Provides a lightweight way of setting up and canceling timeouts.
 * Based on {@link Script.Alarm}.
 */
class Alarm implements Timer.Timeable {
    Alarm(int delay, String testName, PrintWriter out) {
        this(delay, Thread.currentThread(), testName, out);
    }

    Alarm(int delay, Thread threadToInterrupt, String testName, PrintWriter out) {
        super();
        if (threadToInterrupt == null)
            throw new NullPointerException();
        this.testName = testName;
        this.msgOut = out;
        this.delay = delay;
        this.threadToInterrupt = threadToInterrupt;
        entry = alarmTimer.requestDelayedCallback(this, delay);
        if (debugAlarm)
            System.err.println(i18n.getString("alarm.started", this));
        }

    synchronized void cancel() {
        if (debugAlarm)
            System.err.println(i18n.getString("alarm.cancelled", this));
            alarmTimer.cancel(entry);
        }

    public synchronized void timeout() {
        if (count == 0)
            msgOut.println(i18n.getString("alarm.timeout", new Float(delay / 1000.0F)));
        else if (count % 100 == 0) {
            msgOut.println(i18n.getString("alarm.notResponding", new Integer(count)));
            if (count % 1000 == 0)
                System.err.println(i18n.getString("alarm.timedOut", new Object[]{ testName, new Integer(count) }));
        }
        if (debugAlarm)
            System.err.println(i18n.getString("alarm.interrupt", new Object[]{this, threadToInterrupt}));
        threadToInterrupt.interrupt();
        count++;
        entry = alarmTimer.requestDelayedCallback(this, 100);
    }

    public static void finished() {
        alarmTimer.finished();
    }

    @Override
    public String toString() {
        return ("Alarm[" + Integer.toHexString(hashCode()) + "," + delay + "," + threadToInterrupt + "," + testName + "]");
    }

    private final String testName;
    private final PrintWriter msgOut;
    private final int delay;
    private final Thread threadToInterrupt;
    private int count;
    private Timer.Entry entry;


    protected static Timer alarmTimer = new Timer();
    private static boolean debugAlarm = Boolean.getBoolean("debug.com.sun.javatest.regtest.Alarm");
    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(Alarm.class);
}
