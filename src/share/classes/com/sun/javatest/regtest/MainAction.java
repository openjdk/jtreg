/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.sun.javatest.Status;
import com.sun.javatest.TestResult;
import com.sun.javatest.lib.ProcessCommand;

/**
 * This class implements the "main" action as described by the JDK tag
 * specification.
 *
 * @author Iris A Garcia
 * @see Action
 */
public class MainAction extends Action
{
    /** Marker interface for test driver classes, which need to be passed a
     *  class loader to load the classes for the test.
     *  @see JUnitAction.JUnitRunner
     */
    interface TestRunner { }

    /**
     * This method does initial processing of the options and arguments for the
     * action.  Processing is determined by the requirements of run().
     *
     * Verify arguments are not of length 0 and separate them into the options
     * to java, the classname, and the parameters to the named class.
     *
     * Verify that the options are valid for the "main" action.
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
        throws ParseException
    {
        init(opts, args, reason, script, null);
    }

    /**
     * Local version of public init function.
     * Supports extra driverClass option, to interpose before main class.
     * @param driverClass actual class to invoke, with main class as first argument
     */
    void init(String[][] opts, String[] args, String reason,
                     RegressionScript script,
                     String driverClass)
        throws ParseException
    {
        this.script = script;
        this.reason = reason;

        if (args.length == 0)
            throw new ParseException(MAIN_NO_CLASSNAME);

        for (int i = 0; i < opts.length; i++) {
            String optName  = opts[i][0];
            String optValue = opts[i][1];

            if (optName.equals("fail")) {
                reverseStatus = parseFail(optValue);
            } else if (optName.equals("manual")) {
                manual = parseMainManual(optValue);
            } else if (optName.equals("timeout")) {
                timeout  = parseTimeout(optValue);
            } else if (optName.equals("othervm")) {
                othervm = true;
            } else if (optName.equals("policy")) {
                if (!script.isTestJDK11())
                    policyFN = parsePolicy(optValue);
                else
                    throw new ParseException(PARSE_BAD_OPT_JDK + optName);
            } else if (optName.equals("secure")) {
                if (!script.isTestJDK11())
                    secureCN = parseSecure(optValue);
                else
                    throw new ParseException(PARSE_BAD_OPT_JDK + optName);
            } else {
                throw new ParseException(MAIN_BAD_OPT + optName);
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

        if (driverClass != null) {
            this.driverClass = driverClass;
        }

        // separate the arguments into the options to java, the
        // classname and the parameters to the named class
        for (int i = 0; i < args.length; i++) {
            if (mainClassName == null) {
                if (args[i].startsWith("-")) {
                    javaArgs.add(args[i]);
                    if ((args[i].equals("-cp") || args[i].equals("-classpath"))
                        && (i+1 < args.length))
                        javaArgs.add(args[++i]);
                } else {
                    mainClassName = args[i];
                }
            } else {
                mainArgs.add(args[i]);
            }
        }

        if (mainClassName == null)
            throw new ParseException(MAIN_NO_CLASSNAME);
        if (!othervm) {
            if (javaArgs.size() > 0)
                throw new ParseException(javaArgs + MAIN_UNEXPECT_VMOPT);
            if (policyFN != null)
                throw new ParseException(PARSE_POLICY_OTHERVM);
            if (secureCN != null)
                throw new ParseException(PARSE_SECURE_OTHERVM);
        }
    } // init()

    public List<String> getJavaArgs() {
        return javaArgs;
    }
    public List<String> getMainArgs() {
        return mainArgs;
    }
    public String getMainClassName() {
        return mainClassName;
    }

    @Override
    public Set<File> getSourceFiles() {
        Set<File> files = new LinkedHashSet<File>();
        if (mainClassName != null) {
            String[][] buildOpts = {};
            String[]   buildArgs = {mainClassName.replace(File.separatorChar, '.')};
            try {
                BuildAction ba = new BuildAction();
                ba.init(buildOpts, buildArgs, SREASON_ASSUMED_BUILD, script);
                files.addAll(ba.getSourceFiles());
            } catch (ParseException ignore) {
            }
        }
        if (policyFN != null)
            files.add(new File(policyFN));
        return files;
    }

    /**
     * The method that does the work of the action.  The necessary work for the
     * given action is defined by the tag specification.
     *
     * Invoke the main method of the specified class, passing any arguments
     * after the class name.  A "main" action is considered to be finished when
     * the main method returns.
     *
     * A "main" action passes if the main method returns normally and does not
     * cause an exception to be thrown by the main or any subsidiary threads.
     * It fails otherwise.
     *
     * If the <em>othervm</em> option is present, this action requires that the
     * JVM support multiple processes.
     *
     * @return     The result of the action.
     * @exception  TestRunException If an unexpected error occurs while running
     *             the test.
     */
    public Status run() throws TestRunException {
        Status status;

        if (!(status = build()).isPassed())
            return status;

        section = startAction(getActionName(), getActionArgs(), reason);

        if (script.isCheck()) {
            status = Status.passed(CHECK_PASS);
        } else {
            Lock lock = script.getLockIfRequired();
            if (lock != null) lock.lock();
            try {
                switch (othervm ? ExecMode.OTHERVM : script.getExecMode()) {
                    case AGENTVM:
                        status = runAgentJVM();
                        break;
                    case OTHERVM:
                        status = runOtherJVM();
                        break;
                    case SAMEVM:
                        status = runSameJVM();
                        break;
                    default:
                        throw new AssertionError();
                }
            } finally {
                if (lock != null) lock.unlock();
            }
        }

        endAction(status, section);
        return status;
    } // run()

    //----------internal methods------------------------------------------------

    protected Status build() throws TestRunException {
        // TAG-SPEC:  "The named <class> will be compiled on demand, just as
        // though an "@run build <class>" action had been inserted before
        // this action."
        String[][] buildOpts = {};
        String[]   buildArgs = {mainClassName.replace(File.separatorChar, '.')};
        BuildAction ba = new BuildAction();
        return ba.build(buildOpts, buildArgs, SREASON_ASSUMED_BUILD, script);
    }

    protected String getActionName() {
        return "main";
    }

    protected String[] getActionArgs() {
        List<String> args = new ArrayList<String>();
        args.addAll(javaArgs);
        args.add(mainClassName);
        args.addAll(mainArgs);
        return args.toArray(new String[args.size()]);
    }

    private Status runOtherJVM() throws TestRunException {
        // Arguments to wrapper:
        String runClassName;
        List<String> runClassArgs;
        if (driverClass == null) {
            runClassName = mainClassName;
            runClassArgs = mainArgs;
        } else {
            runClassName = driverClass;
            runClassArgs = new ArrayList<String>();
            runClassArgs.add(script.getTestResult().getTestName());
            runClassArgs.add(mainClassName);
            runClassArgs.addAll(mainArgs);
        }

        // WRITE ARGUMENT FILE
        File mainArgFile =
            new File(script.absTestClsDir(), mainClassName + RegressionScript.WRAPPEREXTN);
        FileWriter fw;
        try {
            fw = new FileWriter(mainArgFile);
            fw.write(runClassName + "\0");
            fw.write(StringUtils.join(runClassArgs) + "\0" );
            fw.close();
        } catch (IOException e) {
            return Status.error(MAIN_CANT_WRITE_ARGS);
        } catch (SecurityException e) {
            // shouldn't happen since JavaTestSecurityManager allows file ops
            return Status.error(MAIN_SECMGR_FILEOPS);
        }

        // CONSTRUCT THE COMMAND LINE

        // TAG-SPEC:  "The source and class directories of a test are made
        // available to main and applet actions via the system properties
        // "test.src" and "test.classes", respectively"
        List<String> command = new ArrayList<String>(6);

        // some tests are inappropriately relying on the CLASSPATH environment
        // variable being set, so force the use here.
        final boolean useCLASSPATH = true;
        Path cp = new Path(script.getJavaTestClassPath(), script.getTestClassPath());
        if (script.isJUnitRequired())
            cp.append(script.getJUnitJar());
        if (script.isTestNGRequired())
            cp.append(script.getTestNGJar());
        if (useCLASSPATH || script.isTestJDK11()) {
            command.add("CLASSPATH=" + cp);
        }
        command.add(script.getJavaProg());
        if (!useCLASSPATH && !script.isTestJDK11()) {
            command.add("-classpath");
            command.add(cp.toString());
        }

        command.addAll(script.getTestVMJavaOptions());

        for (Map.Entry<String, String> e: script.getTestProperties().entrySet()) {
            command.add("-D" + e.getKey() + "=" + e.getValue());
        }

        String newPolicyFN;
        if (policyFN != null) {
            // add permission to read JTwork/classes by adding a grant entry
            newPolicyFN = addGrantEntry(policyFN);
            command.add("-Djava.security.policy==" + newPolicyFN);
        }

        if (secureCN != null)
            command.add("-Djava.security.manager=" + secureCN);
        else if (policyFN != null)
            command.add("-Djava.security.manager=default");
//      command.addElement("-Djava.security.debug=all");

        command.addAll(javaArgs);

        command.add("com.sun.javatest.regtest.MainWrapper");
        command.add(mainArgFile.getPath());

        command.addAll(runClassArgs);

        String[] envVars = script.getEnvVars();
        String[] tmpCmd = command.toArray(new String[command.size()]);
        String[] cmdArgs = StringArray.join(envVars, tmpCmd);

        // PASS TO PROCESSCOMMAND
        Status status;
        PrintWriter sysOut = section.createOutput("System.out");
        PrintWriter sysErr = section.createOutput("System.err");
        try {
            if (showMode)
                showMode(getActionName(), ExecMode.OTHERVM, section);
            if (showCmd)
                showCmd(getActionName(), cmdArgs, section);
//          for (int i = 0; i < cmdArgs.length; i++)
//              System.out.print(" " + cmdArgs[i]);
//          System.out.println();

            // RUN THE MAIN WRAPPER CLASS
            ProcessCommand cmd = new ProcessCommand();
            cmd.setExecDir(script.absTestScratchDir());

            // Set the exit codes and their associated strings.  Note that we
            // require the use of a non-zero exit code for a passed test so
            // that we have a chance of detecting whether the test itself has
            // illegally called System.exit(0).
            cmd.setStatusForExit(Status.exitCodes[Status.PASSED],
                                 Status.passed(EXEC_PASS));
            cmd.setStatusForExit(Status.exitCodes[Status.FAILED],
                                 Status.failed(EXEC_FAIL));
            cmd.setDefaultStatus(Status.failed(UNEXPECT_SYS_EXIT));

            if (timeout > 0)
                script.setAlarm(timeout*1000);

            status = cmd.run(cmdArgs, sysErr, sysOut);

        } finally {
            script.setAlarm(0);
            sysOut.close();
            sysErr.close();
        }

        // EVALUATE THE RESULTS
        status = checkReverse(status, reverseStatus);

        return status;
    } // runOtherJVM()

    private Status runSameJVM() throws TestRunException {
        Path runClasspath;
        String runMainClass;
        List<String> runMainArgs;
        if (driverClass == null) {
            runClasspath = script.getTestClassPath();
            runMainClass = mainClassName;
            runMainArgs = mainArgs;
        } else {
            runClasspath = script.getTestClassPath();
            runMainClass = driverClass;
            runMainArgs = new ArrayList<String>();
            runMainArgs.add(script.getTestResult().getTestName());
            runMainArgs.add(mainClassName);
            runMainArgs.addAll(mainArgs);
        }

        if (showMode)
            showMode(getActionName(), ExecMode.SAMEVM, section);

        // TAG-SPEC:  "The source and class directories of a test are made
        // available to main and applet actions via the system properties
        // "test.src" and "test.classes", respectively"
        Map<String, String> p = script.getTestProperties();

        // delegate actual work to shared method
        Status status = runClass(
                script.getTestResult().getTestName(),
                p,
                runClasspath,
                runMainClass,
                runMainArgs.toArray(new String[runMainArgs.size()]),
                timeout,
                getOutputHandler(section));

        // EVALUATE THE RESULTS
        status = checkReverse(status, reverseStatus);

        return status;
    } // runSameJVM

    private Status runAgentJVM() throws TestRunException {
        Path runClasspath;
        String runMainClass;
        List<String> runMainArgs;
        if (driverClass == null) {
            runClasspath = script.getTestClassPath();
            runMainClass = mainClassName;
            runMainArgs = mainArgs;
        } else {
            runClasspath = script.getTestClassPath();
            runMainClass = driverClass;
            runMainArgs = new ArrayList<String>();
            runMainArgs.add(script.getTestResult().getTestName());
            runMainArgs.add(mainClassName);
            runMainArgs.addAll(mainArgs);
        }

        if (showMode)
            showMode(getActionName(), ExecMode.AGENTVM, section);

        // TAG-SPEC:  "The source and class directories of a test are made
        // available to main and applet actions via the system properties
        // "test.src" and "test.classes", respectively"
        Map<String, String> p = script.getTestProperties();

        Agent agent;
        try {
            JDK jdk = script.getTestJDK();
            Path classpath = new Path(script.getJavaTestClassPath(), jdk.getJDKClassPath());
            if (script.isJUnitRequired())
                classpath.append(script.getJUnitJar());
            if (script.isTestNGRequired())
                classpath.append(script.getTestNGJar());
            agent = script.getAgent(jdk, classpath, script.getTestVMJavaOptions());
        } catch (IOException e) {
            return Status.error(AGENTVM_CANT_GET_VM + ": " + e);
        }

        Status status;
        try {
            status = agent.doMainAction(
                    script.getTestResult().getTestName(),
                    p,
                    runClasspath,
                    runMainClass,
                    runMainArgs,
                    timeout,
                    section);
        } catch (Agent.Fault e) {
            if (e.getCause() instanceof IOException)
                status = Status.error(String.format(AGENTVM_IO_EXCEPTION, e.getCause()));
            else
                status = Status.error(String.format(AGENTVM_EXCEPTION, e.getCause()));
        }
        if (status.isError()) {
            script.closeAgent(agent);
        }

        // EVALUATE THE RESULTS
        status = checkReverse(status, reverseStatus);

        return status;
    } // runAgentJVM()

    static Status runClass(
            String testName,
            Map<String, String> props,
            Path classpath,
            String classname,
            String[] classArgs,
            int timeout,
            OutputHandler outputHandler) {
        SaveState saved = new SaveState();

        Properties p = System.getProperties();
        for (Map.Entry<String, String> e: props.entrySet()) {
            String name = e.getKey();
            String value = e.getValue();
            if (name.equals("test.class.path.prefix")) {
                Path cp = new Path(value, System.getProperty("java.class.path"));
                p.put("java.class.path", cp.toString());
            } else {
                p.put(e.getKey(), e.getValue());
            }
        }
        System.setProperties(p);

        PrintByteArrayOutputStream out = new PrintByteArrayOutputStream();
        PrintByteArrayOutputStream err = new PrintByteArrayOutputStream();

        Status status = Status.passed(EXEC_PASS);
        try {
            Class<?> c;
            ClassLoader loader;
            if (classpath != null) {
                List<URL> urls = new ArrayList<URL>();
                for (File f: new Path(classpath).split()) {
                    try {
                        urls.add(f.toURI().toURL());
                    } catch (MalformedURLException e) {
                    }
                }
                loader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
                c = loader.loadClass(classname);
            } else {
                loader = null;
                c = Class.forName(classname);
            }

            // Select signature for main method depending on whether the class
            // implements the TestRunner marker interface.
            Class<?>[] argTypes;
            Object[] methodArgs;
            if (TestRunner.class.isAssignableFrom(c)) {
                // Marker interface found: use main(ClassLoader, String...)
                argTypes = new Class<?>[] { ClassLoader.class, String[].class };
                methodArgs = new Object[] { loader, classArgs };
            } else {
                // Normal case: marker interface not found; use standard main method
                argTypes = new Class<?>[] { String[].class };
                methodArgs = new Object[] { classArgs };
            }

            Method method = c.getMethod("main", argTypes);

            Status stat = redirectOutput(out, err);
            if (!stat.isPassed()) {
                return stat;
            }

            // RUN JAVA IN ANOTHER THREADGROUP

            SameVMThreadGroup tg = new SameVMThreadGroup();
            SameVMRunnable svmt = new SameVMRunnable(method, methodArgs, err);
            Thread t = new Thread(tg, svmt, "SameVMThread");
            Alarm alarm = null;
            if (timeout > 0) {
                PrintWriter alarmOut = outputHandler.createOutput(OutputHandler.OutputKind.LOG);
                alarm = new Alarm(timeout * 1000, t, testName, alarmOut);
            }
            Throwable error = null;
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                if (t.isInterrupted() && (tg.uncaughtThrowable == null)) {
                    error = e;
                    status = Status.error(MAIN_THREAD_INTR + e.getMessage());
                }
            } finally {
                tg.cleanup();
                if (alarm != null) {
                    alarm.cancel();
                    if (alarm.getState() != Alarm.State.WAITING && (error == null)) {
                        error = new Error("timeout");
                        status = Status.error(MAIN_THREAD_TIMEOUT);
                    }
                }
            }

            if (((svmt.t != null) || (tg.uncaughtThrowable != null)) && (error == null)) {
                if (svmt.t == null)
                    error = tg.uncaughtThrowable;
                else
                    error = svmt.t;
                status = Status.failed(MAIN_THREW_EXCEPT + error.toString());
            }

            if (status.getReason().contains("java.lang.SecurityException: System.exit() forbidden")) {
                status = Status.failed(UNEXPECT_SYS_EXIT);
            } else if (!tg.cleanupOK) {
                status = Status.error(EXEC_ERROR_CLEANUP);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace(err);
            err.println();
            err.println("JavaTest Message: main() method must be in a public class named");
            err.println("JavaTest Message: " + classname + " in file " + classname + ".java");
            err.println();
            status = Status.error(MAIN_CANT_LOAD_TEST + e);
        } catch (NoSuchMethodException e) {
            e.printStackTrace(err);
            err.println();
            err.println("JavaTest Message: main() method must be in a public class named");
            err.println("JavaTest Message: " + classname + " in file " + classname + ".java");
            err.println();
            status = Status.error(MAIN_CANT_FIND_MAIN);
        } finally {
            status = saved.restore(testName, status);
        }

        // Write test output
        out.close();
        outputHandler.createOutput(OutputHandler.OutputKind.STDOUT, out.getOutput());

        err.close();
        outputHandler.createOutput(OutputHandler.OutputKind.STDERR, err.getOutput());

        return status;
    }

    //----------utility methods-------------------------------------------------

    private String parseMainManual(String value) throws ParseException {
        if (value != null)
            throw new ParseException(MAIN_MANUAL_NO_VAL + value);
        else
            value = "novalue";
        return value;
    } // parseMainManual()

    private Status checkReverse(Status status, boolean reverseStatus) {
        // The standard rule is that /fail will invert Passed and Failed results
        // but will leave Error results alone.  But, for historical reasons
        // perpetuated by the Basic test program, a test calling System.exit
        // is reported with a Failed result, whereas Error would really be
        // more appropriate.  Therefore, we take care not to invert the
        // status if System.exit was called to exit the test.
        if (!status.isError()
                && !status.getReason().startsWith(UNEXPECT_SYS_EXIT)) {
            boolean ok = status.isPassed();
            int st = status.getType();
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
            if ((st == Status.FAILED) && ! (status.getReason() == null) &&
                    !status.getReason().equals(EXEC_PASS))
                sr += ": " + status.getReason();
            status = new Status(st, sr);
        }

        return status;
    }

    //----------internal classes------------------------------------------------

    private static class SameVMRunnable implements Runnable
    {
        public SameVMRunnable(Method m, Object[] args, PrintStream err) {
            method    = m;
            this.args = args;
            this.err  = err;
        } // SameVMRunnable()

        public void run() {
            try {
                // RUN JAVA PROGRAM
                result = method.invoke(null, args);

                System.err.println();
                System.err.println("JavaTest Message:  Test complete.");
                System.err.println();
            } catch (InvocationTargetException e) {
                // main must have thrown an exception, so the test failed
                e.getTargetException().printStackTrace(err);
                t = e.getTargetException();
                System.err.println();
                System.err.println("JavaTest Message: Test threw exception: " + t.getClass().getName());
                System.err.println("JavaTest Message: shutting down test");
                System.err.println();
            } catch (IllegalAccessException e) {
                e.printStackTrace(err);
                t = e;
                System.err.println();
                System.err.println("JavaTest Message: Verify that the class defining the test is");
                System.err.println("JavaTest Message: declared public (test invoked via reflection)");
                System.err.println();
            }
        } // run()

        //----------member variables--------------------------------------------

        public  Object result;
        private Method method;
        private Object[] args;
        private PrintStream err;

        Throwable t = null;
    }

    static class SameVMThreadGroup extends ThreadGroup
    {
        SameVMThreadGroup() {
            super("SameVMThreadGroup");
        } // SameVMThreadGroup()

        @Override
        public synchronized void uncaughtException(Thread t, Throwable e) {
            if (e instanceof ThreadDeath)
                return;
            if ((uncaughtThrowable == null) && (!cleanMode)) {
                uncaughtThrowable = e;
                uncaughtThread    = t;
            }
            cleanup();
        } // uncaughtException()

        private void cleanup() {
            cleanMode = true;

            final int CLEANUP_ROUNDS = 4;
            final long MAX_CLEANUP_TIME_MILLIS = 2 * 60 * 1000;
            final long CLEANUP_MILLIS_PER_ROUND = MAX_CLEANUP_TIME_MILLIS / CLEANUP_ROUNDS;
            final long NANOS_PER_MILLI = 1000L * 1000L;

            long startCleanupTime = System.nanoTime();

            for (int i = 1; i <= CLEANUP_ROUNDS; i++) {
                long deadline = startCleanupTime + i * CLEANUP_MILLIS_PER_ROUND * NANOS_PER_MILLI;
                List<Thread> liveThreads = liveThreads();
                if (liveThreads.isEmpty()) {
                    // nothing left to cleanup
                    cleanupOK = true;
                    return;
                }

                // kick the remaining live threads
                for (Thread thread : liveThreads)
                    thread.interrupt();

                // try joining as many threads as possible before
                // the round times out
                for (Thread thread : liveThreads) {
                    long millis = (deadline - System.nanoTime()) / NANOS_PER_MILLI;
                    if (millis <= 0)
                        break;
                    try {
                        thread.join(millis);
                    } catch (InterruptedException ignore) {
                    }
                }
            }

            cleanupOK = liveThreads().isEmpty();
        } // cleanup()

        /**
         * Gets all the "interesting" threads in the thread group.
         * @see ThreadGroup#enumerate(Thread[])
         */
        private List<Thread> liveThreads() {
            for (int estSize = activeCount() + 1; ; estSize = estSize * 2) {
                Thread[] threads = new Thread[estSize];
                int num = enumerate(threads);
                if (num < threads.length) {
                    ArrayList<Thread> list = new ArrayList<Thread>(num);
                    for (int i = 0; i < num; i++) {
                        Thread t = threads[i];
                        if (t.isAlive() &&
                                t != Thread.currentThread() &&
                                ! t.isDaemon())
                            list.add(t);
                    }
                    return list;
                }
            }
        }

        //----------member variables--------------------------------------------

        private boolean cleanMode   = false;
        Throwable uncaughtThrowable = null;
        Thread    uncaughtThread    = null;
        boolean cleanupOK = false;
    }

    //----------member variables------------------------------------------------

    private List<String>  javaArgs = new ArrayList<String>();
    private List<String>  mainArgs = new ArrayList<String>();
    private String  driverClass = null;
    private String  mainClassName  = null;
    private String  policyFN = null;
    private String  secureCN = null;

    private boolean reverseStatus = false;
    private boolean othervm = false;
    private int     timeout = -1;
    private String  manual  = "unset";

    private TestResult.Section section;
}
