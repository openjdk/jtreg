/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.sun.javatest.util.I18NResourceBundle;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import static com.sun.javatest.util.HTMLWriter.*;

class SuperDiff extends Diff {
    SuperDiff(File dir) {
        table = new SuperTable(dir, resultPath);
    }

    @Override
    public boolean report(File outDir) throws Fault, InterruptedException {
        baseTitle = title;
        boolean ok = true;
        for (YearDay yearDay: table.getRecentKeys(historySize))
            ok &= diffPlatforms(yearDay, outDir);
        for (String platform : table.platforms) {
            ok &= diffHistory(platform, outDir);
        }
        writeIndex(outDir, baseTitle);
        return ok;
    }

    protected boolean diff(List<File> files, File outFile, String title) throws Fault, InterruptedException {
        this.title = title;
        reporter = null;
        return diff(files, outFile);
    }

    @Override
    protected void initReporter() throws Fault {
        try {
             reporter = new SuperReporter(out);
        } catch (IOException e) {
            throw new Fault(i18n, "main.cantOpenReport", e);
        }
    }

    private boolean diffPlatforms(YearDay yearDay, File outDir) throws Fault, InterruptedException {
        Map<String, File> pMap = table.get(yearDay);
        List<File> pDirs = new ArrayList<File>();
        for (String platform : table.platforms) {
            File dir = pMap.get(platform);
            if (dir != null) {
                pDirs.add(dir);
            }
        }
        File file = new File(outDir, yearDay.year + "_" + yearDay.dayOfYear + ".html");
        platformIndex.put(yearDay.toDateString(monthDayFormat), file);
        String prefix = baseTitle == null ? "" : baseTitle + ": ";
        return diff(pDirs, file, prefix + yearDay.toDateString(mediumDateFormat)); // I18N a better title?
    }

    private boolean diffHistory(String platform, File outDir) throws Fault, InterruptedException {
        List<File> pDirs = new ArrayList<File>();
        for (YearDay yearDay: table.getRecentKeys(historySize, platform)) {
            pDirs.add(table.get(yearDay).get(platform));
        }
        File file = new File(outDir, platform + ".html");
        historyIndex.put(platform, file);
        String prefix = baseTitle == null ? "" : baseTitle + ": ";
        return diff(pDirs, file, prefix + platform);// I18N a better title?
    }

    private void writeIndex(File outDir, String title) throws Fault {
        PrintWriter out;
        try {
            out = new PrintWriter(new BufferedWriter(new FileWriter(new File(outDir, "index.html"))));
        } catch (IOException e) {
            throw new Fault(i18n, "main.cantOpenReport", e);
        }

        try {
            SuperReporter r = new SuperReporter(out);
            r.writeMainIndex(title);
        } catch (IOException e) {
            throw new Fault(i18n, "main.ioError", e);
        } finally {
            out.close();
        }
    }

    protected String resultPath = System.getProperty("jtdiff.super.testResults", "JTreport/text/summary.txt");
    protected int historySize = Integer.getInteger("jtdiff.super.history", 21);

    private SuperTable table;
    private String baseTitle;
    private Map<String,File> historyIndex = new LinkedHashMap<String,File>();
    private Map<String,File> platformIndex = new LinkedHashMap<String,File>();

    private static DateFormat monthDayFormat = new SimpleDateFormat("MMM d");
    private static DateFormat mediumDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(Main.class);

    static class Info {
        Info(String platform, Date date) {
            this.platform = platform;
            this.date = date;
        }

        final String platform;
        final Date date;
    }

    class SuperReporter extends HTMLReporter {
        SuperReporter(Writer out) throws IOException {
            super(out);
        }

        protected void writeIndexTableInfoHeadings() throws IOException {
            out.startTag(TH);
            out.writeI18N("super.th.platform");
            out.endTag(TH);
            out.startTag(TH);
            out.writeI18N("super.th.date");
            out.endTag(TH);
        }

        protected void writeIndexTableInfoValues(String path) throws IOException {
            Info info = table.getInfo(path);
            out.startTag(TD);
            if (info != null)
                out.write(info.platform);
            out.endTag(TD);
            out.startTag(TD);
            if (info != null)
                out.write(monthDayFormat.format(info.date));
            out.endTag(TH);
        }

