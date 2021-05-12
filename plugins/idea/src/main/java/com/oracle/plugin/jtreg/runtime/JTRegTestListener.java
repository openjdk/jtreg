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
import com.sun.javatest.Parameters;
import com.sun.javatest.Status;
import com.sun.javatest.TestResult;
import com.oracle.plugin.jtreg.util.MapSerializerUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

/**
 * The jtreg test listener; this class listens for jtreg test events and maps them into events that the IDE
 * can understand and present back to the user.
 */
public class JTRegTestListener implements Harness.Observer {

    @Override
    public void startingTestRun(Parameters parameters) {
        System.out.println("##teamcity[testSuiteStarted name=\'jtreg\']");
    }

    @Override
    public void startingTest(TestResult testResult) {
        String location = "";
        try {
            location = "locationHint=\'file://" + testResult.getDescription().getFile().getCanonicalPath() + "\'";
        } catch (TestResult.Fault | IOException e) {
            //do nothing (leave location empty)
        }
        System.out.println("##teamcity[testStarted name=\'" + escapeName(testResult.getTestName()) + "\' " +
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
                    System.out.println("##teamcity[testStdOut name=\'" + escapeName(testResult.getTestName()) + "\' " +
                            "out=\'" + escapeName(output) + "\']");
                }
            }
            System.out.println("##teamcity[testFailed name=\'" + escapeName(testResult.getTestName()) + "\' " +
                    "message=\'" + escapeName(status.getReason()) + "\']");
        } else if (status.isNotRun()) {
            System.out.println("##teamcity[testIgnored name=\'" + escapeName(testResult.getTestName()) + "\']");
        }

        String duration = "0";
        try {
            duration = testResult.getProperty("elapsed").split(" ")[0];
        } catch (Throwable t) {
            //do nothing (leave duration unspecified)
        }
        System.out.println("##teamcity[testFinished name=\'" + escapeName(testResult.getTestName()) + "\' " +
                (!duration.equals("0") ? "duration=\'" + duration : "") + "\'" +
                (!status.isFailed() ? "outputFile=\'" + escapeName(file.getAbsolutePath()) + "\'" : "") +
                " ]");
    }

    @Override
    public void stoppingTestRun() {
        //do nothing
    }

    @Override
    public void finishedTesting() {
        //do nothing

    }

    @Override
    public void finishedTestRun(boolean b) {
        System.out.println("##teamcity[testSuiteFinished name=\'jtreg\']");
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
            return Files.readAllLines(file.toPath()).stream().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Failed to load test results.";
        }
    }
}
