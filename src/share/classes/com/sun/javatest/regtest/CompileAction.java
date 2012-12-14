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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import com.sun.javatest.Status;
import com.sun.javatest.TestResult;
import com.sun.javatest.lib.JavaCompileCommand;
import com.sun.javatest.lib.ProcessCommand;

/**
 * This class implements the "compile" action as described by the JDK tag
 * specification.
 *
 * @author Iris A Garcia
 * @see Action
 */
public class CompileAction extends Action {
    /**
     * A method used by sibling classes to run both the init() and run()
     * method of CompileAction.
     *
     * @param opts The options for the action.
     * @param args The arguments for the actions.
     * @param reason Indication of why this action was invoked.
     * @param script The script.
     * @return     The result of the action.
     * @see #init
     * @see #run
     */
    public Status compile(String[][] opts, String[] args, String reason,
            RegressionScript script) throws TestRunException {
        init(opts, args, reason, script);
        return run();
    } // compile()

    /**
     * This method does initial processing of the options and arguments for the
     * action.  Processing is determined by the requirements of run() and
     * getSourceFiles(). If run will be called, script.hasEnv() will be true.
     * If script.hasEnv() is false, there is no context available to determine
     * any class directories.
     *
     * Verify that the options are valid for the "compile" action.
     *
     * Verify that there is at least one argument.  Find the class names to
     * compile (via presence of ".java") and modify to contain fully qualified
     * path.
     *
     * If one of the JVM options is "-classpath" or "-cp", add the test classes
     * and test sources directory to the provided path.
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
        this.script = script;
        this.reason = reason;

        if (args.length == 0)
            throw new ParseException(COMPILE_NO_CLASSNAME);

        for (int i = 0; i < opts.length; i++) {
            String optName  = opts[i][0];
            String optValue = opts[i][1];

            if (optName.equals("fail")) {
                reverseStatus = parseFail(optValue);
            } else if (optName.equals("timeout")) {
                timeout = parseTimeout(optValue);
            } else if (optName.equals("ref")) {
                ref = parseRef(optValue);
            } else if (optName.equals("process")) {
                process = true;
            } else {
                throw new ParseException(COMPILE_BAD_OPT + optName);
            }
        }

        if (timeout < 0)
            timeout = script.getActionTimeout(0);

        // add absolute path name to all the .java files create appropriate
        // class directories
        try {
            for (int i = 0; i < args.length; i++) {
                String currArg = args[i];

                if (currArg.endsWith(".java")) {
                    // make sure the correct file separator has been used
                    currArg = currArg.replace('/', File.separatorChar);

                    File sourceFile = new File(currArg);
                    if (!sourceFile.isAbsolute())
                        // User must have used @compile, so file must be
                        // in the same directory as the defining file.
                        args[i] = script.absTestSrcDir() + FILESEP + currArg;
//                  if (!sourceFile.exists())
//                      throw new ParseException(CANT_FIND_SRC);

                    // set the destination directory only if we've actually
                    // found something to compile
                    if (script.hasEnv()) {
                        destDir = script.absTestClsDestDir(currArg);
                        if (!destDir.exists())
                            destDir.mkdirs();
                    }

                }

                if (currArg.equals("-classpath") || currArg.equals("-cp")) {
                    classpathp = true;
                    // assume the next element provides the classpath, add
                    // test.classes and test.src and lib-list to it
                    if (script.hasEnv()) {
                        args[i+1] = addPath(args[i+1],
                                script.absTestClsDir() + PATHSEP +
                                script.absTestSrcDir() + PATHSEP +
                                script.absClsLibListStr());
                    }
                    args[i+1] = singleQuoteString(args[i+1]);
                }

                if (currArg.equals("-d")) {
                    throw new ParseException(COMPILE_OPT_DISALLOW);
                }

                // note that -sourcepath is only valid for JDK1.2 and beyond
                if (currArg.equals("-sourcepath")) {
                    sourcepathp = true;
                    // assume the next element provides the sourcepath, add test.src
                    // and lib-list to it
                    args[i+1] = addPath(args[i+1],
                            script.absTestSrcDir() + PATHSEP +
                            script.absSrcLibListStr());
                    args[i+1] = singleQuoteString(args[i+1]);
                }
            }

            // If we didn't set the destination directory, then we must not have
            // found something ending with ".java" to compile.
            if (script.hasEnv() && destDir == null) {
                if (process) {
                    destDir = script.absTestClsDir();
                    if (!destDir.exists())
                        destDir.mkdirs();
                } else
                    throw new ParseException(COMPILE_NO_DOT_JAVA);
            }
        } catch (RegressionScript.TestClassException e) {
            throw new ParseException(e.getMessage());
        }

        this.args = args;
    } // init()

    @Override
    public File[] getSourceFiles() {
        List<File> l = new ArrayList<File>();

        for (int i = 0; i < args.length; i++) {
            String currArg = args[i];

            if (currArg.endsWith(".java")) {
                l.add(new File(currArg));
            }
        }

        return l.toArray(new File[l.size()]);
    }

    /**
     * The method that does the work of the action.  The necessary work for the
     * given action is defined by the tag specification.
     *
     * Invoke the compiler on the given arguments which may possibly include
     * compiler options.  Equivalent to "javac <arg>+".
     *
     * Each named class will be compiled if its corresponding class file doesn't
     * exist or is older than its source file.  The class name is fully
     * qualified as necessary and the ".java" extension is added before
     * compilation.
     *
     * Build is allowed to search anywhere in the library-list.  Compile is
     * allowed to search only in the directory containing the defining file of
     * the test.  Thus, compile will always absolutify by adding the directory
     * path of the defining file to the passed filename.  Build must pass an
     * absolute filename to handle files found in the library-list.
     *
     * @return     The result of the action.
     * @exception  TestRunException If an unexpected error occurs while running
     *             the test.
     */
    public Status run() throws TestRunException {
        Status status;

        section = startAction("compile", args, reason);

        // Make sure that all of the .java files we want to compile exist.
        // We could let the compiler handle this, but if we put the extra check
        // here, we get more information in "check" mode.
        for (int i = 0; i < args.length; i++) {
            String currArg = args[i];
            if (currArg.endsWith(".java")) {
                if (!(new File(currArg)).exists())
                    throw new TestRunException(CANT_FIND_SRC + currArg);
            }
        }

        if (script.isCheck()) {
            status = Status.passed(CHECK_PASS);
        } else {
            if (script.isOtherJVM())
                status = runOtherJVM();
            else
                status = runSameJVM();
        }

        endAction(status, section);
        return status;
    } // run()

