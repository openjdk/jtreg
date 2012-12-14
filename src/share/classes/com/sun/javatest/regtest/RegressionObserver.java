/*
 * Copyright 2006-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.javatest.regtest;

import java.io.PrintWriter;
import com.sun.javatest.Harness;
import com.sun.javatest.Parameters;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestResult;
import com.sun.javatest.Status;

// TODO: I18N

public class RegressionObserver implements Harness.Observer {

    RegressionObserver(Verbose verbose, PrintWriter out, PrintWriter err) {
        this.verbose = verbose;
        this.out = out;
        this.err = err;
    }

    public void startingTestRun(Parameters params) { }
    public void stoppingTestRun() { }
    public void finishedTesting() { }
    public void finishedTestRun(boolean allOK) { }


    public synchronized void startingTest(TestResult tr) {
        if (verbose == Verbose.DEFAULT) {
            try {
                TestDescription td = tr.getDescription();
                out.println("runner starting test: "
                        + td.getRootRelativeURL());
            } catch(TestResult.Fault e) {
                e.printStackTrace();
            }
        }
    } // starting()

    public synchronized void finishedTest(TestResult tr) {
        Verbose.Mode m;
        switch (tr.getStatus().getType()) {
            case Status.PASSED:
                m = verbose.passMode;
                break;
            case Status.FAILED:
                m = verbose.failMode;
                break;
            case Status.ERROR:
                m = verbose.errorMode;
                break;
            default:
                m = Verbose.Mode.NONE;
        }

        switch (m) {
            case NONE:
                break;

            case DEFAULT:
                try {
                    TestDescription td = tr.getDescription();
                    if (verbose.time)
                        printElapsedTimes(tr);
                    out.println("runner finished test: "
                            + td.getRootRelativeURL());
                    out.println(tr.getStatus());
                } catch (TestResult.Fault e) {
                    e.printStackTrace();
                }
                break;

            case SUMMARY:
                printSummary(tr, verbose.time);
                break;

            case BRIEF:
                printBriefOutput(tr, verbose.time);
                break;

            case FULL:
                printFullOutput(tr);
                break;
        }
    } // finished()

    /**
     * Print out one line per test indicating the status category for the
     * named test.
     *
     * @param tr TestResult containing all recorded information from a
     *         test's run.
     */
    private void printSummary(TestResult tr, boolean times) {
        try {
            TestDescription td = tr.getDescription();
            String msg;

            if (tr.getStatus().isPassed())
                msg = "Passed: ";
            else if (tr.getStatus().isFailed())
                msg = "FAILED: ";
            else if (tr.getStatus().isError())
                msg = "Error:  ";
            else
                msg = "Unexpected status: ";
            msg += td.getRootRelativeURL();
            out.println(msg);

            if (times)
                printElapsedTimes(tr);

        } catch (TestResult.Fault e) {
            e.printStackTrace();
        }
    } // printSummary()

    /**
     * Print out two lines per test indicating the name of the test and the
     * final status.
     *
     * @param tr TestResult containing all recorded information from a
     *         test's run.
     */
    private void printBriefOutput(TestResult tr, boolean times) {
        if (!doneSeparator) {
            out.println(VERBOSE_TEST_SEP);
            doneSeparator = true;
        }

        try {
            TestDescription td = tr.getDescription();
            out.println("TEST: " + td.getRootRelativeURL());

            if (times)
                printElapsedTimes(tr);

            out.println("TEST RESULT: " + tr.getStatus());
        } catch (TestResult.Fault e) {
            e.printStackTrace();
        }

        out.println(VERBOSE_TEST_SEP);
    } // printBriefOutput()

    /**
     * Print out full information for the test.  This includes the reason
     * for running each action, the action's output streams, the final
     * status, etc.   This is meant to encompass all of the information
     * from the .jtr file that the average user would find useful.
     *
     * @param tr TestResult containing all recorded information from a
     *         test's run.
     */
    private void printFullOutput(TestResult tr) {
        if (!doneSeparator) {
            out.println(VERBOSE_TEST_SEP);
            doneSeparator = true;
        }

        try {
            TestDescription td = tr.getDescription();
            out.println("TEST: " + td.getRootRelativeURL());
            out.println(getTestJDK(tr));

            StringBuffer sb = new StringBuffer();
            for (int i = 1; i < tr.getSectionCount(); i++) {
                TestResult.Section section = tr.getSection(i);
                sb.append(LINESEP);
                sb.append("ACTION: ").append(section.getTitle());
                sb.append(" -- ").append(section.getStatus()).append(LINESEP);
                sb.append("REASON: ").append(getReason(section)).append(LINESEP);
                sb.append("TIME:   ").append(getElapsedTime(section));
                sb.append(" seconds").append(LINESEP);

                String[] outputNames = section.getOutputNames();
                for (int n = 0; n < outputNames.length; n++) {
                    String name = outputNames[n];
                    String output = section.getOutput(name);
                    if (name.equals("System.out"))
                        sb.append("STDOUT:" + LINESEP + output);
                    else if (name.equals("System.err"))
                        sb.append("STDERR:" + LINESEP + output);
                    else
                        sb.append(name + ":" + LINESEP + output);
                }
            }
            out.println(sb.toString());

            out.println("TEST RESULT: " + tr.getStatus());
            out.println(VERBOSE_TEST_SEP);
        } catch (TestResult.Fault e) {
            e.printStackTrace();
        }
    } // printFullOutput()

    /**
     * Print out one line per test indicating the status category for the
     * named test and the elapsed time per action in the test.
     *
     * @param tr TestResult containing all recorded information from a
     *         test's run.
     */
    private void printElapsedTimes(TestResult tr) {
        StringBuffer sb = new StringBuffer();
        try {
            for (int i = 1; i < tr.getSectionCount(); i++) {
                TestResult.Section section = tr.getSection(i);
                sb.append("  ").append(section.getTitle()).append(": ");
                sb.append(getElapsedTime(section)).append(" seconds").append(LINESEP);
            }
            out.print(sb.toString());
        } catch (TestResult.ReloadFault f) {
            f.printStackTrace();
        }
    } // printElapsedTimes()

    /**
     * Find the reason that the action was run.  This method takes
     * advantage of the fact that the reason string begins with
     * <code>reason: </code>, ends with a line separator.
     *
     * @param section The recorded information for a single action.
     * @return The reason string without the beginning
     * <code>reason: </code>
     * string.
     */
    private String getReason(TestResult.Section section) {
        String msg = section.getOutput(TestResult.MESSAGE_OUTPUT_NAME);
        int posStart = msg.indexOf("reason: ") + "reason: ".length();
        int posEnd   = msg.indexOf(LINESEP, posStart);
        return msg.substring(posStart, posEnd);
    } // getReason()

    /**
     * Find the elapsed time for the action.  This method takes advantage
     * of the fact that the string containing the elapsed time begins with
     * <code>elapsed time (seconds): </code>, and ends with a line
     * separator.
     *
     * @param section The recorded information for a single action.
     * @return The elapsed time without the beginning <code> elapsed time
     * (seconds): </code> as a string.
     */
    private String getElapsedTime(TestResult.Section section) {
        String msg = section.getOutput(TestResult.MESSAGE_OUTPUT_NAME);
        int posStart = msg.indexOf("elapsed time (seconds): ") +
                "elapsed time (seconds): ".length();
        int posEnd = msg.indexOf(LINESEP, posStart);
        return msg.substring(posStart, posEnd);
    } // getElapsedTime()


    /**
     * Find the JDK under test.  This string is sent to the System message
     * section.  This method takes advantage of the fact that desired
     * string begins with <code>JDK under test: </code>, ends with a line
     * separator, and is the last string in the test result's default
     * message stream.
     *
     * @param tr The test result where all sections are stored.
     * @return The string indicating the JDK under test.
     */
    private String getTestJDK(TestResult tr) {
        try {
            TestResult.Section section = tr.getSection(0);
            String msg = section.getOutput(TestResult.MESSAGE_OUTPUT_NAME);
            int pos = msg.indexOf("JDK under test: ");
            if (pos >= 0)
                return msg.substring(pos, msg.length() - 1);
        } catch (TestResult.ReloadFault f) {
            f.printStackTrace();
        }
        return "???";
    } // getTestJDK()

    //------methods from Log-----------------------------------------------

    public synchronized void report(String s) {
    }

    public synchronized void report(String[] s) {
    }

    public synchronized void error(String s) {
        err.println("Error: " + s);
    }

    public synchronized void error(String[] s) {
        err.println("Error:");
        for (int i = 0; i < s.length; i++)
            err.println(s[i]);
    }

    //----------member variables-------------------------------------------

    private static final String VERBOSE_TEST_SEP = "--------------------------------------------------";
    private static final String LINESEP = System.getProperty("line.separator");

    private Verbose verbose;
    private PrintWriter out;
    private PrintWriter err;
    private boolean doneSeparator;
}
