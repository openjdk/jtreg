/*
 * Copyright 1998-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @summary Passed: Execution successful
 * @run applet Pass.html
 */

/* @test
 * @summary Passed: Execution successful
 * @run applet/manual Pass.html
 */

/* @test
 * @summary ...Manual test, user evaluated: Result depends on user selection
 * @run applet/manual=yesno Pass.html
 */

/* @test
 * @summary Passed: Manual test: Execution successful
 * @run applet/manual=done Pass.html
 */

/* @test
 * @summary Failed: Execution passed unexpectedly
 * @run applet/fail Pass.html
 */

/* @test
 * @summary Result depends on user selection
 * @run applet/manual/fail Pass.html
 */

/* @test
 * @summary ...Manual test, user evaluated: Result depends on user selection
 * @run applet/manual=yesno/fail Pass.html
 */

/* @test
 * @summary Failed: Manual test: Execution passed unexpectedly
 * @run applet/manual=done/fail Pass.html
 */
import java.applet.Applet;
import java.awt.Dimension;
import java.awt.Graphics;

public class Pass extends Applet
{

    public void paint(Graphics g) {
        g.drawString("Trivial Pass", 50, 25);
        //g.drawRect(0, 0, size().width -1, size().height -1);
    }
    public Dimension getPreferredSize() {
        return new Dimension(100, 300);
    }
}
