/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.javatest.Script;
import com.sun.javatest.Status;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestEnvironment;
import com.sun.javatest.TestResult;
import com.sun.javatest.util.I18NResourceBundle;

/**
  * This class interprets the TestDescription as specified by the JDK tag
  * specification.
  *
  * @author Iris A Garcia
  * @see com.sun.javatest.Script
  */
public class RegressionScript extends Script
{
    /**
     * The method that interprets the tags provided in the test description and
     * performs actions accordingly.
     *
     * @param argv Any arguements that the RegressionScript may use.  Currently
     *             there are none (value ignored).
     * @param td   The current TestDescription.
     * @param env  The test environment giving the details of how to run the
     *             test.
     * @return     The result of running the script on the given test
     *             description.
     */
    public Status run(String[] argv, TestDescription td, TestEnvironment env) {
        if (!(env instanceof RegressionEnvironment))
            throw new AssertionError();

        long started = System.currentTimeMillis();

        regEnv = (RegressionEnvironment) env;
        params = regEnv.params;

        Status status = Status.passed("OK");
        String actions = td.getParameter("run");

//      System.out.println("--- ACTIONS: " + actions);
        // actions != null -- should never happen since we have reasonable
        // defaults

        testResult = getTestResult();
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            hostname = "unknown";
        }
        testResult.putProperty("hostname", hostname);

        PrintWriter msgPW = testResult.getTestCommentWriter();