    //----------internal methods------------------------------------------------

    private Status runOtherJVM() throws TestRunException {
        Status status;
        final boolean jdk11 = script.isJDK11();
        final boolean useCLASSPATHEnv = jdk11;
        final boolean useClassPathOpt = !jdk11;
        final boolean useSourcePathOpt = !jdk11;

        // CONSTRUCT THE COMMAND LINE
        List<String> javacOpts = new ArrayList<String>();

        // Why JavaTest?
        if (useCLASSPATHEnv) {
            javacOpts.add("CLASSPATH=" + script.getJavaTestClassPath() + PATHSEP + script.testClassPath());
        }

        javacOpts.add(script.getJavacProg());

        javacOpts.addAll(script.getTestToolVMOptions());

        javacOpts.addAll(script.getTestCompilerOptions());

        if (destDir != null) {
            javacOpts.add("-d");
            javacOpts.add(destDir.toString());
        }

        // JavaTest added, to match CLASSPATH, but not sure why JavaTest required at all
        if (!classpathp && useClassPathOpt) {
            javacOpts.add("-classpath");
            javacOpts.add(script.getJavaTestClassPath() + PATHSEP + script.testClassPath());
        }

        if (!sourcepathp && useSourcePathOpt) {
            javacOpts.add("-sourcepath");
            javacOpts.add(script.testSourcePath());
        }

        // Set test.src and test.classes for the benefit of annotation processors
        javacOpts.add("-J-Dtest.src=" + script.absTestSrcDir());
        javacOpts.add("-J-Dtest.classes=" + script.absTestClsDir());
        javacOpts.add("-J-Dtest.vm.opts=" + StringUtils.join(script.getTestVMOptions(), " "));
        javacOpts.add("-J-Dtest.tool.vm.opts=" + StringUtils.join(script.getTestToolVMOptions(), " "));
        javacOpts.add("-J-Dtest.compiler.opts=" + StringUtils.join(script.getTestCompilerOptions(), " "));
        javacOpts.add("-J-Dtest.java.opts" + StringUtils.join(script.getTestJavaOptions(), " "));

        String[] envVars = script.getEnvVars();
        String[] jcOpts = javacOpts.toArray(new String[javacOpts.size()]);
        String[] cmdArgs = StringArray.append(envVars, jcOpts);
        cmdArgs = StringArray.append(cmdArgs, args);

        if (showCmd)
            JTCmd("compile", cmdArgs, section);

        // PASS TO PROCESSCOMMAND
        StringWriter outSW = new StringWriter();
        StringWriter errSW = new StringWriter();
        try {
            ProcessCommand cmd = new ProcessCommand();
            cmd.setExecDir(script.absTestScratchDir());

            if (timeout > 0)
                script.setAlarm(timeout*1000);

            status = cmd.run(cmdArgs, new PrintWriter(errSW), new  PrintWriter(outSW));
        } finally {
            script.setAlarm(0);
        }

        // EVALUATE THE RESULTS
        boolean ok = status.isPassed();
        int st   = status.getType();
        String sr;
        if (ok && reverseStatus) {
            sr = COMPILE_PASS_UNEXPECT;
            st = Status.FAILED;
        } else if (ok && !reverseStatus) {
            sr = COMPILE_PASS;
        } else if (!ok && reverseStatus) {
            sr = COMPILE_FAIL_EXPECT;
            st = Status.PASSED;
        } else { /* !ok && !reverseStatus */
            sr = COMPILE_FAIL;
        }
        status = new Status(st, sr);

        String outString = outSW.toString();
        String errString = errSW.toString();
        PrintWriter sysOut = section.createOutput("System.out");
        PrintWriter sysErr = section.createOutput("System.err");
        try {
            sysOut.write(outString);
            sysErr.write(errString);

            // COMPARE OUTPUT TO GOLDENFILE IF REQUIRED
            // tag-spec says that "standard error is redirected to standard out
            // so that /ref can be used."  Simulate this by concatenating streams.

            try {
                if ((ref != null) && (status.getType() == Status.PASSED)) {
                    File refFile = new File(script.absTestSrcDir(), ref);
                    BufferedReader r1 = new BufferedReader(new StringReader(outString + errString));
                    BufferedReader r2 = new BufferedReader(new FileReader(refFile));
                    int lineNum;
                    if ((lineNum = compareGoldenFile(r1, r2)) != 0)
                        status = Status.failed(COMPILE_GOLD_FAIL + ref +
                                COMPILE_GOLD_LINE + lineNum);
                }
            } catch (FileNotFoundException e) {
                File refFile = new File(script.absTestSrcDir(), ref);
                throw new TestRunException(COMPILE_CANT_FIND_REF + refFile);
            }
        } finally {
            if (sysOut != null) sysOut.close();
            if (sysErr != null) sysErr.close();
        }

        return status;
    } // runOtherJVM()

