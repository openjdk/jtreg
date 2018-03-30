/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @run main Test 1000
 */

/*
 * @test
 * @run main Test 3000
 */

/*
 * @test
 * @run main Test 5000
 */

public class Test {
    public static void main(String... args) {
        new Test().run(Integer.parseInt(args[0]));
    }

    void run(int size) {
        StringBuilder sb = new StringBuilder();
        sb.append("%6d 890");
        for (int i = 1; i < 9; i++) {
            sb.append("1234567890");
        }
        sb.append("123456789\n");
        String f = sb.toString();
        for (int i = 0; i < size; i += 100) {
            System.out.format(f, i);
        }
    }
}
