/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

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
                if (!script.hasEnv() || !script.isJDK11())
                    policyFN = parsePolicy(optValue);
                else
                    throw new ParseException(PARSE_BAD_OPT_JDK + optName);
            } else if (optName.equals("secure")) {
                if (!script.hasEnv() || !script.isJDK11())
                    secureFN = parseSecure(optValue);
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
            driverFN = driverClass;
        }

        // separate the arguments into the options to java, the
        // classname and the parameters to the named class
        for (int i = 0; i < args.length; i++) {
            if (buildFN == null) {
                if (args[i].startsWith("-")) {
                    javaArgs += " " + args[i];
                    if ((args[i].equals("-cp") || args[i].equals("-classpath"))
                        && (i+1 < args.length))
                        javaArgs += " " + args[++i];
                } else {
                    buildFN = args[i];
                }
            } else {
                if (mainArgs.equals(""))
                    mainArgs = args[i];
                else
                    mainArgs += " " + args[i];
            }
        }

        if (buildFN == null)
            throw new ParseException(MAIN_NO_CLASSNAME);
        if (!othervm) {
            if (!javaArgs.equals(""))
                throw new ParseException(javaArgs + MAIN_UNEXPECT_VMOPT);
            if (policyFN != null)
                throw new ParseException(PARSE_POLICY_OTHERVM);
            if (secureFN != null)
                throw new ParseException(PARSE_SECURE_OTHERVM);
        }
    } // init()

    public String getJavaArgs() {
        return javaArgs;
    }
    public String getMainArgs() {
        return mainArgs;
    }
    public String getMainClassName() {
        return buildFN;
    }

    @Override
    public File[] getSourceFiles() {
        List<File> l = new ArrayList<File>();
        if (buildFN != null) {
            String[][] buildOpts = {};
            String[]   buildArgs = {buildFN.replace(File.separatorChar, '.')};
            try {
                BuildAction ba = new BuildAction();
                ba.init(buildOpts, buildArgs, SREASON_ASSUMED_BUILD, script);
                l.addAll(Arrays.asList(ba.getSourceFiles()));
            } catch (ParseException ignore) {
            }
        }
        if (policyFN != null)
            l.add(new File(policyFN));
        if (secureFN != null)
            l.add(new File(secureFN));
        return l.toArray(new File[l.size()]);
    }

    /**
     * The method that does the work of the action.  The necessary work for the
     * given action is defined by the tag specification.
     *
     * Invoke the main method of the specified class, passing any arguments
     * after the class name.  A "main" action is considerd to be finished when
     * the main method returns.
     *
     * A "main" action passes if the main method returns normally and does not
     * cause an exception to be thrown by the main or any subsidiary threads.
     * It fails otherwise.
     *
     * If the <em>othervm<em> option is present, this action requires that the
     * JVM support multiple processes.
     *
     * @return     The result of the action.
     * @exception  TestRunException If an unexpected error occurs while running
     *             the test.
     */
    public Status run() throws TestRunException {
        Status status;

        // TAG-SPEC:  "The named <class> will be compiled on demand, just as
        // though an "@run build <class>" action had been inserted before
        // this action."
        String[][] buildOpts = {};
        String[]   buildArgs = {buildFN.replace(File.separatorChar, '.')};
        BuildAction ba = new BuildAction();
        if (!(status = ba.build(buildOpts, buildArgs, SREASON_ASSUMED_BUILD, script)).isPassed())
            return status;

        section = startAction(getActionName(), javaArgs + buildFN + mainArgs, reason);

        if (script.isCheck()) {
            status = Status.passed(CHECK_PASS);
        } else {
            if (othervm || script.isOtherJVM())
                status = runOtherJVM();
            else
                status = runSameJVM();
        }

        endAction(status, section);
        return status;
    } // run()

    //----------internal methods------------------------------------------------

    protected String getActionName() {
        return "main";
    }

    private Status runOtherJVM() throws TestRunException {
        // Arguments to wrapper:
        String mainClass = buildFN;
        String stringifiedArgs = (mainArgs == null ? "" : mainArgs);
        if (driverFN != null) {
            if (stringifiedArgs.equals(""))
                stringifiedArgs = mainClass;
            else
                stringifiedArgs = mainClass + " " + stringifiedArgs;
            mainClass = driverFN;
        }

        // WRITE ARGUMENT FILE
        String mainArgFileName = script.absTestClsDir() + FILESEP + buildFN
            + RegressionScript.WRAPPEREXTN;
        FileWriter fw;
        try {
            fw = new FileWriter(mainArgFileName);
            fw.write(mainClass + "\0");
            fw.write(stringifiedArgs + "\0" );
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

        if (useCLASSPATH || script.isJDK11()) {
            command.add("CLASSPATH=" + script.getJavaTestClassPath() +
                        PATHSEP + script.testClassPath());
        }
        command.add(script.getJavaProg());
        if (!useCLASSPATH && !script.isJDK11()) {
            command.add("-classpath");
            command.add(script.getJavaTestClassPath() + PATHSEP + script.testClassPath());
        }

        command.addAll(script.getTestVMJavaOptions());

        command.add("-Dtest.src=" + script.absTestSrcDir());
        command.add("-Dtest.classes=" + script.absTestClsDir());
        command.add("-Dtest.vm.opts=" + join(script.getTestVMOptions()));
        command.add("-Dtest.tool.vm.opts=" + join(script.getTestToolVMOptions()));
        command.add("-Dtest.javac.opts=" + join(script.getTestCompilerOptions()));
        command.add("-Dtest.java.opts=" + join(script.getTestJavaOptions()));

        String newPolicyFN;
        if (policyFN != null) {
            // add permission to read JTwork/classes by adding a grant entry
            newPolicyFN = addGrantEntry(policyFN);
            command.add("-Djava.security.policy==" + newPolicyFN);
        }

        if (secureFN != null)
            command.add("-Djava.security.manager=" + secureFN);
        else if (policyFN != null)
            command.add("-Djava.security.manager=default");
//      command.addElement("-Djava.security.debug=all");

        String[] jArgs = StringArray.splitWS(javaArgs);
        for (int i = 0; i < jArgs.length; i++)
            command.add(jArgs[i]);

        command.add("com.sun.javatest.regtest.MainWrapper");
        command.add(mainArgFileName);

        String[] mArgs = StringArray.splitWS(stringifiedArgs);
        for (int i = 0; i < mArgs.length; i++)
            command.add(mArgs[i]);

        // convert from List to String[]
        String[] tmpCmd = new String[command.size()];
        for (int i = 0; i < command.size(); i++)
            tmpCmd[i] = command.get(i);

        String[] envVars = script.getEnvVars();
        String[] cmdArgs = StringArray.append(envVars, tmpCmd);

        // PASS TO PROCESSCOMMAND
        Status status;
        PrintWriter sysOut = section.createOutput("System.out");
        PrintWriter sysErr = section.createOutput("System.err");
        try {
            if (showCmd)
                JTCmd(getActionName(), cmdArgs, section);
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
            if (sysOut != null) sysOut.close();
            if (sysErr != null) sysErr.close();
        }

        // EVALUATE THE RESULTS

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
            if ((st == Status.FAILED) && !status.getReason().equals("")
                && !status.getReason().equals(EXEC_PASS))
                sr += ": " + status.getReason();
            status = new Status(st, sr);
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
                p.put("java.class.path",
                        script.absTestClsDir() + PATHSEP +
                        script.absTestSrcDir() + PATHSEP +
                        script.absClsLibListStr() + PATHSEP +
                        p.getProperty("java.class.path"));
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

        String mainClass = buildFN;
        String stringifiedArgs = (mainArgs == null ? "" : mainArgs);
        if (driverFN != null) {
            if (stringifiedArgs.equals(""))
                stringifiedArgs = mainClass;
            else
                stringifiedArgs = mainClass + " " + stringifiedArgs;
            mainClass = driverFN;
        }

        ByteArrayOutputStream newOut = new ByteArrayOutputStream();
        ByteArrayOutputStream newErr = new ByteArrayOutputStream();
        PrintStream psOut = new PrintStream(newOut);
        PrintStream psErr = new PrintStream(newErr);

        Status status;
        PrintStream saveOut = System.out;
        PrintStream saveErr = System.err;
        try {
            status = Status.passed(EXEC_PASS);

            String[] classpath = StringArray.splitSeparator(PATHSEP, script.testClassPath());
            List<URL> urls = new ArrayList<URL>();
            for (int i = 0; i < classpath.length; i++) {
                String p = classpath[i];
                if (p.length() > 0) {
                    try {
                        urls.add(new File(p).toURI().toURL());
                    } catch (MalformedURLException e) {
                    }
                }
            }
            Class<?> c;
            if (driverFN == null) {
                ClassLoader loader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
                c = loader.loadClass(buildFN);
            } else {
                c = Class.forName(driverFN);
            }

            Class<?>[] argTypes = { String[].class };
            Method method = c.getMethod("main", argTypes);

            // XXX 4/1 possible to use splitSeparator instead?
            String[] tmpArgs = StringArray.splitWS(stringifiedArgs);
            Object[] runArgs = {tmpArgs};

            Status stat = redirectOutput(psOut, psErr);
            if (!stat.isPassed()) {
                return stat;
            }

            // RUN JAVA IN ANOTHER THREADGROUP

            SameVMThreadGroup tg = new SameVMThreadGroup();
            SameVMThread svmt = new SameVMThread(method, runArgs, psErr);
            Thread t = new Thread(tg, svmt, "SameVMThread");
            Throwable error = null;
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                if (t.isInterrupted() && (tg.uncaughtThrowable == null)) {
                    error = e;
                    status = Status.error(MAIN_THREAD_INTR + e.getMessage());
                }
            }
            tg.cleanup();

            if (((svmt.t != null) || (tg.uncaughtThrowable != null)) && (error == null)) {
                if (svmt.t == null)
                    error = tg.uncaughtThrowable;
                else
                    error = svmt.t;
                status = Status.failed(MAIN_THREW_EXCEPT + error.toString());
            }

            // EVALUATE RESULTS
            if (status.getReason().endsWith("java.lang.SecurityException: System.exit() forbidden by JavaTest")) {
                status = Status.failed(UNEXPECT_SYS_EXIT);
            } else {

                boolean ok = status.isPassed();
                int st   = status.getType();
                String sr;
                if (!tg.cleanupOK) {
                    // failure to cleanup threads is treated seriously
                    // because it might affect subsequent tests
                    sr = EXEC_ERROR_CLEANUP;
                    st = Status.ERROR;
                } else if (ok && reverseStatus) {
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
        } catch (ClassNotFoundException e) {
            e.printStackTrace(new PrintWriter(psErr, true));
            psErr.println();
            psErr.println("JavaTest Message: main() method must be in a public class named");
            psErr.println("JavaTest Message: " + mainClass + " in file " + mainClass + ".java");
            psErr.println();
            status = Status.error(MAIN_CANT_LOAD_TEST + e);
        } catch (NoSuchMethodException e) {
            e.printStackTrace(new PrintWriter(psErr, true));
            psErr.println();
            psErr.println("JavaTest Message: main() method must be in a public class named");
            psErr.println("JavaTest Message: " + mainClass + " in file " + mainClass + ".java");
            psErr.println();
            status = Status.error(MAIN_CANT_FIND_MAIN);
        } finally {
            SecurityManager sm = System.getSecurityManager();
            if (sm instanceof RegressionSecurityManager) {
                RegressionSecurityManager rsm = (RegressionSecurityManager) sm;
                if (rsm.isPropertiesAccessed()) {
                    System.setProperties(newProperties(savedSystemProperties));
//                    System.err.println("reset properties");
                } else {
                    System.setProperty("java.class.path", (String) savedSystemProperties.get("java.class.path"));
//                    System.err.println("no need to reset properties");
                }
                rsm.setAllowPropertiesAccess(false);
            }

            Status stat = redirectOutput(saveOut, saveErr);
            if (!stat.isPassed()) {
                return stat;
            }

            psOut.close();
            psErr.close();

            String outString = newOut.toString();
            String errString = newErr.toString();
            PrintWriter sysOut = section.createOutput("System.out");
            PrintWriter sysErr = section.createOutput("System.err");
            try {
                sysOut.write(outString);
                sysErr.write(errString);
            } finally {
                if (sysOut != null) sysOut.close();
                if (sysErr != null) sysErr.close();
            }
        }

        return status;
    } // runSameJVM()

    private String parseMainManual(String value) throws ParseException {
        if (value != null)
            throw new ParseException(MAIN_MANUAL_NO_VAL + value);
        else
            value = "novalue";
        return value;
    } // parseMainManual()

    private String join(List<String> list) {
        StringBuffer sb = new StringBuffer();
        for (String s: list) {
            if (sb.length() > 0)
                sb.append(" ");
            sb.append(s);
        }
        return sb.toString();
    }

    //----------internal classes------------------------------------------------

    class SameVMThread extends Thread
    {
        public SameVMThread(Method m, Object[] args, PrintStream psErr) {
            method      = m;
            runArgs     = args;
            this.psErr  = psErr;
        } // SameVMThread()

        @Override
        public void run() {
            try {
                if (timeout > 0)
                    script.setAlarm(timeout*1000);

                // RUN JAVA PROGRAM
                result = method.invoke(null, runArgs);

                System.err.println();
                System.err.println("JavaTest Message:  Test complete.");
                System.err.println();
            } catch (InvocationTargetException e) {
                // main must have thrown an exception, so the test failed
                e.getTargetException().printStackTrace(new PrintWriter(psErr, true));
                t = e.getTargetException();
                System.err.println();
                System.err.println("JavaTest Message: Test threw exception: " + t.getClass().getName());
                System.err.println("JavaTest Message: shutting down test");
                System.err.println();
            } catch (IllegalAccessException e) {
                e.printStackTrace(new PrintWriter(psErr, true));
                t = e;
                System.err.println();
                System.err.println("JavaTest Message: Verify that the class defining the test is");
                System.err.println("JavaTest Message: declared public (test invoked via reflection)");
                System.err.println();
            } finally {
                script.setAlarm(0);
            }
        } // run()

        //----------member variables--------------------------------------------

        public  Object result;
        private Method method;
        private Object[] runArgs;
        private PrintStream psErr;

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

    private String  javaArgs = "";
    private String  mainArgs = "";
    private String  driverFN = null;
    private String  buildFN  = null;
    private String  policyFN = null;
    private String  secureFN = null;

    private boolean reverseStatus = false;
    private boolean othervm = false;
    private int     timeout = -1;
    private String  manual  = "unset";

    private TestResult.Section section;
}
