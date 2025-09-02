/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.report;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.javatest.TestFilter;
import com.sun.javatest.regtest.Main.Fault;
import com.sun.javatest.regtest.config.RegressionParameters;
import com.sun.javatest.regtest.config.RegressionTestSuite;
import com.sun.javatest.regtest.config.TestManager;
import com.sun.javatest.report.Report;
import com.sun.javatest.report.ReportSettings;
import com.sun.javatest.util.HTMLWriter;

/**
 * Handles the generation of reports for test runs.
 * For an individual test run, it uses the basic JT Harness support for
 * writing reports.
 * For a multi-run, it generates the top level combined report directly.
 */
public class RegressionReporter {
    public RegressionReporter(PrintWriter log) {
        this.log = log;
    }

    public void report(RegressionParameters params, ElapsedTimeHandler elapsedTimeHandler,
                       TestStats testStats, TestFilter filter, boolean quiet) {
        File rd = params.getReportDir().toFile();
        File wd = params.getWorkDirectory().getRoot();

        try {
            if (Thread.interrupted()) {
                // It is important to ensure the interrupted bit is cleared before writing
                // a report, because Report.writeReport checks if the interrupted bit is set,
                // and will stop writing the report. This typically manifests itself as
                // writing the HTML files but /not/ writing the text/summary.txt file.
                log.println("WARNING: interrupt status cleared prior to writing report");
            }

            Report r = new Report();
            ReportSettings s = new ReportSettings(params);
            if (reportKinds.contains("html")) {
                s.setEnableHtmlReport(true);
                s.setHtmlMainReport(true, true);
                s.setShowKflReport(false);
            }
            if (reportKinds.contains("text")) {
                s.setEnablePlainReport(true);
            }
            if (reportKinds.contains("xml")) {
                s.setEnableXmlReport(true);
            }
            s.setFilter(filter);
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
            r.writeReports(s, rd);
            if (s.isPlainEnabled()) {
                if (elapsedTimeHandler != null)
                    elapsedTimeHandler.report(r);

                if (testStats != null)
                    testStats.report(r);

                int countTestNG = SummaryReporter.forTestNG(params.getWorkDirectory()).writeReport(rd);
                int countJUnit = SummaryReporter.forJUnit(params.getWorkDirectory()).writeReport(rd);
                int sumOfCounts = countTestNG + countJUnit;
                if (sumOfCounts > 0) {
                    log.println(String.format("Framework-based tests: %,d = %,d TestNG + %,d JUnit",
                            sumOfCounts, countTestNG, countJUnit));
                }
            }
            fixupReports(rd, wd);
            if (!quiet)
                logReportWritten(rd);
        } catch (IOException | SecurityException e) {
            log.println("Error while writing report: " + e);
        }
    }

    public void report(TestManager testManager) throws Fault {
        this.testManager = testManager;
        this.reportDir = testManager.getReportDirectory().toFile();

        parent = getCommonParent(testManager.getTestSuites());
        // ignore the case where the common parent is just the root directory
        if (parent != null && parent.getParentFile() == null)
            parent = null;

        try {
            if (reportKinds.contains("html"))
                writeHTMLReport();

            if (reportKinds.contains("text")) {
                writeCombinedSummary();
                // for extra marks, write a combined elapsedTime file
            }

            writeIndex();

            fixupReports(reportDir, testManager.getWorkDirectory().toFile());

            logReportWritten(reportDir);
        } catch (IOException e) {
            log.println("Error while writing report: " + e);
        }
    }

    private void logReportWritten(File reportDir) {
        File report = new File(new File(reportDir, "html"), "report.html");
        if (report.exists())
            log.println("Report written to " + canon(report));
    }

