/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.report;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javatest.TestDescription;
import com.sun.javatest.TestResult;
import com.sun.javatest.WorkDirectory;
import com.sun.javatest.regtest.agent.ActionHelper.OutputHandler.OutputKind;

/**
 * Class to generate aggregate reports for collections of tests, such as JUnit and TestNG tests.
 */
public abstract class SummaryReporter {
    private static final Map<WorkDirectory, Map<Class<?>, SummaryReporter>> instanceMap
            = new WeakHashMap<>();

    /**
     * A summary reporter that aggregates info for TestNG tests, using info written
     * to stdout in each test by TestNG.
     *
     * @param wd the work directory for the test run
     * @return the summary reporter
     */
    public static synchronized SummaryReporter forTestNG(WorkDirectory wd) {
        return instanceMap.computeIfAbsent(wd, wd_ -> new HashMap<>())
                .computeIfAbsent(TestNGSummaryReporter.class, c_ -> new TestNGSummaryReporter());
    }

    /**
     * A summary reporter that aggregates info for JUnit tests, using info written
     * to stderr in each test by JUnitRunner, using a SummaryGeneratingListener.
     *
     * @param wd the work directory for the test run
     * @return the summary reporter
     */
    public static synchronized SummaryReporter forJUnit(WorkDirectory wd) {
        return instanceMap.computeIfAbsent(wd, wd_ -> new HashMap<>())
                .computeIfAbsent(JUnitSummaryReporter.class, c_ -> new JUnitSummaryReporter());
    }

    /**
     * Returns {@code true} if there is no content to be shown.
     *
     * @return {@code true} if there is no content to be shown
     */
    public abstract boolean isEmpty();

    /**
     * Adds the results for an action in a test.
     *
     * @param tr the test result for the test
     * @param s  the section containing the output for the action
     */
    public abstract void add(TestResult tr, TestResult.Section s);

    /**
     * Writes a summary report about the tests that executed.
     *
     * @param reportDir the directory in which to write the report
     * @throws IOException if there is a problem writing the report
     */
    public abstract void writeReport(File reportDir) throws IOException;

    /**
     * A summary reporter that aggregates info for TestNG tests, using info written
     * to stdout in each test by TestNG.
     */
    private static class TestNGSummaryReporter extends SummaryReporter {

        private final Map<String, Info> infoMap = new TreeMap<>();

        @Override
        public boolean isEmpty() {
            return infoMap.isEmpty();
        }

        static final String testsPrefix = "Total tests run:";
        static final Pattern testsPattern = Pattern.compile("Total tests run: ([0-9]+), Passes: ([0-9]+), Failures: ([0-9]+), Skips: ([0-9]+)");
        static final String configPrefix = "Configuration Failures:";
        static final Pattern configPattern = Pattern.compile("[^0-9]+([0-9]+)[^0-9]+([0-9]+)[^0-9]*");

        @Override
        public synchronized void add(TestResult tr, TestResult.Section s) {
            try {
                TestDescription td = tr.getDescription();
                String group = td.getParameter("packageRoot");
                if (group == null)
                    group = td.getRootRelativePath();
                Info info = infoMap.computeIfAbsent(group, __ -> new Info());
                String out = s.getOutput(OutputKind.STDOUT.name);
                if (out != null) {
                    Matcher tm = getMatcher(out, testsPrefix, testsPattern);
                    if (tm != null && tm.matches()) {
                        info.count += Integer.parseInt(tm.group(1));
                        // info.successCount += Integer.parseInt(tm.group(2));
                        info.failureCount += Integer.parseInt(tm.group(3));
                        info.skippedCount += Integer.parseInt(tm.group(4));
                    }
                    Matcher cm = getMatcher(out, configPrefix, configPattern);
                    if (cm != null && cm.matches()) {
                        info.configFailureCount += Integer.parseInt(cm.group(1));
                        info.configSkippedCount += Integer.parseInt(cm.group(2));
                    }
                }
            } catch (TestResult.Fault e) {
                // should not occur with tr still in memory
            }
        }