    private static Hashtable<?,?> savedSystemProperties;

    private Status runSameJVM() throws TestRunException {
        // TAG-SPEC:  "The source and class directories of a test are made
        // available to main and applet actions via the system properties
        // "test.src" and "test.classes", respectively"
        synchronized(this) {
            SecurityManager sc = System.getSecurityManager();
            if (sc instanceof RegressionSecurityManager) {
                ((RegressionSecurityManager) sc).setAllowPropertiesAccess(true);
                Properties p = System.getProperties();
                if (savedSystemProperties == null)
                    savedSystemProperties = copyProperties(p);
                p.put("test.src", script.absTestSrcDir().getPath());
                p.put("test.classes", script.absTestClsDir().getPath());
                p.put("test.vm.opts", StringUtils.join(script.getTestVMOptions(), " "));
                p.put("test.tool.vm.opts", StringUtils.join(script.getTestToolVMOptions(), " "));
                p.put("test.compiler.opts", StringUtils.join(script.getTestCompilerOptions(), " "));
                p.put("test.java.opts", StringUtils.join(script.getTestJavaOptions(), " "));
                System.setProperties(p);
                //((RegressionSecurityManager) sc).setAllowPropertiesAccess(false);
                ((RegressionSecurityManager) sc).resetPropertiesAccessed();
            } else {
                // XXX Commented out for the ChameleonTestFinderTest to succeed.
                //return Status.error(MAIN_SECMGR_BAD);
            }
        }

        Status status;

        // CONSTRUCT THE COMMAND LINE
        List<String> javacOpts = new ArrayList<String>();

        javacOpts.addAll(script.getTestCompilerOptions());

        if (destDir != null) {
            javacOpts.add("-d");
            javacOpts.add(destDir.toString());
        }

        if (!classpathp) {
            javacOpts.add("-classpath");
            javacOpts.add(script.testClassPath());
        }

        if (!sourcepathp) { // must be JDK1.4 or greater, to even run JavaTest 3
            javacOpts.add("-sourcepath");
            javacOpts.add(script.testSourcePath());
        }

        String[] jcOpts = javacOpts.toArray(new String[javacOpts.size()]);
        String[] cmdArgs = StringArray.append(jcOpts, args);

        if (showCmd)
            JTCmd("compile", cmdArgs, section);

        // RUN THE COMPILER

        // for direct use with JavaCompileCommand
        StringWriter outSW = new StringWriter();
        StringWriter errSW = new StringWriter();
        PrintWriter outPW = new PrintWriter(outSW);
        PrintWriter errPW = new PrintWriter(errSW);

        // to catch sysout and syserr
        ByteArrayOutputStream outOS = new ByteArrayOutputStream();
        ByteArrayOutputStream errOS = new ByteArrayOutputStream();
        PrintStream outPS = new PrintStream(outOS);
        PrintStream errPS = new PrintStream(errOS);

        PrintStream saveOut = System.out;
        PrintStream saveErr = System.err;

        try {
            Status stat = redirectOutput(outPS, errPS);
            if (!stat.isPassed())
                return stat;

            JavaCompileCommand jcc = new JavaCompileCommand();
            if (timeout > 0)
                script.setAlarm(timeout*1000);

            status = jcc.run(cmdArgs, errPW, outPW);
        } finally {
            SecurityManager sm = System.getSecurityManager();
            if (sm instanceof RegressionSecurityManager) {
                RegressionSecurityManager rsm = (RegressionSecurityManager) sm;
                if (rsm.isPropertiesAccessed()) {
                    System.setProperties(newProperties(savedSystemProperties));
                }
                rsm.setAllowPropertiesAccess(false);
            }

            Status stat = redirectOutput(saveOut, saveErr);
            if (!stat.isPassed())
                return stat;

            script.setAlarm(0);
        }

        outPW.close();
        errPW.close();
        outPS.close();
        errPS.close();

        String outString = outSW.toString();
        String errString = errSW.toString();
        String stdoutString = outOS.toString();
        String stderrString = errOS.toString();

        if (outString.length() > 0) {
            PrintWriter pw = section.createOutput("direct");
            pw.write(outString);
            pw.close();
        }

        if (errString.length() > 0) {
            // should never happen -- only if JavaCompilerCommand kicked into verbose mode
            PrintWriter pw = section.createOutput("direct.log");
            pw.write(outString);
            pw.close();
        }

        if (stdoutString.length() > 0 || stderrString.length() > 0) {
            // should never happen -- only if somehow using JDK 1.3 (but JavaTest assumes 1.4.2+)
            PrintWriter pwOut = section.createOutput("System.out");
            pwOut.write(stdoutString);
            pwOut.close();
            PrintWriter pwErr = section.createOutput("System.err");
            pwErr.write(stderrString);
            pwErr.close();
        }

        // XXX remember to comment out!
//      System.out.println("compile command:");
//      for (int i = 0; i < cmdArgs.length; i++)
//          System.out.print("  " + cmdArgs[i]);
//      System.out.println();
        // EVALUATE THE RESULTS
        boolean ok = status.isPassed();
        int st;
        String sr;
        if (ok && reverseStatus) {
            sr = COMPILE_PASS_UNEXPECT;
            st   = Status.FAILED;
        } else if (ok && !reverseStatus) {
            sr = COMPILE_PASS;
            st   = Status.PASSED;
        } else if (!ok && reverseStatus) {
            sr = COMPILE_FAIL_EXPECT;
            st   = Status.PASSED;
        } else { /* !ok && !reverseStatus */
            sr = COMPILE_FAIL + ": " + status;
            st   = Status.FAILED;
        }
        status = new Status(st, sr);

        // COMPARE OUTPUT TO GOLDENFILE IF REQUIRED
        // tag-spec says that "standard error is redirected to standard out
        // so that /ref can be used."  Simulate this by concatenating streams.

        try {
            if ((ref != null) && (status.getType() == Status.PASSED)) {
                File refFile = new File(script.absTestSrcDir(), ref);
                String refName = refFile.getPath();
                BufferedReader r1 = new BufferedReader(new StringReader(outString + errString + stdoutString + stderrString));
                BufferedReader r2 = new BufferedReader(new FileReader(refName));
                int lineNum;
                if ((lineNum = compareGoldenFile(r1, r2)) != 0)
                    status = Status.failed(COMPILE_GOLD_FAIL + ref +
                            COMPILE_GOLD_LINE + lineNum);
            }
        } catch (FileNotFoundException e) {
            File refFile = new File(script.absTestSrcDir(), ref);
            throw new TestRunException(COMPILE_CANT_FIND_REF + refFile);
        }

        return status;
    } // runSameJVM()