    /**
     * Create html/index.html that links to the subdir data.
     * The page is a simple table containing rows
     *      testsuite (link to work subdir) (link to report subdir)
     */
    private void writeHTMLReport() throws IOException, Fault {
        String title = (parent == null)
                ? "MultiRun Report"
                : "MultiRun Report: " + parent;
        File htmlDir = new File(reportDir, "html");
        htmlDir.mkdirs();
        File report = new File(htmlDir, "report.html");
        try (BufferedWriter htmlOut = new BufferedWriter(new FileWriter(report))) {
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
            html.startTag(HTMLWriter.TR);
            for (String s: new String[] { "Test Suite", "Results", "Report" }) {
                html.startTag(HTMLWriter.TH);
                html.write(s);
                html.endTag(HTMLWriter.TH);
            }
            html.endTag(HTMLWriter.TR);
            for (RegressionTestSuite testSuite: testManager.getTestSuites()) {
                html.startTag(HTMLWriter.TR);
                html.startTag(HTMLWriter.TD);
                html.startTag(HTMLWriter.A);
                File ts = testSuite.getRootDir();
                html.writeAttr(HTMLWriter.HREF, getURIPath(ts));
                html.write(relativize(parent, ts).getPath());
                html.endTag(HTMLWriter.A);
                html.endTag(HTMLWriter.TD);
                html.startTag(HTMLWriter.TD);
                html.startTag(HTMLWriter.A);
                File wd = testManager.getWorkDirectory(testSuite).getRoot();
                html.writeAttr(HTMLWriter.HREF, getURIPath(wd));
                html.write(wd.getName());
                html.endTag(HTMLWriter.A);
                html.endTag(HTMLWriter.TD);
                html.startTag(HTMLWriter.TD);
                html.startTag(HTMLWriter.A);
                Path rd = testManager.getReportDirectory(testSuite);
                html.writeAttr(HTMLWriter.HREF, "../" + encode(rd.getFileName().toString()) + "/index.html");
                html.write(rd.getFileName().toString());
                html.endTag(HTMLWriter.A);
                html.endTag(HTMLWriter.TD);
                html.endTag(HTMLWriter.TR);
            }
            html.endTag(HTMLWriter.TABLE);
            html.endTag(HTMLWriter.BODY);
            html.endTag(HTMLWriter.HTML);
            html.close();
        }
    }

    /* If the work dir and report dir are equal or close to each other in the
     * file system, rewrite HTML files in the report directory, replacing
     * absolute paths for the work directory with relative paths.
     */
    private void fixupReports(File report, File work) throws IOException {
        // ensure all files normalized
        work = getCanonicalFile(work);
        report = getCanonicalFile(report);

        File workParent = work.getParentFile();
        File reportParent = report.getParentFile();
        File htmlDir = new File(report, "html");

        if (equal(work, report)) {
            fixupReportFiles(report,  work, ".");
            fixupReportFiles(htmlDir, work, "..");
        } else if (equal(report, workParent)) {
            String relPath = encode(work.getName());
            fixupReportFiles(report,  work, relPath);
            fixupReportFiles(htmlDir, work, "../" + relPath);
        } else if (equal(work, reportParent)) {
            fixupReportFiles(report,  work, "..");
            fixupReportFiles(htmlDir, work, "../..");
        } else if (equal(workParent, reportParent)) {
            String relPath = encode(work.getName());
            fixupReportFiles(report,  work, "../" + relPath);
            fixupReportFiles(htmlDir, work, "../../" + relPath);
        } else if (equal(workParent.getParentFile(), reportParent.getParentFile())) {
            // This case is notable for multi-run jobs
            String relPath = "../../" + encode(workParent.getName()) + "/" + encode(work.getName());
            fixupReportFiles(report,  work, relPath);
            fixupReportFiles(htmlDir, work, "../" + relPath);
        }
    }

