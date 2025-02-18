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
import com.oracle.plugin.jtreg.util.MapSerializerUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The jtreg test listener; this class listens for jtreg test events and maps them into events that the IDE
 * can understand and present back to the user.
 */
public class JTRegTestListener implements Harness.Observer {

    @Override
    public void startingTest(TestResult testResult) {
        String location = "";
        try {
            location = "locationHint='file://" + testResult.getDescription().getFile().getCanonicalPath() + "'";
        } catch (TestResult.Fault | IOException e) {
            //do nothing (leave location empty)
        }
        System.out.println("##teamcity[testStarted name='" + escapeName(testResult.getTestName()) + "' " +
                location + "]");
    }

    @Override
    public void finishedTest(TestResult testResult) {
        final Status status = testResult.getStatus();
        final File file = testResult.getFile();
        if (status.isFailed() || status.isError()) {
            if (file.isFile()) {
                final String output = loadText(file);
                if (output != null && output.length() > 0) {
                    System.out.println("##teamcity[testStdOut name='" + escapeName(testResult.getTestName()) + "' " +
                            "out='" + escapeName(output) + "']");
                }
            }
            System.out.println("##teamcity[testFailed name='" + escapeName(testResult.getTestName()) + "' " +
                    "message='" + escapeName(status.getReason()) + "']");
        } else if (status.isNotRun()) {
            System.out.println("##teamcity[testIgnored name='" + escapeName(testResult.getTestName()) + "']");
        }

        reportJUnitTestMethods(file);

        String duration = "0";
        try {
            duration = testResult.getProperty("elapsed").split(" ")[0];
        } catch (Throwable t) {
            //do nothing (leave duration unspecified)
        }
        System.out.println("##teamcity[testFinished name='" + escapeName(testResult.getTestName()) + "' " +
                (!duration.equals("0") ? "duration='" + duration : "") + "'" +
                (!status.isFailed() ? "outputFile='" + escapeName(file.getAbsolutePath()) + "'" : "") +
                " ]");
    }

    private static void reportJUnitTestMethods(File testOutput) {
        try (Stream<String> lines = Files.lines(testOutput.toPath())) {
            Iterator<String> itt = lines.iterator();
            while (itt.hasNext()) {
                String line = itt.next();
                if (line.startsWith("STARTED")) {
                    List<String> stdErr = new ArrayList<>();
                    stdErr.add(line);
                    String[] logParts = line.split("\\s+", 3);
                    String testName = logParts[1];

                    System.out.println("##teamcity[testStarted name='" + escapeName(testName) + "' ]");

                    do {
                        line = itt.next();
                        stdErr.add(line);
                    } while (!line.startsWith("SUCCESSFUL") && !line.startsWith("ABORTED")
                            && !line.startsWith("SKIPPED") && !line.startsWith("FAILED"));
                    System.out.println("##teamcity[testStdErr name='" + escapeName(testName) + "' " +
                            "out='" + escapeName(String.join("\n", stdErr)) + "']");
                    if (line.startsWith("SKIPPED")) {
                        System.out.println("##teamcity[testIgnored name='" + escapeName(testName) + "']");
                    } else if (line.startsWith("FAILED")) {
                        System.out.println("##teamcity[testFailed name='" + escapeName(testName) + "' ]");
                    }
                    System.out.println("##teamcity[testFinished name='" + escapeName(testName) + "' ]");
                }
            }
        } catch (IOException e) {
            // skip
        }
    }

    @Override
    public void error(String s) {
        System.out.println(s);
    }

    private static String escapeName(String str) {
        return MapSerializerUtil.escapeStr(str);
    }

    private static String loadText(File file) {
        try {
            return String.join("\n", Files.readAllLines(file.toPath()));
        } catch (IOException e) {
            return "Failed to load test results.";
        }
    }
}
