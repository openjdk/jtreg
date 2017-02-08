/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.exec;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.sun.javatest.Script;
import com.sun.javatest.Status;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestEnvironment;
import com.sun.javatest.TestResult;
import com.sun.javatest.TestSuite;
import com.sun.javatest.regtest.agent.JDK_Version;
import com.sun.javatest.regtest.agent.SearchPath;
import com.sun.javatest.regtest.config.ExecMode;
import com.sun.javatest.regtest.config.IgnoreKind;
import com.sun.javatest.regtest.config.JDK;
import com.sun.javatest.regtest.config.JDKOpts;
import com.sun.javatest.regtest.config.Locations;
import com.sun.javatest.regtest.config.Locations.LibLocn;
import com.sun.javatest.regtest.config.Modules;
import com.sun.javatest.regtest.config.ParseException;
import com.sun.javatest.regtest.config.RegressionEnvironment;
import com.sun.javatest.regtest.config.RegressionParameters;
import com.sun.javatest.regtest.config.RegressionTestSuite;
import com.sun.javatest.regtest.report.TestNGReporter;
import com.sun.javatest.regtest.tool.Version;
import com.sun.javatest.regtest.util.StringUtils;

import static com.sun.javatest.regtest.agent.RStatus.error;
import static com.sun.javatest.regtest.agent.RStatus.passed;

/**
  * This class interprets the TestDescription as specified by the JDK tag
  * specification.
  *
  * @author Iris A Garcia
  * @see com.sun.javatest.Script
  */
public class RegressionScript extends Script {
    /**
     * The method that interprets the tags provided in the test description and
     * performs actions accordingly.
     *
     * @param argv Any arguments that the RegressionScript may use.  Currently
     *             there are none (value ignored).
     * @param td   The current TestDescription.
     * @param env  The test environment giving the details of how to run the
     *             test.
     * @return     The result of running the script on the given test
     *             description.
     */
    @Override
    public Status run(String[] argv, TestDescription td, TestEnvironment env) {
        if (!(env instanceof RegressionEnvironment))
            throw new AssertionError();

        long started = System.currentTimeMillis();

        regEnv = (RegressionEnvironment) env;
        params = regEnv.params;
        testSuite = params.getTestSuite();
        defaultModules = params.getTestJDK().getDefaultModules(params);
        systemModules = params.getTestJDK().getSystemModules(params);

        String filterFault = params.filterFaults.get(td);
        if (filterFault != null)
            return Status.error(filterFault);

        Status status = passed("OK");
        String actions = td.getParameter("run");

//      System.out.println("--- ACTIONS: " + actions);
        // actions != null -- should never happen since we have reasonable
        // defaults

        testResult = getTestResult();
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            hostname = "127.0.0.1";
        }
        testResult.putProperty("hostname", hostname);
        String[] props = { "user.name" };
        for (String p: props) {
            testResult.putProperty(p, System.getProperty(p));
        }
        testResult.putProperty("jtregVersion", getVersion());
        testResult.putProperty("testJDK", getTestJDK().getAbsolutePath());
        if (!getCompileJDK().equals(getTestJDK())) {
            testResult.putProperty("compileJDK", getCompileJDK().getAbsolutePath());
        }

        PrintWriter msgPW = testResult.getTestCommentWriter();

