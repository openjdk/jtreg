/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.sun.javatest.Status;
import com.sun.javatest.TestResult;

import static com.sun.javatest.regtest.RStatus.*;

/**
 * Action is an abstract base class providing the ability to control the
 * behaviour of each step in a JDK test description.  This class requires that
 * all derived classes implement the <em>init</em> method (where arguements are
 * processed and other initializations occur) and the <em>run</em> method (where
 * the actual work for the action occurs.  In addition to these methods, the
 * Action abstract class contains a variety of protected methods for parsing and
 * logging.  All static strings used in Action implementations are also defined
 * here.
 *
 * @author Iris A Garcia
 */
public abstract class Action
{
    /**
     * The null constructor.
     */
    public Action() {
    } // Action()

    /**
     * Get the user-visible name of this action.
     * @return the user-visible name of this action.
     */
    public abstract String getName();

    /**
     * This method does initial processing of the options and arguments for the
     * action.  Processing is determined by the requirements of run() which is
     * determined by the tag specification.
     *
     * @param opts The options for the action.
     * @param args The arguments for the actions.
     * @param reason Indication of why this action was invoked.
     * @param script The script.
     * @exception  ParseException If the options or arguments are not expected
     *             for the action or are improperly formated.
     */
    public void init(String[][] opts, String[] args, String reason,
            RegressionScript script)
            throws ParseException {
        this.opts = opts;
        this.args = args;
        this.reason = reason;
        this.script = script;
    }

    /**
     * The method that does the work of the action.  The necessary work for the
     * given action is defined by the tag specification.
     *
     * @return     The result of the action.
     * @exception  TestRunException If an unexpected error occurs while running
     *             the test.
     */
    public abstract Status run() throws TestRunException;

    /**
     * Get any source files directly referenced by this action.
     * @return the source files used by this action.
     **/
    public Set<File> getSourceFiles() {
        return null;
    }

    static synchronized void mkdirs(File dir) {
        dir.mkdirs();
    }

   //------------------- parsing -----------------------------------------------

    /**
     * This method parses the <em>timeout</em> action option used by several
     * actions.  It verifies that the value of the timeout is a valid number.
     *
     * @param value The proposed value of the timeout.
     * @return     An integer representation of the passed value for the
     *             timeout scaled by the timeout factor.
     * @exception  ParseException If the string does not have a valid
     *             interpretation as a number.
     */
    protected int parseTimeout(String value) throws ParseException {
        if (value == null)
            throw new ParseException(PARSE_TIMEOUT_NONE);
        try {
            return script.getActionTimeout(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            throw new ParseException(PARSE_TIMEOUT_BAD_INT + value);
        }
    } // parseTimeout()

    /**
     * This method parses the <em>fail</em> action option used by several
     * actions.  It verifies that there is no associated value for the option.
     *
     * @param value The proposed value of the fail.
     * @return     True if there is no associated value.
     * @exception  ParseException If there is an associated value.
     */
    protected boolean parseFail(String value) throws ParseException {
        if (value != null)
            throw new ParseException(PARSE_FAIL_UEXPECT + value);
        return true;
    } // parseFail()

    //--------------------------------------------------------------------------

    /**
     * Add a grant entry to the policy file so that JavaTest can read
     * JTwork/classes.  The remaining entries in the policy file should remain
     * the same.
     *
     * @param fileName The absolute name of the original policy file.
     * @return     A string indicating the absolute name of the modified policy
     *             file.
     * @throws TestRunException if a problem occurred adding this grant entry.
     */
    protected String addGrantEntry(String fileName) throws TestRunException {
        File newPolicy = new File(script.absTestScratchDir(),
                                  (new File(fileName).getName()) + "_new");

        FileWriter fw;

        try {
            fw = new FileWriter(newPolicy);
            try {
                fw.write("// The following grant entries were added by JavaTest.  Do not edit." + LINESEP);
                fw.write("grant {" + LINESEP);
                fw.write("    permission java.io.FilePermission \""
                        + script.absTestClsTopDir().getPath().replace('\\' + FILESEP, "{/}")
                        + "${/}-\"" + ", \"read\";" + LINESEP);
                fw.write("};" + LINESEP);
                for (File f: script.getJavaTestClassPath().split()) {
                    fw.write("grant codebase \"" + f.toURI().toURL() + "\" {" + LINESEP);
                    fw.write("    permission java.security.AllPermission;" + LINESEP);
                    fw.write("};" + LINESEP);
                }
                fw.write(LINESEP);

                fw.write("// original policy file:" + LINESEP);
                fw.write("// " + fileName + LINESEP);

                BufferedReader in = new BufferedReader(new FileReader(fileName));
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        fw.write(line + LINESEP);
                    }
                } finally {
                    in.close();
                }
                in.close();
            } finally {
                fw.close();
            }
        } catch (IOException e) {
            throw new TestRunException(POLICY_WRITE_PROB + newPolicy.toString());
        } catch (SecurityException e) {
            throw new TestRunException(POLICY_SM_PROB + newPolicy.toString());
        }