    //----------internal methods------------------------------------------------

    /**
     * This method parses the <em>ref</em> action option used by the compile
     * action. It verifies that the indicated reference file exists in the
     * directory containing the defining file of the test.
     *
     * @param value The proposed filename for the reference file.
     * @return     A string indicating the name of the reference file for the
     *             test.
     * @exception  ParseException If the passed filename is null, the empty
     *             string, or does not exist.
     */
    private String parseRef(String value) throws ParseException {
        if ((value == null) || (value.equals("")))
            throw new ParseException(COMPILE_NO_REF_NAME);
        File refFile = new File(script.absTestSrcDir(), value);
        if (!refFile.exists())
            throw new ParseException(COMPILE_CANT_FIND_REF + refFile);
        return value;
    } // parseRef()

    /**
     * This method returns a new path which is the appropriate append of the old
     * and new paths.  The new path will have a trailing
     * <code>File.separatorChar</code> only if the original path had one.
     *
     * @param oldPath The original path.
     * @param path The path to append.
     * @return A string containing the new and improved path.
     *
     */
    private String addPath(String oldPath, String path) {
        String newPath;
        if (oldPath.endsWith(PATHSEP)) {
            if (path.endsWith(PATHSEP))
                newPath = oldPath + path;
            else
                newPath = oldPath + path + PATHSEP;
        } else {
            if (path.endsWith(PATHSEP))
                newPath = oldPath + PATHSEP + path.substring(0, path.length() - 1);
            else
                newPath = oldPath + PATHSEP + path;
        }
        return newPath;
    } // addPath()

