/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @requires 1 + 1 == 2
 * @requires 2 - 1 == 1
 * @requires 2 * 3 == 6
 * @requires 6 / 3 == 2
 * @requires 6 % 3 == 0
 * @requires true
 * @requires !false
 * @requires true & !false
 * @requires 1 < 2 & !(1 > 2) & 1 <= 2
 * @requires 1 <= 1 & 1 >= 1
 * @requires 2 > 1 & !(2 < 1) & 2 >= 1
 * @requires 1 == "1"
 * @requires 1G == 1024M
 * @requires 1M == 1024K
 * @requires file.separator == "/" | file.separator == "\\"
 * @requires line.separator == "\n" | file.separator == "\r\n"
 * @requires os.maxMemory > 1M
 * @requires os.maxSwap > 1M
 * @run main ExprTest
 */

import java.util.*;

public class ExprTest {
    public static void main(String... args) {
    }
}