        void writeMainIndex(String title) throws IOException {
            startReport(title);

            out.startTag(H1);
            out.write(baseTitle);
            out.endTag(H1);

            writeMainIndexList(i18n.getString("super.platforms"), platformIndex);
            writeMainIndexList(i18n.getString("super.history"), historyIndex);

            endReport();
        }

        void writeMainIndexList(String head, Map<String,File> map) throws IOException {
            out.startTag(H2);
            out.write(head);
            out.endTag(H2);
            out.startTag(P);
            String comma = "";
            for (Map.Entry<String,File> e: map.entrySet()) {
                out.write(comma);
                out.startTag(A);
                out.writeAttr(HREF, e.getValue().getName());
                String nbsp = "";
                for (String s: e.getKey().split(" ")) {
                    out.writeEntity(nbsp);
                    out.write(s);
                    nbsp = "&nbsp;";
                }
                out.endTag(A);
                comma = ", ";
            }
        }
    }

    static class SuperTable extends TreeMap<YearDay, Map<String, File>> {

        static final long serialVersionUID = 5933594140534747584L;

        SuperTable(File inDir, String resultPath) {
            super();
            for (File pDir : inDir.listFiles()) {
                if (!pDir.isDirectory()) {
                    continue;
                }
                for (File yDir : pDir.listFiles()) {
                    if (!yDir.isDirectory()) {
                        continue;
                    }
                    for (File dDir : yDir.listFiles()) {
                        if (!dDir.isDirectory()) {
                            continue;
                        }
                        File resultDir = new File(dDir, resultPath);
                        if (resultDir.exists()) {
                            add(pDir.getName(), yDir.getName(), dDir.getName(), resultDir);
                        }
                    }
                }
            }
        }

        private void add(String platform, String year, String day, File dir) {
            platforms.add(platform);
            YearDay yd = new YearDay(year, day);
            Map<String, File> pMap = get(yd);
            if (pMap == null) {
                pMap = new HashMap<String, File>();
                put(yd, pMap);
            }
            pMap.put(platform, dir);

            Date date;
            try {
                Calendar c = Calendar.getInstance();
                c.clear();
                c.set(Calendar.YEAR, Integer.parseInt(year));
                c.set(Calendar.DAY_OF_YEAR, Integer.parseInt(day));
                date = c.getTime();
            } catch (NumberFormatException e) {
                date = null;
            }
            infoTable.put(dir.getPath(), new Info(platform, date));
        }

        List<YearDay> getRecentKeys(int n) {
            return getRecentKeys(n, null);
        }

        List<YearDay> getRecentKeys(int n, String platform) {
            LinkedList<YearDay> results = new LinkedList<YearDay>();
            List<YearDay> keys = new ArrayList<YearDay>(keySet());
            for (ListIterator<YearDay> iter = keys.listIterator(keys.size());
                    iter.hasPrevious() && results.size() < n; ) {
                YearDay key = iter.previous();
                if (platform == null || get(key).get(platform) != null)
                    results.addFirst(key);
            }
            return results;
        }

        Info getInfo(String path) {
            return infoTable.get(path);
        }

        final Set<String> platforms = new TreeSet<String>();
        final Map<String,Info> infoTable = new HashMap<String, Info>();
    }

    static class YearDay implements Comparable<YearDay> {
        YearDay(String year, String dayOfYear) {
            year.getClass();
            dayOfYear.getClass();
            this.year = year;
            this.dayOfYear = dayOfYear;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof YearDay))
                return false;
            YearDay ydo = (YearDay) o;
            return year.equals(ydo.year) && dayOfYear.equals(ydo.dayOfYear);
        }

        @Override
        public int hashCode() {
            return year.hashCode() * 37 + dayOfYear.hashCode();
        }

        //@Override
        public int compareTo(YearDay o) {
            int c = compare(year, o.year);
            return (c == 0 ? compare(dayOfYear, o.dayOfYear) : c);
        }

        public String toString() {
            return year + ":" + dayOfYear;
        }

        public Date asDate() {
            try {
                Calendar c = Calendar.getInstance();
                c.clear();
                c.set(Calendar.YEAR, Integer.parseInt(year));
                c.set(Calendar.DAY_OF_YEAR, Integer.parseInt(dayOfYear));
                return c.getTime();
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public String toDateString(DateFormat f) {
            Date d = asDate();
            return (d == null ? toString() : f.format(d));
        }

        private int compare(String left, String right) {
            return left.compareTo(right);
        }

        final String year;
        final String dayOfYear;
    }

}
