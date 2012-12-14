/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.sun.javatest.Script;
import com.sun.javatest.Status;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestEnvironment;
import com.sun.javatest.TestResult;

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

        regEnv = (RegressionEnvironment) env;
        params = regEnv.params;

        Status status = Status.passed("OK");
        String actions = td.getParameter("run");

//      System.out.println("--- ACTIONS: " + actions);
        // actions != null -- should never happen since we have reasonable
        // defaults

        try {
            setLibList(td.getParameter("library"));

            LinkedList<Action> actionList = parseActions(actions, true);

            boolean needJUnit = false;
            for (Action a: actionList) {
                if (a instanceof JUnitAction)
                    needJUnit = true;
            }
            if (needJUnit)
                addClsLib(params.getJUnitJar());

            initScratchDirectory();

            testResult = getTestResult();
            PrintWriter msgPW = testResult.getTestCommentWriter();
            msgPW.println("JDK under test: " + getJavaFullVersion());

            // if we got an error while parsing the TestDescription, return
            // error immediately
            if (td.getParameter("error") != null)
                status = Status.error(td.getParameter("error"));
            else {
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
            if (params.isRetainEnabled())
                retainScratchFiles(status);
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

    private void retainScratchFiles(Status status) {
        File scratchDir = absTestScratchDir();
        File resultDir = absTestResultDir();

        if (params.getRetainStatus().contains(status.getType())) {
            if (!scratchDir.equals(resultDir))
                saveFiles(scratchDir, resultDir, null, false);
        } else {
            Pattern rp = params.getRetainFilesPattern();
            if (scratchDir.equals(resultDir) || rp == null)
                deleteFiles(resultDir, rp, false);
            else if (rp != null)
                saveFiles(scratchDir, resultDir, rp, true);
        }
    }

    /**
     * Copy all files in a directory that optionally match or don't match a pattern.
     **/
    private void saveFiles(File fromDir, File toDir, Pattern p, boolean match) {
        boolean toDirExists = toDir.exists();
        if (toDirExists) {
            try {
                cleanDirectoryContents(toDir);
            } catch (TestRunException e) {
                System.err.println("warning: failed to empty " + toDir);
            }
        }
        for (File file: fromDir.listFiles()) {
            String fileName = file.getName();
            if (file.isDirectory()) {
                File dest = new File(toDir, fileName);
                saveFiles(file, dest, p, match);
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
                    if (!ok)
                        System.err.println("warning: failed to rename " + file + " to " + dest);
                }
            }
        }
    }

    /**
     * Delete all files in a directory that optionally match or don't
     * match a pattern.  If all files in the directory are deleted,
     * the directory is deleted as well.
     * @returns true if the directory and all its contents are
     * successfully deleted.
     */
    private boolean deleteFiles(File dir, Pattern p, boolean match) {
        if (!dir.exists())
            return true;

        boolean all = true;
        for (File file: dir.listFiles()) {
            if (file.isDirectory()) {
                all &= deleteFiles(file, p, match);
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
        if (all) {
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
                classDir = new File(testClsDir[0], sourceDir);
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
            cacheAbsTestScratchDir = params.isRetainEnabled() && isOtherJVM()
                ? absTestResultDir()
                : workDir.getFile("scratch");
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

    String getStdJavaClassPath() {
        return params.getStdJavaClassPath();
    }

    String getStdJDKClassPath() {
        return params.getStdJDKClassPath();
    }

    private String cacheTestClassPath;
    String testClassPath() throws TestClassException {
        if (cacheTestClassPath == null) {
            cacheTestClassPath = "";
            if (isJDK11()) {
                cacheTestClassPath = absTestClsDir() + PATHSEP +
                    absTestSrcDir() + PATHSEP +
                    absClsLibListStr() +
                    getStdJavaClassPath() +
                    getStdJDKClassPath();
            } else { // isJDK12() or above
                cacheTestClassPath = absTestClsDir() + PATHSEP +
                    absTestSrcDir() + PATHSEP +
                    absClsLibListStr() +
                    absSrcJarLibListStr() +
                    getStdJDKClassPath();
            }

            // handle cpa option to jtreg
            String[] envVars = getEnvVars();
            for (int i = 0; i < envVars.length; i++) {
                if (envVars[i].startsWith("CPAPPEND")) {
                    String cpa = (StringArray.splitEqual(envVars[i]))[1];
                    // the cpa we were passed always uses '/' as FILESEP, make
                    // sure to use the proper one for the platform
                    cpa = cpa.replace('/', File.separatorChar);
                    cacheTestClassPath += PATHSEP + cpa;
                }
            }

        }
        return cacheTestClassPath;
    } // testClassPath()

    // necessary only for JDK1.2 and above
    private String cacheTestSourcePath;
    String testSourcePath() throws TestRunException {
        if (cacheTestSourcePath == null) {
            cacheTestSourcePath = absTestSrcDir() + PATHSEP +
                absSrcLibListStr() + getStdJDKClassPath();
        }
        return cacheTestSourcePath;
    } // testSourcePath()

    /**
     * Returns the fully-qualified directory name where the source resides.
     *
     * @param fileName The exact name of the file to locate.
     * @param dirList  A list of directories in which to search. The list will
     *             contain the directory of the defining file of the test
     *             followed by the library list.
     */
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
        assert cacheAbsClsLibListStr == null;
        File[] newList = new File[cacheAbsClsLibList.length + 1];
        System.arraycopy(cacheAbsClsLibList, 0, newList, 0, cacheAbsClsLibList.length);
        newList[newList.length - 1] = lib;
        cacheAbsClsLibList = newList;
    } // addClsLib()

    private String cacheAbsClsLibListStr;
    String absClsLibListStr() throws TestClassException {
        if (cacheAbsClsLibListStr == null) {
            cacheAbsClsLibListStr = "";
            for (int i = 0; i < cacheAbsClsLibList.length; i++) {
                // It is not clear why the first of the following two statements
                // was commented out in favor of the second. With the addition
                // of addClsLib(), above, it is important to use cacheAbsClsLibList
                // instead of recalculating the entries from  cacheRelSrcLibList.
                // It is possible the change was made as a defensive measure when
                // "if (hasEnv()) { ..}" was added to setLibList for the benefit
                // of the GUI (i.e. getSourceFiles()).  But, absClsLibListStr()
                // should only be called when hasEnv() is true, implying that
                // cacheAbsClsLibList is already initialized correctly.
//              String curr = cacheAbsClsLibList[i];
//                File curr = new File(absTestClsDir(), cacheRelSrcLibList[i]);
                File curr = cacheAbsClsLibList[i];
                cacheAbsClsLibListStr += curr.getPath() + PATHSEP;
            }
        }
        return cacheAbsClsLibListStr;
    } // absClsLibListStr()

    private String cacheAbsSrcLibListStr;
    String absSrcLibListStr() {
        if (cacheAbsSrcLibListStr == null) {
            cacheAbsSrcLibListStr = "";
            for (int i = 0; i < cacheAbsSrcLibList.length; i++) {
                File curr = cacheAbsSrcLibList[i];
                cacheAbsSrcLibListStr += curr + PATHSEP;
            }
        }
        return cacheAbsSrcLibListStr;
    } // absSrcLibListStr()

    private String cacheAbsSrcJarLibListStr;
    String absSrcJarLibListStr() {
        if (cacheAbsSrcJarLibListStr == null) {
            cacheAbsSrcJarLibListStr = "";
            for (int i = 0; i < cacheAbsSrcLibList.length; i++) {
                File curr = cacheAbsSrcLibList[i];
                if (curr.getName().endsWith(".jar")) {
                    if (curr.exists())
                        cacheAbsSrcJarLibListStr += curr + PATHSEP;
                }
            }
        }
        return cacheAbsSrcJarLibListStr;
    } // absSrcJarLibListStr()

    String getJavaTestClassPath() {
        return params.getJavaTestClassPath();
    }

    String getJDK() {
        return params.getJDK().getPath();
    }

    boolean isOtherJVM() {
        return params.isOtherJVM();
    }

    String getJavaProg() {
        return params.getJDK().getJavaProg().getPath();
    }

    String getJavacProg() {
        return params.getJDK().getJavacProg().getPath();
    }

    /**
     * Try to determine the version of Java that is being tested.  If a system
     * has the "-fullversion" option, that string plus the appropriate
     * java.home is returned.  Otherwise only java.home is returned.
     */
    String getJavaFullVersion() {
        return params.getJavaFullVersion();
    }

    /**
     * Attempt to distinguish whether the JDK under test is JDK1.1.* or JDK1.2.
     *
     * @return     If the test JDK is JDK1.1.*, then 1.1 is returned.  If the
     *             test JDK is JDK1.2, then 1.2 is returned.
     */
    String javaVersion() {
        return params.getJavaVersion();
    }

    boolean isJDK11() {
        return javaVersion().equals("1.1");
    }

    boolean isJDK12() {
        return javaVersion().equals("1.2");
    }

    boolean isJDK13() {
        return javaVersion().equals("1.3");
    }

    //----------internal classes-----------------------------------------------

    /*
     * Exception used to indicate that there is a problem with the destination
     * of class files generated by the actual tests.
     */
    public static class TestClassException extends TestRunException {
        static final long serialVersionUID = -5087319602062056951L;
        public TestClassException(String msg) {
            super("Test Class Exception: " + msg);
        } // TestClassException()
    }

    //----------misc statics---------------------------------------------------

    static final String WRAPPEREXTN = ".jta";

    private static final String FILESEP  = System.getProperty("file.separator");
    private static final String PATHSEP  = System.getProperty("path.separator");
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

    //----------member variables-----------------------------------------------

    private Map<String,Class<?>> actionTable = new HashMap<String,Class<?>>();
    private TestResult testResult;
    // the library-list resolved to the test-src directory
    //private String[] libList;

    private RegressionEnvironment regEnv;
    private RegressionParameters params;
}

