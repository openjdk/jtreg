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
 * @summary Passed: Execution successful
 */

/* @test
 * @summary Passed: Execution successful
 * @run main Pass
 */

/* @test
 * @summary Failed: Execution passed unexpectedly
 * @run main/fail Pass
 */

/* @test
 * @summary Passed: Execution successful
 * @run main Pass blah 1
 */

/* @test
 * @summary Failed: Execution passed unexpectedly
 * @run main/fail Pass 7 10
 */

/* @test
 * @summary Passed: Execution successful
 * @run main/othervm Pass
 */

/* @test
 * @summary Failed: Execution passed unexpectedly
 * @run main/othervm/fail Pass
 */

/* @test
 * @summary Passed: Execution successful
 * @run main/othervm Pass foo bar
 */

/* @test
 * @summary Failed: Execution passed unexpectedly
 * @run main/othervm/fail Pass foo bar bas
 */

public class Pass
{
    public static void main(String [] args) {
        if (args.length == 0)
            System.out.println("no args");
        else {
            System.out.print("args:");
            for (int i = 0; i < args.length; i++)
                System.out.print(args[i] + " ");
        }
    }
}
