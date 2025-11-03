/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.oracle.plugin.jtreg.runtime;


import com.oracle.plugin.jtreg.util.MapSerializerUtil;
import com.sun.javatest.Harness;
import com.sun.javatest.Status;
import com.sun.javatest.TestResult;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The jtreg test listener; this class listens for jtreg test events and maps them into events that the IDE
 * can understand and present back to the user.
 */
public class JTRegTestListener implements Harness.Observer {

    private static final AtomicReference<String> CURRENT_WRITER = new AtomicReference<>();

    @Override
    public void startingTest(TestResult testResult) {
        if (tryLock(testResult)) {
            tcSuiteStarted(testResult.getTestName(), getFileLocationHint(testResult));
        }
    }

    @Override
    public void finishedTest(TestResult testResult) {
        // For now, we synchronize all output, so that events displays correctly in the IDE
        // even when running tests concurrently (e.g. with -conc:auto).
        // There doesn't seem to be a way to write test events out of order, but still
        // have them display correctly in the IDE, except for writing out the entire
        // even tree first before any testing starts, but we can not do this as we don't
        // know which nested events we are going to get when running tests.
        // The downside of this is that some tests will only start to show up in the UI once they finish,
        // and some test events might 'stall' until a longer running test that grabbed the lock early has finished.
         try {
            if (!hasLock(testResult)) {
                lock(testResult);
                // The lock was held by another test in 'startingTest', so we write this event here.
                tcSuiteStarted(testResult.getTestName(), getFileLocationHint(testResult));
            }
            reportJTRegResult(testResult);
            tryReportJUnitResults(testResult);
            tcSuiteFinished(testResult.getTestName());
        } finally {
            // Release lock if we managed to grab it/held it when entering this method
            // This might be false if an exception occurred before we managed to grab the lock.
            if (hasLock(testResult)) {
                releaseLock();
            }
        }
    }

    private boolean tryLock(TestResult testResult) {
        return CURRENT_WRITER.compareAndSet(null, testResult.getTestName());
    }

    private boolean hasLock(TestResult testResult) {
        return Objects.equals(CURRENT_WRITER.get(), testResult.getTestName());
    }

    private void lock(TestResult testResult) {
        while (!CURRENT_WRITER.compareAndSet(null, testResult.getTestName())) {
            Thread.onSpinWait();
        }
    }

    private void releaseLock() {
        CURRENT_WRITER.set(null);
    }

    private static void reportJTRegResult(TestResult testResult) {
        // report the overall jtreg results as a pseudo test called 'jtreg'
        tcTestStarted("jtreg");
        Status status = testResult.getStatus();
        if (status.isFailed() || status.isError()) {
            tcTestFailed("jtreg", status.getReason());
        } else if (status.isNotRun()) {
            tcTestIgnored("jtreg", status.getReason());
        }

        String duration = null;
        try {
            duration = testResult.getProperty("elapsed").split(" ")[0];
        } catch (Throwable t) {
            //do nothing (leave duration unspecified)
        }
        tcTestFinished("jtreg", duration, testResult.getFile().getAbsolutePath());
    }

