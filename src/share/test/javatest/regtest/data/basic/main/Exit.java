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

/**
 * @test
 * @summary Passed: Execution successful
 * @run main Exit
 */

/**
 * @test
 * @summary Failed: Unexpected exit from test [exit code: 0]
 * @run main Exit 0
 */

/**
 * @test
 * @summary Failed: Unexpected exit from test [exit code: 1]
 * @run main Exit 1
 */

/**
 * @test
 * @summary Passed: Execution successful
 * @run main/othervm Exit
 */

/**
 * @test
 * @summary Failed: Unexpected exit from test [exit code: 0]
 * @run main/othervm Exit 0
 */

/**
 * @test
 * @summary Failed: Unexpected exit from test [exit code: 1]
 * @run main/othervm Exit 1
 */

/**
 * @test
 * @summary Failed: Execution passed unexpectedly
 * @run main/fail Exit
 */

/**
 * @test
 * @summary Failed: Unexpected exit from test [exit code: 0]
 * @run main/fail Exit 0
 */

/**
 * @test
 * @summary Failed: Unexpected exit from test [exit code: 1]
 * @run main/fail Exit 1
 */

/**
 * @test
 * @summary Failed: Execution passed unexpectedly
 * @run main/othervm/fail Exit
 */

/**
 * @test
 * @summary Failed: Unexpected exit from test [exit code: 0]
 * @run main/othervm/fail Exit 0
 */

/**
 * @test
 * @summary Failed: Unexpected exit from test [exit code: 1]
 * @run main/othervm/fail Exit 1
 */

public class Exit
{
    public static void main(String [] args) {
        if (args.length < 1) {
            System.out.println("not calling System.exit");
            return;
        }
        System.exit(Integer.parseInt(args[0]));
    }
}
