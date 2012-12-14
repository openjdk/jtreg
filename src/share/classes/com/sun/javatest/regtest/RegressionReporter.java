/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.javatest.CompositeFilter;
import com.sun.javatest.report.Report;
import com.sun.javatest.util.HTMLWriter;

/**
 * Handles the generation of reports for test runs.
 * For an individual test run, it uses the basic JT Harness support for
 * writing reports.
 * For a multi-run, it generates the top level combined report directly.
 */
public class RegressionReporter {
    RegressionReporter(File workDirArg, File reportDirArg, PrintWriter out) {
        this.workDirArg = workDirArg;
        this.reportDirArg = reportDirArg;
        this.out = out;
    }

    void report(RegressionParameters params, ElapsedTimeHandler elapsedTimeHandler, TestStats testStats, boolean quiet) {
        File rd = params.getReportDir();

        try {
            if (Thread.interrupted()) {
                // It is important to ensure the interrupted bit is cleared before writing
                // a report, because Report.writeReport checks if the interrupted bit is set,
                // and will stop writing the report. This typically manifests itself as
                // writing the HTML files but /not/ writing the text/summary.txt file.
                out.println("WARNING: interrupt status cleared prior to writing report");
            }

            Report r = new Report();
            Report.Settings s = new Report.Settings(params);
            if (reportKinds.contains("html")) {
                s.setEnableHtmlReport(true);
                s.setHtmlMainReport(true, true);
            }
            if (reportKinds.contains("text")) {
                s.setEnablePlainReport(true);
            }
            if (reportKinds.contains("xml")) {
                s.setEnableXmlReport(true);
            }
            s.setFilter(new CompositeFilter(params.getFilters()));
            if (backups == null)
                s.setEnableBackups(false);
            else {
                try {
                    s.setBackupLevels(Integer.parseInt(backups));
                    s.setEnableBackups(true);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            rd.mkdirs();
            r.writeReport(s, rd);
            if (s.isPlainEnabled()) {
                if (elapsedTimeHandler != null)
                    elapsedTimeHandler.report(r);
                if (testStats != null)
                    testStats.report(r);
            }
            fixupReports(rd, workDirArg, reportDirArg);
            if (!quiet)
                logReportWritten(rd);
        } catch (IOException e) {
            out.println("Error while writing report: " + e);
        } catch (SecurityException e) {
            out.println("Error while writing report: " + e);
        }
    }

    void report(Map<File, String> subdirMap) {
        File parent = getCommonParent(subdirMap.keySet());
        // ignore the case where the common parent is just the root directory
        if (parent != null && parent.getParentFile() == null)
            parent = null;

        try {
            if (reportKinds.contains("html"))
                writeHTMLReport(subdirMap, parent);

            if (reportKinds.contains("text")) {
                writeCombinedSummary(subdirMap);
                // for extra marks, write a combined elapsedTime file
            }

            writeIndex(parent);

            logReportWritten(reportDirArg);
        } catch (IOException e) {
            out.println("Error while writing report: " + e);
        }
    }

    void logReportWritten(File reportDir) {
        File report = new File(reportDir, "report.html"); // std through version 3.*
        if (!report.exists())
            report = new File(new File(reportDir, "html"), "report.html"); // version 4.*
        if (report.exists())
            out.println("Report written to " + canon(report));
    }

    /**
     * Create html/index.html that links to the subdir data.
     * The page is a simple table containing rows
     *      testsuite (link to work subdir) (link to report subdir)
     */
    void writeHTMLReport(Map<File, String> subdirMap, File parent) throws IOException {
        String title = (parent == null)
                ? "MultiRun Report"
                : "MultiRun Report: " + parent;
        File htmlDir = new File(reportDirArg, "html");
        htmlDir.mkdirs();
        File report = new File(htmlDir, "report.html");
        BufferedWriter htmlOut = new BufferedWriter(new FileWriter(report));
        try {
            HTMLWriter html = new HTMLWriter(htmlOut);
            html.startTag(HTMLWriter.HTML);
            html.startTag(HTMLWriter.HEAD);
            html.startTag(HTMLWriter.TITLE);
            html.write(title);
            html.endTag(HTMLWriter.TITLE);
            html.endTag(HTMLWriter.HEAD);
            html.startTag(HTMLWriter.BODY);
            html.startTag(HTMLWriter.H1);
            html.write(title);
            html.endTag(HTMLWriter.H1);
            html.startTag(HTMLWriter.P);
            html.write("Date of report: " + df.format(new Date()));
            html.endTag(HTMLWriter.P);
            html.startTag(HTMLWriter.TABLE);
            html.writeAttr(HTMLWriter.BORDER, "1");
            html.endTag(HTMLWriter.TR);
            for (String s: new String[] { "Test Suite", "Results", "Report" }) {
                html.startTag(HTMLWriter.TH);
                html.write(s);
                html.endTag(HTMLWriter.TH);
            }
            html.startTag(HTMLWriter.TR);
            for (Map.Entry<File, String> e: subdirMap.entrySet()) {
                File testSuite = e.getKey();
                String subdir = e.getValue();
                html.startTag(HTMLWriter.TR);
                html.startTag(HTMLWriter.TD);
                html.startTag(HTMLWriter.A);
                html.writeAttr(HTMLWriter.HREF, testSuite.getAbsolutePath());
                html.write(relativize(parent, testSuite).getPath());
                html.endTag(HTMLWriter.A);
                html.endTag(HTMLWriter.TD);
                html.startTag(HTMLWriter.TD);
                html.startTag(HTMLWriter.A);
                File wd = new File(workDirArg, subdir);
                html.writeAttr(HTMLWriter.HREF, wd.getAbsolutePath());
                html.write(subdir);
                html.endTag(HTMLWriter.A);
                html.endTag(HTMLWriter.TD);
                html.startTag(HTMLWriter.TD);
                html.startTag(HTMLWriter.A);
                File r = new File(new File(reportDirArg, subdir), "index.html");
                html.writeAttr(HTMLWriter.HREF, r.getAbsolutePath());
                html.write(subdir);
                html.endTag(HTMLWriter.A);
                html.endTag(HTMLWriter.TD);
                html.endTag(HTMLWriter.TR);
            }
            html.endTag(HTMLWriter.TABLE);
            html.endTag(HTMLWriter.BODY);
            html.endTag(HTMLWriter.HTML);
            html.close();
        } finally {
            htmlOut.close();
        }
    }

    /* If the work dir and report dir are equal or close to each other in the
     * file system, rewrite HTML files in the report directory, replacing
     * absolute paths for the work directory with relative paths.
     */
    private void fixupReports(File dir, File work, File report) {
//        System.err.println("fixupreports: " + dir + " " + work + " " + report);
        File workParent = work.getParentFile();
        String canonWorkPath;
        try {
            canonWorkPath = work.getCanonicalPath();
        } catch (IOException e) {
            canonWorkPath = work.getAbsolutePath();
        }

        File reportParent = report.getParentFile();

        File htmlDir = new File(dir, "html");

        if (equal(work, report)) {
            fixupReportFiles(dir,        canonWorkPath, ".");
            fixupReportFiles(htmlDir, canonWorkPath, "..");
        } else if (equal(report, workParent)) {
            fixupReportFiles(dir,        canonWorkPath, work.getName());
            fixupReportFiles(htmlDir, canonWorkPath, "../" + work.getName());
        } else if (equal(work, reportParent)) {
            fixupReportFiles(dir,        canonWorkPath, work.getName());
            fixupReportFiles(htmlDir, canonWorkPath, "../" + work.getName());
        } else if (equal(workParent, reportParent)) {
            fixupReportFiles(dir,        canonWorkPath, "../" + work.getName());
            fixupReportFiles(htmlDir, canonWorkPath, "../../" + work.getName());
        }
    }

    /* Rewrite html files in the given directory, replacing hrefs to the old path
     * with references to the new path. */
    private void fixupReportFiles(File dir, String oldPath, String newPath) {
        String dirPath;
        try {
            dirPath = dir.getCanonicalPath();
        } catch (IOException e) {
            dirPath = dir.getAbsolutePath();
        }

        for (File f: dir.listFiles()) {
            if (f.getName().endsWith(".html")) {
                try {
                    write(f, read(f)
                            .replace("href=\"" + oldPath + "/", "href=\"" + newPath + "/")
                            .replace("href=\"" + oldPath + "\"", "href=\"" + newPath + "\"")
                            .replace("href=\"" + dirPath + "\"", "href=\".\""));
                } catch (IOException e) {
                    out.println("Error while updating report: " + e);
                }
            }
        }
    }

    private void writeCombinedSummary(Map<File, String> subdirMap) throws IOException {
        File textDir = new File(reportDirArg, "text");
        textDir.mkdirs();
        File report = new File(textDir, "summary.txt");
        BufferedWriter summaryOut = new BufferedWriter(new FileWriter(report));
        try {
            for (String subdir: subdirMap.values()) {
                File f = new File(new File(new File(reportDirArg, subdir), "text"), "summary.txt");
                if (f.exists()) {
                    String s = read(f);
                    if (!s.endsWith("\n"))
                        s += "\n";
                    summaryOut.write(s);
                }
            }
        } finally {
            summaryOut.close();
        }
    }

    private void writeIndex(File parent) throws IOException {
        String title = (parent == null)
                ? "MultiRun Report"
                : "MultiRun Report: " + parent;
        File index = new File(reportDirArg, "index.html");
        BufferedWriter indexOut = new BufferedWriter(new FileWriter(index));
        try {
            HTMLWriter html = new HTMLWriter(indexOut);
            html.startTag(HTMLWriter.HTML);
            html.startTag(HTMLWriter.HEAD);
            html.startTag(HTMLWriter.TITLE);
            html.write(title);
            html.endTag(HTMLWriter.TITLE);
            html.endTag(HTMLWriter.HEAD);
            html.startTag(HTMLWriter.BODY);
            html.startTag(HTMLWriter.H1);
            html.write(title);
            html.endTag(HTMLWriter.H1);
            html.startTag(HTMLWriter.P);
            html.write("Date of report: " + df.format(new Date()));
            html.endTag(HTMLWriter.P);

            if (reportKinds.contains("html")) {
                html.startTag(HTMLWriter.P);
                html.startTag(HTMLWriter.A);
                html.writeAttr(HTMLWriter.HREF, "html/report.html");
                html.write("HTML Report");
                html.endTag(HTMLWriter.A);
                html.startTag(HTMLWriter.BR);
                html.write("Contains links to the reports for the tests grouped by test suite.");
                html.endTag(HTMLWriter.P);
            }

            if (reportKinds.contains("text")) {
                html.startTag(HTMLWriter.P);
                html.startTag(HTMLWriter.A);
                html.writeAttr(HTMLWriter.HREF, "text/summary.txt");
                html.write("Plain Text Report");
                html.endTag(HTMLWriter.A);
                html.startTag(HTMLWriter.BR);
                html.write("Combined text report for all the tests.");
                html.endTag(HTMLWriter.P);
            }

            html.endTag(HTMLWriter.BODY);
            html.endTag(HTMLWriter.HTML);

        } finally {
            indexOut.close();
        }
    }

    private String read(File f) throws IOException {
        byte[] bytes = new byte[(int) f.length()];
        DataInputStream fIn = new DataInputStream(new FileInputStream(f));
        try {
            fIn.readFully(bytes);
            return new String(bytes);
        } finally {
            fIn.close();
        }
    }

    private void write(File f, String s) throws IOException {
        FileOutputStream fOut = new FileOutputStream(f);
        try {
            fOut.write(s.getBytes());
        } finally {
            fOut.close();
        }
    }

    public static void main(String... args) {
        Set<File> files = new LinkedHashSet<File>();
        for (String arg: args)
            files.add(new File(arg));
        System.err.println("result: " + getCommonParent(files));
    }

    private static File getCommonParent(Set<File> files) {
        String FS = File.separator;
        String path = null;
        for (File file: files) {
            File dir = file.isDirectory() ? file : file.getParentFile();
            File absDir = (dir == null) ? new File(System.getProperty("user.dir")) : dir.getAbsoluteFile();
            String p = absDir.getPath();
            if (!p.endsWith(FS))
                p += FS;
            if (path == null || path.startsWith(p))
                path = p;
            else if (!p.startsWith(path)) {
                int i = -1;
                while (true) {
                    int next = path.indexOf(FS, i + 1);
                    if (next == -1
                            || next >= p.length()
                            || !p.substring(i + 1, next).equals(path.substring(i + 1, next)))
                        break;
                    i = next;
                }
                if (i == -1)
                    return null;
                path = path.substring(0, i + 1);
            }
        }
        return (path == null ? null : new File(path));
    }

    private static File relativize(File base, File f) {
        if (base != null) {
            StringBuilder sb = new StringBuilder();
            for ( ; f != null; f = f.getParentFile()) {
                if (f.equals(base))
                    return new File(sb.toString());
                if (sb.length() > 0)
                    sb.insert(0, File.separator);
                sb.insert(0, f.getName());
            }
        }
        return f;
    }

    private static File canon(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    private static <T> boolean equal(T t1, T t2) {
        return (t1 == null ? t2 == null : t1.equals(t2));
    }

    File workDirArg;
    File reportDirArg;
    PrintWriter out;

    DateFormat df = DateFormat.getDateTimeInstance();
    String backups = System.getProperty("javatest.report.backups"); // default: none
    List<String> reportKinds =
            Arrays.asList(System.getProperty("javatest.report.kinds", "html text").split("[ ,]+"));
}
