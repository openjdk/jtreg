/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.exec;

import java.util.regex.Pattern;

public class BuildPatternTest {
    public static void main(String... args) {
        new BuildPatternTest().run();
    }

    void run() {
        String[] valid = {
            "C",
            "p.C",
            "p.q.C",
            "_C",
            "_p._C",
            "_p._q._C",
            "*",
            "p.*",
            "p.q.*",
            "m/module-info",
            "m/p.C",
            "m/p.q.C",
            "m/_C",
            "m/_p._C",
            "m/_p._q._C",
            "m/*",
            "m/p.*",
            "m/p.q.*",
            "_m/module-info",
            "_m/p.C",
            "_m/p.q.C",
            "_m/_C",
            "_m/_p._C",
            "_m/_p._q._C",
            "_m/*",
            "_m/p.*",
            "_m/p.q.*",
            "m.n/module-info",
            "m.n/p.C",
            "m.n/p.q.C",
            "m.n/_C",
            "m.n/_p._C",
            "m.n/_p._q._C",
            "m.n/*",
            "m.n/p.*",
            "m.n/p.q.*",
            "_HelloImpl_Tie"
        };

        String[] invalid = {
            "p*",
            ".*",
            "p/q/C",
            "m/"
        };

        Pattern ptn = BuildAction.BUILD_PTN;
        int errors = 0;

        System.out.printf("Checking valid arguments:%n");
        for (String s: valid) {
            if (ptn.matcher(s).matches()) {
                System.out.printf("%-15s OK%n", s);
            } else {
                System.out.printf("%-15s ERROR%n", s);
                errors++;
            }
        }

        System.out.println();
        System.out.printf("Checking invalid arguments:%n");
        for (String s: invalid) {
            if (ptn.matcher(s).matches()) {
                System.out.printf("%-15s ERROR%n", s);
                errors++;
            } else {
                System.out.printf("%-15s OK%n", s);
            }
        }

        if (errors > 0) {
            System.out.printf("%d errors occurred%n", errors);
            System.exit(1);
        }
    }
}

