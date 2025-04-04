/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package p;

/*
 * @test
 * @run main ${test.main.class} --test.src ${test.src}
 */

import java.util.Arrays;
import java.util.Objects;

public class Test3 {
    private static final boolean expectDollar = false; // opt-auto, i.e. in

    public static void main(String... args) throws Exception {
        System.out.println(Arrays.toString(args));

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String value = args[++i];

            switch (arg) {
                case "--test.src":
                case "--test.classes":
                case "--test.class.path":
                    if (expectDollar) {
                        checkEqual(value, "${" + arg.substring(2) + "}");
                    } else {
                        checkEqual(value, System.getProperty(arg.substring(2)));
                    }
                    break;
            }
        }
    }

    private static void checkEqual(String found, String expect) throws Exception {
        if (!Objects.equals(found, expect)) {
            System.err.println("Error: mismatch");
            System.err.println("  Expect: " + expect);
            System.err.println("  Found:  " + found);
            throw new Exception("Command line not as expected.");
        }
    }
}

