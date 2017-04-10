/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @clean DirTest
 * @clean dir.DirTest
 * @run compile dir/DirTest.java
 * @run main dir.DirTest
 */

/* @test
 * @summary Error: Test Class Exception: Can't find source file: dir.DirTest.java
 * @clean DirTest
 * @clean dir.DirTest
 * @run compile dir.DirTest.java
 */

/* test
 * @summary Error: Can't load test: java.lang.ClassNotFoundException: dir/DirTest
 * @clean DirTest
 * @clean dir.DirTest
 * @run compile dir/DirTest.java
 * @run main dir/DirTest
 */

// Verify that we can compile and run a file specified by classname only
// Verify that the class names are properly specified.

public class DirTest {
    public static void main(String [] args) {
        // If we accidentally compile this file instead of the one in the
        // subdirectory, something is wrong!
        throw RuntimeException("Wrong file compiled/run!");
    }
}
