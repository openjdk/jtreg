/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/* @test
 * @summary Failed: Execution failed: Applet thread threw exception: java.lang.RuntimeException: I should fail
 * @run applet Fail.html
 */

/* @test
 * @summary Failed: Execution failed: Applet thread threw exception: java.lang.RuntimeException: I should fail
 * @run applet/manual Fail.html
 */

/* @test
 * @summary Failed: Execution failed: Applet thread threw exception: java.lang.RuntimeException: I should fail
 * @run applet/manual=yesno Fail.html
 */

/* @test
 * @summary Failed: Execution failed: Applet thread threw exception: java.lang.RuntimeException: I should fail
 * @run applet/manual=done Fail.html
 */

/* @test
 * @summary Passed: Execution failed as expected
 * @run applet/fail Fail.html
 */

/* @test
 * @summary Passed: Execution failed as expected
 * @run applet/manual/fail Fail.html
 */

/* @test
 * @summary Passed: Execution failed as expected
 * @run applet/manual=yesno/fail Fail.html
 */

/* @test
 * @summary Passed: Execution failed as expected
 * @run applet/manual=done/fail Fail.html
 */
import java.applet.Applet;
import java.awt.Graphics;

public class Fail extends Applet
{
    public class TimedSleep extends Thread
    {
        public void run() {
            try {
                // potential race condition
                Thread.currentThread().sleep(100);
            } catch (InterruptedException e) {
            }
            throw new RuntimeException("I should fail");
        }
    }

    public void start() {
        sleeper = new TimedSleep();
        sleeper.start();
    }

    public void stop() {
        // ensure sleeper has a chance to do its thing
        try {
            sleeper.join();
        }
        catch (InterruptedException e) {
            throw new RuntimeException("interrupted waiting to join with sleeper thread");
        }
    }

    public void paint(Graphics g) {
        g.drawString("Trivail Fail", 50, 25);
    }

    private Thread sleeper;
}
