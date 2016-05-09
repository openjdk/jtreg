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
    void testAddMods() {
        String[] opts = { "-addmods", "m1,m2", "-addmods", "m2,m3" };
        String[] expect = { "-addmods", "m1,m2,m3" };
        test(opts, expect);
    }

    @Test
    void testLimitMods() {
        String[] opts = { "-limitmods", "m2,m1", "-limitmods", "m3,m2" };
        String[] expect = { "-limitmods", "m2,m1,m3" };
        test(opts, expect);
    }

    @Test
    void testXpatch_sameModule_differentPatches() {
        String[] opts = { "-Xpatch:a=" + file(patchDir1, "a"), "-Xpatch:a=" + file(patchDir2, "a") };
        String[] expect = { "-Xpatch:a=" + file(patchDir1, "a") + PS + file(patchDir2, "a") };
        test(opts, expect);
    }

    @Test
    void testXpatch_differentModules() {
        String[] opts = { "-Xpatch:a=" + file(patchDir1, "a"), "-Xpatch:b=" + file(patchDir2, "b") };
        String[] expect = { "-Xpatch:a=" + file(patchDir1, "a"), "-Xpatch:b=" + file(patchDir2, "b") };
        test(opts, expect);
    }

    @Test
    void testXaddExports() {
        String[] opts = { "-XaddExports:m1/p1=ALL-UNNAMED", "-XaddExports:m2/p2=ALL-UNNAMED", "-XaddExports:m1/p1=m11" };
        String[] expect = { "-XaddExports:m1/p1=ALL-UNNAMED,m11", "-XaddExports:m2/p2=ALL-UNNAMED" };
        test(opts, expect);
    }

    @Test
    void testMix() {
        String[] opts = {
            "-classpath", "cp1", "-sourcepath", "sp1", "-Xpatch:xp1=xp1", "-XaddExports:m1/p1=ALL-UNNAMED",
            "-classpath", "cp2", "-sourcepath", "sp2", "-Xpatch:xp2=xp2", "-XaddExports:m2/p2=ALL-UNNAMED",
            "-classpath", "cp3", "-sourcepath", "sp3", "-Xpatch:xp3=xp3", "-XaddExports:m3/p3=ALL-UNNAMED",
            "-addmods", "m1,m2,m3",
            "-limitmods", "m1,m2,m3",
            "-Xpatch:xp1=xp1a",
            "-Xpatch:xp2=xp2a",
            "-Xpatch:xp3=xp3a",
            "-XaddExports:m1/p1=m11",
            "-XaddExports:m2/p2=m22",
            "-XaddExports:m3/p3=m33",
            "-addmods", "m2,m3,m4",
            "-limitmods", "m2,m3,m4",
        };
        String[] expect = {
            "-classpath", "cp1" + PS + "cp2" + PS + "cp3",
            "-sourcepath", "sp1" + PS + "sp2" + PS + "sp3",
            "-Xpatch:xp1=xp1" + PS + "xp1a",
            "-XaddExports:m1/p1=ALL-UNNAMED,m11",
            "-Xpatch:xp2=xp2" + PS + "xp2a",
            "-XaddExports:m2/p2=ALL-UNNAMED,m22",
            "-Xpatch:xp3=xp3" + PS + "xp3a",
            "-XaddExports:m3/p3=ALL-UNNAMED,m33",
            "-addmods", "m1,m2,m3,m4",
            "-limitmods", "m1,m2,m3,m4"
        };
        test(opts, expect);
    }

    static final String PS = File.pathSeparator;

    /** Marker annotation for test cases. */
    @Retention(RetentionPolicy.RUNTIME)
    @interface Test { }

    final File patchDir1;
    final File patchDir2;

    JDKOptsTest() {
        File baseDir = new File(System.getProperty("java.io.tmpdir"));
        System.err.println("baseDir: " + baseDir);
        patchDir1 = new File(baseDir, "patch1");
        createDummyPatches(patchDir1, "a", "b", "c");

        patchDir2 = new File(baseDir, "patch2");
        createDummyPatches(patchDir2, "a", "b", "c");
    }

    void createDummyPatches(File patchDir, String... modules) {
        for (String m: modules) {
            new File(patchDir, m).mkdirs();
        }
    }

    File file(File dir, String name) {
        return new File(dir, name);
    }

    /**
     * Combo test to run all test cases in all modes.
     */
    void run() throws Exception {
        for (Method m : getClass().getDeclaredMethods()) {
            Annotation a = m.getAnnotation(Test.class);
            if (a != null) {
                try {
                    System.out.println("Test: " + m.getName());
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

