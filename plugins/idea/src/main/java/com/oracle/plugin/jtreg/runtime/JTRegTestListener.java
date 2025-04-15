/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The jtreg test listener; this class listens for jtreg test events and maps them into events that the IDE
 * can understand and present back to the user.
 */
public class JTRegTestListener implements Harness.Observer {

    @Override
    public void startingTest(TestResult testResult) {
        System.out.println("##teamcity[testSuiteStarted name='" + escapeName(testResult.getTestName()) + "' " +
                getFileLocationHint(testResult) + "]");
        System.out.println("##teamcity[testStarted name='jtreg' ]");
    }

    private static String getFileLocationHint(TestResult testResult) {
        String location = "";
        try {
            location = "locationHint='file://" + testResult.getDescription().getFile().getCanonicalPath() + "'";
        } catch (TestResult.Fault | IOException e) {
            //do nothing (leave location empty)
        }
        return location;
    }

    @Override
    public void finishedTest(TestResult testResult) {
        final Status status = testResult.getStatus();
        final File file = testResult.getFile();
        if (status.isFailed() || status.isError()) {
            if (file.isFile()) {
                final String output = String.join("\n", loadText(file));
                if (!output.isEmpty()) {
                    System.out.println("##teamcity[testStdOut name='jtreg' " +
                            "out='" + escapeName(output) + "']");
                }
            }
            System.out.println("##teamcity[testFailed name='jtreg' " +
                    "message='" + escapeName(status.getReason()) + "']");
        } else if (status.isNotRun()) {
            System.out.println("##teamcity[testIgnored name='jtreg']");
        }

        try {
            tryReportJUnitResults(file);
        } catch (Throwable t) {
            t.printStackTrace();
            // failed. ignore
        }

        String duration = "0";
        try {
            duration = testResult.getProperty("elapsed").split(" ")[0];
        } catch (Throwable t) {
            //do nothing (leave duration unspecified)
        }
        System.out.println("##teamcity[testFinished name='jtreg' " +
                (!duration.equals("0") ? "duration='" + duration : "") + "'" +
                (!status.isFailed() ? "outputFile='" + escapeName(file.getAbsolutePath()) + "'" : "") +
                " ]");
        System.out.println("##teamcity[testSuiteFinished name='" + escapeName(testResult.getTestName()) + "' ]");
    }

    private void tryReportJUnitResults(File file) {
        if (file.isFile()) {
            Iterator<String> itt = loadText(file).iterator();
            while (itt.hasNext()) {
                String line = itt.next();
                if (line.startsWith("#section:junit")) {
                    reportJUnitSection(itt);
                }
            }
        }
    }

    private static final Pattern JUNIT_TEST_START = Pattern.compile(
            "STARTED\\s+(?<class>[A-Za-z0-9._$]+)::(?<method>[A-Za-z0-9._$]+)\\s+(?<rest>.*)");

    private static void reportJUnitSection(Iterator<String> itt) {
        Deque<String> nesting = new ArrayDeque<>();
        System.out.println("##teamcity[testSuiteStarted name='junit' ]");
        while (itt.hasNext()) {
            String line = itt.next();
            if (line.startsWith("result:")) {
                break; // end of section. Stop parsing
            }
            Matcher m = JUNIT_TEST_START.matcher(line);
            if (m.find()) {
                List<String> stdErr = new ArrayList<>();
                stdErr.add(line);
                String className = m.group("class").replace("$", ".");
                String methodName = m.group("method");
                Iteration iteration = parseIteration(m.group("rest"));
                String displayTestName = methodName + (iteration != null ? " [" + iteration.name() + "]" : "");

                while (!nesting.isEmpty() && !className.contains(nesting.peek())) {
                    System.out.println("##teamcity[testSuiteFinished name='" + simpleClassName(nesting.pop()) + "' ]");
                }
                if (nesting.isEmpty() || !nesting.peek().equals(className)) {
                    nesting.push(className);
                    System.out.println("##teamcity[testSuiteStarted name='" + simpleClassName(className) + "'"
                            + getClassLocationHint(className)
                            + " ]");
                }

                System.out.println("##teamcity[testStarted name='" + escapeName(displayTestName) + "'"
                        + getMethodLocationHint(className, methodName, iteration)
                        + " ]");

                do {
                    line = itt.next();
                    if (line.startsWith("JT Harness has limited the test output")) {
                        // jtharness truncated the output. Discard this result
                        // and look for the next one.
                        continue;
                    }
                    stdErr.add(line);
                } while (!line.startsWith("SUCCESSFUL") && !line.startsWith("ABORTED")
                        && !line.startsWith("SKIPPED") && !line.startsWith("FAILED"));
                System.out.println("##teamcity[testStdErr name='" + escapeName(displayTestName) + "' " +
                        "out='" + escapeName(String.join("\n", stdErr) + '\n') + "']");
                if (line.startsWith("SKIPPED")) {
                    System.out.println("##teamcity[testIgnored name='" + escapeName(displayTestName) + "']");
                } else if (line.startsWith("FAILED")) {
                    System.out.println("##teamcity[testFailed name='" + escapeName(displayTestName) + "' " +
                        "message='']");
                }
                System.out.println("##teamcity[testFinished name='" + escapeName(displayTestName) + "' ]");
            }
        }
        while (!nesting.isEmpty()) {
            System.out.println("##teamcity[testSuiteFinished name='" + simpleClassName(nesting.pop()) + "' ]");
        }
        System.out.println("##teamcity[testSuiteFinished name='junit' ]");
    }

    private static String getClassLocationHint(String className) {
        return "locationHint='jtreg://" + escapeName(className) + "'";
    }

    private static String getMethodLocationHint(String className, String methodName, Iteration iteration) {
        String path = className + "/" + methodName;
        if (iteration != null) {
            path += "/" + iteration.num() + "/" + iteration.name();
        }
        return "locationHint='jtreg://" + escapeName(path) + "'";
    }

    private static final Pattern ITERATION_PATTERN = Pattern.compile("'\\[(?<num>\\d+)] (?<name>.*)'");
    private record Iteration(int num, String name) {}

    private static Iteration parseIteration(String iteration) {
        Matcher m = ITERATION_PATTERN.matcher(iteration);
        if (!m.find()) {
            return null;
        }
        return new Iteration(Integer.parseInt(m.group("num")) - 1, m.group("name")); // convert to zero indexed
    }

    private static String simpleClassName(String className) {
        int lastDot = className.lastIndexOf(".");
        return lastDot != -1 ? className.substring(lastDot + 1) : className;
    }

    @Override
    public void error(String s) {
        System.out.println(s);
    }

    private static String escapeName(String str) {
        return MapSerializerUtil.escapeStr(str);
    }

    private static List<String> loadText(File file) {
        try {
            return Files.readAllLines(file.toPath());
        } catch (IOException e) {
            System.out.println("Failed to load test results.");
            e.printStackTrace(System.out);
            return List.of();
        }
    }
}
