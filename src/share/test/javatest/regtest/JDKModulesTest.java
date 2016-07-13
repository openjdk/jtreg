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

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.sun.javatest.TestSuite;
import com.sun.javatest.WorkDirectory;
import com.sun.javatest.regtest.JDK;
import com.sun.javatest.regtest.RegressionParameters;
import com.sun.javatest.regtest.RegressionTestSuite;

public class JDKModulesTest {
    public static void main(String... args) throws Exception {
        new JDKModulesTest().run(args);
    }

    private final JDK jdk;
    private final RegressionTestSuite dummyTestSuite;
    private final WorkDirectory dummyWorkDir;

    JDKModulesTest() throws IOException, TestSuite.Fault, WorkDirectory.Fault {
        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Path testSuiteDir = tmpDir.resolve("dummyTestSuite");
        Files.createDirectories(testSuiteDir);
        try (Writer out = new FileWriter(testSuiteDir.resolve("TEST.ROOT").toFile())) {
            out.write("useNewOptions=true"); // temporary, during transition
        }
        dummyTestSuite = RegressionTestSuite.open(testSuiteDir.toFile(), null);

        Path dwd = tmpDir.resolve("dummyWorkDir");
        dummyWorkDir = Files.exists(dwd)
                ? WorkDirectory.open(dwd.toFile(), dummyTestSuite)
                : WorkDirectory.create(dwd.toFile(), dummyTestSuite);

        jdk = JDK.of(System.getProperty("java.home"));
    }

    @Test
    void testNoVMOpts() throws TestSuite.Fault {
        test(Collections.<String>emptyList(),
                Arrays.asList("java.desktop", "jdk.compiler", "java.corba"),
                Arrays.asList("java.corba"));
    }

    @Test
    void testAddAllSystem() throws TestSuite.Fault {
        test(Arrays.asList("--add-modules", "ALL-SYSTEM"),
                Arrays.asList("java.desktop", "jdk.compiler", "java.corba"),
                Collections.<String>emptyList());
    }

    @Test
    void testLimitJDKCompiler() throws TestSuite.Fault {
        test(Arrays.asList("--limit-modules", "jdk.compiler"),
                Arrays.asList("java.desktop", "jdk.compiler", "java.corba"),
                Arrays.asList("java.desktop", "java.corba"));
    }


    void run(String... args) throws Exception {
        if (args.length == 0) {
            runTests();
        } else {
            showModules(Arrays.asList(args));
        }
    }

    void showModules(List<String> vmOpts) throws TestSuite.Fault {
        RegressionParameters params = dummyTestSuite.createInterview();
        params.setWorkDirectory(dummyWorkDir);
        params.setTestVMOptions(vmOpts);

        jdk.getVersion(params);

        Set<String> defaultModules = new TreeSet<>(jdk.getDefaultModules(params));
        Set<String> systemModules = new TreeSet<>(jdk.getSystemModules(params));

        System.err.println("default modules: (" + defaultModules.size() + ") " + defaultModules);
        System.err.println("system modules: (" + systemModules.size() + ") " + systemModules);

        Set<String> nonDefaultModules = new TreeSet<>(systemModules);
        nonDefaultModules.removeAll(defaultModules);
        System.err.println("non-default modules: (" + nonDefaultModules.size() + ") " + nonDefaultModules);
    }

    /** Marker annotation for test cases. */
    @Retention(RetentionPolicy.RUNTIME)
    @interface Test { }

    /**
     * Combo test to run all test cases in all modes.
     */
    void runTests() throws Exception {
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

    void test(List<String> vmOpts,
            List<String> modules,
            List<String> expectNonDefaultModules) throws TestSuite.Fault {
        testCount++;

        RegressionParameters params = dummyTestSuite.createInterview();
        params.setWorkDirectory(dummyWorkDir);
        params.setTestVMOptions(vmOpts);

        jdk.getVersion(params);

        Set<String> defaultModules = new TreeSet<>(jdk.getDefaultModules(params));

        List<String> foundNonDefaultModules = new ArrayList<>();
        for (String m : modules) {
            if (!defaultModules.contains(m)) {
                foundNonDefaultModules.add(m);
            }
        }

        System.err.println("Expect: " + expectNonDefaultModules);
        System.err.println("Found:  " + foundNonDefaultModules);

        if (!foundNonDefaultModules.equals(expectNonDefaultModules)) {
            System.out.println("ERROR");
            errorCount++;
        }

    }

    int testCount;
    int errorCount;
}
