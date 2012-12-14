/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest;

import com.sun.javatest.Status;

public class StatsTest {
    static String[][] tests = {
        { "-p:1", "-f:10", "-e:100", "-n:1000",
                "--format:passed:%p failed:%f error:%e not run:%n",
                "passed:1 failed:10 error:100 not run:1000",
                "--format:%?{passed:p}% %?{failed:f}% %?{error:e}% %?{not run:n}",
                "passed:1 failed:10 error:100 not run:1000" },
        { "--format:%?{passed:p}% %?{failed:f}% %?{error:e}% %?{not run:n}",
                "-p:1",  "passed:1",
                "-f:10", "passed:1 failed:10",
                "-p:0",  "failed:10" },
        { "--format:%?{passed:p}%,% %?{failed:f}%,% %?{error:e}%,% %?{not run:n}",
                "-p:1",  "passed:1",
                "-f:10", "passed:1, failed:10",
                "-p:0",  "failed:10" },
        { "--format:%?{passed:p}% %?{failed:F}% %?{not run:n}",
                "-p:1",   "passed:1",
                "-f:10",  "passed:1 failed:10",
                "-e:100", "passed:1 failed:110",
                "-p:0",   "failed:110" },
        { "--format:%z", "%z" },
        { "--format:%?z", "%?z" },
        { "--format:%?{textz}", "%?{textz}" },
        { "--format:%?{text", "%?{text" },
    };

    public static void main(String... args) {
        StatsTest t = new StatsTest();
        try {
            if (args.length > 0)
                t.run(args);
            else {
                for (String[] test: tests) {
                    t.reset();
                    t.run(test);
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    void run(String... args) throws Exception {
        for (String arg: args) {
            if (arg.startsWith("-e:")) {
                ts.counts[Status.ERROR] = Integer.valueOf(arg.substring(3));
            } else if (arg.startsWith("-f:")) {
                ts.counts[Status.FAILED] = Integer.valueOf(arg.substring(3));
            } else if (arg.startsWith("-n:")) {
                ts.counts[Status.NOT_RUN] = Integer.valueOf(arg.substring(3));
            } else if (arg.startsWith("-p:")) {
                ts.counts[Status.PASSED] = Integer.valueOf(arg.substring(3));
            } else if (arg.startsWith("-i:")) {
                ts.ignored = Integer.valueOf(arg.substring(3));
            } else if (arg.startsWith("-x:")) {
                ts.excluded = Integer.valueOf(arg.substring(3));
            } else if (arg.equals("-0")) {
                reset();
            } else if (arg.startsWith("--format:")) {
                format = arg.substring(9);
            } else if (arg.startsWith("-"))
                throw new IllegalArgumentException(arg);
            else
                test(arg);
        }

        if (errors > 0)
            throw new Exception(errors + " errors occurred");
    }

    void reset() {
        ts.counts[Status.ERROR] = 0;
        ts.counts[Status.FAILED] = 0;
        ts.counts[Status.NOT_RUN] = 0;
        ts.counts[Status.PASSED] = 0;
        ts.excluded = 0;
        ts.ignored = 0;
    }

    void test(String expect) {
        System.err.println("test: " + expect);
        String result = ts.getText(format);
        if (!equal(expect, result)) {
            error("unexpected result");
            System.err.println("    expected: '" + expect + "'");
            System.err.println("       found: '" + result + "'");
        }
    }

    void error(String msg) {
        System.err.println("Error: " + msg);
        errors++;
    }

    static <T> boolean equal(T t1, T t2) {
        return (t1 == null) ? (t2 == null) : t1.equals(t2);
    }



    TestStats ts = new TestStats();
    String format;
    int errors;
}
