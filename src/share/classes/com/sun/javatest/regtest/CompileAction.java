/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.sun.javatest.Status;

import static com.sun.javatest.regtest.RStatus.*;

/**
 * This class implements the "compile" action as described by the JDK tag
 * specification.
 *
 * @author Iris A Garcia
 * @see Action
 */
public class CompileAction extends Action {
    public static final String NAME = "compile";

    /**
     * {@inheritdoc}
     * @return "compile"
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * A method used by sibling classes to run both the init() and run()
     * method of CompileAction.
     *
     * @param destDir Where to place the compiled classes
     * @param opts The options for the action.
     * @param args The arguments for the actions.
     * @param reason Indication of why this action was invoked.
     * @param script The script.
     * @return     The result of the action.
     * @throws TestRunException if an error occurs while executing this action
     * @see #init
     * @see #run
     */
    public Status compile(File destDir, String[][] opts, String[] args, String reason,
            RegressionScript script) throws TestRunException {
        this.destDir = destDir;
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
    @Override
    public void init(String[][] opts, String[] args, String reason,
                RegressionScript script)
            throws ParseException {
        super.init(opts, args, reason, script);

        if (args.length == 0)
            throw new ParseException(COMPILE_NO_CLASSNAME);

        for (String[] opt : opts) {
            String optName = opt[0];
            String optValue = opt[1];

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
            Locations locations = script.locations;
            if (destDir == null)
                destDir = locations.absTestClsDir();
            if (!script.isCheck())
                mkdirs(destDir);

            boolean foundJavaFile = false;
            boolean foundAsmFile = false;

            for (int i = 0; i < args.length; i++) {
                String currArg = args[i];

                if (currArg.endsWith(".java")) {
                    foundJavaFile = true;
                    File sourceFile = new File(currArg.replace('/', File.separatorChar));
                    if (!sourceFile.isAbsolute()) {
                        // User must have used @compile, so file must be
                        // in the same directory as the defining file.
                        File absSourceFile = locations.absTestSrcFile(sourceFile);
                        if (!absSourceFile.exists())
                            throw new RegressionScript.TestClassException(CANT_FIND_SRC + currArg);
                        args[i] = absSourceFile.getPath();
                    }
                } else if (currArg.endsWith(".jasm") || currArg.endsWith("jcod")) {
                    foundAsmFile = true;
                    File sourceFile = new File(currArg.replace('/', File.separatorChar));
                    if (!sourceFile.isAbsolute()) {
                        // User must have used @compile, so file must be
                        // in the same directory as the defining file.
                        File absSourceFile = locations.absTestSrcFile(sourceFile);
                        if (!absSourceFile.exists())
                            throw new RegressionScript.TestClassException(CANT_FIND_SRC + currArg);
                        args[i] = absSourceFile.getPath();
                    }
                }

                if (currArg.equals("-classpath") || currArg.equals("-cp")) {
                    classpathp = true;
                    // assume the next element provides the classpath, add
                    // test.classes and test.src and lib-list to it
                    SearchPath p = new SearchPath(args[i+1]).append(script.getCompileClassPath());
                    args[i+1] = p.toString();
                }

                if (currArg.equals("-d")) {
                    throw new ParseException(COMPILE_OPT_DISALLOW);
                }

                // note that -sourcepath is only valid for JDK1.2 and beyond
                if (currArg.equals("-sourcepath")) {
                    sourcepathp = true;
                    // assume the next element provides the sourcepath, add test.src
                    // and lib-list to it
                    SearchPath p = new SearchPath(args[i+1]).append(script.getCompileSourcePath());
                    args[i+1] = p.toString();
                }
            }

            if (!foundJavaFile && !process && !foundAsmFile) {
                throw new ParseException(COMPILE_NO_DOT_JAVA);
            }
            if (foundAsmFile) {
                if (sourcepathp || classpathp || process) {
                    throw new ParseException(COMPILE_OPT_DISALLOW);
                }
                if (reverseStatus || ref != null) {
                    throw new ParseException(COMPILE_OPT_DISALLOW);
                }
            }
        } catch (RegressionScript.TestClassException e) {
            throw new ParseException(e.getMessage());
        }
    } // init()

    @Override
    public Set<File> getSourceFiles() {
        Set<File> files = new LinkedHashSet<File>();

        for (String currArg : args) {
            if (currArg.endsWith(".java")
                    || currArg.endsWith(".jasm")
                    || currArg.endsWith(".jcod")) {
                files.add(new File(currArg));
            }
        }

        return files;
    }

    /**
     * The method that does the work of the action.  The necessary work for the
     * given action is defined by the tag specification.
     *
     * Invoke the compiler on the given arguments which may possibly include
     * compiler options.  Equivalent to "javac arg+".
     *
     * Each named class will be compiled if its corresponding class file doesn't
     * exist or is older than its source file.  The class name is fully
     * qualified as necessary and the ".java" extension is added before
     * compilation.
     *
     * Build is allowed to search anywhere in the library-list.  Compile is
     * allowed to search only in the directory containing the defining file of
     * the test.  Thus, compile will always make files absolute by adding the
     * directory path of the defining file to the passed filename.
     * Build must pass an absolute filename to handle files found in the
     * library-list.
     *
     * @return  The result of the action.
     * @throws  TestRunException If an unexpected error occurs while executing
     *          the action.
     */
    public Status run() throws TestRunException {
        startAction();

        List<String> javacArgs = new ArrayList<String>();
        List<String> jasmArgs = new ArrayList<String>();
        List<String> jcodArgs = new ArrayList<String>();

        for (String currArg : args) {
            if (currArg.endsWith(".java")) {
                if (!(new File(currArg)).exists())
                    throw new TestRunException(CANT_FIND_SRC + currArg);
                javacArgs.add(currArg);
            } else if (currArg.endsWith(".jasm")) {
                jasmArgs.add(currArg);
            } else if (currArg.endsWith(".jcod")) {
                jcodArgs.add(currArg);
            } else
                javacArgs.add(currArg);
        }

        Status status;

        if (script.isCheck()) {
            status = passed(CHECK_PASS);
        } else {
            // run jasm and jcod first (if needed) in case the resulting class
            // files will be required when compiling the .java files.
            status = jasm(jasmArgs);
            if (status.isPassed())
                status = jcod(jcodArgs);
            if (status.isPassed() && !javacArgs.isEmpty()) {
                switch (script.getExecMode()) {
                    case AGENTVM:
                        status = runAgentJVM(javacArgs);
                        break;
                    case OTHERVM:
                        status = runOtherJVM(javacArgs);
                        break;
                    case SAMEVM:
                        status = runSameJVM(javacArgs);
                        break;
                    default:
                        throw new AssertionError();
                }
            }
        }

        endAction(status);
        return status;
    } // run()

    //----------internal methods------------------------------------------------

    private Status jasm(List<String> files) {
        return asmtools("jasm", files);
    }

    private Status jcod(List<String> files) {
        return asmtools("jcoder", files);
    }

    private Status asmtools(String toolName, List<String> files) {
        if (files.isEmpty())
            return Status.passed(toolName + ": no files");

        List<String> toolArgs = new ArrayList<String>();
        toolArgs.add("-d");
        toolArgs.add(destDir.getPath());
        toolArgs.addAll(files);
        try {
            String toolClassName = "org.openjdk.asmtools." + toolName + ".Main";
            Class<?> toolClass = Class.forName(toolClassName);
            Constructor c = toolClass.getConstructor(new Class[] { PrintStream.class, String.class });
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            try {
                Object tool = c.newInstance(ps, toolName);
                Method m = toolClass.getMethod("compile", new Class[] { String[].class });
                Object r = m.invoke(tool, new Object[] { toolArgs.toArray(new String[0]) });
                if (r instanceof Boolean) {
                    boolean ok = (Boolean) r;
                    return ok ? Status.passed(toolName + " OK") : Status.failed(toolName + " failed");
                } else
                    return Status.error("unexpected result from " + toolName + ": " + r.toString());
            } finally {
                PrintWriter out = section.createOutput(toolName);
                out.write(baos.toString());
                out.close();
            }
        } catch (ClassNotFoundException e) {
            return Status.error("can't find " + toolName);
//        } catch (ReflectiveOperationException e) {
//            return Status.error("error invoking " + toolName + ": " + e);
//        }
        } catch (NoSuchMethodException e) {
            return Status.error("error invoking " + toolName + ": " + e);
        } catch (InstantiationException e) {
            return Status.error("error invoking " + toolName + ": " + e);
        } catch (IllegalAccessException e) {
            return Status.error("error invoking " + toolName + ": " + e);
        } catch (InvocationTargetException e) {
            return Status.error("error invoking " + toolName + ": " + e);
        } catch (IllegalArgumentException t) {
            return Status.error("error invoking " + toolName + ": " + t);
        } catch (SecurityException t) {
            return Status.error("error invoking " + toolName + ": " + t);
        }
    }

    private Status runOtherJVM(List<String> args) throws TestRunException {
        Status status;

        // WRITE ARGUMENT FILE
        File compileArgFile;
        if (args.size() < 10)
            compileArgFile = null;
        else {
            script.absTestClsDir().mkdirs();
            String baseName = new File(script.getTestResult().getWorkRelativePath())
                    .getName().replace(".jtr", ".compile.");
            //compileArgFile = File.createTempFile(baseName, RegressionScript.WRAPPEREXTN, script.absTestClsDir());
            compileArgFile = new File(script.absTestClsDir(), baseName + script.getNextSerial() + RegressionScript.WRAPPEREXTN);
            BufferedWriter w;
            try {
                w = new BufferedWriter(new FileWriter(compileArgFile));
                for (String arg: args) {
                    w.write(arg);
                    w.newLine();
                }
                w.close();
            } catch (IOException e) {
                return error(COMPILE_CANT_WRITE_ARGS);
            } catch (SecurityException e) {
                // shouldn't happen since JavaTestSecurityManager allows file ops
                return error(COMPILE_SECMGR_FILEOPS);
            }
        }

        // Set test.src and test.classes for the benefit of annotation processors
        Map<String, String> javacProps = script.getTestProperties();

        // CONSTRUCT THE COMMAND LINE
        Map<String, String> envArgs = new LinkedHashMap<String, String>();
        envArgs.putAll(script.getEnvVars());

        // Why JavaTest?
        SearchPath cp = new SearchPath(script.getJavaTestClassPath(), script.getCompileClassPath());

        String javacCmd = script.getJavacProg();

        List<String> javacVMOpts = new ArrayList<String>();
        javacVMOpts.addAll(script.getTestVMOptions());

        List<String> javacArgs = new ArrayList<String>();
        javacArgs.addAll(script.getTestCompilerOptions());

        if (destDir != null) {
            javacArgs.add("-d");
            javacArgs.add(destDir.toString());
        }

        // JavaTest added, to match CLASSPATH, but not sure why JavaTest required at all
        if (!classpathp) {
            javacArgs.add("-classpath");
            javacArgs.add(cp.toString());
        }

        if (!sourcepathp) {
            javacArgs.add("-sourcepath");
            javacArgs.add(script.getCompileSourcePath().toString());
        }

        if (compileArgFile != null) {
            javacArgs.add("@" + compileArgFile);
        } else {
            javacArgs.addAll(args);
        }

        List<String> command = new ArrayList<String>();
        command.add(javacCmd);
        for (String opt: javacVMOpts)
            command.add("-J" + opt);
        for (Map.Entry<String,String> e: javacProps.entrySet())
            command.add("-J-D" + e.getKey() + "=" + e.getValue());
        command.addAll(javacArgs);

        if (showMode)
            showMode("compile", ExecMode.OTHERVM, section);
        if (showCmd)
            showCmd("compile", command, section);

        recorder.javac(envArgs, javacCmd, javacVMOpts, javacProps, javacArgs);

        // PASS TO PROCESSCOMMAND
        PrintStringWriter stdOut = new PrintStringWriter();
        PrintStringWriter stdErr = new PrintStringWriter();
        ProcessCommand cmd = new ProcessCommand() {
            @Override
            protected Status getStatus(int exitCode, Status logStatus) {
                // logStatus is never used by javac, so ignore it
                JDK.Version v = script.getCompileJDKVersion();
                return getStatusForJavacExitCode(v, exitCode);
            }
        };
        cmd.setExecDir(script.absTestScratchDir());

        TimeoutHandler timeoutHandler =
            TimeoutHandlerProvider.createHandler(script, section);

        String[] cmdArgs = command.toArray(new String[command.size()]);
        status = normalize(cmd.exec(cmdArgs, envArgs, stdOut, stdErr,
                                    (long) timeout * 1000, timeoutHandler));

        PrintWriter sysOut = section.createOutput("System.out");
        sysOut.write(stdOut.getOutput());
        sysOut.close();

        PrintWriter sysErr = section.createOutput("System.err");
        sysErr.write(stdErr.getOutput());
        sysErr.close();

        // EVALUATE THE RESULTS
        status = checkReverse(status, reverseStatus);

        // COMPARE OUTPUT TO GOLDENFILE IF REQUIRED
        // tag-spec says that "standard error is redirected to standard out
        // so that /ref can be used."  Simulate this by concatenating streams.
        if ((ref != null) && status.isPassed()) {
            String combined = stdOut.getOutput() + stdErr.getOutput();
            status = checkGoldenFile(combined, status);
        }

        return status;
    } // runOtherJVM()


    private Status runSameJVM(List<String> args) throws TestRunException {
        // TAG-SPEC:  "The source and class directories of a test are made
        // available to main and applet actions via the system properties
        // "test.src" and "test.classes", respectively"
        Map<String, String> javacProps = script.getTestProperties();

        // CONSTRUCT THE COMMAND LINE
        List<String> javacArgs = new ArrayList<String>();

        javacArgs.addAll(script.getTestCompilerOptions());

        if (destDir != null) {
            javacArgs.add("-d");
            javacArgs.add(destDir.toString());
        }

        if (!classpathp) {
            javacArgs.add("-classpath");
            javacArgs.add(script.getCompileClassPath().toString());
        }

        if (!sourcepathp) { // must be JDK1.4 or greater, to even run JavaTest 3
            javacArgs.add("-sourcepath");
            javacArgs.add(script.getCompileSourcePath().toString());
        }

        javacArgs.addAll(args);

        if (showMode)
            showMode("compile", ExecMode.SAMEVM, section);
        if (showCmd)
            showCmd("compile", javacArgs, section);

        String javacProg = script.getJavacProg();
        List<String> javacVMOpts = script.getTestVMJavaOptions();
        recorder.javac(script.getEnvVars(), javacProg, javacVMOpts, javacProps, javacArgs);

        Status status = runCompile(
                script.getTestResult().getTestName(),
                javacProps,
                javacArgs,
                timeout,
                getOutputHandler(section));

        // EVALUATE THE RESULTS
        status = checkReverse(status, reverseStatus);

        // COMPARE OUTPUT TO GOLDENFILE IF REQUIRED
        // tag-spec says that "standard error is redirected to standard out
        // so that /ref can be used."  Simulate this by concatenating streams.
        if ((ref != null) && status.isPassed()) {
            String outString = getOutput(OutputHandler.OutputKind.DIRECT);
            String errString = getOutput(OutputHandler.OutputKind.DIRECT_LOG);
            String stdOutString = getOutput(OutputHandler.OutputKind.STDOUT);
            String stdErrString = getOutput(OutputHandler.OutputKind.STDERR);
            String combined = (outString + errString + stdOutString + stdErrString);
            status = checkGoldenFile(combined, status);
        }

        return status;
    } // runSameJVM()


    private Status runAgentJVM(List<String> args) throws TestRunException {
        // TAG-SPEC:  "The source and class directories of a test are made
        // available to main and applet actions via the system properties
        // "test.src" and "test.classes", respectively"
        Map<String, String> javacProps = script.getTestProperties();

        // CONSTRUCT THE COMMAND LINE
        List<String> javacArgs = new ArrayList<String>();

        javacArgs.addAll(script.getTestCompilerOptions());

        if (destDir != null) {
            javacArgs.add("-d");
            javacArgs.add(destDir.toString());
        }

        if (!classpathp) {
            javacArgs.add("-classpath");
            javacArgs.add(script.getCompileClassPath().toString());
        }

        if (!sourcepathp) { // must be JDK1.4 or greater, to even run JavaTest 3
            javacArgs.add("-sourcepath");
            javacArgs.add(script.getCompileSourcePath().toString());
        }

        javacArgs.addAll(args);

        if (showMode)
            showMode("compile", ExecMode.AGENTVM, section);
        if (showCmd)
            showCmd("compile", javacArgs, section);

        String javacProg = script.getJavacProg();
        List<String> javacVMOpts = script.getTestVMJavaOptions();
        recorder.javac(script.getEnvVars(), javacProg, javacVMOpts, javacProps, javacArgs);

        Agent agent;
        try {
            JDK jdk = script.getCompileJDK();
            SearchPath classpath = new SearchPath(script.getJavaTestClassPath(), jdk.getJDKClassPath());
            agent = script.getAgent(jdk, classpath, script.getTestVMJavaOptions());
        } catch (IOException e) {
            return error(AGENTVM_CANT_GET_VM + ": " + e);
        }

        TimeoutHandler timeoutHandler =
            TimeoutHandlerProvider.createHandler(script, section);

        Status status;
        try {
            status = agent.doCompileAction(
                    script.getTestResult().getTestName(),
                    javacProps,
                    javacArgs,
                    timeout,
                    timeoutHandler,
                    section);
        } catch (Agent.Fault e) {
            if (e.getCause() instanceof IOException)
                status = error(String.format(AGENTVM_IO_EXCEPTION, e.getCause()));
            else
                status = error(String.format(AGENTVM_EXCEPTION, e.getCause()));
        }
        if (status.isError()) {
            script.closeAgent(agent);
        }

        // EVALUATE THE RESULTS
        status = checkReverse(status, reverseStatus);

        // COMPARE OUTPUT TO GOLDENFILE IF REQUIRED
        // tag-spec says that "standard error is redirected to standard out
        // so that /ref can be used."  Simulate this by concatenating streams.
        if ((ref != null) && status.isPassed()) {
            String outString = getOutput(OutputHandler.OutputKind.DIRECT);
            String errString = getOutput(OutputHandler.OutputKind.DIRECT_LOG);
            String stdOutString = getOutput(OutputHandler.OutputKind.STDOUT);
            String stdErrString = getOutput(OutputHandler.OutputKind.STDERR);
            String combined = (outString + errString + stdOutString + stdErrString);
            status = checkGoldenFile(combined, status);
        }

        return status;
    } // runAgentJVM()

    private String getOutput(OutputHandler.OutputKind kind) {
        String s = section.getOutput(kind.name);
        return (s == null) ? "" : s;
    }

    static Status runCompile(String testName,
            Map<String, String> props,
            List<String> cmdArgs,
            int timeout,
            OutputHandler outputHandler) {
        SaveState saved = new SaveState();

        Properties p = System.getProperties();
        for (Map.Entry<String, String> e: props.entrySet()) {
            String name = e.getKey();
            String value = e.getValue();
            if (name.equals("test.class.path.prefix")) {
                SearchPath cp = new SearchPath(value, System.getProperty("java.class.path"));
                p.put("java.class.path", cp.toString());
            } else {
                p.put(e.getKey(), e.getValue());
            }
        }
        System.setProperties(p);

        // RUN THE COMPILER

        // Setup streams for the test

        // to catch sysout and syserr
        PrintByteArrayOutputStream sysOut = new PrintByteArrayOutputStream();
        PrintByteArrayOutputStream sysErr = new PrintByteArrayOutputStream();

        // for direct use with RegressionCompileCommand
        PrintStringWriter out = new PrintStringWriter();
        PrintStringWriter err = new PrintStringWriter();

        Status status = error("");
        try {
            Status stat = redirectOutput(sysOut, sysErr);
            if (!stat.isPassed())
                return stat;

            Alarm alarm = null;
            if (timeout > 0) {
                PrintWriter alarmOut = outputHandler.createOutput(OutputHandler.OutputKind.LOG);
                alarm = new Alarm(timeout * 1000, Thread.currentThread(), testName, alarmOut);
            }
            try {
                RegressionCompileCommand jcc = new RegressionCompileCommand() {
                    @Override
                    protected Status getStatus(int exitCode) {
                        JDK.Version v = JDK.Version.forThisJVM();
                        return getStatusForJavacExitCode(v, exitCode);
                    }
                };
                String[] c = cmdArgs.toArray(new String[cmdArgs.size()]);
                status = normalize(jcc.run(c, err, out));
            } finally {
                if (alarm != null)
                    alarm.cancel();
            }

        } finally {
            status = saved.restore(testName, status);
        }

        out.close();
        String outOutput = out.getOutput();
        if (outOutput.length() > 0) {
            outputHandler.createOutput(OutputHandler.OutputKind.DIRECT, outOutput);
        }

        err.close();
        String errOutput = err.getOutput();
        if (errOutput.length() > 0) {
            // should never happen -- only if JavaCompilerCommand kicked into verbose mode
            outputHandler.createOutput(OutputHandler.OutputKind.DIRECT_LOG, errOutput);
        }

        sysOut.close();
        String sysOutOutput = sysOut.getOutput();
        sysErr.close();
        String sysErrOutput = sysErr.getOutput();

        if (sysOutOutput.length() > 0 || sysErrOutput.length() > 0) {
            // should never happen -- only if somehow using JDK 1.3 (but JavaTest assumes 1.4.2+)
            outputHandler.createOutput(OutputHandler.OutputKind.STDOUT, sysOutOutput);
            outputHandler.createOutput(OutputHandler.OutputKind.STDERR, sysErrOutput);
        }

        return status;
    }

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

    static Status getStatusForJavacExitCode(JDK.Version v, int exitCode) {
        if (v == null || v.compareTo(JDK.Version.V1_6) < 0)
            return (exitCode == 0 ? passed : failed);

        // The following exit codes are standard in JDK 6 or later
        switch (exitCode) {
            case 0:  return passed;
            case 1:  return failed;
            case 2:  return error("command line error (exit code 2)");
            case 3:  return error("system error (exit code 3)");
            case 4:  return error("compiler crashed (exit code 4)");
            default: return error("unexpected exit code from javac: " + exitCode);
        }
    }

    private static final Status passed = passed("Compilation successful");
    private static final Status failed = failed("Compilation failed");

    private Status checkReverse(Status status, boolean reverseStatus) {
        if (!status.isError()) {
            boolean ok = status.isPassed();
            int st = status.getType();
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
            if ((st == Status.FAILED) && ! (status.getReason() == null) &&
                    !status.getReason().equals(EXEC_PASS))
                sr += ": " + status.getReason();
            status = createStatus(st, sr);
        }
        return status;
    }

    /**
     * Compare output against a reference file.
     * @param status default result if no differences found
     * @param actual the text to be compared against the reference file
     * @return a status indicating the first difference, or the default status
     *          if no differences found
     * @throws TestRunException if the reference file can't be found
     */
    private Status checkGoldenFile(String actual, Status status) throws TestRunException {
        File refFile = new File(script.absTestSrcDir(), ref);
        try {
            BufferedReader r1 = new BufferedReader(new StringReader(actual));
            BufferedReader r2 = new BufferedReader(new FileReader(refFile));
            int lineNum;
            if ((lineNum = compareGoldenFile(r1, r2)) != 0) {
                return failed(COMPILE_GOLD_FAIL + ref +
                        COMPILE_GOLD_LINE + lineNum);
            }
            return status;
        } catch (FileNotFoundException e) {
            throw new TestRunException(COMPILE_CANT_FIND_REF + refFile);
        }
    }

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
            for ( ; ; ) {
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

    private File destDir;

    private boolean reverseStatus = false;
    private String  ref = null;
    private int     timeout = -1;
    private boolean classpathp  = false;
    private boolean sourcepathp = false;
    private boolean process = false;
}
