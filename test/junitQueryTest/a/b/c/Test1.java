/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * A collection of test methods, to help exercise the query mechanism.
 * Each method just prints its name.
 */
public class Test1 {
    @Test
    public void m11() {
        System.out.println("Test1.m11");
    }

    @Test
    public void m12() {
        System.out.println("Test1.m12");
    }

    @Test
    public void m13() {
        System.out.println("Test1.m13");
    }

    @ParameterizedTest
    @MethodSource("params")
    public void parameterized(String str,
                              NestedClass nested,
                              boolean z, byte b, char c, short s, int i, long l, float f, double d,
                              String[] stra,
                              boolean[] za, byte[] ba, char[] ca, short[] sa, int[] ia, long[] la, float[] fa, double[] da) {
        System.out.println("Test1.parameterized");
    }

    static Stream<Arguments> params() {
        return Stream.of(
            arguments(
                    "a",
                    new NestedClass(),
                    true, (byte) 42, 'x', (short) 42, 42, 42L, 42.0F, 42.0D,
                    new String[0],
                    new boolean[0], new byte[0], new char[0], new short[0], new int[0], new long[0], new float[0], new double[0]
            )
        );
    }

    static class NestedClass {}

    @Nested
    class NestedTests {
        @Test
        public void nested() {
            System.out.println("Test1.nested");
        }
    }
}