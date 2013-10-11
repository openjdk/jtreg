/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.TestNG;
import org.testng.reporters.XMLReporter;
import static org.testng.ITestResult.*;

import com.sun.javatest.Status;
import org.testng.IConfigurationListener;
import org.testng.ITestNGListener;

/**
 * This class implements the implicit "testng" action for TestNG tests.
 *
 * @see Action
 */
public class TestNGAction extends MainAction {
    public static final String NAME = "testng";

    /**
     * {@inheritdoc}
     * @return "testng"
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * This method does initial processing of the options and arguments for the
     * action.  Processing is determined by the requirements of run().
     *
     * Verify arguments are not of length 0 and separate them into the options
     * to java, the classname, and the parameters to the named class.
     *
     * Verify that the options are valid for the "testng" action.
     *
     * @param opts The options for the action.
     * @param args The arguments for the actions.
     * @param reason Indication of why this action was invoked.
     * @param script The script.
     * @exception  ParseException If the options or arguments are not expected
     *             for the action or are improperly formated.
     */
    @Override
    public void init(String[][] opts, String[] args, String reason,
                     RegressionScript script)
        throws ParseException
    {
//        if (args.length == 0)
//            throw new ParseException(TESTNG_NO_CLASSNAME);
        userSpecified = reason.startsWith(SREASON_USER_SPECIFIED);
        init(opts, args, reason, script, TestNGRunner.class.getName());

//        if (getMainArgs().size() != 0)
//            throw new ParseException(TESTNG_BAD_MAIN_ARG);

    } // init()

    boolean userSpecified = false;

    // cache results?
    @Override
    protected Status build() throws TestRunException {
        if (userSpecified) {
            return super.build();
        } else {
            List<String> classes = listClasses(script.getCompileSourcePath().split());
            JDK.Version v = script.getCompileJDKVersion();
            String[][] buildOpts = v.compareTo(JDK.Version.V1_6) >= 0
                    ? new String[][] {{ "implicit", "none" }}
                    : new String[][] { };
            String[]   buildArgs = classes.toArray(new String[classes.size()]);
            BuildAction ba = new BuildAction();
            return ba.build(buildOpts, buildArgs, SREASON_ASSUMED_BUILD, script);
        }
    }

    List<String> listClasses(List<File> roots) {
        List<String> classes = new ArrayList<String>();
        for (File root: roots)
            listClasses(root, null, classes);
        return classes;
    }

    private void listClasses(File dir, String pkg, List<String> classes) {
        for (File f: dir.listFiles()) {
            String f_name = f.getName();
            if (f.isDirectory())
                listClasses(f, pkg == null ? f_name : pkg + "." + f_name, classes);
            else if (f_name.endsWith(".java")) {
                String c_name = f_name.substring(0, f_name.length() - 5);
                classes.add(pkg == null ? c_name : pkg + "." + c_name);
            }
        }
    }

    private static final File TESTNG_RESULTS_XML = new File("testng-results.xml");

    @Override
    public void endAction(Status s) {
        super.endAction(s);
        if (script.isCheck())
            return;
        script.getTestNGReporter().add(script.getTestResult(), section);
        String jtrPath = script.getTestResult().getWorkRelativePath();
        String tngPath = jtrPath.replaceAll("\\.jtr$", ".testng-results.xml");
        script.saveScratchFile(TESTNG_RESULTS_XML, new File(tngPath));
    }

    public static class TestNGRunner implements TestRunner {
        public static void main(String... args) throws Exception {
            main(null, args);
        }

        public static void main(ClassLoader loader, String... args) throws Exception {
            if (args.length != 2)
                throw new Error("wrong number of arguments");
            String testName = args[0];
//            Class<?> mainClass = Class.forName(args[1], true, loader);
            Class<?> mainClass = (loader == null) ? Class.forName(args[1]) : loader.loadClass(args[1]);
            RegressionListener listener = new RegressionListener();
            TestNG testng = new TestNG(false);
            testng.setDefaultSuiteName(testName);
            testng.setTestClasses(new Class<?>[] { mainClass });
            testng.addListener((ITestNGListener) listener); // recognizes both ITestListener and IConfigurationListener
            testng.addListener(new XMLReporter());
            testng.setOutputDirectory(new File(".").getPath()); // current dir, i.e. scratch dir
            testng.run();

            if (listener.configFailureCount > 0 || listener.failureCount > 0) {
                throw new Exception("failures: " + listener.failureCount);
            }
        }
    }

