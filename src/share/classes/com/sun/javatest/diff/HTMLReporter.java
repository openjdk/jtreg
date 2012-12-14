/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.Map;

import com.sun.javatest.TestResult;
import com.sun.javatest.Status;
import com.sun.javatest.util.I18NResourceBundle;

import static com.sun.javatest.util.HTMLWriter.*;

/*
 * TODO: import CSS
 * TODO: links to .jtr files
 */

/**
 * Report differences to an HTML file.
 */
public class HTMLReporter extends Reporter {

    /** Creates a new instance of HTMLReporter */
    public HTMLReporter(Writer out) throws IOException {
        this.out = new HTMLWriter(out, DOCTYPE);
        this.out.setI18NResourceBundle(i18n);
    }

    public void write(MultiMap<String, TestResult> table) throws IOException {
        this.table = table;
        size = table.getColumns();

        startReport(title);

        out.startTag(H1);
        if (title == null)
            out.writeI18N("html.head.notitle");
        else
            out.writeI18N("html.head.title", title);
        out.endTag(H1);

        writeIndexTable();
        writeMainTable();
        writeSummary();

        endReport();
    }

    protected void startReport(String title) throws IOException {
        out.startTag(HTML);
        writeHead(title);
        out.startTag(BODY);
    }

    protected void endReport() throws IOException {
        out.startTag(HR);
        out.writeI18N("html.generatedAt", new Date());
        out.endTag(BODY);

        out.endTag(HTML);
        out.flush();
    }

    protected void writeHead(String title) throws IOException {
        out.startTag(HEAD);
        out.startTag(TITLE);
        if (title == null)
            out.writeI18N("html.head.notitle");
        else
            out.writeI18N("html.head.title", title);
        out.endTag(TITLE);
        out.startTag(STYLE);
        out.writeAttr(TYPE, "text/css");
        out.write("\n");
        out.write("table   { background-color:white }");
        out.write("tr.head { background-color:#dddddd }");
        out.write("tr.odd  { background-color:#eeeeee }");
        out.write("tr.even { background-color:white } ");
        out.write("td { padding: 0 .5em }");
        out.write("td.pass { background-color:#ddffdd } ");
        out.write("td.fail { background-color:#ffdddd } ");
        out.write("td.error { background-color:#ddddff } ");
        out.write("td.notRun { background-color:#dddddd } ");
        out.write("th { padding: 0 .5em }");
        out.write("hr      { margin-top:30px; }");
        out.write("\n");
        out.endTag(STYLE);
        out.endTag(HEAD);

    }

    private void writeIndexTable() throws IOException {
        out.startTag(H2);
        out.writeI18N("html.head.sets");
        out.endTag(H2);

        out.startTag(TABLE);
        out.writeAttr(FRAME, BOX);
        out.writeAttr(RULES, GROUPS);
        out.startTag(THEAD);
        out.startTag(TR);
        out.writeAttr(CLASS, HEAD);
        out.startTag(TH);
        out.writeI18N("html.th.set");
        out.endTag(TH);
        out.startTag(TH);
        out.writeI18N("html.th.location");
        out.endTag(TH);
        writeIndexTableInfoHeadings();
//        out.startTag(TH);
//        out.writeI18N("html.th.type");
//        out.endTag(TH);
        for (int c = 0; c < Status.NUM_STATES; c++) {
            out.startTag(TH);
            switch (c) {
                case Status.PASSED:
                    out.writeI18N("html.th.pass");
                    break;
                case Status.FAILED:
                    out.writeI18N("html.th.fail");
                    break;
                case Status.ERROR:
                    out.writeI18N("html.th.error");
                    break;
                default:
                    out.writeI18N("html.th.notRun");
                    break;
            }
            out.endTag(TH);
        }
        out.startTag(TH);
        out.writeI18N("html.th.total");
        out.endTag(TH);
        out.endTag(TR);
        out.endTag(THEAD);

        out.startTag(TBODY);
        for (int i = 0; i < size; i++) {
            out.startTag(TR);
            out.writeAttr(CLASS, (i % 2 == 0 ? EVEN : ODD));
            out.startTag(TD);
            out.write(String.valueOf(i + 1));
            out.endTag(TD);
            out.startTag(TD);
            out.write(table.getColumnName(i));
            writeIndexTableInfoValues(table.getColumnName(i));
            out.endTag(TD);
//            out.startTag(TD);
//            out.write("??");
//            out.endTag(TD);
            int total = 0;
            int[] counts = testCounts.get(i);
            for (int c = 0; c < Status.NUM_STATES; c++) {
                out.startTag(TD);
                if (counts[c] > 0)
                    out.write(String.valueOf(counts[c]));
                else
                    out.writeEntity("&nbsp;");
                total += counts[c];
                out.endTag(TD);
            }
            out.startTag(TD);
            out.write(String.valueOf(total));
            out.endTag(TD);
            out.endTag(TR);
        }
        out.endTag(TBODY);
        out.endTag(TABLE);
    }