        private Matcher getMatcher(String out, String prefix, Pattern p) {
            int pos = out.lastIndexOf(prefix);
            if (pos == -1)
                return null;

            int endPos = out.indexOf("\n", pos);
            if (endPos == -1)
                return null;

            return p.matcher(out.substring(pos, endPos).strip()); // get rid of any "\r", too
        }

        @Override
        public void writeReport(File reportDir) throws IOException {
            File reportTextDir = new File(reportDir, "text");
            reportTextDir.mkdirs();
            File f = new File(reportTextDir, "testng.txt");
            try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)))) {
                for (Map.Entry<String, Info> e : infoMap.entrySet()) {
                    out.println(e.getKey() + " " + e.getValue());
                }
            }
        }

        static class Info {
            int count;
            //        int successCount;
            int failureCount;
            int skippedCount;
            int configFailureCount;
            int configSkippedCount;

            @Override
            public String toString() {
                return "total: " + count
                        + ", passed: " + (count - failureCount - skippedCount)
                        + ", failed: " + failureCount
                        + ", skipped: " + skippedCount
                        + ", config failed: " + configFailureCount
                        + ", config skipped: " + configSkippedCount;
            }
        }
    }

    /**
     * A summary reporter that aggregates info for JUnit tests, using info written
     * to stderr in each test by JUnitRunner, using a SummaryGeneratingListener.
     */
    private static class JUnitSummaryReporter extends SummaryReporter {

        private final Map<String, Info> infoMap = new TreeMap<>();

        @Override
        public boolean isEmpty() {
            return infoMap.isEmpty();
        }

        static final Pattern infoPattern = Pattern.compile("(?s)\\[ JUnit Containers:.*JUnit Tests:.*]");
        static final Pattern numberPattern = Pattern.compile("[0-9]+");

        @Override
        public synchronized void add(TestResult tr, TestResult.Section s) {
            try {
                TestDescription td = tr.getDescription();
                String group = td.getParameter("packageRoot");
                if (group == null)
                    group = td.getRootRelativePath();
                Info info = infoMap.computeIfAbsent(group, g -> new Info());
                String out = s.getOutput(OutputKind.STDERR.name);
                if (out != null) {
                    Matcher m1 = infoPattern.matcher(out);
                    if (m1.find()) {
                        Matcher m2 = numberPattern.matcher(m1.group());

                        info.containers.count += nextInt(m2);
                        info.containers.started += nextInt(m2);
                        info.containers.succeeded += nextInt(m2);
                        info.containers.failed += nextInt(m2);
                        info.containers.aborted += nextInt(m2);
                        info.containers.skipped += nextInt(m2);
                        info.tests.count += nextInt(m2);
                        info.tests.started += nextInt(m2);
                        info.tests.succeeded += nextInt(m2);
                        info.tests.failed += nextInt(m2);
                        info.tests.aborted += nextInt(m2);
                        info.tests.skipped += nextInt(m2);
                    }
                }
            } catch (TestResult.Fault e) {
                // should not occur with tr still in memory
            }
        }

        private int nextInt(Matcher m) {
            return m.find() ? Integer.parseInt(m.group()) : 0;
        }

        @Override
        public void writeReport(File reportDir) throws IOException {
            File reportTextDir = new File(reportDir, "text");
            reportTextDir.mkdirs();
            File f = new File(reportTextDir, "junit.txt");
            try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)))) {
                for (Map.Entry<String, Info> e: infoMap.entrySet()) {
                    out.println(e.getKey() + " " + e.getValue());
                }
            }
        }

        static class Counts {
            int count;
            int started;
            int succeeded;
            int failed;
            int aborted;
            int skipped;
            public String toString() {
                return count
                        + ", skipped: " + skipped
                        + ", started: " + started
                        + ", succeeded: " + succeeded
                        + ", failed: " + failed
                        + ", aborted: " + aborted;
            }
        }

        static class Info {
            final Counts containers = new Counts();
            final Counts tests = new Counts();

            @Override
            public String toString() {
                return "containers: " + containers + "; tests: " + tests;
            }
        }
    }

}
