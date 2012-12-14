/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.javatest.diff;

import com.sun.javatest.Status;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestResult;
import com.sun.javatest.util.I18NResourceBundle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Read a set of test results from summary.txt, possibly located in a
 * report directory.
 */
public class ReportReader implements DiffReader {
    private static final String SUMMARY_TXT = "summary.txt";

    public static boolean accepts(File f) {
        if (!f.exists())
            return false;

        if (f.isFile() && f.getName().equals(SUMMARY_TXT))
            return true;

        if (f.isDirectory() && new File(f, SUMMARY_TXT).exists())
            return true;

        if (f.isDirectory() && new File(new File(f, "text"), SUMMARY_TXT).exists())
            return true;

        return false;
    }

    /** Creates a new instance of SummaryReader */
    public ReportReader(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public String getFileType() {
        if (file != null && file.isDirectory())
            return i18n.getString("report.reportDir");
        else
            return i18n.getString("report.reportFile");
    }

    public File getWorkDirectory() {
        return null;
    }

    public Iterator<TestResult> iterator() {
        return readSummary().iterator();
    }

    private List<TestResult> readSummary() {
        List<TestResult> list = new ArrayList<TestResult>();
        File root = getRoot();
        File f;
        if (file.isFile() && file.getName().equals(SUMMARY_TXT))
            f = file;
        else if (file.isDirectory() && new File(file, SUMMARY_TXT).exists())
            f = new File(file, SUMMARY_TXT);
        else if (file.isDirectory() && new File(new File(file, "text"), SUMMARY_TXT).exists())
            f = new File(new File(file, "text"), SUMMARY_TXT);
        else
            throw new IllegalStateException();

        try {
            BufferedReader in = new BufferedReader(new FileReader(f));
            String line;
            while ((line = in.readLine()) != null) {
                int sp = line.indexOf(' ');
                String t = line.substring(0, sp);
                Status s = Status.parse(line.substring(sp).trim());
                TestDescription td = new TestDescription(root, new File(t), Collections.emptyMap());
                TestResult tr = new TestResult(td, s);
                list.add(tr);
            }
        } catch (IOException e) {
        }
        return list;
    }

    private File getRoot() {
        return UNKNOWN;
    }

    private static File UNKNOWN = new File("unknown");

    private File file;

    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(WorkDirectoryReader.class);
}
