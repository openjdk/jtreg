/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Properties;

import com.sun.javatest.TestResult;
import com.sun.javatest.TestResultTable;
import com.sun.javatest.TestSuite;
import com.sun.javatest.WorkDirectory;
import com.sun.javatest.regtest.config.RegressionTestSuite;
import com.sun.javatest.util.I18NResourceBundle;

/**
 * Read test results from a work directory.
 */
public class WorkDirectoryReader implements DiffReader {
    public static boolean accepts(File f) {
        return WorkDirectory.isWorkDirectory(f);
    }

    /** Creates a new instance of WorkDirectoryReader */
    public WorkDirectoryReader(File file)
            throws FileNotFoundException, WorkDirectory.Fault, TestSuite.Fault {
        this.file = file;

        // Because regtest testsuites don't contain testsuite.jtt
        // files, we can't use the standard WorkDirectory.open call.
        File tsp = getTestSuitePath(file);
        if (tsp != null && new File(tsp, "TEST.ROOT").exists()) {
            TestSuite ts = new RegressionTestSuite(tsp, WorkDirectoryReader.this::error);
            wd = WorkDirectory.open(file, ts);
        } else
            wd = WorkDirectory.open(file);
    }

    public File getFile() {
        return file;
    }

    public String getFileType() {
        return i18n.getString("wd.name");
    }

    public File getWorkDirectory() {
        return wd.getRoot();
    }

    public Iterator<TestResult> iterator() {
        TestResultTable trt = wd.getTestResultTable();
        trt.waitUntilReady();
        return trt.getIterator();
    }

    private static File getTestSuitePath(File workDir) {
        File f = new File(new File(workDir, "jtData"), "testsuite");
        if (!f.exists())
            return null;

        InputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(f));
            Properties p = new Properties();
            p.load(in);
            in.close();
            String ts = p.getProperty("root");
            return (ts == null ? null : new File(ts));
        } catch (IOException e) {
            try {
                if (in != null)
                    in.close();
            } catch (IOException ignore) {
            }
            return null;
        }
    }

    private void error(String msg) {
        System.err.println("Error: " + msg);
    }

    private final File file;
    private final WorkDirectory wd;

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(WorkDirectoryReader.class);
}
