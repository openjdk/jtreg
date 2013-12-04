/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.sun.javatest.Status;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestResult;
import com.sun.javatest.TestResult.Section;

/**
 * Write out results in JUnit-compatible XML format, for processing by tools
 * that can process such files, such as CI systems like Hudson and Jenkins.
 *
 * @author ksrini
 */
public class XMLWriter {
    static final String PASSED = "Passed.";
    static final String FAILED = "Failed.";

    public final TestResult tr;
    private final XPrintStream xps;
    private final String classname;
    private final Date start;
    private final double duration;
    private final Status status;
    private final File xmlFile;
    private final boolean verify;
    private final PrintWriter harnessOut;
    private final PrintWriter harnessErr;
    private final SimpleDateFormat defDateFmt;
    private final DateFormat isoDateFmt;

    XMLWriter(TestResult tr, boolean mustVerify, PrintWriter out, PrintWriter err)
            throws ParseException, UnsupportedEncodingException, TestResult.Fault {
        String encoding = Charset.defaultCharset().name();
        defDateFmt = new SimpleDateFormat("EEE MMM dd hh:mm:ss z yyyy");
        isoDateFmt = new SimpleDateFormat("yyyy-MM-DD'T'HH:mm:ssZ");
        harnessOut = out;
        harnessErr = err;
        xps = new XPrintStream(harnessOut, harnessErr, encoding);
        this.tr = tr;
        verify = mustVerify;
        status = tr.getStatus();
        classname = tr.getProperty("test");
        xmlFile = new File(tr.getFile().getAbsolutePath() + ".xml");
        start = defDateFmt.parse(tr.getProperty("start"));
        duration = getElapsedTime();
        process(encoding);
    }

    private double getElapsedTime() throws TestResult.Fault {
        String elapsedTime = tr.getProperty("elapsed");
        if (elapsedTime == null) {
            elapsedTime = tr.getProperty("totalTime");
        }
        if (elapsedTime == null) {
            return 0.0;
        }
        String f[] = elapsedTime.split("\\s");
        double elapsed = Double.parseDouble(f[0]);
        return elapsed/1000;
    }

    private void createHeader(String encoding) {
        xps.println("<?xml version=\"1.0\" encoding=\"" + encoding + "\" ?>");
    }

    private void startTestSuite() throws TestResult.Fault {
        xps.print("<testsuite");
        xps.print(" errors=\"");
        xps.print(status.isError() ? "1" : "0");
        xps.print("\"");
        xps.print(" failures=\"");
        xps.print(status.isFailed() ? "1" : "0");
        xps.print("\"");
        xps.print(" tests=\"1\"");
        xps.print(" hostname=\"");
        xps.print(tr.getProperty("hostname"));
        xps.print("\"");
        xps.print(" name=\"");
        xps.print(classname);
        xps.print("\"");
        xps.print(" time=\"");
        xps.print(duration);
        xps.print("\"");
        xps.print(" timestamp=\"");
        xps.print(isoDateFmt.format(start));
        xps.print("\"");
        xps.println(">");
    }

    private void endTestSuite() {
        xps.println("</testsuite>");
    }

    private void insertProperties() throws TestResult.Fault {
        xps.indent();
        xps.println("<properties>");
        TestDescription td = tr.getDescription();
        @SuppressWarnings("unchecked")
        Iterator<String> iterator = td.getParameterKeys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            xps.indent(2);
            xps.print("<property name=\"");
            xps.print(key);
            xps.print("\"");
            xps.print(" value=\"");
            xps.sanitize(td.getParameter(key));
            xps.println("\" />");
        }

