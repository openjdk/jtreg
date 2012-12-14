/*
 * Copyright 1998-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.sun.javatest.Status;
import com.sun.javatest.TestResult;
import com.sun.javatest.lib.ProcessCommand;

/**
 * This class implements the "shell" action as described by the JDK tag
 * specification.
 *
 * @author Iris A Garcia
 * @see Action
 */
public class ShellAction extends Action
{
    /**
     * This method does initial processing of the options and arguments for the
     * action.  Processing is determined by the requirements of run().
     *
     * Verify arguments are of length 0.
     *
     * Verify that the options are valid for the "shell" action and separate the
     * shell filename from the shell arguments.
     *
     * @param opts The options for the action.
     * @param args The arguments for the actions.
     * @param reason Indication of why this action was invoked.
     * @param script The script.
     * @exception  ParseException If the options or arguments are not expected
     *             for the action or are improperly formated.
     */
    public void init(String [][] opts, String [] args, String reason,
                     RegressionScript script)
        throws ParseException
    {
        this.script = script;
        this.reason = reason;

        if (args.length == 0)
            throw new ParseException(SHELL_NO_SCRIPT_NAME);

        for (int i = 0; i < opts.length; i++) {
            String optName  = opts[i][0];
            String optValue = opts[i][1];

            if (optName.equals("fail")) {
                reverseStatus = parseFail(optValue);
            } else if (optName.equals("timeout")) {
                timeout  = parseTimeout(optValue);
            } else if (optName.equals("manual")) {
                manual = parseShellManual(optValue);
            } else {
                throw new ParseException(SHELL_BAD_OPTION + optName);
            }
        }

        if (manual.equals("unset")) {
            if (timeout < 0)
                timeout = script.getActionTimeout(0);
        } else {
            if (timeout >= 0)
                // can't have both timeout and manual
                throw new ParseException(PARSE_TIMEOUT_MANUAL);
            timeout = 0;
        }

        // the first argument is the name of the shell script, the rest are
        // arguments to the shell script
        shellFN = args[0];
        //shellArgs = "";
//        shellArgs = new ArrayList();
//      for (int i = 1; i < args.length; i++) {
//          //shellArgs += " " + args[i];
//          shellArgs.add(args[i]);
//      }
        // support simple unescaped ' characters
        shellArgs = new ArrayList<String>();
        StringBuilder curr = null;
        for (int i = 1; i < args.length; i++) {
            if (curr == null)
                curr = new StringBuilder(args[i]);
            else
                curr.append(" ").append(args[i]);
            if (isEvenQuotes(curr)) {
                shellArgs.add(curr.toString().replace("'", ""));
                curr = null;
            }
        }
        if (curr != null)
            shellArgs.add(curr.toString());
    } // init()
    // where
        private static boolean isEvenQuotes(StringBuilder s) {
            int n = 0;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == '\'')
                    n++;
            }
            return (n % 2 == 0);
        }

    @Override
    public File[] getSourceFiles() {
        return new File[] { new File(script.absTestSrcDir(), shellFN) };
    }

    /**
     * The method that does the work of the action.  The necessary work for the
     * given action is defined by the tag specification.
     *
     * Invoke the Bourne shell to run the shell filename with the provided
     * arguments.  The shell filename is fully qualified as necessary and all
     * environment variables are set according to the tag specification.
     *
     * A "shell" action passes if the script exits with an exit code of 0.  It
     * fails otherwise.
     *
     * Note that this action inherently assumes that the JVM supports multiple
     * processes.
     *
     * @return     The result of the action.
     * @exception  TestRunException If an unexpected error occurs while running
     *             the test.
     */
    public Status run() throws TestRunException {
        Status status;

        section = startAction("shell", shellFN + " " + shellArgs, reason);

        File shellFile = new File(script.absTestSrcDir(), shellFN);
        if (!shellFile.exists())
            throw new TestRunException(CANT_FIND_SRC + shellFile);

        // If we're only running checks on the contents of the test description
        // and we got this far, we can just set a successful status. Everything
        // after this point is preparation to run the actual test.
        if (script.isCheck()) {
            status = Status.passed(CHECK_PASS);
        } else {

            // CONSTRUCT THE COMMAND LINE

            // TAG-SPEC:  "The source, class, and Java home directories are made
            // available to shell-action scripts via the environment variables
            // TESTSRC, TESTCLASSES, and TESTJAVA."
            String [] envVars = script.getEnvVars();
            /*
            String [] tmpArgs = {"sh", "-c", EXECQUOTE +
                                 StringArray.join(envVars) +
                                 " TESTSRC=" + script.absTestSrcDir() +
                                 " TESTCLASSES=" + script.absTestClsDir() +
                                 " TESTJAVA=" + script.javaHome() +
                                 " sh " +
                                 shellFile.toString() +
                                 shellArgs + EXECQUOTE};
            */
            List<String> tmpArgs = new ArrayList<String>();
            for (int i = 0; i < envVars.length; i++)
                tmpArgs.add(fixupSep(envVars[i]));
            tmpArgs.add("TESTSRC=" + fixupSep(script.absTestSrcDir()));
            tmpArgs.add("TESTCLASSES=" + fixupSep(script.absTestClsDir()));
            tmpArgs.add("TESTJAVA=" + fixupSep(script.getJDK()));
            List<String> vmOpts = script.getTestVMOptions();
            tmpArgs.add("TESTVMOPTS=" + fixupSep(StringUtils.join(vmOpts, " ")));
            List<String> toolVMOpts = script.getTestToolVMOptions();
            tmpArgs.add("TESTTOOLVMOPTS=" + fixupSep(StringUtils.join(toolVMOpts, " ")));
            List<String> compilerOpts = script.getTestCompilerOptions();
            tmpArgs.add("TESTJAVACOPTS=" + fixupSep(StringUtils.join(compilerOpts, " ")));
            List<String> javaOpts = script.getTestJavaOptions();
            tmpArgs.add("TESTJAVAOPTS=" + fixupSep(StringUtils.join(javaOpts, " ")));
            tmpArgs.add("sh");
            tmpArgs.add(shellFile.getPath());
            tmpArgs.addAll(shellArgs);
            String [] cmdArgs = tmpArgs.toArray(new String[tmpArgs.size()]);

            // PASS TO PROCESSCOMMAND
            PrintWriter sysOut = section.createOutput("System.out");
            PrintWriter sysErr = section.createOutput("System.err");
            try {
                if (showCmd)
                    JTCmd("shell", cmdArgs, section);

                // RUN THE SHELL SCRIPT
                ProcessCommand cmd = new ProcessCommand();
                cmd.setExecDir(script.absTestScratchDir());

                if (timeout > 0)
                    script.setAlarm(timeout*1000);

                status = cmd.run(cmdArgs, sysErr, sysOut);
            } finally {
                script.setAlarm(0);
                if (sysOut != null) sysOut.close();
                if (sysErr != null) sysErr.close();
            }

            // EVALUATE RESULTS
            boolean ok = status.isPassed();
            int st   = status.getType();
            String sr;
            if (ok && reverseStatus) {
                sr = EXEC_PASS_UNEXPECT;
                st = Status.FAILED;
            } else if (ok && !reverseStatus) {
                sr = EXEC_PASS;
            } else if (!ok && reverseStatus) {
                sr = EXEC_FAIL_EXPECT;
                st = Status.PASSED;
            } else { /* !ok && !reverseStatus */
                sr = EXEC_FAIL;
            }
            if (st == Status.FAILED)
                sr += ": " + status.getReason();
            status = new Status(st, sr);
        }

        endAction(status, section);
        return status;
    } // run()
        // where
        private String fixupSep(File f) {
            return fixupSep(f.getPath());
        }
        private String fixupSep(String s) {
            return (sep == null ? s : s.replace(File.separator, sep));
        }
        private static final String sep = getSeparator();
        private static String getSeparator() {
            return (File.separatorChar == '\\'
                ? System.getProperty("javatest.shell.separator", "/")
                : null);
        }

    private String parseShellManual(String value) throws ParseException {
        if (value != null)
            throw new ParseException(SHELL_MANUAL_NO_VAL + value);
        else
            value = "novalue";
        return value;
    } // parseShellManual()

    //----------member variables------------------------------------------------

    private String shellFN;
    private List<String> shellArgs;

    private boolean reverseStatus = false;
    private int     timeout = -1;
    private String  manual  = "unset";

    private TestResult.Section section;
}
