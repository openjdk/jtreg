/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import com.sun.javatest.regtest.JDKOpts;

public class JDKOptsTest {
    public static void main(String... args) throws Exception {
        new JDKOptsTest().run();
    }

    @Test
    void testClassPath() {
        String[] opts = { "-classpath", "a", "-classpath", "b" };
        String[] expect = { "-classpath", "a" + PS + "b" };
        test(opts, expect);
    }

    @Test
    void testSourcePath() {
        String[] opts = { "-sourcepath", "a", "-sourcepath", "b" };
        String[] expect = { "-sourcepath", "a" + PS + "b" };
        test(opts, expect);
    }

    @Test
    void testXpatch() {
        String[] opts = { "-Xpatch:a", "-Xpatch:b" };
        String[] expect = { "-Xpatch:a" + PS + "b" };
        test(opts, expect);
    }

    @Test
    void testXpatch2() {
        String[] opts = { "-Xpatch:a", "-Xpatch:a" };
        String[] expect = { "-Xpatch:a" };
        test(opts, expect);
    }

    @Test
    void testXpatch3() {
        String[] opts = { "-Xpatch:a", "-Xpatch:b", "-Xpatch:a" };
        String[] expect = { "-Xpatch:a" + PS + "b" };
        test(opts, expect);
    }

    @Test
    void testXpatch4() {
        String[] opts = { "-Xpatch:a" + PS + "b" + PS + "c", "-Xpatch:c" + PS + "b" + PS + "a" };
        String[] expect = { "-Xpatch:a" + PS + "b" + PS + "c" };
        test(opts, expect);
    }

    @Test
    void testXaddExports() {
        String[] opts = { "-XaddExports:m1/p1=ALL-UNNAMED", "-XaddExports:m2/p2=ALL-UNNAMED" };
        String[] expect = { "-XaddExports:m1/p1=ALL-UNNAMED,m2/p2=ALL-UNNAMED" };
        test(opts, expect);
    }

    @Test
    void testMix() {
        String[] opts = {
            "-classpath", "cp1", "-sourcepath", "sp1", "-Xpatch:xp1", "-XaddExports:m1/p1=ALL-UNNAMED",
            "-classpath", "cp2", "-sourcepath", "sp2", "-Xpatch:xp2", "-XaddExports:m2/p2=ALL-UNNAMED",
            "-classpath", "cp3", "-sourcepath", "sp3", "-Xpatch:xp3", "-XaddExports:m3/p3=ALL-UNNAMED"
        };
        String[] expect = {
            "-classpath", "cp1" + PS + "cp2" + PS + "cp3",
            "-sourcepath", "sp1" + PS + "sp2" + PS + "sp3",
            "-Xpatch:xp1" + PS + "xp2" + PS + "xp3",
            "-XaddExports:m1/p1=ALL-UNNAMED,m2/p2=ALL-UNNAMED,m3/p3=ALL-UNNAMED",
        };
        test(opts, expect);
    }

    static final String PS = File.pathSeparator;

    /** Marker annotation for test cases. */
    @Retention(RetentionPolicy.RUNTIME)
    @interface Test { }

    /**
     * Combo test to run all test cases in all modes.
     */
    void run() throws Exception {
        for (Method m : getClass().getDeclaredMethods()) {
            Annotation a = m.getAnnotation(Test.class);
            if (a != null) {
                try {
                    m.invoke(this);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    throw (cause instanceof Exception) ? ((Exception) cause) : e;
                }
                System.err.println();
            }
        }
        System.err.println(testCount + " tests" + ((errorCount == 0) ? "" : ", " + errorCount + " errors"));
        if (errorCount > 0) {
            throw new Exception(errorCount + " errors found");
        }
    }

    void test(String[] opts, String[] expect) {
        testCount++;
        JDKOpts jdkOpts = new JDKOpts();
        jdkOpts.addAll(opts);
        List<String> result = jdkOpts.toList();
        System.out.println("Options: " + Arrays.toString(opts));
        System.out.println("Expect:  " + Arrays.toString(expect));
        System.out.println("Found:   " + result);
        if (!result.equals(Arrays.asList(expect))) {
            System.out.println("ERROR");
            errorCount++;
        }
    }

    int testCount;
    int errorCount;
}