    /**
     * Returns the URL encoding of a string.
     */
    private String encode(String s) throws IOException {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /* Rewrite html files in the given directory, replacing hrefs to the old path
     * with references to the new path.
     * Since all files have been canonicalized, we should not need to worry
     * about inconsistent case on case-equivalent file systems like Mac and Windows.
     */
    private void fixupReportFiles(File dir, File oldFile, String newPath) throws IOException {
        /*
         * For compatibility, we detect and replace the form of the URL as
         * encoded by JT Harness 5.0, and the anticipated form, generated by
         * using file.toURI().getRawPath().
         */
        Map<String, String> replaceMap = new LinkedHashMap<>();
        replaceMap.put("href=\"" + getURIPath(oldFile) + "/", "href=\"" + newPath + "/");
        replaceMap.put("href=\"" + getURIPath(oldFile) + "\"", "href=\"" + newPath + "\"");
        replaceMap.put("href=\"" + getCanonicalURIPath(oldFile) + "/", "href=\"" + newPath + "/");
        replaceMap.put("href=\"" + getCanonicalURIPath(oldFile) + "\"", "href=\"" + newPath + "\"");
        replaceMap.put("href=\"" + getURIPath(dir) + "/", "href=\"");
        replaceMap.put("href=\"" + getURIPath(dir) + "\"", "href=\".\"");
        replaceMap.put("href=\"" + getCanonicalURIPath(dir) + "/", "href=\"");
        replaceMap.put("href=\"" + getCanonicalURIPath(dir) + "\"", "href=\".\"");

        // Additional fixup to workaround bugs
        replaceMap.put("href=\"#Configuration and Other Settings\"",
                "href=\"#Configuration%20and%20Other%20Settings\"");
        replaceMap.put("href=\"#Known Failure Analysis\"",
                "href=\"#Known%20Failure%20Analysis\"");

//        System.err.println("fixUpReportFiles " + dir);
//        for (Map.Entry<String,String> e : replaceMap.entrySet()) {
//            System.err.println("  replace: " + e.getKey());
//            System.err.println("     with: " + e.getValue());
//        }

        File[] children = dir.listFiles();
        if (children == null) {
            log.println("Cannot update report files for " + dir);
        } else {
            for (File f: children) {
                if (f.getName().endsWith(".html")) {
                    try {
                        String content = read(f);
                        for (Map.Entry<String,String> e : replaceMap.entrySet()) {
                            content = content.replace(e.getKey(), e.getValue());
                        }
                        write(f, content);
                    } catch (IOException e) {
                        log.println("Error while updating report: " + e);
                    }
                }
            }
        }
    }

    /**
     * Returns the URL-encoded form of a file, with any trailing '/' removed.
     */
    String getURIPath(File f) {
        String p = f.toURI().getRawPath();
        return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }

    // This method mimics the code in JTHarness class com.sun.javatest.util.HtmlWriter,
    // method writeLink, which writes out absolute files as URI paths.
    String getCanonicalURIPath(File f) throws IOException {
        File cf = getCanonicalFile(f);

        StringBuilder sb = new StringBuilder();
        String path = cf.getPath();
        if (cf.isAbsolute() && !path.startsWith("/"))
            sb.append('/');
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            String encoded = (ch == File.separatorChar) ? "/" :
                    URLEncoder.encode(String.valueOf(ch), StandardCharsets.UTF_8);
            sb.append(encoded);
        }
        return sb.toString();
    }

    File getCanonicalFile(File f) {
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return f.getAbsoluteFile();
        }
    }

    private void writeCombinedSummary() throws IOException, Fault {
        File textDir = new File(reportDir, "text");
        textDir.mkdirs();
        File report = new File(textDir, "summary.txt");
        try (BufferedWriter summaryOut = new BufferedWriter(new FileWriter(report))) {
            for (RegressionTestSuite ts: testManager.getTestSuites()) {
                Path f = testManager.getReportDirectory(ts).resolve("text").resolve("summary.txt");
                if (Files.exists(f)) {
                    String s = Files.readString(f);
                    if (!s.endsWith("\n"))
                        s += "\n";
                    summaryOut.write(s);
                }
            }
        }
    }

    private void writeIndex() throws IOException {
        String title = (parent == null)
                ? "MultiRun Report"
                : "MultiRun Report: " + parent;
        File index = new File(reportDir, "index.html");
        try (BufferedWriter indexOut = new BufferedWriter(new FileWriter(index))) {
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

        }
    }

    private String read(File f) throws IOException {
        byte[] bytes = new byte[(int) f.length()];
        try (DataInputStream fIn = new DataInputStream(new FileInputStream(f))) {
            fIn.readFully(bytes);
            return new String(bytes);
        }
    }

    private void write(File f, String s) throws IOException {
        try (FileOutputStream fOut = new FileOutputStream(f)) {
            fOut.write(s.getBytes());
        }
    }

    private static File getCommonParent(Set<RegressionTestSuite> testSuites) {
        String FS = File.separator;
        String path = null;
        for (RegressionTestSuite testSuite: testSuites) {
            File file = testSuite.getRootDir();
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

    final PrintWriter log;

    private File reportDir;
    private TestManager testManager;
    private File parent;

    DateFormat df = DateFormat.getDateTimeInstance();
    String backups = System.getProperty("javatest.report.backups"); // default: none
    List<String> reportKinds =
            List.of(System.getProperty("javatest.report.kinds", "html text").split("[ ,]+"));
}