        @SuppressWarnings("unchecked")
        Enumeration<String> e = tr.getPropertyNames();
        while (e.hasMoreElements()) {
            String x = e.nextElement();
            xps.indent(2);
            xps.print("<property name=\"");
            xps.print(x);
            xps.print("\"");
            xps.print(" value=\"");
            xps.sanitize(tr.getProperty(x));
            xps.println("\" />");
        }
        xps.indent();
        xps.println("</properties>");
    }

    private String getOutput(String name) throws TestResult.Fault {
        String[] titles = tr.getSectionTitles();
        for (int i = 0; i < titles.length; i++) {
            if (titles[i].equals("main")) {
                Section s = tr.getSection(i);
                for (String x : s.getOutputNames()) {
                    return s.getOutput(name);
                }
            }
        }
        return "";
    }

    private void insertSystemOut() throws TestResult.Fault {
        xps.indent();
        xps.print("<system-out>");
        xps.sanitize(getOutput("System.out"));
        xps.indent();
        xps.println("</system-out>");
    }

    private void insertSystemErr() throws TestResult.Fault {
        xps.indent();
        xps.print("<system-err>");
        xps.sanitize(getOutput("System.err"));
        xps.indent();
        xps.println("</system-err>");
    }

    private void insertFailure() {
        if (status.isPassed())
            return;
        xps.indent();
        xps.print("<failure type=\"");
        xps.print(XMLWriter.FAILED);
        xps.println("\">");
        xps.sanitize(status.getReason());
        xps.indent();
        xps.println("");
        xps.println("</failure>");
    }

    private void insertTestCase() throws TestResult.Fault {
        xps.indent();
        xps.print("<testcase ");
        xps.print("classname=\"");
        xps.print(classname);
        xps.print("\"");
        xps.print(" name=\"");
        xps.print(tr.getDescription().getName());
        xps.print("\"");
        xps.print(" time=\"");
        xps.print(duration);
        xps.print("\"");
        xps.println(" >");
        insertFailure();
        xps.indent();
        xps.println("</testcase>");
    }

    private void process(String encoding) throws ParseException, TestResult.Fault {
        createHeader(encoding);
        startTestSuite();
        insertProperties();
        insertTestCase();
        insertSystemOut();
        insertSystemErr();
        endTestSuite();
    }

    @Override
    public String toString() {
        return xps.toString();
    }

    public void toXML() throws IOException  {
        File baseDir = xmlFile.getParentFile();
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        FileOutputStream fos = new FileOutputStream(xmlFile);
        try {
            xps.writeTo(fos);
        } finally {
            fos.close();
        }
        if (verify)
            xps.verifyXML(xmlFile);
    }

    private static void usage(PrintStream out, String message) {
        if (message != null) {
            out.println(message);
        }
        out.println("Usage:");
        out.println("  java -cp jtreg.jar com.sun.javatest.regtest.XMLWriter -@list");
        out.println("      where list is a file containing the .jtr files to be processed");
        out.println("  java -cp jtreg.jar com.sun.javatest.regtest.XMLWriter dir");
        out.println("      where dir is a JTwork dir containing the .jtr files to be processed");
        out.println("  java -cp jtreg.jar com.sun.javatest.regtest.XMLWriter 1.jtr 2.jtr.. n.jtr");
        out.println("      where args are a list of .jtr files to be processed");
        System.exit(1);
    }

    private static List<File> fileToList(String arg) throws Exception {
        BufferedReader br = null;
        List<File> outList = new ArrayList<File>();
        String in = arg.replaceFirst("^\\-@|^@", "");
        try {
            br = new BufferedReader(new FileReader(in));
            String line = br.readLine();
            while (line != null) {
                outList.add(new File(line));
                line = br.readLine();
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }
        return outList;
    }

    private static void scan(File file, List<File> xlist) {
        if (file.isFile() && file.getName().endsWith(".jtr")) {
            xlist.add(file);
        } else if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                scan(f, xlist);
            }
        }
    }

    private static List<File> dirToList(File f) {
        List<File> outList = new ArrayList<File>();
        scan(f, outList);
        return outList;
    }

    private static void translateList(List<File> xmlFileList) throws Exception {
        if (xmlFileList == null || xmlFileList.isEmpty()) {
            usage(System.out, "Warning: nothing to process");
        }
        for (File jtrFile : xmlFileList) {
            if (jtrFile.exists() && jtrFile.isFile() && jtrFile.getName().endsWith(".jtr")) {
                XMLWriter jutr = new XMLWriter(new TestResult(jtrFile), true,
                        new PrintWriter(System.out, true), new PrintWriter(System.err, true));
                jutr.toXML();
            } else {
                System.out.println("Warning: skipping file " + jtrFile);
            }
        }
    }

    private static List<File> argsToList(String... args) {
        List<File> outList = new ArrayList<File>();
        for (String x : args) {
            outList.add(new File(x));
        }
        return outList;
    }

    // utility to populate a previous run with xml files
    public static void main(String[] args) {
        try {
            if (args == null || args.length < 1 ) {
                usage(System.err, "Error: insufficent arguments");
            }
            if (args[0].startsWith("-@") || args[0].startsWith("@")) {
                translateList(fileToList(args[0]));
            } else {
                File f = new File(args[0]);
                if (f.isDirectory()) {
                    translateList(dirToList(f));
                } else {
                    translateList(argsToList(args));
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static class XMLHarnessObserver extends BasicObserver {
        private final boolean mustVerify;
        private final PrintWriter harnessOut;
        private final PrintWriter harnessErr;

        private XMLHarnessObserver() {
            harnessOut = null;
            harnessErr = null;
            mustVerify = false;
        }

        public XMLHarnessObserver(boolean mustVerify,
                PrintWriter out, PrintWriter err) {
            harnessOut = out;
            harnessErr = err;
            this.mustVerify = mustVerify;
        }

        @Override
        public void finishedTest(TestResult tr) {
            try {
                super.finishedTest(tr);
                new XMLWriter(tr, mustVerify, harnessOut, harnessErr).toXML();
            } catch (IOException ex) {
                ex.printStackTrace(harnessOut);
            } catch (ParseException ex) {
                ex.printStackTrace(harnessOut);
            } catch (TestResult.Fault ex) {
                ex.printStackTrace(harnessOut);
            }
        }
    }
}

class XPrintStream {
    private final ByteArrayOutputStream bstream;
    private final PrintStream ps;
    private final PrintWriter out;
    private final PrintWriter err;

    private static final String INDENT = "    ";

    XPrintStream(PrintWriter out, PrintWriter err, String encoding)
            throws UnsupportedEncodingException {
        this.out = out;
        this.err = err;
        this.bstream = new ByteArrayOutputStream();
        this.ps = new PrintStream(bstream, false, encoding);
    }

    void indent() {
        ps.print(INDENT);
    }

    public void indent(int n) {
        for (int i = 0; i < n; i++) {
            indent();
        }
    }

    public void println(String in) {
        ps.println(in);
    }

    public void print(String in) {
        ps.print(in);
    }

    public void print(double d) {
        ps.print(d);
    }

    // sanitize the string for xml presentation
    public void sanitize(String in) {
        if (in == null)
            return;

        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);
            switch (ch) {
                case '&':  ps.print("&amp;");  break;
                case '<':  ps.print("&lt;");   break;
                case '>':  ps.print("&gt;");   break;
                case '"':  ps.print("&quot;"); break;
                case '\'': ps.print("&apos;"); break;

                // case 'f': // not a valid XML character
                case '\n':
                case '\r':
                case '\t':
                    ps.print(ch);
                    break;

                case '\\':
                    if (i + 1 < in.length() && in.charAt(i + 1) == 'u') {
                        // encode "backslash u" as "backslash u u" to distinguish
                        // it from use of unicode escapes for control characters.
                        ps.print("\\uu");
                        i++;
                    } else {
                        ps.print(ch);
                    }
                    break;

                default:
                    if (ch < 32 || !Character.isDefined(ch)) {
                        // Ideally, we'd print control characters as numeric
                        // character entities, but a validating SAX parser
                        // rejects that, so we encode them as Unicode instead.
                        ps.print(String.format("\\u%04x", (int) ch));
                    } else {
                        ps.print(ch);
                    }
            }
        }
    }

    public void close() {
        ps.close();
    }

    @Override
    public String toString() {
        return bstream.toString();
    }

    public void writeTo(OutputStream os) throws IOException {
        bstream.writeTo(os);
    }

    // we don't want to see errors in hudson and it may take a while, depending
    // on the job, so we verify for xml conformance.
    public void verifyXML(File xmlFile) throws IOException {
        try {
            SAXParserFactory sax = SAXParserFactory.newInstance();
            sax.setValidating(false);
            SAXParser parser = sax.newSAXParser();
            XMLReader xmlreader = parser.getXMLReader();
            xmlreader.parse(new InputSource(new ByteArrayInputStream(bstream.toByteArray())));
            //out.println("File: " + xmlFile + ": verified");
        } catch (IOException ex) {
            err.println("File: " + xmlFile + ":" + ex);
            err.println(toString());
        } catch (ParserConfigurationException ex) {
            err.println("File: " + xmlFile + ":" + ex);
            err.println(toString());
        } catch (SAXException ex) {
            err.println("File: " + xmlFile + ":" + ex);
            err.println(toString());
        }
    }
}
