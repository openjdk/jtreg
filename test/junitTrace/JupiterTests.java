/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

/*
 * @test
 * @run junit JupiterTests
 */
class JupiterTests {

    @BeforeAll
    static void initAll() {
    }

    @BeforeEach
    void init() {
    }

    @Test
    void succeedingTest() {
    }

    @Test
    @Disabled("for demonstration purposes")
    void skippedTest() {
        Assertions.fail("test should NOT be executed");
    }

    @Test
    void abortedTest() {
        Assumptions.assumeTrue(false, "abort test execution mid-flight");
        Assertions.fail("test should have been aborted");
    }

    @ParameterizedTest(name = "[{index}] test(''{0}'')")
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void nullEmptyAndBlankStrings(String text) {
        Assertions.assertTrue(text == null || text.isBlank());
    }

    @Disabled
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void test(int i) {
        System.err.println("i=" + i);
        Assertions.assertNotEquals(1, i);
        System.err.println(i + " is not 1");
    }

    @AfterEach
    void tearDown() {
    }

    @AfterAll
    static void tearDownAll() {
    }

}
