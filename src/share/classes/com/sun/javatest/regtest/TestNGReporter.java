/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javatest.TestDescription;
import com.sun.javatest.TestResult;
import com.sun.javatest.WorkDirectory;
import com.sun.javatest.regtest.Action.OutputHandler.OutputKind;

/**
 * Class to generate aggregate reports for groups of TestNG tests
 */
public class TestNGReporter {
    private static final Map<WorkDirectory,TestNGReporter> instanceMap
            = new WeakHashMap<WorkDirectory,TestNGReporter>();

    public static TestNGReporter instance(WorkDirectory wd) {
        TestNGReporter r = instanceMap.get(wd);
        if (r == null) {
            instanceMap.put(wd, r = new TestNGReporter());
        }
        return r;
    }

    Map<String, Info> infoMap = new TreeMap<String, Info>();

    boolean isEmpty() {
        return infoMap.isEmpty();
    }

    static final String testsPrefix = "Total tests run:";
    static final Pattern testsPattern = Pattern.compile("[^0-9]+([0-9]+)[^0-9]+([0-9]+)[^0-9]+([0-9]+)[^0-9]*");
    static final String configPrefix = "Configuration Failures:";
    static final Pattern configPattern = Pattern.compile("[^0-9]+([0-9]+)[^0-9]+([0-9]+)[^0-9]*");

    public synchronized void add(TestResult tr, TestResult.Section s) {
        try {
            TestDescription td = tr.getDescription();
            String group = td.getParameter("packageRoot");
            if (group == null)
                group = td.getRootRelativePath();
            Info info = infoMap.get(group);
            if (info == null)
                infoMap.put(group, info = new Info());
            String out = s.getOutput(OutputKind.STDOUT.name);
            if (out != null) {
                Matcher tm = getMatcher(out, testsPrefix, testsPattern);
                if (tm != null && tm.matches()) {
                    info.count += Integer.parseInt(tm.group(1));
                    info.failureCount += Integer.parseInt(tm.group(2));
                    info.skippedCount += Integer.parseInt(tm.group(3));
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

        return p.matcher(out.substring(pos, endPos));
    }

    public void writeReport(File reportDir) throws IOException {
        File f = new File(reportDir, "text/testng.txt");
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)));
        for (Map.Entry<String,Info> e: infoMap.entrySet()) {
            out.println(e.getKey() + " " + e.getValue());
        }
        out.close();
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
                    + ", passed: " + (count - failureCount -skippedCount)
                    + ", failed: " + failureCount
                    + ", skipped: " + skippedCount
                    + ", config failed: " + configFailureCount
                    + ", config skipped: " + configSkippedCount;
        }
    }
}