    protected void writeIndexTableInfoHeadings() throws IOException {
    }

    protected void writeIndexTableInfoValues(String name) throws IOException {
    }

    private void writeMainTable() throws IOException {
        diffs = 0;
        for (Map.Entry<String, MultiMap.Entry<TestResult>> e: table.entrySet()) {
            String testName = e.getKey();
            MultiMap.Entry<TestResult> result = e.getValue();
            if (result.allEqual(comparator))
                continue;
            if (diffs == 0) {
                out.startTag(H2);
                out.writeI18N("html.head.differences");
                out.endTag(H2);
                out.startTag(TABLE);
                out.writeAttr(FRAME, BOX);
                out.writeAttr(RULES, GROUPS);
                out.startTag(THEAD);
                out.startTag(TR);
                out.writeAttr(CLASS, HEAD);
                out.startTag(TH);
                out.writeI18N("html.th.test");
                out.endTag(TH);
                for (int i = 0; i < result.getSize(); i++) {
                    out.startTag(TH);
                    if (compact)
                        out.write(String.valueOf(i + 1));
                    else
                        out.writeI18N("html.th.setN", i + 1);
                    out.endTag(TH);
                }
                out.endTag(TR);
                out.endTag(THEAD);
                out.startTag(TBODY);
            }
            out.startTag(TR);
            out.writeAttr(CLASS, (diffs % 2 == 0 ? EVEN : ODD));
            out.startTag(TD);
            out.write(testName);
            out.endTag(TD);
            for (int i = 0; i < result.getSize(); i++) {
                TestResult tr = result.get(i);
                File trFile = (tr == null ? null : tr.getFile());
                if (trFile == null) {
                    File wd = readers.get(i).getWorkDirectory();
                    if (wd != null)
                        trFile = new File(wd, tr.getWorkRelativePath());
                }
                out.startTag(TD);
                Status s = (tr == null ? null : tr.getStatus());
                out.writeAttr(CLASS, getClassAttr(s));
                String text = getText(s);
                if (trFile != null && trFile.exists()) {
                    out.startTag(A);
                    out.writeAttr(HREF, trFile.toURI().toString());
                    if (text.startsWith("&"))
                        out.writeEntity(text);
                    else
                        out.write(text);
                    out.endTag(A);
                } else {
                    if (text.startsWith("&"))
                        out.writeEntity(text);
                    else
                        out.write(text);
                }
                out.endTag(TD);
            }
            out.endTag(TR);
            diffs++;
        }
        if (diffs > 0) {
            out.endTag(TBODY);
            out.endTag(TABLE);
        }
    }

    private void writeSummary() throws IOException {
        out.startTag(P);
        if (diffs == 0)
            out.writeI18N("html.diffs.none");
        else
            out.writeI18N("html.diffs.count", diffs);
        out.endTag(P);
    }

    protected String getClassAttr(Status s) {
        switch (s == null ? Status.NOT_RUN : s.getType()) {
            case Status.PASSED:
                return PASS;
            case Status.FAILED:
                return FAIL;
            case Status.ERROR:
                return ERROR;
            default:
                return NOT_RUN;
        }
    }

    protected String getText(Status s) {
        if (statusStrings == null) {
            statusStrings = new String[Status.NUM_STATES];
            if (compact) {
                statusStrings[Status.PASSED] = i18n.getString("html.pass.compact");
                statusStrings[Status.FAILED] = i18n.getString("html.fail.compact");
                statusStrings[Status.ERROR] = i18n.getString("html.error.compact");
                statusStrings[Status.NOT_RUN] = i18n.getString("html.notRun.compact");
            } else {
                statusStrings[Status.PASSED] = i18n.getString("html.pass");
                statusStrings[Status.FAILED] = i18n.getString("html.fail");
                statusStrings[Status.ERROR] = i18n.getString("html.error");
                statusStrings[Status.NOT_RUN] = i18n.getString("html.notRun");
            }
        }
        return statusStrings[s == null ? Status.NOT_RUN : s.getType()];
    }

    private String[] statusStrings;

    protected final HTMLWriter out;
    private MultiMap<String, TestResult> table;
    private int size;

    private static final String DOCTYPE =
            "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3c.org/TR/1999/REC-html401-19991224/loose.dtd\">";

    // HTML tags
    private static final String THEAD = "thead";
    private static final String TBODY = "tbody";

    // HTML attribute names
    private static final String CLASS = "class";
    private static final String FRAME = "frame";
    private static final String RULES = "rules";

    // HTML attribute values
    private static final String BOX = "box";
    private static final String GROUPS = "groups";

    // HTML class values
    private static final String HEAD = "head";
    private static final String ODD  = "odd";
    private static final String EVEN = "even";
    private static final String PASS = "pass";
    private static final String FAIL = "fail";
    private static final String ERROR = "error";
    private static final String NOT_RUN = "notRun";

    private boolean compact = Boolean.TRUE.equals(Boolean.getBoolean("jtdiff.html.compact"));
    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(HTMLReporter.class);
}
