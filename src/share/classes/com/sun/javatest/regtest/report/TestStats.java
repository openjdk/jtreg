/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.javatest.Harness;
import com.sun.javatest.Parameters;
import com.sun.javatest.Status;
import com.sun.javatest.TestResult;
import com.sun.javatest.regtest.agent.MainActionHelper;
import com.sun.javatest.regtest.config.CachingTestFilter;
import com.sun.javatest.regtest.config.RegressionParameters;
import com.sun.javatest.report.Report;
import com.sun.javatest.util.I18NResourceBundle;

/**
 * Track test status statistics
 */
public class TestStats {
    public void register(Harness h) {
        // See the comments for RegressionParameters.getExcludeListFilter
        // for notes regarding the use of CachingTestFilter vs. ObservableTestFilter
        // for params.getExcludeListFilter.
        h.addObserver(new BasicObserver() {
            @Override
            public void startingTestRun(Parameters params) {
                this.params = (RegressionParameters) params;
            }

            @Override
            public void finishedTesting() {
                CachingTestFilter ef = params.getExcludeListFilter();
                if (ef != null) {
                    for (CachingTestFilter.Entry e: ef.getCacheEntries()) {
                        if (!e.value)
                            excluded++;
                    }
                }
                RegressionParameters.KeywordsTestFilter kf = params.getKeywordsFilter();
                if (kf != null) {
                    ignored = kf.ignored.size();
                }
            }

            @Override
            public void finishedTest(TestResult tr) {
                add(tr);
            }

            RegressionParameters params;
        });
    }

    public void add(TestResult tr) {
        counts[tr.getStatus().getType()]++;
        if (tr.getStatus().getReason().startsWith(MainActionHelper.MAIN_SKIPPED_STATUS_PREFIX)) {
            skipped++;
        }
        if (tr.getStatus().isNotRun()) {
            notRunResults.add(tr);
        }
    }

    public void addAll(TestStats other) {
        for (int i = 0; i < counts.length; i++) {
            counts[i] += other.counts[i];
        }
        notRunResults.addAll(other.notRunResults);
    }

    public boolean isOK() {
        return (counts[Status.FAILED] == 0 && counts[Status.ERROR] ==0);
    }

    public void showResultStats(PrintWriter out) {
        int p = counts[Status.PASSED];
        int f = counts[Status.FAILED];
        int e = counts[Status.ERROR];
        int nr = counts[Status.NOT_RUN];

        String msg;
        if (p + f + e + nr == 0)
            msg = i18n.getString("stats.noTests");
        else {
            String format = System.getProperty("jtreg.stats.format");
            if (format != null)
                msg = getText(format);
            else {
                msg = i18n.getString("stats.tests",
                    p, ((p > 0) && (f + e + nr > 0) ? 1 : 0),
                    f, ((f > 0) && (    e + nr > 0) ? 1 : 0),
                    e, ((e > 0) && (        nr > 0) ? 1 : 0),
                    nr);
                if (excluded > 0) {
                    msg += i18n.getString("stats.tests.excluded", excluded);
                }
                if (ignored > 0) {
                    msg += i18n.getString("stats.tests.ignored", ignored);
                }
                if (skipped > 0) {
                    msg += i18n.getString("stats.tests.skipped", skipped);
                }
            }
        }
        out.println(msg);
    }

    public void report(Report report) throws IOException {
        File reportDir = report.getReportDir();
        File reportTextDir = new File(reportDir, "text");
        reportTextDir.mkdirs();
        report(new File(reportTextDir, "stats.txt"));
        reportNotRunSummary(new File(reportTextDir, "not-run-summary.txt"));
    }