    public static class RegressionListener
            implements ITestListener, IConfigurationListener {
        enum InfoKind { CONFIG, TEST };

        public void onTestStart(ITestResult itr) {
            count++;
//            report(itr);
        }

        public void onTestSuccess(ITestResult itr) {
            successCount++;
            report(InfoKind.TEST, itr);
        }

        public void onTestFailure(ITestResult itr) {
            failureCount++;
            report(InfoKind.TEST, itr);
        }

        public void onTestSkipped(ITestResult itr) {
            if (itr.getThrowable() != null) {
                // Report a skipped test, due to an exception, as a failure
                onTestFailure(itr);
                return;
            }
            skippedCount++;
            report(InfoKind.TEST, itr);
        }

        public void onTestFailedButWithinSuccessPercentage(ITestResult itr) {
            failedButWithinSuccessPercentageCount++;
            report(InfoKind.TEST, itr);
        }

        public void onStart(ITestContext itc) {
        }

        public void onFinish(ITestContext itc) {
        }

        public void onConfigurationSuccess(ITestResult itr) {
            configSuccessCount++;
            report(InfoKind.CONFIG, itr);
        }

        public void onConfigurationFailure(ITestResult itr) {
            configFailureCount++;
            report(InfoKind.CONFIG, itr);
        }

        public void onConfigurationSkip(ITestResult itr) {
            configSkippedCount++;
            report(InfoKind.CONFIG, itr);
        }

        void report(InfoKind k, ITestResult itr) {
            Throwable t = itr.getThrowable();
            String suffix;
            if (t != null  && itr.getStatus() != SUCCESS) {
                // combine in stack trace so we can issue single println
                // threading may otherwise result in interleaved output
                StringWriter trace = new StringWriter();
                PrintWriter pw = new PrintWriter(trace);

                t.printStackTrace(pw);
                pw.close();

                suffix = "\n" + trace;
            } else {
                suffix = "\n";
            }

            System.out.print(k.toString().toLowerCase()
                    + " " + itr.getMethod().getMethod().getDeclaringClass().getName()
                    + "." + itr.getMethod().getMethodName()
                    + formatParams(itr)
                        + ": " + statusToString(itr.getStatus())
                        + suffix);
        }

        private String formatParams(ITestResult itr) {
            StringBuilder sb = new StringBuilder(80);
            sb.append('(');
            String sep = "";
            for(Object arg : itr.getParameters()) {
                sb.append(sep);
                formatParam(sb, arg);
                sep = ", ";
            }
            sb.append(")");
            return sb.toString();
        }

        private void formatParam(StringBuilder sb, Object param) {
            if (param instanceof String) {
                sb.append('"');
                sb.append((String) param);
                sb.append('"');
            } else {
                String value = String.valueOf(param);
                if (value.length() > 30) {
                   sb.append(param.getClass().getName());
                   sb.append('@');
                   sb.append(Integer.toHexString(System.identityHashCode(param)));
                } else {
                   sb.append(value);
                }
            }
        }

        private String statusToString(int s) {
            switch (s) {
                case SUCCESS:
                    return "success";
                case FAILURE:
                    return "failure";
                case SKIP:
                    return "skip";
                case SUCCESS_PERCENTAGE_FAILURE:
                    return "success_percentage_failure";
                default:
                    return "??";
            }
        }

        int count;
        int successCount;
        int failureCount;
        int skippedCount;
        int configSuccessCount;
        int configFailureCount;
        int configSkippedCount;
        int failedButWithinSuccessPercentageCount;
    }
}