        try {
            locations = new Locations(params, td);
            modules = new Modules(params, td);
            if (!modules.isEmpty())
                testResult.putProperty("modules", modules.toString());


            // defaultExecMode may still be overridden in individual actions with /othervm
            defaultExecMode = testSuite.useOtherVM(td) ? ExecMode.OTHERVM : params.getExecMode();
            useBootClassPath = testSuite.useBootClassPath(td.getRootRelativePath());

            LinkedList<Action> actionList = parseActions(actions, true);

            needJUnit = false;
            needTestNG = false;

            if (td.getParameter("importsJUnit") != null) {
                needJUnit = true;
                needTestNG = true;
            } else {
                for (Action a: actionList) {
                    if (a instanceof JUnitAction) {
                        needJUnit = true;
                    } else if (a instanceof TestNGAction) {
                        needTestNG = true;
                    }
                }
            }

            if (needJUnit && !params.isJUnitAvailable()) {
                throw new TestRunException("JUnit not available: see the FAQ for details");
            }
            if (needTestNG && !params.isTestNGAvailable()) {
                throw new TestRunException("TestNG not available: see the FAQ for details");
            }

            if (!locations.absLibClsList(LibLocn.Kind.SYS_MODULE).isEmpty()) {
                usePatchModules = true;
            } else {
                // check actions for test-specific modules
                actionLoop:
                for (Action a: actionList) {
                    for (String m: a.getModules()) {
                        if (systemModules.contains(m)) {
                            usePatchModules = true;
                            break actionLoop;
                        }
                    }
                }
            }

            if (!locations.absLibClsList(LibLocn.Kind.USER_MODULE).isEmpty()) {
                useModulePath = true;
            }

            scratchDirectory = ScratchDirectory.get(params, defaultExecMode, td);
            scratchDirectory.init(msgPW);

            // if we got an error while parsing the TestDescription, return
            // error immediately
            if (td.getParameter("error") != null)
                status = error(td.getParameter("error"));
            else {
                if (getTestJDK().equals(getCompileJDK())) {
                    // output for default case unchanged
                    printJDKInfo(msgPW, "JDK under test", getTestJDK(), getTestVMOptions());
                } else {
                    printJDKInfo(msgPW, "compile JDK", getCompileJDK(), Collections.<String>emptyList());
                    printJDKInfo(msgPW, "test JDK", getTestJDK(), getTestVMOptions());
                }

                for (LibLocn lib: locations.getLibs()) {
                    String kind;
                    switch (lib.kind) {
                        case PACKAGE:
                            kind = "packages";
                            break;
                        case PRECOMPILED_JAR:
                            kind = "precompiled jar";
                            break;
                        case SYS_MODULE:
                            kind = "system module patches";
                            break;
                        case USER_MODULE:
                            kind = "user modules";
                            break;
                        default:
                            kind = "unknown";
                    }
                    msgPW.println("Library " + lib.name + "; kind: " + kind);
                    if (lib.absSrcDir != null) {
                        msgPW.println("   source directory: " + lib.absSrcDir);
                    }
                    if (lib.absClsDir.isFile() && lib.absClsDir.getName().endsWith(".jar")) {
                        msgPW.println("   jar file: " + lib.absClsDir);
                    } else {
                        msgPW.println("   class directory: " + lib.absClsDir);
                    }
                }

                while (! actionList.isEmpty()) {
                    Action action = actionList.remove();
                    status = action.run();
                    if (status.getType() != Status.PASSED)
                        break;
                }
            }
        } catch (InterruptedException e) {
            status = error("Interrupted! " + e.getLocalizedMessage());
        } catch (ScratchDirectory.Fault e) {
            String msg = e.getLocalizedMessage();
            if (e.getCause() != null)
                msg += " (" + e.getCause() + ")";
            status = error(msg);
        } catch (TestSuite.Fault e) {
            status = error(e.getMessage());
        } catch (Locations.Fault e) {
            status = error(e.getMessage());
        } catch (Modules.Fault e) {
            status = error(e.getMessage());
        } catch (ParseActionsException e) {
            status = error(e.getMessage());
        } catch (TestRunException e) {
            status = error(e.getMessage());
        } finally {
            int elapsed = (int) (System.currentTimeMillis() - started);
            int millis = (elapsed % 1000);
            int secs = (elapsed / 1000) % 60;
            int mins = (elapsed / (1000 * 60)) % 60;
            int hours = elapsed / (1000 * 60 * 60);
            testResult.putProperty("elapsed", String.format("%d %d:%02d:%02d.%03d",
                    elapsed, hours, mins, secs, millis));
            if (scratchDirectory != null && params.isRetainEnabled()) {
                String errmsg = null;
                try {
                    scratchDirectory.retainFiles(status, msgPW);
                } catch (InterruptedException e) {
                    errmsg = "Interrupted! " + e.getLocalizedMessage();
                } catch (ScratchDirectory.Fault e) {
                    errmsg = e.getMessage();
                    if (e.getCause() != null)
                        errmsg += " (" + e.getCause() + ")";
                }
                if (errmsg != null) {
                    msgPW.println(errmsg);
                    msgPW.println("Test result (overridden): " + status);
                    status = error("failed to clean up files after test");
                    closeAgents();
                }
            }

            releaseAgents();
        }
        return status;
    } // run()

    private void printJDKInfo(PrintWriter pw, String label, JDK jdk, List<String> opts) {
        pw.print(label);
        pw.print(": ");
        pw.println(jdk.getAbsoluteFile());
        String v = jdk.getVersionText(opts);
        if (v.length() > 0) {
            pw.println(v);
        }
    }

    /**
     * Get the set of source files used by the actions in a test description.
     * @param p  The parameters providing the necessary context
     * @param td The test description for which to find the test files
     * @return the set of source files known to the test
     **/
    // Arguably, this would be better as a static method that internally created
    // a private temporary RegressionScript.
    public Set<File> getSourceFiles(RegressionParameters p, TestDescription td) {
        this.td = td;
        try {
            if (locations == null) {
                locations = new Locations(p, td);
            }
            String actions = td.getParameter("run");
            LinkedList<Action> actionList = parseActions(actions, false);
            Set<File> files = new TreeSet<>();
            while (! actionList.isEmpty()) {
                Action action = actionList.remove();
                Set<File> a = action.getSourceFiles();
                if (a != null)
                    files.addAll(a);
            }
            return files;
        } catch (Locations.Fault e) {
            return Collections.<File>emptySet();
        } catch (ParseException e) {
            return Collections.<File>emptySet();
        } catch (ParseActionsException shouldNotHappen) {
            throw new Error(shouldNotHappen);
        }
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
        LinkedList<Action> actionList = new LinkedList<>();
        String[] runCmds = StringUtils.splitTerminator(LINESEP, actions);
        populateActionTable();

        for (String runCmd : runCmds) {
            // e.g. reason compile/fail/ref=Foo.ref -debug Foo.java
            // where "reason" indicates why the action should run
            String[] tokens = StringUtils.splitWS(runCmd);
            // [reason, compile/fail/ref=Foo.ref, -debug, Foo.java]
            String[] verbopts = StringUtils.splitSeparator("/", tokens[1]);
            // [compile, fail, ref=Foo.ref]
            String verb = verbopts[0];
            Map<String,String> opts = new LinkedHashMap<>();
            for (int i = 1; i < verbopts.length; i++) {
                String[] keyValue = StringUtils.splitEqual(verbopts[i]);
                opts.put(keyValue[0], keyValue[1]);
                // [[fail,], [ref, Foo.ref]]
            }
            List<String> args = new ArrayList<>();
            for (int i = 2; i < tokens.length; i++)
                args.add(tokens[i]);
            // [-debug, Foo.java] (everything after the big options token)
            Class<?> c = null;
            try {
                c = (Class<?>) (actionTable.get(verb));
                if (c == null) {
                    if (stopOnError)
                        throw new ParseActionsException(BAD_ACTION + verb);
                    continue;
                }
                Action action = (Action) (c.newInstance());
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
     *             less than zero, then the default timeout will be returned.
     * @return     The timeout in seconds.
     */
    protected int getActionTimeout(int time) {
        if (time < 0)
            time = 120;
        return (int) (time * getTimeoutFactor());
    }

    protected float getTimeoutFactor() {
        if (cacheJavaTestTimeoutFactor == -1) {
            // not synchronized, so in worst case may be set more than once
            float value = 1; // default
            try {
                // use [1] to get the floating point timeout factor
                String f = (regEnv == null ? null : regEnv.lookup("javatestTimeoutFactor")[1]);
                if (f != null)
                    value = Float.parseFloat(f);
            } catch (TestEnvironment.Fault e) {
            } catch (NumberFormatException e) {
            }
            cacheJavaTestTimeoutFactor = value;
        }
        return cacheJavaTestTimeoutFactor;
    }

    private static float cacheJavaTestTimeoutFactor = -1;

    /**
     * Set an alarm that will interrupt the calling thread after a specified
     * delay (in milliseconds), and repeatedly thereafter until canceled.  The
     * testCommentWriter will contain a confirmation string indicating that a
     * timeout has been signaled.
     *
     * @param timeout The delay in milliseconds.
     */
    @Override
    protected void setAlarm(int timeout) {
        super.setAlarm(timeout);
    }

    //----------internal methods------------------------------------------------

    private void populateActionTable() {
        addAction(AppletAction.NAME,  AppletAction.class);
        addAction(BuildAction.NAME,   BuildAction.class);
        addAction(CleanAction.NAME,   CleanAction.class);
        addAction(CompileAction.NAME, CompileAction.class);
        addAction(DriverAction.NAME,  DriverAction.class);
        addAction(IgnoreAction.NAME,  IgnoreAction.class);
        addAction(JUnitAction.NAME,   JUnitAction.class);
        addAction(MainAction.NAME,    MainAction.class);
        addAction(ShellAction.NAME,   ShellAction.class);
        addAction(TestNGAction.NAME,  TestNGAction.class);
    } // populateActionTable()

    private void addAction(String actionName, Class<? extends Action> actionClass) {
        actionTable.put(actionName, actionClass);
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
        switch (reason) {
            case Action.REASON_ASSUMED_ACTION:
                for (int i = 1; i < cmd.length; i++)
                    sb.append(cmd[i]).append(" ");
                retVal = Action.SREASON_ASSUMED_ACTION + sb;
                break;
            case Action.REASON_USER_SPECIFIED:
                for (int i = 1; i < cmd.length; i++)
                    sb.append(cmd[i]).append(" ");
                retVal = Action.SREASON_USER_SPECIFIED + sb;
                break;
            default:
                retVal = "Unknown";
                break;
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
    Map<String, String> getEnvVars() {
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
     * Debug options for tests
     */
    List<String> getTestDebugOptions() {
        return params.getTestDebugOptions();
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

    /**
     * Path to native components.
     */
    File getNativeDir() {
        return params.getNativeDir();
    }

    /**
     * Get content of @modules.
     */
    Modules getModules() {
        return modules;
    }

    Set<String> getLibBuildArgs() throws TestRunException {
        try {
            return testSuite.getLibBuildArgs(td);
        } catch (TestSuite.Fault e) {
            throw new TestRunException(e.getMessage(), e);
        }
    }

    /**
     * Determine whether to use new --patch-module option instead of -Xmodule.
     */
    boolean useNewPatchModule() {
        return testSuite.useNewPatchModule();
    }

    //----------------------- computing paths ---------------------------------

    File absTestWorkFile(String name) {
        return locations.absTestWorkFile(name);
    }

    File absTestSrcDir() {
        return locations.absTestSrcDir();
    } // absTestSrcDir()

    File absTestClsDir() {
        return locations.absTestClsDir();
    } // absTestClsDir()

    File absTestScratchDir() {
        return scratchDirectory.dir.getAbsoluteFile();
    } // absTestScratchDir()

    File absTestClsTopDir() {
        return locations.absBaseClsDir();
    } // absTestClsTopDir()

    private boolean useBootClassPath(File classdir) throws TestClassException {
        try {
            String rel = locations.absBaseClsDir().toURI().relativize(classdir.toURI()).getPath();
            return testSuite.useBootClassPath(rel);
        } catch (TestSuite.Fault f) {
            throw new TestClassException(f.toString());
        }
    }

    enum PathKind {
        BOOTCLASSPATH_APPEND,
        CLASSPATH,
        MODULEPATH,
        MODULESOURCEPATH,
        PATCHPATH,
        SOURCEPATH
    }

    Map<PathKind, SearchPath> getCompilePaths(LibLocn libLocn, boolean multiModule, String module)
            throws TestRunException {
        SearchPath bcp = new SearchPath();
        SearchPath cp = new SearchPath();
        SearchPath mp = new SearchPath();
        SearchPath msp = new SearchPath();
        SearchPath pp = new SearchPath();
        SearchPath sp = new SearchPath();

        // Test:
        if (libLocn == null || libLocn.name == null) {
            if (multiModule) {
                msp.append(locations.absTestSrcDir());
            } else {
                File testSrcDir = locations.absTestSrcDir(module);
                sp.append(testSrcDir);
                // Ideally, the source directory need only go on the source path
                // but some tests rely on precompiled .class files existing in
                // the source directory. Allow such legacy usage for package-
                // oriented tests, and also put the source dir on classpath.
                // Note: it is not enough to just put it on the classpath only
                // in those cases where there are other items on the source path.
                cp.append(testSrcDir);
            }
        }

        if (!multiModule)
            cp.append(locations.absTestClsDir());

        if (useModulePath()) {
            mp.append(locations.absTestModulesDir());
        }

        if (usePatchModules()) {
            pp.append(locations.absTestPatchDir());
        }

        // Libraries:
        if (libLocn != null) {
            if (multiModule)
                msp.append(libLocn.absSrcDir);
            else if (module != null)
                sp.append(new File(libLocn.absSrcDir, module));
        }

        if (module == null) {
            sp.append(locations.absLibSrcList(LibLocn.Kind.PACKAGE));
        }

        // could split stuff onto bootclasspath to match execution paths, but not necessary
        cp.append(locations.absLibClsList(LibLocn.Kind.PACKAGE));
        cp.append(locations.absLibSrcJarList());

        if (useModulePath()) {
            mp.append(locations.absLibClsList(LibLocn.Kind.USER_MODULE));
        }

        if (usePatchModules()) {
            pp.append(locations.absLibClsList(LibLocn.Kind.SYS_MODULE));
        }

        // Frameworks:
        if (multiModule) {
            if (needJUnit || needTestNG) {
                // Put necessary jar files onto the module path as automatic modules.
                // We cannot use the ${jtreg.home}/lib directory directly since it contains
                // other jar files which are not valid as automatic modules.
                File md = workDir.getFile("modules");
                if (needJUnit)
                    install(params.getJUnitPath(), md);
                if (needTestNG)
                    install(params.getTestNGPath(), md);
                mp.append(md);
            }
        } else {
            if (needJUnit)
                cp.append(params.getJUnitPath());

            if (needTestNG)
                cp.append(params.getTestNGPath());
        }

        // Extras:

        // tools.jar, when present
        JDK jdk = getCompileJDK();
        cp.append(jdk.getJDKClassPath());

        // handle cpa option to jtreg
        Map<String, String> envVars = getEnvVars();
        String cpa = envVars.get("CPAPPEND");
        if (cpa != null) {
            // the cpa we were passed always uses '/' as FILESEP, make
            // sure to use the proper one for the platform
            cpa = cpa.replace('/', File.separatorChar);
            cp.append(cpa);
        }

        // Results:
        Map<PathKind, SearchPath> map = new EnumMap<>(PathKind.class);
        if (!bcp.isEmpty())
            map.put(PathKind.BOOTCLASSPATH_APPEND, bcp);
        if (!cp.isEmpty())
            map.put(PathKind.CLASSPATH, cp);
        if (!mp.isEmpty())
            map.put(PathKind.MODULEPATH, mp);
        if (!msp.isEmpty())
            map.put(PathKind.MODULESOURCEPATH, msp);
        if (!pp.isEmpty())
            map.put(PathKind.PATCHPATH, pp);
        if (!sp.isEmpty())
            map.put(PathKind.SOURCEPATH, sp);
        return map;
    }

    Map<PathKind, SearchPath> getExecutionPaths(
            boolean multiModule, String module, boolean testOnBootClassPath, boolean include_jtreg)
                throws TestRunException {
        SearchPath bcp = new SearchPath();
        SearchPath cp = new SearchPath();
        SearchPath mp = new SearchPath();
        SearchPath pp = new SearchPath();

        // Test:
        SearchPath tp = testOnBootClassPath ? bcp : cp;
        tp.append(locations.absTestClsDir());
        tp.append(locations.absTestSrcDir()); // include source dir for access to resource files

        if (hasTestPatchMods()) {
            pp.append(locations.absTestPatchDir());
        }

        if (hasTestUserMods()) {
            mp.append(locations.absTestModulesDir());
        }

        // Libraries:
        if (testOnBootClassPath) {
            // all libraries unconditionally also on bootclasspath
            bcp.append(locations.absLibClsList(LibLocn.Kind.PACKAGE));
            bcp.append(locations.absLibSrcJarList());
        } else {
            // only put libraries on bootclasspath that need to be there
            for (LibLocn libLocn: locations.getLibs()) {
                if (libLocn.kind == LibLocn.Kind.PACKAGE) {
                    SearchPath p = (useBootClassPath(libLocn.absClsDir)) ? bcp : cp;
                    p.append(libLocn.absClsDir);
                    p.append(libLocn.absSrcDir); // include source dir for access to resource files
                }
            }
            cp.append(locations.absLibSrcJarList());
        }

        if (useModulePath()) {
            mp.append(locations.absLibClsList(LibLocn.Kind.USER_MODULE));
        }

        if (usePatchModules()) {
            pp.append(locations.absLibClsList(LibLocn.Kind.SYS_MODULE));
        }

        // Frameworks:
        if (multiModule) {
            // assert !testOnBootClassPath && !useXPatch()
            if (needJUnit || needTestNG) {
                // Put necessary jar files onto the module path as automatic modules.
                // We cannot use the ${jtreg.home}/lib directory directly since it contains
                // other jar files which are not valid as automatic modules.
                File md = workDir.getFile("modules");
                if (needJUnit)
                    install(params.getJUnitPath(), md);
                if (needTestNG)
                    install(params.getTestNGPath(), md);
                mp.append(md);
            }
        } else {
            SearchPath fp = (!bcp.isEmpty() || usePatchModules()) ? bcp : cp;
            if (needJUnit)
                fp.append(params.getJUnitPath());

            if (needTestNG)
                fp.append(params.getTestNGPath());
        }

        // Extras:

        // tools.jar, when present
        JDK jdk = getCompileJDK();
        tp.append(jdk.getJDKClassPath());

        // handle cpa option to jtreg
        Map<String, String> envVars = getEnvVars();
        String cpa = envVars.get("CPAPPEND");
        if (cpa != null) {
            // the cpa we were passed always uses '/' as FILESEP, make
            // sure to use the proper one for the platform
            cpa = cpa.replace('/', File.separatorChar);
            cp.append(cpa);
        }

        // javatest.jar and jtreg.jar
        if (include_jtreg) {
            (testOnBootClassPath ? bcp : cp).append(getJavaTestClassPath());
        }

        Map<PathKind, SearchPath> map = new EnumMap<>(PathKind.class);
        if (!bcp.isEmpty())
            map.put(PathKind.BOOTCLASSPATH_APPEND, bcp);
        if (!cp.isEmpty())
            map.put(PathKind.CLASSPATH, cp);
        if (!mp.isEmpty())
            map.put(PathKind.MODULEPATH, mp);
        if (!pp.isEmpty())
            map.put(PathKind.PATCHPATH, pp);
        return map;
    }

    private void install(SearchPath path, File dir) throws TestRunException {
        synchronized (params.getWorkDirectory()) { // avoid multiple tests doing simultaneous install
            if (dir.exists()) {
                if (!dir.isDirectory())
                    throw new TestRunException("modules in work directory is not a directory");
            } else {
                dir.mkdirs();
            }

            for (File jar: path.asList()) {
                File target = new File(dir, jar.getName());
                if (target.exists()
                        && target.length() == jar.length()
                        && target.lastModified() == jar.lastModified()) {
                    return;
                }

                // Eventually replace with java.nio.file.Files::copy
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target));
                        InputStream in = new BufferedInputStream(new FileInputStream(jar))) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                } catch (IOException e) {
                    throw new TestRunException("cannot init modules directory", e);
                }
                target.setLastModified(jar.lastModified());
            }
        }
    }

    boolean useBootClassPath() {
        return useBootClassPath;
    }

    boolean usePatchModules() {
        return usePatchModules;
    }

    boolean hasTestPatchMods() {
        File testModulesDir = locations.absTestPatchDir();
        if (testModulesDir.isDirectory()) {
            for (File f: testModulesDir.listFiles()) {
                if (f.isDirectory())
                    return true;
            }
        }
        return false;
    }

    // currently unused
    boolean useModulePath() {
        return useModulePath;
    }

    boolean hasTestUserMods() {
        File testModulesDir = locations.absTestModulesDir();
        if (testModulesDir.isDirectory()) {
            for (File f: testModulesDir.listFiles()) {
                if (f.isDirectory())
                    return true;
            }
        }
        return false;
    }

    ExecMode getExecMode() {
        return defaultExecMode;
    }

    SearchPath getJavaTestClassPath() {
        return params.getJavaTestClassPath();
    }

    boolean isJUnitRequired() {
        return needJUnit;
    }

    SearchPath getJUnitPath() {
        return params.getJUnitPath();
    }

    boolean isTestNGRequired() {
        return needTestNG;
    }

    SearchPath getTestNGPath() {
        return params.getTestNGPath();
    }

    SearchPath getAsmToolsPath() {
        return params.getAsmToolsPath();
    }

    TestNGReporter getTestNGReporter() {
        return TestNGReporter.instance(workDir);
    }

    Lock getLockIfRequired() throws TestRunException {
        try {
            if (!testSuite.needsExclusiveAccess(td))
                return null;
        } catch (TestSuite.Fault e) {
            throw new TestRunException("Can't determine if lock required", e);
        }

        return Lock.get(params);
    }

    int getNextSerial() {
        return nextSerial++;
    }

    private int nextSerial = 0;

    //--------------------------------------------------------------------------

    TimeoutHandlerProvider getTimeoutHandlerProvider() throws TestRunException {
        try {
            return params.getTimeoutHandlerProvider();
        } catch (MalformedURLException e) {
            throw new TestRunException("Can't get timeout handler provider", e);
        }
    }

    //--------------------------------------------------------------------------

    JDK getTestJDK() {
        return params.getTestJDK();
    }

    JDK_Version getTestJDKVersion() {
        return getTestJDK().getVersion(params);
    }

    String getJavaProg() {
        return params.getTestJDK().getJavaProg().getPath();
    }

    //--------------------------------------------------------------------------

    JDK getCompileJDK() {
        return params.getCompileJDK();
    }

    JDK_Version getCompileJDKVersion() {
        return getCompileJDK().getVersion(params);
    }

    String getJavacProg() {
        return params.getCompileJDK().getJavacProg().getPath();
    }

    //--------------------------------------------------------------------------

    // Get the standard properties to be set for tests

    Map<String, String> getTestProperties() throws TestClassException {
        Map<String, String> p = new LinkedHashMap<>();
        // The following will be added to javac.class.path on the test JVM
        switch (getExecMode()) {
            case AGENTVM:
                SearchPath path = new SearchPath()
                    .append(locations.absTestClsDir())
                    .append(locations.absTestSrcDir())
                    .append(locations.absLibClsList(LibLocn.Kind.PACKAGE))
                    .append(locations.absLibSrcJarList());
                p.put("test.class.path.prefix", path.toString());
        }
        p.put("test.src", locations.absTestSrcDir().getPath());
        p.put("test.src.path", toString(locations.absTestSrcPath()));
        p.put("test.classes", locations.absTestClsDir().getPath());
        p.put("test.class.path", toString(locations.absTestClsPath()));
        p.put("test.vm.opts", StringUtils.join(getTestVMOptions(), " "));
        p.put("test.tool.vm.opts", StringUtils.join(getTestToolVMOptions(), " "));
        p.put("test.compiler.opts", StringUtils.join(getTestCompilerOptions(), " "));
        p.put("test.java.opts", StringUtils.join(getTestJavaOptions(), " "));
        p.put("test.jdk", getTestJDK().getAbsolutePath());
        p.put("compile.jdk", getCompileJDK().getAbsolutePath());
        p.put("test.timeout.factor", String.valueOf(getTimeoutFactor()));
        if (!modules.isEmpty())
            p.put("test.modules", modules.toString());
        File nativeDir = getNativeDir();
        if (nativeDir != null)
            p.put("test.nativepath", nativeDir.getAbsolutePath());
        if (usePatchModules()) {
            SearchPath pp = new SearchPath();
            pp.append(locations.absLibClsList(LibLocn.Kind.SYS_MODULE));
            p.put("test.patch.path", pp.toString());
        }
        if (useModulePath()) {
            SearchPath pp = new SearchPath();
            pp.append(locations.absLibClsList(LibLocn.Kind.USER_MODULE));
            p.put("test.module.path", pp.toString());
        }
        return Collections.unmodifiableMap(p);
    }
    // where
    private String toString(List<File> files) {
        StringBuilder sb = new StringBuilder();
        for (File f: files) {
            if (sb.length() > 0) sb.append(File.pathSeparator);
            sb.append(f.getPath());
        }
        return sb.toString();
    }

    //--------------------------------------------------------------------------

    /*
     * Get an agent for a VM with the given VM options.
     */
    Agent getAgent(JDK jdk, SearchPath classpath, List<String> testVMOpts) throws Agent.Fault {
        JDKOpts vmOpts = new JDKOpts();
        vmOpts.addAll("-classpath", classpath.toString());
        vmOpts.addAll(testVMOpts);
        if (params.getTestJDK().hasModules()) {
            vmOpts.addAllPatchModules(new SearchPath(params.getWorkDirectory().getFile("patches")));
        }

        /*
         * A script only uses one agent at a time, and only one, maybe two,
         * different agents overall, for actions that use agentVM mode (i.e.
         * CompileAction and MainAction.) Therefore, use a simple list to
         * record the agents that the script has already obtained for use.
         */
        for (Agent agent: agents) {
            if (agent.matches(absTestScratchDir(), jdk, vmOpts.toList()))
                return agent;
        }

        Map<String, String> envVars = new HashMap<>();
        envVars.putAll(getEnvVars());
        // some tests are inappropriately relying on the CLASSPATH environment
        // variable being set, so ensure it is set. See equivalent code in MainAction
        // and Main.execChild. Note we cannot set exactly the same classpath as
        // for othervm, because we should not include test-specific info
        SearchPath cp = new SearchPath().append(jdk.getJDKClassPath()).append(getJavaTestClassPath());
        envVars.put("CLASSPATH", cp.toString());

        Agent.Pool p = Agent.Pool.instance();
        Agent agent = p.getAgent(absTestScratchDir(), jdk, vmOpts.toList(), envVars);
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

    List<Agent> agents = new ArrayList<>();

    //----------internal classes-----------------------------------------------

    void saveScratchFile(File file, File dest) {
        scratchDirectory.retainFile(file, dest);
    }

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

    static String getVersion() {
        if (version == null) {
            StringBuilder sb = new StringBuilder();
            Version v = Version.getCurrent();
            sb.append(v.product == null ? "jtreg" : v.product);
            if (v.version != null)
                sb.append(' ').append(v.version);
            if (v.milestone != null)
                sb.append(' ').append(v.milestone);
            if (v.build != null)
                sb.append(' ').append(v.build);
            version = sb.toString();
        }
        return version;
    }
    // where
    static String version;

    //----------misc statics---------------------------------------------------

    private static final String LINESEP  = System.getProperty("line.separator");

    private static final String
        CANT_INSTANTIATE      = "Unable to instantiate: ",
        NOT_EXT_ACTION        = " does not extend Action",
        ILLEGAL_ACCESS_INIT   = "Illegal access to init method: ",
        BAD_ACTION            = "Bad action for script: ";

    //----------member variables-----------------------------------------------

    private final Map<String, Class<?>> actionTable = new HashMap<>();
    private TestResult testResult;

    private RegressionEnvironment regEnv;
    private RegressionParameters params;
    private RegressionTestSuite testSuite;
    Set<String> defaultModules;
    Set<String> systemModules;
    private boolean useBootClassPath;
    private boolean usePatchModules;
    private boolean useModulePath;
    private ExecMode defaultExecMode;
    private boolean needJUnit;
    private boolean needTestNG;
    private Modules modules;
    private ScratchDirectory scratchDirectory;
    Locations locations;

}