    private void tryReportJUnitResults(TestResult testResult) {
        // try to report each 'junit' section of the test results
        // by parsing the junit results from stderr
        for (int i = 0; i < testResult.getSectionCount(); i++) {
            try {
                TestResult.Section section = testResult.getSection(i);
                if (section.getTitle().equals("junit")) {
                    try (Stream<String> lines = section.getOutput("System.err").lines()) {
                        Collection<JUnitResults.TestClass> classes = JUnitResults.parse(lines.iterator());
                        tcSuiteStarted("junit");
                        for (JUnitResults.TestClass cls : classes) {
                            reportTestClass(cls);
                        }
                        tcSuiteFinished("junit");
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
                // failed. ignore.
            }
        }
    }

    private static void reportTestClass(JUnitResults.TestClass testClass) {
        tcSuiteStarted(testClass.simpleName(), classLocationHint(testClass.name()));
        for (JUnitResults.TestMethod test : testClass.testMethods()) {
            reportTestMethod(test, testClass.name());
        }
        for (JUnitResults.TestClass nestedTestClass : testClass.nestedClasses().values()) {
            reportTestClass(nestedTestClass);
        }
        tcSuiteFinished(testClass.simpleName());
    }

    private static void reportTestMethod(JUnitResults.TestMethod test, String className) {
        tcTestStarted(test.name(), methodLocationHint(className, test.methodName(), test.iteration()));
        tcTestStdErr(test.name(), test.stderrLines());
        switch (test.result()) {
            case FAILED -> tcTestFailed(test.name(), "");
            case SKIPPED -> tcTestIgnored(test.name(), test.skipReason());
        }
        tcTestFinished(test.name(), test.duration());
    }

    private static String getFileLocationHint(TestResult testResult) {
        try {
            return "file://" + testResult.getDescription().getFile().getCanonicalPath();
        } catch (TestResult.Fault | IOException e) {
            //do nothing (leave location empty)
            return null;
        }
    }

    private static String classLocationHint(String className) {
        return "jtreg://" + className;
    }

    private static String methodLocationHint(String className, String methodName,
                                             JUnitResults.TestMethod.Iteration iteration) {
        String path = className + "/" + methodName;
        if (iteration != null) {
            path += "/" + iteration.num() + "/" + iteration.name();
        }
        return "jtreg://" + path;
    }

    private static void tcSuiteStarted(String suiteName) {
        tcSuiteStarted(suiteName, null);
    }

    private static void tcSuiteStarted(String suiteName, String locationHint) {
        System.out.println("##teamcity[testSuiteStarted name='" + escapeName(suiteName) + "'"
                + (locationHint != null ? " locationHint='" + escapeName(locationHint) + "'" : "")
                + " ]");
    }

    private static void tcSuiteFinished(String suiteName) {
        System.out.println("##teamcity[testSuiteFinished name='" + escapeName(suiteName) + "' ]");
    }

    private static void tcTestStarted(String testName) {
        tcTestStarted(testName, null);
    }

    private static void tcTestStarted(String testName, String locationHint) {
        System.out.println("##teamcity[testStarted name='" + escapeName(testName) + "'"
                + (locationHint != null ? " locationHint='" + escapeName(locationHint) + "'" : "")
                + " ]");
    }

    private static void tcTestStdErr(String testName, List<String> lines) {
        System.out.println("##teamcity[testStdErr name='" + escapeName(testName) + "' " +
                "out='" + escapeName(String.join("\n", lines) + '\n') + "']");
    }

    private static void tcTestFailed(String testName, String message) {
        System.out.println("##teamcity[testFailed name='" + escapeName(testName) + "' " +
                "message='" + escapeName(message) + "' ]");
    }

    private static void tcTestIgnored(String testName, String reason) {
        System.out.println("##teamcity[testIgnored name='" + escapeName(testName) + "' "
                + "message='" + escapeName(reason) + "']");
    }

    private static void tcTestFinished(String testName, String duration) {
        tcTestFinished(testName, duration, null);
    }

    private static void tcTestFinished(String testName, String duration, String outputFile) {
        System.out.println("##teamcity[testFinished name='" + escapeName(testName)  + "'"
                + (duration != null ? "duration='" + duration + "'" : "")
                + (outputFile != null ? "outputFile='" + escapeName(outputFile) + "'" : "")
                + " ]");
    }

    @Override
    public void error(String s) {
        System.out.println(s);
    }

    private static String escapeName(String str) {
        return MapSerializerUtil.escapeStr(str);
    }

    private static class JUnitResults {
        private record TestMethod(String name, String methodName, Iteration iteration, Result result,
                                  List<String> stderrLines, String duration, String skipReason) {
            enum Result {SUCCESSFUL, SKIPPED, FAILED}

            private record Iteration(int num, String name) {
                private static final Pattern PATTERN = Pattern.compile("\\[(?<num>\\d+)] (?<name>.*)");

                private static Iteration parse(String iteration) {
                    Matcher m = PATTERN.matcher(iteration);
                    if (!m.find()) {
                        return null;
                    }
                    int iterationNum = Integer.parseInt(m.group("num")) - 1; // convert to zero indexed
                    return new Iteration(iterationNum, m.group("name"));
                }
            }
        }

        private record TestClass(String name, String simpleName, List<TestMethod> testMethods,
                                 Map<String, TestClass> nestedClasses) {
            public TestClass(String name, String simpleName) {
                this(name, simpleName, new ArrayList<>(), new HashMap<>());
            }
        }

        private static final Pattern JUNIT_TEST_START = Pattern.compile(
                "(?<kind>STARTED|SKIPPED)\\s+(?<class>[A-Za-z0-9._$]+)::(?<method>[A-Za-z0-9._$]+)\\s+'(?<name>[^']+)'(?: (?<reason>.*))?");

        private static final Pattern JUNIT_TEST_END = Pattern.compile(
                "(?<status>SUCCESSFUL|ABORTED|FAILED)\\s+\\S+ '[^']+' \\[(?<milis>\\d+)ms]");

        private static Collection<TestClass> parse(Iterator<String> itt) {
            Map<String, TestClass> classesByName = new HashMap<>();
            outer:
            while (itt.hasNext()) {
                String line = itt.next();
                if (line.startsWith("result:")) {
                    break; // end of section. Stop parsing
                }
                Matcher m = JUNIT_TEST_START.matcher(line);
                if (m.find()) {
                    List<String> stdErr = new ArrayList<>();
                    stdErr.add(line);
                    String className = m.group("class");
                    String methodName = m.group("method");
                    TestMethod.Iteration iteration = TestMethod.Iteration.parse(m.group("name"));
                    String displayTestName = methodName + (iteration != null ? " [" + iteration.name() + "]" : "");

                    TestMethod.Result result;
                    String duration = null;
                    String skipReason = null;
                    String kind = m.group("kind");
                    if (kind.equals("STARTED")) {
                        Matcher endMatcher;
                        do {
                            line = itt.next();
                            if (line.startsWith("JT Harness has limited the test output")) {
                                // jtharness truncated the output. Discard this result
                                // and look for the next one.
                                continue outer;
                            }
                            stdErr.add(line);
                            endMatcher = JUNIT_TEST_END.matcher(line);
                        } while (!endMatcher.find());

                        String status = endMatcher.group("status");
                        if (status.contains("FAILED") || status.contains("ABORTED")) {
                            result = TestMethod.Result.FAILED;
                        } else {
                            result = TestMethod.Result.SUCCESSFUL;
                        }

                        duration = endMatcher.group("milis"); // may be null for 'SKIPPED'
                    } else {
                        result = TestMethod.Result.SKIPPED;
                        skipReason = m.group("reason");
                    }

                    TestMethod test = new TestMethod(displayTestName, methodName,
                            iteration, result, stdErr, duration, skipReason);
                    String[] nestedClasses = dropPackage(className).split("\\$");
                    String classNameForLookup = className.replace('$', '.');
                    TestClass current = classesByName.computeIfAbsent(nestedClasses[0],
                            k -> new TestClass(classNameForLookup, k));
                    for (int i = 1; i < nestedClasses.length; i++) {
                        current = current.nestedClasses().computeIfAbsent(nestedClasses[i],
                                k -> new TestClass(classNameForLookup, k));
                    }
                    current.testMethods().add(test);
                }
            }
            return classesByName.values();
        }

        private static String dropPackage(String className) {
            int lastDot = className.lastIndexOf(".");
            return lastDot != -1 ? className.substring(lastDot + 1) : className;
        }
    }
}
