/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

// TODO: colorize output?

package com.sun.javatest.diff;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import com.sun.javatest.Status;
import com.sun.javatest.TestResult;
import com.sun.javatest.util.I18NResourceBundle;

/**
 * Write simple reports to a text file.
 */
public class SimpleReporter extends Reporter {

    /**
     * Creates a new instance of SimpleReporter
     */
    public SimpleReporter(PrintWriter out) {
        if (out == null)
            throw new NullPointerException();

        this.out = out;

        statusStrings = new String[4];
        statusStrings[Status.PASSED] = i18n.getString("simple.pass");
        statusStrings[Status.FAILED] = i18n.getString("simple.fail");
        statusStrings[Status.ERROR] = i18n.getString("simple.error");
        statusStrings[Status.NOT_RUN] = i18n.getString("simple.notRun");
        for (String ss: statusStrings)
            maxStatusStringLength = Math.max(maxStatusStringLength, ss.length());
    }

    public void write(MultiMap<String, TestResult> table) throws IOException {
        this.table = table;
        size = table.getColumns();

        if (title != null) {
            println(title);
            println();
        }

        writeHead();
        writeBody();
        writeSummary();
    }

    private void writeHead() throws IOException {
        for (int i = 0; i < size; i++) {
            int[] c = testCounts.get(i);
            int p = c[Status.PASSED];
            int f = c[Status.FAILED];
            int e = c[Status.ERROR];
            int nr = c[Status.NOT_RUN];
            writeI18N("simple.set", i, table.getColumnName(i));
            print("  ");
            writeI18N("simple.counts",
                    new Object[] {
                new Integer(p),
                new Integer((p > 0) && (f + e + nr > 0) ? 1 : 0),
                new Integer(f),
                new Integer((f > 0) && (e + nr > 0) ? 1 : 0),
                new Integer(e),
                new Integer((e > 0) && (nr > 0) ? 1 : 0),
                new Integer(nr)
            });
            println();
        }
    }

    private void writeBody() throws IOException {
        diffs = 0;
        for (Map.Entry<String, MultiMap.Entry<TestResult>> e: table.entrySet()) {
            String testName = e.getKey();
            MultiMap.Entry<TestResult> result = e.getValue();
            if (result.allEqual(comparator))
                continue;
            if (diffs == 0) {
                println();
                for (int i = 0; i < result.getSize(); i++) {
                    print(String.valueOf(i), maxStatusStringLength + 2);
                }
                writeI18N("simple.test");
                println();
            }
            for (int i = 0; i < result.getSize(); i++) {
                TestResult tr = result.get(i);
                Status s = (tr == null ? null : tr.getStatus());
                print(getStatusString(s), maxStatusStringLength + 2);
            }
            println(testName);
            diffs++;
        }
    }

    private void writeSummary() throws IOException {
        println();
        if (diffs == 0)
            writeI18N("simple.diffs.none");
        else
            writeI18N("simple.diffs.count", diffs);
        println();
    }

    private void writeI18N(String key, Object... args) throws IOException {
        print(i18n.getString(key, args));
    }

    private void print(Object o) throws IOException {
        out.print(o.toString());
    }

    private void print(String s, int width) throws IOException {
        out.print(s);
        for (int i = s.length(); i < width; i++)
            out.print(' ');
    }

    private void println() throws IOException {
        out.println();
    }

    private void println(Object o) throws IOException {
        out.println(o.toString());
    }

    private String getStatusString(Status s) {
        return statusStrings[s == null ? Status.NOT_RUN : s.getType()];
    }

    private MultiMap<String, TestResult> table;
    private int size;
    private PrintWriter out;

    private String[] statusStrings;
    private int maxStatusStringLength;

    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(SimpleReporter.class);
}
