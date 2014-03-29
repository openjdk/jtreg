/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;

import com.sun.javatest.Harness;
import com.sun.javatest.InterviewParameters;
import com.sun.javatest.Parameters;
import com.sun.javatest.Status;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestFilter;
import com.sun.javatest.TestResult;
import com.sun.javatest.TestResultTable;
import com.sun.javatest.TestSuite;
import com.sun.javatest.WorkDirectory;
import com.sun.javatest.regtest.RegressionScript;
import com.sun.javatest.regtest.RegressionTestFinder;
import com.sun.javatest.regtest.RegressionTestSuite;
import com.sun.javatest.regtest.RegressionParameters;

public class Basic
{
    public static void main(String[] args) {
        try {
            Basic basic = new Basic(args);
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    public Basic(String[] args) {

        if (args.length != 6) {
            failed("wrong number of args, expected 6, got " + args.length);
        }

        int argc = 0;
        String testSuitePath = args[argc++];
        String reportDir     = args[argc++];
        String workDirPath   = args[argc++];
        String jdkPath       = args[argc++];
        String envVars       = args[argc++];
        String modeOpt       = args[argc++];

        System.out.println("testsuite: " + testSuitePath);
        System.out.println("reportDir: " + reportDir);
        System.out.println("workDir:   " + workDirPath);
        System.out.println("jdk:       " + jdkPath);
        System.out.println("envVars:   " + envVars);
        System.out.println("mode:      " + modeOpt);

        try {
            testSuite = new RegressionTestSuite(new File(testSuitePath));

            File wd = new File(workDirPath);
            if (WorkDirectory.isWorkDirectory(wd))
                workDir = WorkDirectory.open(wd, testSuite);
            else
                workDir = WorkDirectory.convert(wd, testSuite);

            String[] regtest_args = {
                "-a",
                "-e", envVars,
                "-w", workDir.getPath(),
                "-r", reportDir,
                "-jdk", jdkPath,
                "-observer", Basic.Observer.class.getName(),
                "-observerPath", System.getProperty("java.class.path"),
                modeOpt,
                testSuite.getPath()
            };

            System.out.println(INFO_INITIAL_RUN);
            System.setProperty("javatest.regtest.args", "old");
            com.sun.javatest.regtest.Main m = new com.sun.javatest.regtest.Main();
            m.run(regtest_args);
            System.out.println(INFO_OK + "...batch execution");

            setExpectedTestStats();

            System.out.println("checking selection according to previous state");
            for (int i = 0; i < statusList.length; i++)
                checkPreviousStateSelection(statusList[i]);
            System.out.println(INFO_OK);

            System.out.println("checking selection according to verb");
            for (int i = 0; i < actionList.length; i++)
                checkAction(actionList[i]);
            System.out.println(INFO_OK);

        } catch (Harness.Fault e) {
            failed(ERR_HARNESS_RUN_PROB, e);
        } catch (InterruptedException e) {
            failed(ERR_HARNESS_INTR);
        } catch (WorkDirectory.Fault e) {
            failed(ERR_BAD_VAL, e);
        } catch (FileNotFoundException e) {
            failed(ERR_BAD_VAL, e);
        } catch (IOException e) {
            failed(ERR_IO_ERROR, e);
        } catch (TestSuite.Fault f) {
            failed(ERR_SUITE, f);
        } catch (com.sun.javatest.regtest.BadArgs e) {
            failed(ERR_BAD_ARGS, e);
        } catch (com.sun.javatest.regtest.Main.Fault e) {
            failed(ERR_FAULT, e);
        }
    }

    //----------internal methods------------------------------------------------

    /**
     * When a new test is added to the basic test suite, in general, the only
     * modifications that should be needed will occur in this method.
     */
    private void setExpectedTestStats() {

        // status counts
        int numPassed = 0, numFailed = 0, numError  = 0;

        // action counts
        int numApplet = 0, numBuild  = 0, numClean  = 0, numCompile = 0,
            numIgnore = 0, numMain   = 0, numShell  = 0, numMisc    = 0;
        int numJUnit  = 0;
        int numTestNG = 0;
        int numDriver = 0;

        // applet
        numPassed += 2; numFailed += 4; numError  += 11;
        numApplet += 17;

        // build
        numPassed += 6; numFailed += 0; numError  += 5;
        numBuild   = 10;
        numClean  += 7;
        numMain   += 1;

        // clean
        numPassed += 4; numFailed += 0; numError  += 6;
        numClean  += 10;
        numShell  += 1;

        // compile
        numPassed += 5; numFailed += 4; numError  += 12;
        numClean   +=  2;
        numCompile += 21;
        numMain    +=  1;

        // ignore
        numPassed += 0; numFailed += 0; numError  += 2;
        numIgnore += 2;

        // junit
        numPassed += 6; numFailed += 6; numError  += 8 + 8 /*Pass/Fail, badarg*/;
        numJUnit  += 28;

        // TestNG
        numPassed += 3; numFailed += 2; numError += 2;
        numTestNG += 5;
        numMain   += 2;

        // main
        numPassed += 12; numFailed += 19; numError  += 12;
        numMain   += 43;

        // driver
        numPassed += 3; numFailed += 4; numError  += 10;
        numDriver += 17;

        // shell
        numPassed += 7; numFailed += 5; numError  += 3;
        numShell  += 15;

        // misc
        numPassed += 41; numFailed += 0; numError  += 8;
        numMain   += 49;

        // tempFiles
        numPassed += 3;
        numMain += 2;
        numShell += 1;

        actionTable.put("applet",  Integer.valueOf(numApplet));
        actionTable.put("build",   Integer.valueOf(numBuild));
        actionTable.put("clean",   Integer.valueOf(numClean));
        actionTable.put("compile", Integer.valueOf(numCompile));
        actionTable.put("driver",  Integer.valueOf(numDriver));
        actionTable.put("ignore",  Integer.valueOf(numIgnore));
        actionTable.put("main",    Integer.valueOf(numMain));
        actionTable.put("junit",   Integer.valueOf(numJUnit));
        actionTable.put("testng",  Integer.valueOf(numTestNG));
        actionTable.put("shell",   Integer.valueOf(numShell));
        actionTable.put("misc",    Integer.valueOf(numMisc));

        statusTable.put("passed",  Integer.valueOf(numPassed));
        statusTable.put("failed",  Integer.valueOf(numFailed));
        statusTable.put("error",   Integer.valueOf(numError));
    }

    private void checkPreviousStateSelection(String s) {
        int expected = statusTable.get(s);
        int state;
        String prefix;
        if (s.equals("passed")) {
            state = Status.PASSED;
            prefix = "Passed: ";
        } else if (s.equals("failed")) {
            state = Status.FAILED;
            prefix = "Failed: ";
        } else { // if (s.equals("Error:"))
            state = Status.ERROR;
            prefix = "Error: ";
        }

        try {
            RegressionParameters params = setParameters();
            boolean[] b = new boolean[Status.NUM_STATES];
            b[state] = true;
            params.setPriorStatusValues(b);

            int found = 0;

            Iterator it = createTestQueue(params);

            while (it.hasNext()) {
                TestResult tr = (TestResult) (it.next());
                TestDescription td = tr.getDescription();
                if (!td.getTitle().startsWith(prefix)) {
                    System.err.println(REP_STATE + state);
                    System.err.println(REP_FOUND_TEST + tr.getTestName());
                    System.err.println(REP_TITLE + td.getTitle());
                    System.err.println(REP_EXP_TITLE + prefix);
                    failed(ERR_REP_UEXP_TEST);
                }
                found++;
            }

            if (found != expected) {
                System.err.println(REP_STATE + state + " (" + s + ")");
                System.err.println(REP_EXP_NUM_TEST + expected);
                System.err.println(REP_FOUND + found);
                failed(ERR_REP_WRONG_NUM);
            }
        }
        catch (FileNotFoundException e) {
            failed(ERR_HARNESS_RUN_PROB, e);
        }
        catch (TestSuite.Fault e) {
            failed(ERR_HARNESS_RUN_PROB, e);
        }
        catch (TestResult.Fault f) {
            failed(ERR_BAD_TR, f);
        }
        catch (TestResultTable.Fault f) {
            failed(ERR_BAD_FILES, f);
        }

        System.out.println("  " + s + ": " + expected);
    }

    private void checkAction(String action) {
        int expected = actionTable.get(action);
        try {
            Iterator it = createTestQueue(setParameters());

            int found = 0;

            TestDescription td;
            while (it.hasNext()) {
                td = ((TestResult)it.next()).getDescription();
                String actions = td.getParameter("run");
                int pos = 0;
                // a td may have more than one of the target action
                while ((pos = actions.indexOf(action, pos)) != -1) {
                    found++;
                    if (found > expected) {
                        System.err.println(REP_ACTION + action);
                        System.err.println(REP_FOUND_TEST + td.getRootRelativeFile());
                        System.err.println(ERR_REP_UEXP_TEST);
                    }
                    pos += actions.length();
                }
            }

            if (found != expected) {
                System.err.println(REP_ACTION + action);
                System.err.println(REP_EXP_NUM_TEST + expected);
                System.err.println(REP_FOUND + found);
                failed(ERR_REP_WRONG_NUM);
            }
        }
        catch (FileNotFoundException e) {
            failed(ERR_HARNESS_RUN_PROB, e);
        }
        catch (TestSuite.Fault e) {
            failed(ERR_HARNESS_RUN_PROB, e);
        }
        catch (TestResult.Fault f) {
            failed(ERR_BAD_TR, f);
        }
        catch (TestResultTable.Fault f) {
            failed(ERR_BAD_FILES, f);
        }

        System.out.println("  " + action + ": " + expected);
    }

    private void failed(String s, Exception e) {
        e.printStackTrace();
        System.err.println(s + e);
        throw new Error(s);
    }

    private void failed(String s) {
        System.err.println(s);
        throw new Error(s);
    }

    private RegressionParameters setParameters() {
        RegressionParameters rp = null;

        try {
            rp = (RegressionParameters) (testSuite.createInterview());

            rp.setWorkDirectory(workDir);
            rp.setTests((String[])null);
            rp.setExcludeLists(new File[0]);
            rp.setKeywordsExpr("!manual");
            rp.setPriorStatusValues(null);
        }
        catch (TestSuite.Fault e) {
            failed(ERR_BAD_PARAMS, e);
        };

        return rp;
    }

    /**
     * Returns an iterator containing the selected TestResults.
     */
    private Iterator createTestQueue(InterviewParameters p)
        throws FileNotFoundException, TestSuite.Fault, TestResultTable.Fault
    {
        TestResultTable trt = p.getWorkDirectory().getTestResultTable();
        if (trt.getTestFinder() == null)
            trt.setTestFinder(p.getTestSuite().getTestFinder());

        File[] tests = stringsToFiles(p.getTests());
        TestFilter[] filters = p.getFilters();

        trt.waitUntilReady(); // required for samevm mode
        return trt.getIterator(tests, filters);
    }

    private static File[] stringsToFiles(String[] tests) {
        if (tests == null)
            return null;

        File[] files = new File[tests.length];
        for (int i = 0; i < tests.length; i++)
            files[i] = new File(tests[i]);

        return files;
    }

    //--------------------------------------------------------------------------

    public static class Observer implements Harness.Observer
    {
        public void startingTestRun(Parameters params) { }
        public void startingTest(TestResult tr) { }
        public void finishedTest(TestResult tr) {  }
        public void stoppingTestRun() { }
        public void finishedTesting() { }
        public void finishedTestRun(boolean allOK) { }

        public void error(String s) {
            System.err.println("Error: " + s);
        }
    }

    //----------statics---------------------------------------------------------

    private static final String
        INFO_INITIAL_RUN     = "running tests to set status to known state",
        INFO_OK              = "ok",
        ERR_HARNESS_RUN_PROB = "problem running harness: ",
        ERR_HARNESS_INTR     = "harness interrupted: ",
        ERR_BAD_FILES        = "bad initial files: ",
        ERR_BAD_VAL          = "bad value: ",
        ERR_BAD_TR           = "bad test result: ",
        ERR_BAD_ARGS         = "bad arguments: ",
        ERR_BAD_PARAMS       = "bad parameters: ",
        ERR_FAULT            = "fault: ",
        ERR_IO_ERROR         = "IOException: ",
        ERR_REP_UEXP_TEST    = "unexpected test found",
        ERR_REP_WRONG_NUM    = "wrong number of tests found",
        ERR_SUITE            = "problem initializing testsuite: ",
        REP_STATE            = "state: ",
        REP_FOUND_TEST       = "found test: ",
        REP_TITLE            = "title: ",
        REP_EXP_TITLE        = "expected prefix of title: ",
        REP_EXP_NUM_TEST     = "expected number of tests: ",
        REP_FOUND            = "found: ",
        REP_ACTION           = "action: ";

    private static final String[] actionList = {
        "applet", "build", "clean", "compile", "driver", "ignore", "junit", "testng", "main", "shell", "misc"
    };

    private static final String[] statusList = {
        "passed", "failed", "error"
    };

    private static final String FILESEP  = System.getProperty("file.separator");

    //----------member variables------------------------------------------------

    private TestSuite testSuite;
    private WorkDirectory workDir;

    private Hashtable<String,Integer> statusTable = new Hashtable<String,Integer>();
    private Hashtable<String,Integer> actionTable = new Hashtable<String,Integer>();
}