        try {
            setLibList(td.getParameter("library"));

            LinkedList<Action> actionList = parseActions(actions, true);

            boolean needJUnit = false;
            for (Action a: actionList) {
                if (a instanceof JUnitAction)
                    needJUnit = true;
            }
            if (needJUnit) {
                if (params.isJUnitAvailable())
                    addClsLib(params.getJUnitJar());
                else
                    throw new TestRunException("JUnit not available: see the FAQ or online help for details");
            }

            try {
                initScratchDirectory();
            } catch (TestRunException e) {
                if (getExecMode() != ExecMode.AGENTVM)
                    throw e;
                // The following something of a last ditch measure.
                // The most likely reason we can't init the scratch
                // directory is that we're on Windows and the previous
                // test has left a file open and we're not using -retain.
                // (If we were using -retain, we'd have caught the problem
                // while cleaning up after the last test.)
                // So, since we no longer know which agents the previous test
                // might have been using, close all agents, and try again.
                // If that fixes the problem, write a warning message to
                // stderr and let the test continue.
                Agent.Pool.instance().close();
                try {
                    initScratchDirectory();
                    // need to localize this
                    System.err.println(i18n.getString("script.warn.openfiles", testResult.getTestName()));
                } catch (TestRunException e2) {
                    e2.initCause(e);
                    throw e2;
                }
            }

            // if we got an error while parsing the TestDescription, return
            // error immediately
            if (td.getParameter("error") != null)
                status = Status.error(td.getParameter("error"));
            else {
                if (getTestJDK().equals(getCompileJDK())) {
                    // output for default case unchanged
                    msgPW.println("JDK under test: " + getTestJDK().getFullVersion(getTestVMOptions()));
                } else {
                    msgPW.println("compile JDK: " + getCompileJDK().getFullVersion(getTestToolVMOptions()));
                    msgPW.println("test JDK: " + getTestJDK().getFullVersion(getTestVMOptions()));
                }
                while (! actionList.isEmpty()) {
                    Action action = actionList.remove();
                    status = action.run();
                    if (status.getType() != Status.PASSED)
                        break;
                }
            }
        } catch (ParseActionsException e) {
            status = Status.error(e.getMessage());
        } catch (TestRunException e) {
            status = Status.error(e.getMessage());
        } finally {
            int elapsed = (int) (System.currentTimeMillis() - started);
            int millis = (elapsed % 1000);
            int secs = (elapsed / 1000) % 60;
            int mins = (elapsed / (1000 * 60)) % 60;
            int hours = elapsed / (1000 * 60 * 60);
            testResult.putProperty("elapsed", String.format("%d %d:%02d:%02d.%03d",
                    elapsed, hours, mins, secs, millis));
            if (params.isRetainEnabled()) {
                boolean ok = retainScratchFiles(status);
                if (!ok) {
                    msgPW.println("Test result (overridden): " + status);
                    status = Status.error("failed to clean up files after test");
                    closeAgents();
                }
            }

            releaseAgents();
        }
        return status;
    } // run()

    /**
     * Get the set of source files used by the actions in a test description.
     **/
    public File[] getSourceFiles(TestDescription td) {
        this.td = td;
        try {
            setLibList(td.getParameter("library"));
            String actions = td.getParameter("run");
            LinkedList<Action> actionList = parseActions(actions, false);
            Set<File> files = new LinkedHashSet<File>();
            while (! actionList.isEmpty()) {
                Action action = actionList.remove();
                File[] a = action.getSourceFiles();
                if (a != null)
                    files.addAll(Arrays.asList(a));
            }
            return files.toArray(new File[files.size()]);
        } catch (TestRunException e) {
            return new File[0];
        } catch (ParseActionsException shouldNotHappen) {
            throw new Error(shouldNotHappen);
        }
    }

    public boolean hasEnv() {
        return (regEnv != null);
    }

    static class ParseActionsException extends Exception {
        static final long serialVersionUID = -3369214582449830917L;
        ParseActionsException(String msg) {
            super(msg);
        }
    }

    /**
     * Parse a sequence of actions.
     *
     * @param actions a series of actions, separated by LINESEP
     * @param stopOnError whether or not to ignore any parse errors; if true and an error
     * is found, a ParseActionsException will be thrown, giving a detail message.
     * @return a Fifo of Action objects
     */
    LinkedList<Action> parseActions(String actions, boolean stopOnError) throws ParseActionsException, ParseException {
        LinkedList<Action> actionList = new LinkedList<Action>();
        String[] runCmds = StringArray.splitTerminator(LINESEP, actions);
        populateActionTable();

        for (int j = 0; j < runCmds.length; j++) {
            // e.g. reason compile/fail/ref=Foo.ref -debug Foo.java
            // where "reason" indicates why the action should run
            String[] tokens = StringArray.splitWS(runCmds[j]);
            // [reason, compile/fail/ref=Foo.ref, -debug, Foo.java]

            String[] verbopts = StringArray.splitSeparator("/", tokens[1]);
            // [compile, fail, ref=Foo.ref]
            String verb = verbopts[0];

            String[][] opts = new String[verbopts.length -1][];
            for (int i = 1; i < verbopts.length; i++) {
                opts[i-1] = StringArray.splitEqual(verbopts[i]);
                // [[fail,], [ref, Foo.ref]]
            }

            String[] args = new String[tokens.length-2];
            for (int i = 2; i < tokens.length; i++)
                args[i-2] = tokens[i];
            // [-debug, Foo.java] (everything after the big options token)

            Class<?> c = null;
            try {
                c = (Class<?>)(actionTable.get(verb));
                if (c == null) {
                    if (stopOnError)
                        throw new ParseActionsException(BAD_ACTION + verb);
                    continue;
                }
                Action action = (Action)(c.newInstance());
                action.init(opts, args, getReason(tokens), this);
                actionList.add(action);
            } catch (InstantiationException e) {
                if (stopOnError)
                    throw new ParseActionsException(CANT_INSTANTIATE + c + NOT_EXT_ACTION);
            } catch (IllegalAccessException e) {
                if (stopOnError)
                    throw new ParseActionsException(ILLEGAL_ACCESS_INIT + c);
            }
        }

        return actionList;

    }

    //---------- methods for timing --------------------------------------------

    /**
     * Get the timeout to be used for a test.  Since the timeout for regression
     * tests is on a per action basis rather than on a per test basis, this
     * method should always return zero which indicates that there is no
     * timeout.
     *
     * @return     0
     */
    @Override
    protected int getTestTimeout() {
        return 0;
    }

    private static float cacheJavaTestTimeoutFactor = -1;
    /**
     * Get the timeout to be used for an action.  The timeout will be scaled by
     * the timeoutFactor as necessary.  The default timeout for any action as
     * per the tag-spec is 120 seconds scaled by a value found in the
     * environment ("javatestTimeoutFactor").
     * The timeout factor is available as both an integer (for backward
     * compatibility) and a floating point number
     *
     * @param time The initial timeout which may need to be scaled according
     *             to the provided timeoutFactor.  If the initial timeout is
     *             zero, then the default timeout will be returned.
     * @return     The timeout in seconds.
     */
    protected int getActionTimeout(int time) {
        if (cacheJavaTestTimeoutFactor == -1) {
            try {
                // use [1] to get the floating point timeout factor
                String f = (regEnv == null ? null : regEnv.lookup("javatestTimeoutFactor")[1]);
                if (f != null)
                    cacheJavaTestTimeoutFactor = Float.parseFloat(f);
                else
                    cacheJavaTestTimeoutFactor = 1;
            } catch (TestEnvironment.Fault e) {
            } catch (NumberFormatException e) {
            }
        }
        if (time == 0)
            time = 120;
        return (int) (time * cacheJavaTestTimeoutFactor);
    }

    /**
     * Set an alarm that will interrupt the calling thread after a specified
     * delay (in milliseconds), and repeatedly thereafter until cancelled.  The
     * testCommentWriter will contain a confirmation string indicating that a
     * timeout has been signelled.
     *
     * @param timeout The delay in milliseconds.
     */
    @Override
    protected void setAlarm(int timeout) {
        super.setAlarm(timeout);
    }

    //----------internal methods------------------------------------------------

    private void initScratchDirectory() throws TestRunException {
        File dir = absTestScratchDir();
        if (dir.exists()) {
            if (dir.isDirectory()) {
                cleanDirectoryContents(dir);
                return;
            } else {
                if (!dir.delete())
                    throw new TestRunException(CLEAN_RM_PROB + dir);
            }
        }
        if (!dir.mkdirs())
            throw new TestRunException(PATH_SCRATCH_CREATE + dir);
    }

    private boolean retainScratchFiles(Status status) {
        // In sameVM mode, or in otherVM mode when no files need to be retained,
        // the scratch directory is shared between all tests. In sameVM mode,
        // this is because there is no way to change the current directory of the
        // running process -- and jtreg requires the current directory to be a
        // scratch directory. In otherVM mode, this is for historical compatibility.
        //
        // In otherVM mode, with retain enabled, we set the scratch directory to
        // be the result directory, to reduce the cost of retaining files.
        // This means that in this case, we delete the files we don't want, as
        // compared to retaining the files we do way.

        boolean ok;
        File scratchDir = absTestScratchDir();
        File resultDir = absTestResultDir();

        if (scratchDir.equals(resultDir)) {
            // if scratchDir is the same as resultDir, we just need to delete
            // the files we don't want to keep; the ones we want to keep are
            // already in the right place.
            if (params.getRetainStatus().contains(status.getType())) {
                // all files to be retained; no need to delete any files
                ok = true;
            } else {
                Pattern rp = params.getRetainFilesPattern();
                if (rp != null) {
                    // delete files which do not match pattern
                    // extend pattern so as not to delete *.jtr files
                    Pattern rp_jtr = Pattern.compile(".*\\.jtr|" + rp.pattern());
                    ok = deleteFiles(resultDir, rp_jtr, false);
                } else {
                    // test result doesn't match status set, no patterns specified:
                    // delete all except *.jtr files
                    Pattern jtr = Pattern.compile(".*\\.jtr");
                    ok = deleteFiles(resultDir, jtr, false);
                }
            }
        } else {
            // if scratchDir is not the same as resultDir, we need to
            // save the files we want and delete the rest.
            if (params.getRetainStatus().contains(status.getType())) {
                // save all files; no need to delete any files
                ok = saveFiles(scratchDir, resultDir, null, false);
            } else {
                Pattern rp = params.getRetainFilesPattern();
                if (rp != null) {
                    // save files which need to be retained
                    ok = saveFiles(scratchDir, resultDir, rp, true);
                } else {
                    // test result doesn't match status set, no patterns specified:
                    // no files need saving
                    ok = true;
                }
            }
            // delete any files remaining in the scratch dir
            ok &= deleteFiles(scratchDir, null, false);
        }

        return ok;
    }

    /**
     * Copy all files in a directory that optionally match or don't match a pattern.
     **/
    private boolean saveFiles(File fromDir, File toDir, Pattern p, boolean match) {
        boolean result = true;
        boolean toDirExists = toDir.exists();
        if (toDirExists) {
            try {
                cleanDirectoryContents(toDir);
            } catch (TestRunException e) {
                System.err.println("warning: failed to empty " + toDir);
                //result = false;
            }
        }
        for (File file: fromDir.listFiles()) {
            String fileName = file.getName();
            if (file.isDirectory()) {
                File dest = new File(toDir, fileName);
                result &= saveFiles(file, dest, p, match);
            } else {
                boolean save = (p == null) || (p.matcher(fileName).matches() == match);
                if (save) {
                    if (!toDirExists) {
                        toDir.mkdirs();
                        toDirExists = toDir.exists();
                    }
                    File dest = new File(toDir, fileName);
                    if (dest.exists())
                        dest.delete();
                    boolean ok = file.renameTo(dest);
                    if (!ok) {
                        System.err.println("error: failed to rename " + file + " to " + dest);
                        result = false;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Delete all files in a directory that optionally match or don't
     * match a pattern.
     * @returns true if the contents of the directory are successfully deleted.
     */
    private boolean deleteFiles(File dir, Pattern p, boolean match) {
        return deleteFiles(dir, p, match, false);
    }

    /**
     * Delete all files in a directory that optionally match or don't
     * match a pattern.  If deleteDir is set and all files in the directory
     * are deleted, the directory is deleted as well.
     * @returns true if all files and directories are deleted successfully.
     */
    private boolean deleteFiles(File dir, Pattern p, boolean match, boolean deleteDir) {
        if (!dir.exists())
            return true;

        boolean all = true;
        for (File file: dir.listFiles()) {
            if (file.isDirectory()) {
                all &= deleteFiles(file, p, match, true);
            } else {
                boolean delete = (p == null) || (p.matcher(file.getName()).matches() == match);
                if (delete) {
                    boolean ok = file.delete();
                    if (!ok)
                        System.err.println("warning: failed to delete " + file);
                    all &= ok;
                } else {
                    all = false;
                }
            }
        }
        if (all && deleteDir) {
            all = dir.delete();
            // warning if delete fails?
        }
        return all;
    }

    private void cleanDirectoryContents(File dir) throws TestRunException {
        if (dir.exists()) {
            try {
                cleanDirectoryContents0(dir.getCanonicalFile());
            } catch (IOException e) {
                throw new TestRunException(CLEAN_RM_PROB + dir);
            }
        }
    }

    private void cleanDirectoryContents0(File dir) throws TestRunException {
        File[] children = dir.listFiles();
        if (children != null) {
            try {
                for (int i = 0; i < children.length; i++) {
                    File child = children[i];
                    try {
                        // check that file is a real directory, not a symbolic link to a directory
                        if (child.isDirectory() && child.equals(child.getCanonicalFile())) {
                            cleanDirectoryContents(child);
                            File[] remaining = child.listFiles();
                            if (remaining == null)
                                throw new TestRunException(CLEAN_RM_PROB + child + " cannot determine remaining files");
                            if (remaining.length > 0)
                                throw new TestRunException(CLEAN_RM_PROB + child + " remaining: " + Arrays.asList(remaining));
                        }
                        if (!child.delete())
                            throw new TestRunException(CLEAN_RM_PROB + child);
                    } catch (IOException e) {
                        throw new TestRunException(CLEAN_RM_PROB + child);
                    }
                }
            } catch (SecurityException e) {
                throw new TestRunException(CLEAN_SECMGR_PROB + Arrays.asList(children));
            }
        }
    } // cleanDirectoryContents()

    private void populateActionTable() {
        addAction("applet", AppletAction.class);
        addAction("build", BuildAction.class);
        addAction("clean", CleanAction.class);
        addAction("compile", CompileAction.class);
        addAction("ignore", IgnoreAction.class);
        addAction("main", MainAction.class);
        addAction("junit", JUnitAction.class);
        addAction("shell", ShellAction.class);
    } // populateActionTable()

    private void addAction(String action, Class<?> actionClass) {
        if (!Action.class.isAssignableFrom(actionClass))
            throw new IllegalArgumentException(ADD_BAD_SUBTYPE + Action.class.getName());
        actionTable.put(action, actionClass);
    } // addAction()

    /**
     * Decode the reason and set the appropriate string.  At this point, we
     * should only get reasons that are generated by the test finder.
     *
     * @param cmd  The command we will run.  Includes the encoded reason.
     */
    private String getReason(String[] cmd) {
        String retVal;
        StringBuffer sb = new StringBuffer();
        String reason = cmd[0];
        if (reason.equals(Action.REASON_ASSUMED_ACTION)) {
            for (int i = 1; i < cmd.length; i++)
                sb.append(cmd[i]).append(" ");
            retVal = Action.SREASON_ASSUMED_ACTION + sb;
        } else if (reason.equals(Action.REASON_USER_SPECIFIED)) {
            for (int i = 1; i < cmd.length; i++)
                sb.append(cmd[i]).append(" ");
            retVal = Action.SREASON_USER_SPECIFIED + sb;
        } else {
            retVal = "Unknown";
        }
        return retVal;
    } // getReason()

    /**
     * Determine whether environment variables have been tunneled using the
     * following syntax:  -DenvVars="name0=value0,name1=value1". If they
     * have, return a string array of name=value pairs.  Otherwise, return a
     * string array with 0 elements.
     *
     * @return     A string array containing the tunneled environment variables.
     */
    String[] getEnvVars() {
        return params.getEnvVars();
    }

    /**
     * Determine whether we just want to check the validity of the
     * user-provided test description without actually running the test.
     */
    boolean isCheck() {
        return params.isCheck();
    }

    /**
     * VM options for otherJVM tests
     */
    List<String> getTestVMOptions() {
        return params.getTestVMOptions();
    }

    /**
     * Tool VM options for otherJVM tests
     */
    List<String> getTestToolVMOptions() {
        return params.getTestToolVMOptions();
    }

    /**
     * VM options and java for otherJVM tests
     */
    List<String> getTestVMJavaOptions() {
        return params.getTestVMJavaOptions();
    }

    /**
     * compiler options
     */
    List<String> getTestCompilerOptions() {
        return params.getTestCompilerOptions();
    }

    /**
     * java command options
     */
    List<String> getTestJavaOptions() {
        return params.getTestJavaOptions();
    }

    /**
     * What to do with @ignore tags
     */
    IgnoreKind getIgnoreKind() {
        return params.getIgnoreKind();
    }

    //----------------------- computing paths ---------------------------------

    private String cacheRelTestSrcDir;
    private String relTestSrcDir() {
        if (cacheRelTestSrcDir == null) {
            String d = td.getRootRelativeFile().getParent();
            if (d == null)
                cacheRelTestSrcDir = "";
            else
                cacheRelTestSrcDir = d;
        }
        return cacheRelTestSrcDir;
    } // relTestSrcDir()

    private File cacheAbsTestSrcDir;
    File absTestSrcDir() {
        if (cacheAbsTestSrcDir == null)
            cacheAbsTestSrcDir = td.getFile().getParentFile();

        return cacheAbsTestSrcDir;
    } // absTestSrcDir()

    private File cacheAbsTestClsDir;
    File absTestClsDir() throws TestClassException {
        if (cacheAbsTestClsDir == null) {
            String sourceDir = relTestSrcDir();
            File classDir;
            try {
                String[] testClsDir = regEnv.lookup("testClassDir");
                if (testClsDir == null || testClsDir.length != 1)
                    throw new TestClassException(PATH_TESTCLASS);
                classDir = new File(getThreadSafeDir(testClsDir[0]), sourceDir);
                if (!classDir.exists())
                    classDir.mkdirs();
            } catch (TestEnvironment.Fault e) {
                throw new TestClassException(PATH_TESTCLASS);
            }
            cacheAbsTestClsDir = classDir;
        }

        try {
            cacheAbsTestClsDir = cacheAbsTestClsDir.getCanonicalFile();
        } catch (IOException e) {
            throw new TestClassException(PROB_CANT_CANON + cacheAbsTestClsDir);
        }
        return cacheAbsTestClsDir;
    } // absTestClsDir()

    private File cacheAbsTestScratchDir;
    File absTestScratchDir() {
        if (cacheAbsTestScratchDir == null) {
            cacheAbsTestScratchDir = params.isRetainEnabled() && getExecMode() == ExecMode.OTHERVM
                ? absTestResultDir()
                : workDir.getFile(getThreadSafeDir("scratch").getPath());
        }
        return cacheAbsTestScratchDir;
    } // absTestScratchDir()

    private File cacheAbsTestResultDir;
    File absTestResultDir() {
        if (cacheAbsTestResultDir == null) {
            String wrp = TestResult.getWorkRelativePath(getTestDescription());
            // assert wrp.endsWith(".jtr")
            if (wrp.endsWith(".jtr"))
                wrp = wrp.substring(0, wrp.length() - 4);
            cacheAbsTestResultDir = workDir.getFile(wrp);
        }
        return cacheAbsTestResultDir;
    }

    private File cacheAbsTestClsTopDir;
    File absTestClsTopDir() throws TestClassException {
        String absTestClsDir = absTestClsDir().getPath();
        String clsStr = "classes";

        // locate trailing "classes"
        int pos = absTestClsDir.lastIndexOf(FILESEP + clsStr);
        String clsTopDir = absTestClsDir.substring(0, pos + clsStr.length() + 1);

        cacheAbsTestClsTopDir = new File(clsTopDir);

        return cacheAbsTestClsTopDir;
    } // absTestClsTopDir()

    /**
     * Determine the destination directory for the compiled class file given the
     * .java file.  If we are passed a file which should live in a library, make
     * sure that we compile to the correct library directory.
     */
    File absTestClsDestDir(String fileName) throws TestClassException {
        return absTestClsDestDir(new File(fileName));
    }

    File absTestClsDestDir(File file) throws TestClassException {
        File retVal = null;
        String name = null;
        if (file.isAbsolute()) {
            // from build
            String fileName = file.getPath();
            String srcDir = absTestSrcDir().getPath();
            int len = srcDir.length();
            if (fileName.startsWith(srcDir)) {
                name = fileName.substring(len+1);
            } else {
                // highly unlikely to occur, but just in case
                throw new TestClassException(TEST_SRC + fileName
                                             + UNEXPECTED_LOC + srcDir);
            }
        } else {
            // relative, from compile
            // preserve directory name if passed
            name = file.getPath();
        }

        // try looking in the test source directory
        File testSrc = new File(absTestSrcDir(), name);
        if (testSrc.exists())
            // no need to modify the class directory
            retVal = absTestClsDir();
        else {
            // the .java file lives in a library
            for (int i = 0; i < cacheAbsSrcLibList.length; i++) {
                File libSrc = new File(cacheAbsSrcLibList[i], name);
                if (libSrc.exists()) {
                    // in a library
                    retVal = new File(absTestClsDir(), cacheRelSrcLibList[i]);
                    break;
                }
            }
        }

        if (retVal == null)
            throw new TestClassException(CANT_FIND_SRC + file);

        return retVal;
    } // absTestClsDestDir()

    private Path cacheTestClassPath;
    Path getTestClassPath() throws TestClassException {
        if (cacheTestClassPath == null) {
            cacheTestClassPath = new Path();
            JDK jdk = getTestJDK();
            if (jdk.isVersion(JDK.Version.V1_1, params)) {
                cacheTestClassPath.append(absTestClsDir());
                cacheTestClassPath.append(absTestSrcDir());
                cacheTestClassPath.append(absClsLibList());
                cacheTestClassPath.append(jdk.getJavaClassPath());
                cacheTestClassPath.append(jdk.getJDKClassPath());
            } else { // isTestJDK12() or above
                cacheTestClassPath.append(absTestClsDir());
                cacheTestClassPath.append(absTestSrcDir()); // required??
                cacheTestClassPath.append(absClsLibList());
                cacheTestClassPath.append(absSrcJarLibList());
                cacheTestClassPath.append(jdk.getJDKClassPath());
            }

            // handle cpa option to jtreg
            String[] envVars = getEnvVars();
            for (int i = 0; i < envVars.length; i++) {
                if (envVars[i].startsWith("CPAPPEND")) {
                    String cpa = (StringArray.splitEqual(envVars[i]))[1];
                    // the cpa we were passed always uses '/' as FILESEP, make
                    // sure to use the proper one for the platform
                    cpa = cpa.replace('/', File.separatorChar);
                    cacheTestClassPath.append(cpa);
                }
            }

        }
        return cacheTestClassPath;
    } // getTestClassPath()

    private Path cacheCompileClassPath;
    Path getCompileClassPath() throws TestClassException {
        if (cacheCompileClassPath == null) {
            cacheCompileClassPath = new Path();
            JDK jdk = getCompileJDK();
            if (jdk.isVersion(JDK.Version.V1_1, params)) {
                cacheCompileClassPath.append(absTestClsDir());
                cacheCompileClassPath.append(absTestSrcDir());
                cacheCompileClassPath.append(absClsLibList());
                cacheCompileClassPath.append(jdk.getJavaClassPath());
                cacheCompileClassPath.append(jdk.getJDKClassPath());
            } else { // isTestJDK12() or above
                cacheCompileClassPath.append(absTestClsDir());
                cacheCompileClassPath.append(absTestSrcDir()); // required??
                cacheCompileClassPath.append(absClsLibList());
                cacheCompileClassPath.append(absSrcJarLibList());
                cacheCompileClassPath.append(jdk.getJDKClassPath());
            }

            // handle cpa option to jtreg
            String[] envVars = getEnvVars();
            for (int i = 0; i < envVars.length; i++) {
                if (envVars[i].startsWith("CPAPPEND")) {
                    String cpa = (StringArray.splitEqual(envVars[i]))[1];
                    // the cpa we were passed always uses '/' as FILESEP, make
                    // sure to use the proper one for the platform
                    cpa = cpa.replace('/', File.separatorChar);
                    cacheCompileClassPath.append(cpa);
                }
            }

        }
        return cacheCompileClassPath;
    } // getCompileClassPath()

    // necessary only for JDK1.2 and above
    private Path cacheCompileSourcePath;

    /**
     * Returns the fully-qualified directory name where the source resides.
     *
     * @param fileName The exact name of the file to locate.
     * @param dirList  A list of directories in which to search. The list will
     *             contain the directory of the defining file of the test
     *             followed by the library list.
     */
    Path getCompileSourcePath() throws TestRunException {
        if (cacheCompileSourcePath == null) {
            cacheCompileSourcePath = new Path();
            JDK jdk = getCompileJDK();
            cacheCompileSourcePath.append(absTestSrcDir());
            cacheCompileSourcePath.append(absSrcLibList());
            cacheCompileSourcePath.append(jdk.getJDKClassPath()); // required??
        }
        return cacheCompileSourcePath;
    } // getCompileSourcePath()

    private Map<File,Set<String>> cacheDirContents = new HashMap<File,Set<String>>();
    private File locateFile(String fileName, File[] dirList)
        throws TestRunException
    {
        for (int i = 0; i < dirList.length; i++) {
            File dir = dirList[i];

            if (fileName.indexOf(FILESEP) == -1) {
                // file name provided

                // set of the directory contents
                Set<String> dirSet = cacheDirContents.get(dir);
                if (dirSet == null) {
                    String[] fileList = dir.list();
                    dirSet = new HashSet<String>(1);
                    if (fileList != null)
                        dirSet.addAll(Arrays.asList(fileList));
                    cacheDirContents.put(dir, dirSet);
                }
                if (dirSet.contains(fileName))
                    // success
                    return new File(dir, fileName);
            } else {
                // file name specifies directory
                File f = new File(dir, fileName);
                if (f.exists())
                    return f;
            }
        }

        // create the list of directory names that we looked in and fail
        StringBuilder dirListStr = new StringBuilder();
        for (int i = 0; i < dirList.length; i++)
            dirListStr.append(dirList[i]).append(" ");
        throw new TestRunException(CANT_FIND_SRC + fileName +
                                   LIB_LIST + dirListStr);

    } // locateFile()

    /**
     * Returns an absolute path to the given ".java" file.
     *
     * @param fileName The exact name of the file to search for (must be called
     *             with ".java" as necessary).
     */
    private File[] cacheJavaSrcPath = null;
    File locateJavaSrc(String fileName) throws TestRunException {
        if (cacheJavaSrcPath == null) {
            cacheJavaSrcPath = new File[cacheAbsSrcLibList.length + 1];
            cacheJavaSrcPath[0] = absTestSrcDir();
            System.arraycopy(cacheAbsSrcLibList, 0, cacheJavaSrcPath, 1,
                             cacheAbsSrcLibList.length);
        }
        try {
            return locateFile(fileName, cacheJavaSrcPath);
        } catch (TestRunException ex) {
            // Allow the file to define a class in a package,
            // even though it is directly inside the test dir.
            int sep = fileName.lastIndexOf(FILESEP);
            if (sep >= 0) {
                String baseName = fileName.substring(sep+1);
                try {
                    File[] path0 = { absTestSrcDir() };
                    return locateFile(baseName, path0);
                } catch (TestRunException ignoreEx) {
                }
            }
            throw ex;
        }
    } // locateJavaSrc()

    /**
     * For a given .java file, find the absolute path to the .class file.
     *
     * @param fileNamethe .java file we are interested in.
     */
    File locateJavaCls(String fileName) throws TestRunException {
        String sn = locateJavaSrc(fileName).getName();
        File cp = new File(absTestClsDir(),
            sn.substring(0, sn.length() - ".java".length()) + ".class");
        return cp;
    } // locateJavaCls()

    File locateJavaClsDir(String fileName) throws TestRunException {
        return (locateJavaCls(fileName)).getParentFile();
    } // locateJavaClsDir()

    private String[] cacheRelSrcLibList;
    private File[] cacheAbsSrcLibList;
    private File[] cacheAbsClsLibList;
    private void setLibList(String libPath) throws TestClassException {
        if ((cacheAbsSrcLibList == null) || (cacheAbsClsLibList == null)) {
            cacheRelSrcLibList = StringArray.splitWS(libPath);

            cacheAbsSrcLibList = new File[cacheRelSrcLibList.length];
            for (int i = 0; i < cacheRelSrcLibList.length; i++) {
                cacheAbsSrcLibList[i] = new File(absTestSrcDir(), cacheRelSrcLibList[i]);
            }

            if (hasEnv()) {
                cacheAbsClsLibList = new File[cacheRelSrcLibList.length];
                for (int i = 0; i < cacheRelSrcLibList.length; i++) {
                    cacheAbsClsLibList[i] = new File(absTestClsDir(), cacheRelSrcLibList[i]);
                }
            }
        }
    } // setLibList()

    private void addClsLib(File lib) {
        assert cacheAbsClsLibListPath == null;
        File[] newList = new File[cacheAbsClsLibList.length + 1];
        System.arraycopy(cacheAbsClsLibList, 0, newList, 0, cacheAbsClsLibList.length);
        newList[newList.length - 1] = lib;
        cacheAbsClsLibList = newList;
    } // addClsLib()

    private Path cacheAbsClsLibListPath;
    Path absClsLibList() throws TestClassException {
        if (cacheAbsClsLibListPath == null) {
            cacheAbsClsLibListPath = new Path();
            for (int i = 0; i < cacheAbsClsLibList.length; i++) {
                // It is not clear why the first of the following two statements
                // was commented out in favor of the second. With the addition
                // of addClsLib(), above, it is important to use cacheAbsClsLibList
                // instead of recalculating the entries from  cacheRelSrcLibList.
                // It is possible the change was made as a defensive measure when
                // "if (hasEnv()) { ..}" was added to setLibList for the benefit
                // of the GUI (i.e. getSourceFiles()).  But, absClsLibList()
                // should only be called when hasEnv() is true, implying that
                // cacheAbsClsLibList is already initialized correctly.
//              String curr = cacheAbsClsLibList[i];
//                File curr = new File(absTestClsDir(), cacheRelSrcLibList[i]);
                File curr = cacheAbsClsLibList[i];
                cacheAbsClsLibListPath.append(curr);
            }
        }
        return cacheAbsClsLibListPath;
    } // absClsLibList()

    private Path cacheAbsSrcLibListPath;
    Path absSrcLibList() {
        if (cacheAbsSrcLibListPath == null) {
            cacheAbsSrcLibListPath = new Path(cacheAbsSrcLibList);
        }
        return cacheAbsSrcLibListPath;
    } // absSrcLibList()

    private Path cacheAbsSrcJarLibListPath;
    Path absSrcJarLibList() {
        if (cacheAbsSrcJarLibListPath == null) {
            cacheAbsSrcJarLibListPath = new Path();
            for (File f: cacheAbsSrcLibList) {
                if (f.getName().endsWith(".jar")) {
                    if (f.exists())
                        cacheAbsSrcJarLibListPath.append(f);
                }
            }
        }
        return cacheAbsSrcJarLibListPath;
    } // absSrcJarLibList()

    ExecMode getExecMode() {
        return params.getExecMode();
    }

    Path getJavaTestClassPath() {
        return params.getJavaTestClassPath();
    }

    //--------------------------------------------------------------------------

    JDK getTestJDK() {
        return params.getTestJDK();
    }

    JDK.Version getTestJDKVersion() {
        try {
            return JDK.Version.forName(getTestJDK().getVersion(params));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    boolean isTestJDK11() {
        return params.getTestJDK().isVersion(JDK.Version.V1_1, params);
    }

    String getJavaProg() {
        return params.getTestJDK().getJavaProg().getPath();
    }

    //--------------------------------------------------------------------------

    JDK getCompileJDK() {
        return params.getCompileJDK();
    }

    JDK.Version getCompileJDKVersion() {
        try {
            return JDK.Version.forName(getCompileJDK().getVersion(params));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    boolean isCompileJDK11() {
        return params.getCompileJDK().isVersion(JDK.Version.V1_1, params);
    }

    String getJavacProg() {
        return params.getCompileJDK().getJavacProg().getPath();
    }

    //--------------------------------------------------------------------------

    // Get the standard properties to be set for tests

    Map<String,String> getTestProperties() throws TestClassException {
        Map<String,String> p = new HashMap<String,String>();
        // The following will be added to javac.class.path on the test JVM
        switch (getExecMode()) {
            case AGENTVM:
            case SAMEVM:
                Path path = new Path()
                    .append(absTestClsDir(), absTestSrcDir())
                    .append(absClsLibList());
                p.put("test.class.path.prefix", path.toString());
        }
        p.put("test.src", absTestSrcDir().getPath());
        p.put("test.classes", absTestClsDir().getPath());
        p.put("test.vm.opts", StringUtils.join(getTestVMOptions(), " "));
        p.put("test.tool.vm.opts", StringUtils.join(getTestToolVMOptions(), " "));
        p.put("test.compiler.opts", StringUtils.join(getTestCompilerOptions(), " "));
        p.put("test.java.opts", StringUtils.join(getTestJavaOptions(), " "));
        p.put("test.jdk", getTestJDK().getPath());
        p.put("compile.jdk", getCompileJDK().getPath());
        return p;
    }

    //--------------------------------------------------------------------------

    /*
     * Get an agent for a VM with the given VM options.
     */
    Agent getAgent(JDK jdk, Path classpath, List<String> testVMOpts) throws IOException {
        List<String> vmOpts = new ArrayList<String>();
        vmOpts.add("-classpath");
        vmOpts.add(classpath.toString());
        vmOpts.addAll(testVMOpts);

        /*
         * A script only uses one agent at a time, and only one, maybe two,
         * different agents overall, for actions that use agentVM mode (i.e.
         * CompileAction and MainAction.) Therefore, use a simple list to
         * record the agents that the script has already obtained for use.
         */
        for (Agent agent: agents) {
            if (agent.matches(absTestScratchDir(), jdk, vmOpts))
                return agent;
        }

        List<String> envVars = new ArrayList<String>();
        envVars.addAll(Arrays.asList(getEnvVars()));
        // some tests are inappropriately relying on the CLASSPATH environment
        // variable being set, so ensure it is set. See equivalent code in MainAction
        // and Main.execChild. Note we cannot set exactly the same classpath as
        // for othervm, because we should not include test-specific info
        Path cp = new Path(getJavaTestClassPath()).append(jdk.getToolsJar());
        envVars.add("CLASSPATH=" + cp);

        Agent.Pool p = Agent.Pool.instance();
        Agent agent = p.getAgent(absTestScratchDir(), jdk, vmOpts, envVars);
        agents.add(agent);
        return agent;
    }

    /**
     * Close an agent, typically because an error has occurred while using it.
     */
    void closeAgent(Agent agent) {
        agent.close();
        agents.remove(agent);
    }

    /*
     * Close all the agents this script has obtained for use. This will
     * terminate the VMs used by those agents.
     */
    void closeAgents() {
        for (Agent agent: agents) {
            agent.close();
        }
        agents.clear();
    }

    /*
     * Release all the agents this script has obtained for use.
     * The agents are made available for future reuse.
     */
    void releaseAgents() {
        Agent.Pool pool = Agent.Pool.instance();
        for (Agent agent: agents) {
            pool.save(agent);
        }
    }

    List<Agent> agents = new ArrayList<Agent>();

    //----------internal classes-----------------------------------------------

    /*
     * Exception used to indicate that there is a problem with the destination
     * of class files generated by the actual tests.
     */
    public static class TestClassException extends TestRunException {
        private static final long serialVersionUID = -5087319602062056951L;
        public TestClassException(String msg) {
            super("Test Class Exception: " + msg);
        } // TestClassException()
    }

    //----------misc statics---------------------------------------------------

    static final String WRAPPEREXTN = ".jta";

    private static final String FILESEP  = System.getProperty("file.separator");
    private static final String LINESEP  = System.getProperty("line.separator");

    private static final String
        CANT_INSTANTIATE      = "Unable to instantiate: ",
        NOT_EXT_ACTION        = " does not extend Action",
        ILLEGAL_ACCESS_INIT   = "Illegal access to init method: ",
        BAD_ACTION            = "Bad action for script: ",
        CLEAN_RM_PROB         = "Problem deleting file: ",
        CLEAN_SECMGR_PROB     = "Problem deleting scratch directory: ",
        ADD_BAD_SUBTYPE       = "Class must be a subtype of ",
        PATH_TESTCLASS        = "Unable to locate test class directory!?",
        PATH_SCRATCH_CREATE   = "Can't create test scratch directory: ",
        TEST_SRC              = "Test source: ",
        UNEXPECTED_LOC        = "does not reside in: ",
        CANT_FIND_SRC         = "Can't find source file: ",
        LIB_LIST              = " in directory-list: ",
        PROB_CANT_CANON       = "Unable to canonicalize file: ";

    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(RegressionScript.class);

    //----------member variables-----------------------------------------------

    private Map<String,Class<?>> actionTable = new HashMap<String,Class<?>>();
    private TestResult testResult;
    // the library-list resolved to the test-src directory
    //private String[] libList;

    private RegressionEnvironment regEnv;
    private RegressionParameters params;

    //----------thread safety-----------------------------------------------

    private static final AtomicInteger uniqueId = new AtomicInteger(0);

    private static final ThreadLocal < Integer > uniqueNum =
        new ThreadLocal < Integer > () {
            @Override protected Integer initialValue() {
                return uniqueId.getAndIncrement();
        }
    };

    File getThreadSafeDir(String name) {
        return (regEnv.params.getConcurrency() == 1)
                ? new File(name)
                : new File(name, String.valueOf(getCurrentThreadId()));
    }

    private static int getCurrentThreadId() {
        return uniqueNum.get();
    }
}