    private void report(File file) throws IOException {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            showResultStats(out);
        }
    }

    private void reportNotRunSummary(File file) throws IOException {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            for (TestResult tr : notRunResults) {
                out.println(tr.getTestName() + ": " + tr.getStatus().getReason());
            }
        }
    }

    /*
     * Evaluate a format string.  The following characters are supported.
     * <pre>
     * %f       number of failed tests
     * %F       number of failed and error tests
     * %e       number of error tests
     * %p       number of passed tests, including skipped tests
     * %P       number of passed tests, excluding skipped tests
     * %n       number of tests not run
     * %r       number of tests run
     * %s       number of skipped tests
     * %x       number of excluded tests
     * %i       number of keyword-ignored tests
     * %,       conditional comma
     * %<space> conditional space
     * %%       %
     * %?X      prints given number if not zero, where X is one of f, F, e, p, P, s, x, i
     * %?{textX} prints text and given number if number is not zero, where
     *              X is one of f, F, e, p, x, i
     * </pre>
     */
    String getText(String format) {
        StringBuilder sb = new StringBuilder();
        int sbLen = 0;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '%' && i + 1 < format.length()) {
                c = format.charAt(++i);
                switch (c) {
                    case 'f':
                    case 'F':
                    case 'e':
                    case 'i':
                    case 'n':
                    case 'p':
                    case 'P':
                    case 'r':
                    case 's':
                    case 'x': {
                        int count = getNumber(c);
                        if (count >= 0)
                            sb.append(String.valueOf(count));
                        else
                            sb.append("%").append(c);
                        break;
                    }

                    case ',':
                        if (sb.length() > 0)
                            sb.append(",");
                        continue; // don't update sbLen

                    case ' ':
                        if (sb.length() > 0
                                && !Character.isWhitespace(sb.charAt(sb.length() - 1))) {
                            sb.append(' ');
                        }
                        continue; // don't update sbLen

                    case '%':
                        sb.append("%");
                        break;

                    case '?':
                        if (i + 1 < format.length()) {
                            c = format.charAt(++i);
                            if (c == '{') {
                                int j = format.indexOf("}", i);
                                if (j == -1) {
                                    sb.append("%?").append(format.substring(i));
                                    i = format.length();
                                } else {
                                    String text = format.substring(i + 1, j);
                                    i = j;
                                    if (text.length() > 0) {
                                        c = text.charAt(text.length() - 1);
                                        int count = getNumber(c);
                                        if (count > 0) {
                                            sb.append(text, 0, text.length() - 1)
                                                .append(String.valueOf(count));
                                        } else if (count < 0)
                                            sb.append("%?{").append(text).append("}");
                                        else {
                                            continue; // don't update sbLen
                                        }
                                    }
                                }
                            } else {
                                int count = getNumber(c);
                                if (count > 0) {
                                    sb.append(String.valueOf(count));
                                } else if (count < 0) {
                                    sb.append("%?").append(c);
                                } else {
                                    continue; // don't update sbLen
                                }
                                break;

                            }
                        } else {
                            sb.append("%").append("?");
                        }
                        break;

                    default: {
                        sb.append("%").append(c);
                    }
                }
            } else
                sb.append(c);

            sbLen = sb.length();
        }
        return sb.substring(0, sbLen);
    }

    int getNumber(char c) {
        switch (c) {
            case 'f':
                return counts[Status.FAILED];
            case 'F':
                return counts[Status.FAILED] + counts[Status.ERROR];
            case 'e':
                return counts[Status.ERROR];
            case 'i':
                return ignored;
            case 'n':
                return counts[Status.NOT_RUN];
            case 'p':
                return counts[Status.PASSED];
            case 'P':
                return counts[Status.PASSED] - skipped;
            case 'r':
                return counts[Status.PASSED] + counts[Status.FAILED] + counts[Status.ERROR];
            case 's':
                return skipped;
            case 'x':
                return excluded;
            default:
                return -1;
        }
    }

    public int[] counts = new int[Status.NUM_STATES];
    int excluded;
    int ignored;
    int skipped;
    private final List<TestResult> notRunResults = new CopyOnWriteArrayList<>();

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(TestStats.class);
}