        return newPolicy.toString();
    } // addGrantEntry()

    /**
     * This method parses the <em>policy</em> action option used by several
     * actions.  It verifies that the indicated policy file exists in the
     * directory containing the defining file of the test.
     *
     * @param value The proposed filename for the policy file.
     * @return     A string indicating the absolute name of the policy file for
     *             the test.
     * @exception  ParseException If the passed filename is null, the empty
     *             string, or does not exist.
     */
    protected String parsePolicy(String value) throws ParseException {
        if ((value == null) || value.equals(""))
            throw new ParseException(MAIN_NO_POLICY_NAME);
        File policyFile = new File(script.absTestSrcDir(), value);
        if (!policyFile.exists())
            throw new ParseException(MAIN_CANT_FIND_POLICY + policyFile);
        return policyFile.toString();
    } // parsePolicy()

    /**
     * This method parses the <em>secure</em> action option used to provide the
     * name of a subclass to be installed as the security manager.  No
     * verification of the existence of the .class is done.
     *
     * @param value The proposed class name for the security manager.
     * @return    A string indicating the absolute name of the security manager
     *            class.
     * @exception ParseException If the passed classname is null, the empty
     *            string
     */
    protected String parseSecure(String value) throws ParseException {
        if ((value == null) || value.equals(""))
            throw new ParseException(MAIN_NO_SECURE_NAME);
        return value;
    } // parseSecure()

    //----------redirect streams------------------------------------------------

    // if we wanted to allow more concurrency, we could try and acquire a lock here
    static Status redirectOutput(PrintStream out, PrintStream err) {
        synchronized (System.class) {
            SecurityManager sc = System.getSecurityManager();
            if (sc instanceof RegressionSecurityManager) {
                boolean prev = ((RegressionSecurityManager) sc).setAllowSetIO(true);
                System.setOut(out);
                System.setErr(err);
                ((RegressionSecurityManager) sc).setAllowSetIO(prev);
            } else {
                //return Status.error(MAIN_SECMGR_BAD);
            }
        }
        return passed("OK");
    } // redirectOutput()

    //----------logging methods-------------------------------------------------

    /**
     * Set up a recording area for the action.  The initial contents of the
     * default message area are set and will be of the form:
     * <pre>
     * command: action [command_args]
     * reason: [reason_string]
     * </pre>
     */
    protected void startAction() {
        String name = getName();
        section = script.getTestResult().createSection(name);

        PrintWriter pw = section.getMessageWriter();
        pw.println(LOG_COMMAND + name + " " + StringArray.join(args, " "));
        pw.println(LOG_REASON + reason);

        recorder = new ActionRecorder(this);

        startTime = (new Date()).getTime();
    } // startAction()

    /**
     * Set the status for the passed action. After this call, the recording area
     * for the action become immutable.
     *
     * @param status The final status of the action.
     */
    protected void endAction(Status status) {
        long elapsedTime = (new Date()).getTime() - startTime;
        PrintWriter pw = section.getMessageWriter();
        pw.println(LOG_ELAPSED_TIME + ((double) elapsedTime/1000.0));
        recorder.close();
        section.setStatus(status);
    } // endAction()

    //----------workarounds-------------------------------------------------------

    /**
     * This method pushes the full, constructed command for the action to the
     * log.  The constructed command contains the the action and its arguments
     * modified to run in another process.  The command may also contain
     * additional things necessary to run the action according to spec.  This
     * may include things such as a modified classpath, absolute names of files,
     * and environment variables.
     *
     * Used primarily for debugging purposes.
     *
     * @param action The name of the action currently being processed.
     * @param cmdArgs An array of the command to pass to ProcessCommand.
     * @param section The section of the result file for this action.
     * @see com.sun.javatest.lib.ProcessCommand#run
     */
    protected void showCmd(String action, String[] cmdArgs, TestResult.Section section) {
        showCmd(action, Arrays.asList(cmdArgs), section);
    }

    protected void showCmd(String action, List<String> cmdArgs, TestResult.Section section) {
        PrintWriter pw = section.getMessageWriter();
        pw.println(LOG_JT_COMMAND + action);
        for (String s: cmdArgs)
            pw.print("'" + s + "' ");
        pw.println();
    } // showCmd()

    protected void showMode(String action, ExecMode mode, TestResult.Section section) {
        PrintWriter pw = section.getMessageWriter();
        pw.println("Mode: " + mode);
    }

    /**
     * Given a string, change "\\" into "\\\\" for windows platforms.  This method
     * must be called exactly once before the string is used to start a new
     * process.
     *
     * @param s    The string to translate.
     * @return     For Windows systems, a modified string.  For all other
     *             systems including i386 (win32 sparc and Linux), the same
     *             string.
     */
    String[] quoteBackslash(String[] s) {
        String bs = "\\";
        String[] retVal = new String[s.length];
        if (System.getProperty("file.separator").equals(bs)) {
            for (int i = 0; i < s.length; i++) {
                String victim = s[i];
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < victim.length(); j++) {
                    String c = String.valueOf(victim.charAt(j));
                    sb.append(c);
                    if (c.equals(bs))
                        sb.append(c);
                }
                retVal[i] = sb.toString();
            }
        } else
            retVal = s;

        return retVal;
    } // quoteBackslash()

    /**
     * Single quote the given string.  This method should be used if the string
     * contains characters which should not be interpreted by the shell.
     *
     * @param s    The string to translate.
     * @return     The same string, surrounded by "'".
     */
    String singleQuoteString(String s) {
        StringBuilder b = new StringBuilder();
        b.append("'").append(s).append("'");
        return(b.toString());
    } // singleQuoteString()

    //----------for saving/restoring properties---------------------------------

    protected static Map<?, ?> copyProperties(Properties p) {
        Map<Object, Object> h = new HashMap<Object, Object>();
        for (Enumeration<?> e = p.propertyNames(); e.hasMoreElements(); ) {
            Object key = e.nextElement();
            h.put(key, p.get(key));
        }
        return h;
    }

    protected static Properties newProperties(Map<?, ?> h) {
        Properties p = new Properties();
        p.putAll(h);
        return p;
    }

    //----------output handler--------------------------------------------------

    /**
     * OutputHandler provides an abstract way to get the streams used to record
     * the output from an action of a test.
     */
    interface OutputHandler {
        enum OutputKind {
            LOG(""),
            STDOUT("System.out"),
            STDERR("System.err"),
            DIRECT("direct"),
            DIRECT_LOG("direct.log");
            OutputKind(String name) { this.name = name; }
            final String name;
        };
        PrintWriter createOutput(OutputKind kind);
        void createOutput(OutputKind kind, String output);
    }

    static OutputHandler getOutputHandler(final TestResult.Section section) {
        return new OutputHandler() {
            public PrintWriter createOutput(OutputKind kind) {
                if (kind == OutputKind.LOG)
                    return section.getMessageWriter();
                else
                    return section.createOutput(kind.name);
            }

            public void createOutput(OutputKind kind, String output) {
                PrintWriter pw = createOutput(kind);
                pw.write(output);
                pw.close();
            }
        };
    }


    //----------save state------------------------------------------------------

    /**
     * SaveState captures  important system state, such as the security manager,
     * standard IO streams and system properties, and provides a way to
     * subsequently restore that state.
     */
    static class SaveState {
        SaveState() {
            if (sysProps == null)
                sysProps = copyProperties(System.getProperties());

            // Save and setup streams for the test
            stdOut = System.out;
            stdErr = System.err;

            // Default Locale
            locale = Locale.getDefault();

            // Save security manager in case changed by test
            secMgr = System.getSecurityManager();

            // If using default security manager, allow props access, and reset dirty bit
            if (secMgr instanceof RegressionSecurityManager) {
                RegressionSecurityManager rsm = (RegressionSecurityManager) secMgr;
                rsm.setAllowPropertiesAccess(true);
                rsm.resetPropertiesModified();
            }

            securityProviders = Security.getProviders();
        }

        Status restore(String testName, Status status) {
            Status cleanupStatus = null;

            // Reset security manager, if necessary
            // Do this first, to ensure we reset permissions
            try {
                if (System.getSecurityManager() != secMgr) {
                    AccessController.doPrivileged(new PrivilegedAction<Object>() {
                        public Object run() {
                            System.setSecurityManager(secMgr);
                            return null;
                        }
                    });
                    //System.setSecurityManager(secMgr);
                }
            } catch (SecurityException e) {
                // If we cannot reset the security manager, we might not be able to do
                // much at all -- such as write files.  So, be very noisy to the
                // primary system error stream about this badly behaved test.
                stdErr.println();
                stdErr.println("***");
                stdErr.println("*** " + testName);
                stdErr.println("*** Cannot reset security manager after test");
                stdErr.println("*** " + e.getMessage());
                stdErr.println("***");
                stdErr.println();
                cleanupStatus = error(SAMEVM_CANT_RESET_SECMGR + ": " + e);
            }

            try {
                final Provider[] sp = Security.getProviders();
                if (!equal(securityProviders, sp)) {
                    AccessController.doPrivileged(new PrivilegedAction<Object>() {
                        public Object run() {
                            for (Provider p : sp) {
                                Security.removeProvider(p.getName());
                            }
                            for (Provider p : securityProviders) {
                                Security.addProvider(p);
                            }
                            return null;
                        }
                    });
                }
            } catch (SecurityException e) {
                cleanupStatus = error(SAMEVM_CANT_RESET_SECPROVS + ": " + e);
            }


            // Reset system properties, if necessary
            // The default security manager tracks whether system properties may have
            // been written: if so, we reset all the system properties, otherwise
            // we just reset important props that were written in the test setup
            boolean resetAllSysProps;
            SecurityManager sm = System.getSecurityManager();
            if (sm instanceof RegressionSecurityManager) {
                resetAllSysProps = ((RegressionSecurityManager) sm).isPropertiesModified();
            } else {
                resetAllSysProps = true;
            }
            try {
                if (resetAllSysProps) {
                    System.setProperties(newProperties(sysProps));
//                    System.err.println("reset properties");
                } else {
                    System.setProperty("java.class.path", (String) sysProps.get("java.class.path"));
//                    System.err.println("no need to reset properties");
                }
            } catch (SecurityException e) {
                if (cleanupStatus == null)
                    cleanupStatus = error(SAMEVM_CANT_RESET_PROPS + ": " + e);
            }

            // Reset output streams
            Status stat = redirectOutput(stdOut, stdErr);
            if (cleanupStatus == null && !stat.isPassed())
                cleanupStatus = stat;

            // Reset locale
            if (locale != Locale.getDefault()) {
                Locale.setDefault(locale);
            }

            return (cleanupStatus != null ? cleanupStatus : status);
        }

        final SecurityManager secMgr;
        final PrintStream stdOut;
        final PrintStream stdErr;
        final Locale locale;
        final Provider[] securityProviders;
        static Map<?, ?> sysProps;
    }

    private static <T> boolean equal(T[] a, T[] b) {
        if (a == null || b == null)
            return a == b;
        if (a.length != b.length)
            return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i])
                return false;
        }
        return true;
    }

    //----------in memory streams-----------------------------------------------

    static class PrintByteArrayOutputStream extends PrintStream {
        PrintByteArrayOutputStream() {
            super(new ByteArrayOutputStream());
            s = (ByteArrayOutputStream) out;
        }

        String getOutput() {
            return s.toString();
        }

        private final ByteArrayOutputStream s;
    }

    static class PrintStringWriter extends PrintWriter {
        PrintStringWriter() {
            super(new StringWriter());
            w = (StringWriter) out;
        }

        String getOutput() {
            return w.toString();
        }

        private final StringWriter w;
    }

    //----------misc statics----------------------------------------------------

    protected static final String FILESEP  = System.getProperty("file.separator");
    protected static final String LINESEP  = System.getProperty("line.separator");

    // This is a hack to deal with the fact that the implementation of
    // Runtime.exec() for Windows stringifies the arguments.
    protected static final String EXECQUOTE = (System.getProperty("os.name").startsWith("Windows") ? "\"" : "");

    protected static final String
        REASON_ASSUMED_ACTION = "ASSUMED_ACTION",
        REASON_USER_SPECIFIED = "USER_SPECIFIED",
        REASON_ASSUMED_BUILD  = "ASSUMED_BUILD",
        REASON_FILE_TOO_OLD   = "FILE_OUT_OF_DATE";

    protected static final String
        SREASON_ASSUMED_ACTION= "Assumed action based on file name: run ",
        SREASON_USER_SPECIFIED= "User specified action: run ",
        SREASON_ASSUMED_BUILD = "Named class compiled on demand",
        SREASON_FILE_TOO_OLD  = ".class file out of date or does not exist";

    // These are all of the error messages used in all actions.
    protected static final String
        PARSE_TIMEOUT_NONE    = "No timeout value",
        PARSE_TIMEOUT_BAD_INT = "Bad integer specification: ",
        PARSE_FAIL_UEXPECT    = "Unexpected value for `fail': ",

        // policy and security manager
        PARSE_BAD_OPT_JDK     = "Option not allowed using provided test JDK: ",
        PARSE_NO_POLICY_NAME  = "No policy file name",
        PARSE_CANT_FIND_POLICY= "Can't find policy file: ",
        PARSE_NO_SECURE_NAME  = "No security manager file name",
        PARSE_POLICY_OTHERVM  = "`/policy' and `/java.security.policy` require use of `/othervm'",
        PARSE_SECURE_OTHERVM  = "`/secure' requires use of `/othervm'",
        PARSE_TIMEOUT_MANUAL  = "`/manual' disables use of `/timeout'",

        POLICY_WRITE_PROB     = "Problems writing new policy file: ",
        POLICY_SM_PROB        = "Unable to create new policy file: ",

        LOG_COMMAND           = "command: ",
        LOG_RESULT            = " result: ",
        LOG_JT_COMMAND        = "JavaTest command: ",
        LOG_REASON            = "reason: ",
        LOG_ELAPSED_TIME      = "elapsed time (seconds): ",
        //LOG_JDK               = "JDK under test: ",

        // COMMON
        // used in:  shell, main, applet
        EXEC_FAIL             = "Execution failed",
        EXEC_FAIL_EXPECT      = "Execution failed as expected",
        EXEC_PASS             = "Execution successful",
        EXEC_PASS_UNEXPECT    = "Execution passed unexpectedly",
        EXEC_ERROR_CLEANUP    = "Error while cleaning up threads after test",
        CHECK_PASS            = "Test description appears acceptable",

        // used in:  compile, main
        SAMEVM_CANT_RESET_SECMGR   = "Cannot reset security manager",
        SAMEVM_CANT_RESET_SECPROVS = "Cannot reset security providers",
        SAMEVM_CANT_RESET_PROPS    = "Cannot reset system properties",

        // used in:  compile, main
        AGENTVM_CANT_GET_VM      = "Cannot get VM for test",
        AGENTVM_IO_EXCEPTION     = "Agent communication error: %s; check console log for any additional details",
        AGENTVM_EXCEPTION        = "Agent error: %s; check console log for any additional details",

        UNEXPECT_SYS_EXIT     = "Unexpected exit from test",
        CANT_FIND_SRC         = "Can't find source file: ",

        // applet
        APPLET_ONE_ARG_REQ    = "`applet' requires exactly one file argument",
        APPLET_BAD_VAL_MANUAL = "Bad value for `manual' option: ",
        APPLET_BAD_OPT        = "Bad option for applet: ",
        APPLET_CANT_FIND_HTML = "Can't find HTML file: ",
        APPLET_HTML_READ_PROB = "Problem reading HTML file: ",
        APPLET_MISS_ENDBODY   = "No </body> tag in ",
        APPLET_MISS_APPLET    = "No <applet> tag in ",
        APPLET_MISS_ENDAPPLET = "No </applet> tag in ",
        APPLET_MISS_REQ_ATTRIB= " missing required attribute ",
        APPLET_ARCHIVE_USUPP  = "`archive' not supported in file: ",
        APPLET_MISS_REQ_PARAM = "Missing required name or value for param in <param> tag",
        APPLET_CANT_WRITE_ARGS= "Can't write `applet' argument file",
        APPLET_SECMGR_FILEOPS = "Unable to create applet argument file",

        APPLET_USER_EVAL      = ", user evaluated",
        APPLET_MANUAL_TEST    = "Manual test",

        // build
        BUILD_UNEXPECT_OPT    = "Unexpected options for `build'",
        BUILD_NO_CLASSNAME    = "No classname(s) provided for `build'",
        BUILD_BAD_CLASSNAME   = "Bad classname provided for `build': ",
        BUILD_NO_COMP_NEED    = "No need to compile: ",
        BUILD_UP_TO_DATE      = "All files up to date",
        BUILD_SUCC            = "Build successful",
        BUILD_LIB_LIST        = " in directory-list: ",
        BUILD_FUTURE_SOURCE   = "WARNING: file %s has a modification time in the future: %s",
        BUILD_FUTURE_SOURCE_2 = "Unexpected results may occur",

        // clean
        CLEAN_SUCC            = "Clean successful",
        CLEAN_UNEXPECT_OPT    = "Unexpected option(s) for `clean'",
        CLEAN_NO_CLASSNAME    = "No classname(s) provided for `clean'",
        CLEAN_BAD_CLASSNAME   = "Bad classname provided for `clean': ",
        CLEAN_RM_FAILED       = "`clean' unable to delete file: ",
        CLEAN_SECMGR_PROB     = "Problem deleting directory contents: ",

        // compile
        COMPILE_NO_CLASSNAME  = "No classname provided for `compile'",
        COMPILE_NO_DOT_JAVA   = "No classname ending with `.java' found",
        COMPILE_BAD_OPT       = "Bad option for compile: ",
        COMPILE_OPT_DISALLOW  = "Compile option not allowed: ",
        COMPILE_NO_REF_NAME   = "No reference file name",
        COMPILE_CANT_FIND_REF = "Can't find reference file: ",
        COMPILE_GOLD_FAIL     = "Output does not match reference file: ",
        COMPILE_GOLD_LINE     = ", line ",
        COMPILE_GOLD_READ_PROB= "Problem reading reference file: ",

        COMPILE_CANT_CREATE_ARG_FILE = "Can't create `compile' argument file",
        COMPILE_CANT_WRITE_ARGS  = "Can't write `compile' argument file",
        COMPILE_SECMGR_FILEOPS   = "Unable to create `compile' argument file",

        COMPILE_PASS_UNEXPECT = "Compilation passed unexpectedly",
        COMPILE_PASS          = "Compilation successful",
        COMPILE_FAIL_EXPECT   = "Compilation failed as expected",
        COMPILE_FAIL          = "Compilation failed",
        COMPILE_CANT_RESET_SECMGR= "Cannot reset security manager",
        COMPILE_CANT_RESET_PROPS = "Cannot reset system properties",

        // ignore
        IGNORE_UNEXPECT_OPTS  = "Unexpected option(s) for `ignore'",
        IGNORE_TEST_IGNORED   = "Test ignored",
        IGNORE_TEST_IGNORED_C = "Test ignored: ",
        IGNORE_TEST_SUPPRESSED   = "@ignore suppressed by command line option",
        IGNORE_TEST_SUPPRESSED_C = "@ignore suppressed by command line option: ",

        // junit
        JUNIT_NO_DRIVER        = "No JUnit 4 driver (install junit.jar next to jtreg.jar)",
        JUNIT_NO_CLASSNAME     = "No class provided for `junit'",
        JUNIT_BAD_MAIN_ARG     = "Bad argument provided for class in `junit'",

        // driver
        DRIVER_NO_CLASSNAME    = "No class provided for `driver'",
        DRIVER_UNEXPECT_VMOPT  = "VM options not allowed",
        DRIVER_BAD_OPT         = "Bad option for driver: ",

        // main
        MAIN_NO_CLASSNAME     = "No class provided for `main'",
        MAIN_MANUAL_NO_VAL    = "Arguments to `manual' option not supported: ",
        MAIN_BAD_OPT          = "Bad option for main: ",
        MAIN_CANT_FIND_SECURE = "Can't find security manager file name: ",
        MAIN_BAD_OPT_JDK      = "Option not allowed using provided test JDK: ",
        MAIN_NO_POLICY_NAME   = "No policy file name",
        MAIN_CANT_FIND_POLICY = "Can't find policy file: ",
        MAIN_POLICY_OTHERVM   = "`/policy' requires use of `/othervm'",
        MAIN_NO_SECURE_NAME   = "No security manager file name",
        MAIN_SECURE_OTHERVM   = "`/secure' requires use of `/othervm'",
        MAIN_UNEXPECT_VMOPT   = ": vm option(s) found, need to specify /othervm",
        MAIN_POLICY_WRITE_PROB= "Problems writing new policy file: ",
        MAIN_POLICY_SM_PROB   = "Unable to create new policy file: ",
        MAIN_CANT_RESET_SECMGR= "Cannot reset security manager",
        MAIN_CANT_RESET_PROPS = "Cannot reset system properties",
        MAIN_NO_NATIVES       = "Use -nativepath to specify the location of native code",

        //    runOtherJVM
        MAIN_CANT_WRITE_ARGS  = "Can't write `main' argument file",
        MAIN_SECMGR_FILEOPS   = "Unable to create `main' argument file",

        //    runSameJVM
        MAIN_SECMGR_BAD       = "JavaTest not running its own security manager",
        MAIN_THREAD_INTR      = "Thread interrupted: ",
        MAIN_THREAD_TIMEOUT   = "Timeout",
        MAIN_THREW_EXCEPT     = "`main' threw exception: ",
        MAIN_CANT_LOAD_TEST   = "Can't load test: ",
        MAIN_CANT_FIND_MAIN   = "Can't find `main' method",

        // shell
        SHELL_NO_SCRIPT_NAME  = "No script name provided for `shell'",
        SHELL_MANUAL_NO_VAL   = "Arguments to `manual' option not supported: ",
        SHELL_BAD_OPTION      = "Bad option for shell: ";

    //----------member variables------------------------------------------------

    protected /*final*/ String[][] opts;
    protected /*final*/ String[] args;
    protected /*final*/ String reason;
    protected /*final*/ RegressionScript script;

    protected /*final*/ TestResult.Section section;
    protected /*final*/ ActionRecorder recorder;
    private long startTime;

    protected static final boolean showCmd = show("showCmd");
    protected static final boolean showMode = show("showMode");
    protected static final boolean showJDK = show("showJDK");
    static boolean show(String name) {
        return Boolean.getBoolean("javatest.regtest." + name)
                || (System.getenv("JTREG_" + name.toUpperCase()) != null);
    }
}


