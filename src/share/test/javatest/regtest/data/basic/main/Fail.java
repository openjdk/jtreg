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
 * @summary Failed: Execution failed: `main' threw exception:
 * java.lang.RuntimeException: I should fail
 */

/* @test
 * @summary Failed: Execution failed: `main' threw exception:
 * java.lang.RuntimeException: I should fail
 * @run main Fail
 */

/* @test
 * @summary Passed: Execution failed as expected
 * @run main/fail Fail
 */

/* @test
 * @summary Failed: Execution failed: `main' threw exception:
 * java.lang.RuntimeException: I should fail
 * @run main Fail arg0 arg1 arg2
 */

/* @test
 * @summary Passed: Execution failed as expected
 * @run main/fail Fail arg0 arg1 arg2
 */

/* @test
 * @summary Failed: Execution failed: `main' threw exception:
 * java.lang.RuntimeException: I should fail
 * @run main/othervm Fail
 */

/* @test
 * @summary Passed: Execution failed as expected
 * @run main/othervm/fail Fail
 */

/* @test
 * @summary Failed: Execution failed: `main' threw exception:
 * java.lang.RuntimeException: I should fail
 * @run main/othervm Fail 3 1 2 1 2
 */

/* @test
 * @summary Passed: Execution failed as expected
 * @run main/othervm/fail Fail 3 7 6 1 9 *
 */


public class Fail
{
    public static void main(String [] args) {
        if (args.length == 0)
            System.out.println("no args");
        else {
            System.out.print("Args:");
            for (int i = 0; i < args.length; i++)
                System.out.print(args[i] + " ");
        }
        throw new RuntimeException("I should fail");
    }
}