    /**
     * Line by line comparison of compile output and a reference file.  If no
     * differences are found, then 0 is returned.  Otherwise, the line number
     * where differences are first detected is returned.
     *
     * @param r1   The first item for comparison.
     * @param r2   The second item for comparison.
     * @return 0   If no differences are returned.  Otherwise, the line number
     *             where differences were first detected.
     */
    private int compareGoldenFile(BufferedReader r1, BufferedReader r2)
    throws TestRunException {
        try {
            int lineNum = 0;
            for (;;) {
                String s1 = r1.readLine();
                String s2 = r2.readLine();
                lineNum++;

                if ((s1 == null) && (s2 == null))
                    return 0;
                if ((s1 == null) || (s2 == null) || !s1.equals(s2)) {
                    return lineNum;
                }
            }
        } catch (IOException e) {
            File refFile = new File(script.absTestSrcDir(), ref);
            throw new TestRunException(COMPILE_GOLD_READ_PROB + refFile);
        }
    } // compareGoldenFile()

    //----------member variables------------------------------------------------

    private String[] args;
    private File destDir;

    private boolean reverseStatus = false;
    private String  ref = null;
    private int     timeout = -1;
    private boolean classpathp  = false;
    private boolean sourcepathp = false;
    private boolean process = false;

    private TestResult.Section section;
}
