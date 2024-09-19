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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.sun.javatest.Harness;
import com.sun.javatest.Parameters;
import com.sun.javatest.Status;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestFilter;
import com.sun.javatest.TestResult;
import com.sun.javatest.TestResultTable;
import com.sun.javatest.regtest.agent.MainActionHelper;
import com.sun.javatest.regtest.config.RegressionParameters;
import com.sun.javatest.report.Report;
import com.sun.javatest.util.I18NResourceBundle;

/**
 * Track test status statistics
 */
public class TestStats {
    private Map<TestFilter, ? extends List<TestDescription>> filterDetails;
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
            public void finishedTesting(TestResultTable.TreeIterator treeIterator) {
                filterDetails = treeIterator.getFilterStats();
                filterDetails.forEach((f, tds) -> {
                    int count = tds.size();
                    switch (f.getName()) {
                        case "jtregExcludeListFilter":
                            notRun_excluded_count += count;
                            break;
                        case "jtregMatchListFilter":
                            notRun_matchList_count += count;
                            break;
                        case "jtregPriorStatusFilter":
                            notRun_priorStatus_count += count;
                            break;
                        case "Keywords":
                            notRun_keywords_count += count;
                            break;
                        case "ModulesFilter":
                            notRun_modules_count += count;
                            break;
                        case "RequiresFilter":
                            notRun_requires_count += count;
                            break;
                        case "TimeLimitFilter":
                            notRun_timeLimit_count += count;
                            break;

                        default: {
                            System.err.println("Filter not recognized: " + f.getName() + "(" + f + ")");
                            notRun_other_count += count;
                        }
                    }
                });
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
            passed_skipped_count++;
        }
    }

    public void addAll(TestStats other) {
        for (int i = 0; i < counts.length; i++)
            counts[i] += other.counts[i];
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
                if (passed_skipped_count > 0) {
                    msg += i18n.getString("stats.tests.skipped", passed_skipped_count);
                }
                if (notRun_excluded_count > 0) {
                    msg += i18n.getString("stats.tests.excluded", notRun_excluded_count);
                }
                if (notRun_matchList_count > 0) {
                    msg += i18n.getString("stats.tests.matchList", notRun_matchList_count);
                }
                if (notRun_keywords_count > 0) {
                    msg += i18n.getString("stats.tests.keywords", notRun_keywords_count);
                }
                if (notRun_modules_count > 0) {
                    msg += i18n.getString("stats.tests.modules", notRun_modules_count);
                }
                if (notRun_requires_count > 0) {
                    msg += i18n.getString("stats.tests.requires", notRun_requires_count);
                }
                if (notRun_priorStatus_count > 0) {
                    msg += i18n.getString("stats.tests.priorStatus", notRun_priorStatus_count);
                }
                if (notRun_timeLimit_count > 0) {
                    msg += i18n.getString("stats.tests.timeLimit", notRun_timeLimit_count);
                }
            }
        }
        out.println(msg);
    }

    public void report(Report report) throws IOException {
        File reportDir = report.getReportDir();
        File reportTextDir = new File(reportDir, "text");
        reportTextDir.mkdirs();
        File statsTxt = new File(reportTextDir, "stats.txt");
        report(statsTxt);
        File notRunTxt = new File(reportTextDir, "notRun.txt");
        reportNotRunTests(notRunTxt);
    }

    public void report(File file) throws IOException {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            showResultStats(out);
        }
    }

    public void reportNotRunTests(File file) throws IOException {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            reportNotRunTests(out);
        }
    }

    public void reportNotRunTests(PrintWriter out) {
        if (filterDetails == null || filterDetails.isEmpty()) {
            out.println("no tests were filtered out");
        } else {
            Map<TestDescription, Status> map = new TreeMap<>(Comparator.comparing(TestDescription::getRootRelativeURL));
            filterDetails.forEach((f, tds) -> {
                var status = new Status(Status.NOT_RUN, f.getReason());
                tds.forEach(td -> map.put(td, status));
            });
            var maxNameLength = map.keySet().stream()
                    .map(TestDescription::getRootRelativeURL)
                    .mapToInt(String::length)
                    .max().getAsInt();
            for (var e : map.entrySet()) {
                var td = e.getKey();
                var td_url = td.getRootRelativeURL();
                var status = e.getValue();
                out.println(td_url
                        + " ".repeat(maxNameLength - td_url.length() + 1)
                        + status);
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
     * %r       number of tests run
     * %s       number of skipped tests (run but threw SkippedException)
     *
     * %n       number of tests not run
     * %m       number of tests not meeting module requirements
     * %R       number of tests not meeting platform requirements
     * %S       number of tests not matching specified status
     * %t       number of tests not matching time limit requirements
     * %x       number of excluded tests
     * %x       number of tests not on match list
     * %k %i    number of keyword-ignored tests
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
                    case 'k':
                    case 'n':
                    case 'p':
                    case 'P':
                    case 'r':
                    case 'R':
                    case 's':
                    case 't':
                    case 'S':
                    case 'x':
                    case 'X': {
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
            case 'i': case 'k': // i for backward compatibility; was "ignored"
                return notRun_keywords_count;
            case 'm':
                return notRun_modules_count;
            case 'n':
                return counts[Status.NOT_RUN];
            case 'o':
                return notRun_other_count;
            case 'p':
                return counts[Status.PASSED];
            case 'P':
                return counts[Status.PASSED] - passed_skipped_count;
            case 'r':
                return counts[Status.PASSED] + counts[Status.FAILED] + counts[Status.ERROR];
            case 'R':
                return notRun_requires_count;
            case 's':
                return passed_skipped_count;
            case 'S':
                return notRun_priorStatus_count;
            case 't':
                return notRun_timeLimit_count;
            case 'x':
                return notRun_excluded_count;
            case 'X':
                return notRun_matchList_count;
            default:
                return -1;
        }
    }

    /**
     * The numbers of passed, failed, and error tests.
     */
    public int[] counts = new int[Status.NUM_STATES];

    /**
     * The number of "skipped tests".
     * Skipped tests are a subset of passed tests:
     * the test class was executed, but it threw jtreg.SkippedException
     */
    int passed_skipped_count;

    // not run tests

    /**
     * The number of tests excluded by an exclude list (problem list).
     * See -exclude option.
     */
    int notRun_excluded_count;

    /**
     * The number of tests not run because of keywords.
     * See -k option.
     */
    int notRun_keywords_count;

    /**
     * The number of tests not run because not on a match list.
     * See -match option.
     */
    int notRun_matchList_count;

    /**
     * The number of tests not run because required modules were not available.
     * See the @modules tag and the modules in the JDK under test.
     */
    int notRun_modules_count;

    /**
     * The number of tests not run because of the prior status.
     * See the -status option.
     */
    int notRun_priorStatus_count;

    /**
     * The number of tests not run because the platform requirements were not met.
     * See the @requires tag and the full set of platform properties.
     */
    int notRun_requires_count;

    /**
     * The number of tests not run because of the potential run time.
     * See the -timelimit and other timeout-related options, and the
     * declared timeout value for the test.
     */
    int notRun_timeLimit_count;

    /**
     * Fall through value for unrecognized filters. Should normally be zero.
     */
    int notRun_other_count;

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(TestStats.class);
}
