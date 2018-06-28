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
 * @run main MainTest
 * @run main MainTest --out 1
 * @run main MainTest --out 5
 * @run main MainTest --err 1
 * @run main MainTest --err 5
 */

import java.io.PrintStream;

public class MainTest {
    private static final String lorem =
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor "
        + "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis "
        + "nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. "
        + "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore "
        + "eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, "
        + "sunt in culpa qui officia deserunt mollit anim id est laborum.";

    public static void main(String... args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--out") && i + 1 < args.length) {
                write(System.out, Integer.valueOf(args[++i]));
            } else if (arg.equals("--err") && i + 1 < args.length) {
                write(System.err, Integer.valueOf(args[++i]));
            } else {
                throw new Error("unknown arg: " + arg);
            }
        }
    }

    private static void write(PrintStream out, int n) {
        for (int i = 0; i < n; i++) {
            out.println(lorem);
        }
    }
}
