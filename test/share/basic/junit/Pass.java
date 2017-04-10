/*
 * Copyright (c) 1998, 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit Pass
 */

/* @test
 * @summary Failed: Execution passed unexpectedly
 * @run junit/fail Pass
 */

/* @test
 * @summary Error: Parse Exception: Bad argument provided for class in `junit'
 * @run junit Pass badarg
 */

/* @test
 * @summary Error: Parse Exception: Bad argument provided for class in `junit'
 * @run junit/fail Pass badarg
 */

/* @test
 * @summary Passed: Execution successful
 * @run junit/othervm Pass
 */

/* @test
 * @summary Failed: Execution passed unexpectedly
 * @run junit/othervm/fail Pass
 */

/* @test
 * @summary Error: Parse Exception: Bad argument provided for class in `junit'
 * @run junit/othervm Pass badarg
 */

/* @test
 * @summary Error: Parse Exception: Bad argument provided for class in `junit'
 * @run junit/othervm/fail Pass badarg
 */

import org.junit.*;
import static org.junit.Assert.*;

public class Pass
{
    @Test
    public void test1() {
    }

    @Test
    public void test2() {
    }

    @Test
    public void test3() {
    }
}
