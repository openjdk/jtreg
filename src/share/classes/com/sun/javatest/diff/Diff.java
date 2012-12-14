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

package com.sun.javatest.diff;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.sun.javatest.Status;
import com.sun.javatest.TestResult;
import com.sun.javatest.TestSuite;
import com.sun.javatest.WorkDirectory;
import com.sun.javatest.util.I18NResourceBundle;

public abstract class Diff {

    public abstract boolean report(File outFile) throws Fault, InterruptedException;

    protected boolean diff(List<File> files, File outFile)
            throws Fault, InterruptedException {
        this.outFile = outFile;
        List<DiffReader> list = new ArrayList<DiffReader>();
        for (File f: files)
            list.add(open(f));

        PrintWriter prevOut = out;
        if (outFile != null) {
            try {
                out = new PrintWriter(new BufferedWriter(new FileWriter(outFile))); // FIXME don't want to use PrintWriter
            } catch (IOException e) {
                throw new Fault(i18n, "diff.cantOpenFile", outFile, e);
            }
        }

        try {
            initComparator();

            initReporter();
            reporter.setTitle(title);
            reporter.setComparator(comparator);
            reporter.setReaders(list);

            List<int[]> testCounts = new ArrayList<int[]>();
            MultiMap<String, TestResult> table = new MultiMap<String, TestResult>();
            for (DiffReader r: list) {
                int index = table.addColumn(r.getFile().getPath());
                int[] counts = new int[Status.NUM_STATES];
                for (TestResult tr: r) {
                    table.addRow(index, tr.getTestName(), tr);
                    counts[tr.getStatus().getType()]++;
                }
                testCounts.add(counts);
            }
            reporter.setTestCounts(testCounts);

            try {
                reporter.write(table);
            } catch (IOException e) {
                throw new Fault(i18n, "diff.ioError", e);
            }

            return (reporter.diffs == 0);
        } finally {
            if (out != prevOut) {
//                try {
                    out.close();
//                } catch (IOException e) {
//                    throw new Fault(i18n, "main.ioError", e);
//                }
                out = prevOut;
            }
        }
    }

    protected void initFormat() {
        if (format == null && outFile != null) {
            String name = outFile.getName();
            int dot = name.lastIndexOf(".");
            if (dot != -1)
                format = name.substring(dot + 1).toLowerCase();
        }
    }

    protected void initReporter() throws Fault {
        if (reporter == null) {
            try {
                initFormat();
                if (format != null && format.equals("html"))
                    reporter = new HTMLReporter(out);
                else
                    reporter = new SimpleReporter(out);
            } catch (IOException e) {
                throw new Fault(i18n, "diff.cantOpenReport", e);
            }
        }
    }

    protected void initComparator() {
        if (comparator == null)
            comparator = new StatusComparator(includeReason);
    }

    protected DiffReader open(File f) throws Fault {
        if (!f.exists())
            throw new Fault(i18n, "main.cantFindFile", f);

        try {
            if (WorkDirectoryReader.accepts(f))
                return new WorkDirectoryReader(f);

            if (ReportReader.accepts(f))
                return new ReportReader(f);

            throw new Fault(i18n, "main.unrecognizedFile", f);

        } catch (TestSuite.Fault e) {
            throw new Fault(i18n, "main.cantOpenFile", f, e);
        } catch (WorkDirectory.Fault e) {
            throw new Fault(i18n, "main.cantOpenFile", f, e);
        } catch (IOException e) {
            throw new Fault(i18n, "main.cantOpenFile", f, e);
        }

    }

    protected File outFile;
    protected PrintWriter out;
    protected Comparator<TestResult> comparator;
    protected Reporter reporter;
    protected boolean includeReason;
    protected String format;
    protected String title;
    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(Diff.class);
}
