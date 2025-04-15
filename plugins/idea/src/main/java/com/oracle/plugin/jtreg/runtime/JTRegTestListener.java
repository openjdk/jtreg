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

/**
 * The jtreg test listener; this class listens for jtreg test events and maps them into events that the IDE
 * can understand and present back to the user.
 */
public class JTRegTestListener implements Harness.Observer {

    @Override
    public void startingTest(TestResult testResult) {
        System.out.println("##teamcity[testSuiteStarted name='" + escapeName(testResult.getTestName()) + "' " +
                getFileLocationHint(testResult) + "]");
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

    private record Result(Kind kind, String reason) {
        enum Kind { SUCCESS, FAILED, SKIPPED }
    }

    private record Section(String name, List<String> lines) {
        Result findResult() {
            for (String line : lines.reversed()) {
                if (line.startsWith("result: ")) {
                    int firstDot = line.indexOf('.');
                    String result = line.substring("result: ".length(), firstDot);
                    String reason = line.substring(firstDot + 1);
                    Result.Kind resultKind = switch (result) {
                        case "Failed" -> Result.Kind.FAILED;
                        case "Skipped" -> Result.Kind.SKIPPED;
                        default -> Result.Kind.SUCCESS;
                    };
                    return new Result(resultKind, reason);
                }
            }
            return new Result(Result.Kind.SUCCESS, "");
        }
    }

    @Override
    public void finishedTest(TestResult testResult) {
        final File file = testResult.getFile();
        List<String> outLines = loadText(file);
        List<Section> sections = parseSections(outLines);

        // report sections
        for (Section section : sections) {
            if (section.name().equals("junit")) {
                reportJUnitResults(section.lines());
            } else {
                reportSection(section);
            }
        }

        String duration = "0";
        try {
            duration = testResult.getProperty("elapsed").split(" ")[0];
        } catch (Throwable t) {
            //do nothing (leave duration unspecified)
        }
        System.out.println("##teamcity[testSuiteFinished name='" + escapeName(testResult.getTestName()) + "' "
                + (!duration.equals("0") ? "duration='" + duration : "")  + "'"
                + " ]");
    }

    private static void reportSection(Section section) {
        System.out.println("##teamcity[testStarted name='" + escapeName(section.name()) + "' ]");
        System.out.println("##teamcity[testStdOut name='" + escapeName(section.name()) + "' " +
                    "out='" + escapeName(String.join("\n", section.lines()) + '\n') + "']");
        Result result = section.findResult();
        switch (result.kind) {
            case FAILED -> System.out.println("##teamcity[testFailed name='" + escapeName(section.name()) + "' " +
                    "message='" + escapeName(result.reason()) + "' ]");
            case SKIPPED -> System.out.println("##teamcity[testIgnored name='" + escapeName(section.name()) + "' ]");
        }
        System.out.println("##teamcity[testFinished name='" + escapeName(section.name()) + "' ]");
    }

    private static List<Section> parseSections(List<String> outLines) {
        List<Section> sections = new ArrayList<>();
        String currentSection = "test description";
        List<String> sectionLines = new ArrayList<>();
        for (String line : outLines) {
            if (line.startsWith("#section:")) {
                sections.add(new Section(currentSection, List.copyOf(sectionLines)));
                sectionLines.clear();
                currentSection = line.substring("#section:".length());
            }
            sectionLines.add(line);
        }
        // final section
        sections.add(new Section(currentSection, List.copyOf(sectionLines)));
        return sections;
    }

    private static void reportJUnitResults(List<String> lines) {
        Deque<String> nesting = new ArrayDeque<>();
        Iterator<String> itt = lines.iterator();
        System.out.println("##teamcity[testSuiteStarted name='junit' ]");
        System.out.println("##teamcity[testStarted name='jtreg output' ]");
        while (itt.hasNext()) {
            String line = itt.next();
            if (line.startsWith("STARTED")) {
                List<String> stdErr = new ArrayList<>();
                stdErr.add(line);
                String[] logParts = line.split("\\s+", 3);
                String testName = logParts[1];
                String[] testNameParts = testName.split("::");
                String rawClassName = testNameParts[0];
                String className = rawClassName.replace("$", ".");
                String methodName = testNameParts[1];

                if (!nesting.isEmpty() && !className.contains(nesting.peek())) {
                    System.out.println("##teamcity[testSuiteFinished name='" + simpleClassName(nesting.pop()) + "' ]");
                }
                if (nesting.isEmpty() || !nesting.peek().equals(className)) {
                    nesting.push(className);
                    System.out.println("##teamcity[testSuiteStarted name='" + simpleClassName(className) + "'"
                            + " locationHint='jtreg://" + escapeName(rawClassName) + "'"
                            + " ]");
                }

                System.out.println("##teamcity[testStarted name='" + escapeName(methodName) + "'"
                        // TODO add iteration as meta info
                        + " locationHint='jtreg://" + escapeName(rawClassName + "::" + methodName) + "'"
                        + " ]");

                do {
                    line = itt.next();
                    stdErr.add(line);
                } while (!line.startsWith("SUCCESSFUL") && !line.startsWith("ABORTED")
                        && !line.startsWith("SKIPPED") && !line.startsWith("FAILED"));
                System.out.println("##teamcity[testStdErr name='" + escapeName(methodName) + "' " +
                        "out='" + escapeName(String.join("\n", stdErr) + '\n') + "']");
                if (line.startsWith("SKIPPED")) {
                    System.out.println("##teamcity[testIgnored name='" + escapeName(methodName) + "']");
                } else if (line.startsWith("FAILED")) {
                    System.out.println("##teamcity[testFailed name='" + escapeName(methodName) + "' " +
                        "message='']");
                }
                System.out.println("##teamcity[testFinished name='" + escapeName(methodName) + "' ]");
            }
        }
        while (!nesting.isEmpty()) {
            System.out.println("##teamcity[testSuiteFinished name='" + simpleClassName(nesting.pop()) + "' ]");
        }
        // report other output
        System.out.println("##teamcity[testStdOut name='jtreg output' " +
                    "out='" + escapeName(String.join("\n", lines) + '\n') + "']");
        System.out.println("##teamcity[testFinished name='jtreg output' ]");
        System.out.println("##teamcity[testSuiteFinished name='junit' ]");
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
