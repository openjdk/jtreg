/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.SkipException;

/*
 * @test
 * @run testng Test
 */
public class Test {

    @org.testng.annotations.Test
    public void alwaysPass() {
        System.out.println("running alwaysPass");
    }

    @org.testng.annotations.Test
    public void alwaysFail() throws Exception {
        System.out.println("running alwaysFail");
        // intentionally sleep for while to allow the test duration to report
        // a value that has more than one digit
        Thread.sleep(30);
        throw new RuntimeException("intentional failure from alwaysFail");
    }

    @org.testng.annotations.Test
    public void alwaysSkip() throws Exception {
        System.out.println("running alwaysFail");
        Thread.sleep(2);
        throw new SkipException("intentionally skipped from alwaysSkip");
    }

    @org.testng.annotations.Test(enabled = false)
    public void disabledTest() {
        throw new RuntimeException("should not have been invoked");
    }
}
