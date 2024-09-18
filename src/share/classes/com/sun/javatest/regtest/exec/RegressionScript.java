/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import com.sun.javatest.Script;
import com.sun.javatest.Status;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestEnvironment;
import com.sun.javatest.TestResult;
import com.sun.javatest.TestSuite;
import com.sun.javatest.regtest.agent.JDK_Version;
import com.sun.javatest.regtest.agent.MainWrapper;
import com.sun.javatest.regtest.agent.SearchPath;
import com.sun.javatest.regtest.config.ExecMode;
import com.sun.javatest.regtest.config.Expr;
import com.sun.javatest.regtest.config.IgnoreKind;
import com.sun.javatest.regtest.config.JDK;
import com.sun.javatest.regtest.config.JDKOpts;
import com.sun.javatest.regtest.config.Locations;
import com.sun.javatest.regtest.config.Locations.LibLocn;
import com.sun.javatest.regtest.config.Modules;
import com.sun.javatest.regtest.config.OS;
import com.sun.javatest.regtest.config.ParseException;
import com.sun.javatest.regtest.config.RegressionEnvironment;
import com.sun.javatest.regtest.config.RegressionParameters;
import com.sun.javatest.regtest.config.RegressionTestSuite;
import com.sun.javatest.regtest.report.SummaryReporter;
import com.sun.javatest.regtest.report.Verbose;
import com.sun.javatest.regtest.tool.Version;
import com.sun.javatest.regtest.util.FileUtils;
import com.sun.javatest.regtest.util.StringUtils;

import static com.sun.javatest.regtest.RStatus.error;
import static com.sun.javatest.regtest.RStatus.passed;

/**
  * This class interprets the TestDescription as specified by the JDK tag
  * specification.
  *
  * @see com.sun.javatest.Script
  */
public class RegressionScript extends Script {
    /**
     * The method that interprets the tags provided in the test description and
     * performs actions accordingly.
     *
     * Any messages that are reported while accessing the {@link JDK} and related classes
     * will be reported to the main message section in the resulting {@code .jtr} file.
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

        String filterFault = params.filterFaults.get(td.getRootRelativeURL());
        if (filterFault != null)
            return Status.error(filterFault);

        Status status = passed("OK");
        String actions = td.getParameter("run");

//      System.out.println("--- ACTIONS: " + actions);
        // actions != null -- should never happen since we have reasonable
        // defaults

        testResult = getTestResult();
        testResult.putProperty("hostname", regEnv.getHostName());
        String[] props = { "user.name" };
        for (String p: props) {
            testResult.putProperty(p, System.getProperty(p));
        }
        testResult.putProperty("jtregVersion", getVersion());
        testResult.putProperty("testJDK", getTestJDK().getAbsolutePath());
        OS testOs = params.getTestOS();
        testResult.putProperty("testJDK_OS", testOs.toString());
        testResult.putProperty("testJDK_os.name", testOs.name);
        testResult.putProperty("testJDK_os.version", testOs.version);
        testResult.putProperty("testJDK_os.arch", testOs.arch);
        if (!getCompileJDK().equals(getTestJDK())) {
            testResult.putProperty("compileJDK", getCompileJDK().getAbsolutePath());
        }

        msgPW = testResult.getTestCommentWriter();

        defaultModules = params.getTestJDK().getDefaultModules(params, msgPW::println);
        systemModules = params.getTestJDK().getSystemModules(params, msgPW::println);

        try {
            int maxOutputSize = testSuite.getMaxOutputSize(td);
            if (maxOutputSize > 0) {
                try {
                    Method m = TestResult.class.getMethod("setMaxOutputSize", int.class);
                    m.invoke(testResult, maxOutputSize);
                } catch (ReflectiveOperationException e) {
                    System.err.println("Cannot set maxOutputSize in this build of jtreg: setting ignored");
                }
            }

            locations = new Locations(params, td, msgPW::println);
            if (params.getTestJDK().hasModules()) {
                modules = new Modules(params, td);
                if (!modules.isEmpty())
                    testResult.putProperty("modules", modules.toString());
            } else {
                modules = Modules.noModules;
            }


            // defaultExecMode may still be overridden in individual actions with /othervm
            defaultExecMode = testSuite.useOtherVM(td) ? ExecMode.OTHERVM : params.getExecMode();
            useBootClassPath = testSuite.useBootClassPath(td.getRootRelativePath());

            LinkedList<Action> actionList = parseActions(actions, true);

            needJUnit = false;
            needTestNG = false;

            for (Action a : actionList) {
                if (a instanceof JUnitAction) {
                    needJUnit = true;
                } else if (a instanceof TestNGAction) {
                    needTestNG = true;
                    // check for using mixed-mode
                    if (td.getParameter("importsJUnit") != null) {
                        needJUnit = true;
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
                for (Action a : actionList) {
                    for (String m : a.getModules()) {
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
                    printJDKInfo(msgPW, "compile JDK", getCompileJDK(), Collections.emptyList());
                    printJDKInfo(msgPW, "test JDK", getTestJDK(), getTestVMOptions());
                }

                for (LibLocn lib : locations.getLibs()) {
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
                    if (Files.isRegularFile(lib.absClsDir) && lib.absClsDir.getFileName().toString().endsWith(".jar")) {
                        msgPW.println("   jar file: " + lib.absClsDir);
                    } else {
                        msgPW.println("   class directory: " + lib.absClsDir);
                    }
                }

                while (!actionList.isEmpty()) {
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
        } catch (Expr.Fault
                | Locations.Fault
                | Modules.Fault
                | ParseActionsException
                | TestRunException
                | TestSuite.Fault
                | FileUtils.NIOFileOperationException e) {
            status = error(e.getMessage());
        } catch (UncheckedIOException e) {
            String msg = "IO exception: " + e.getMessage();
            if (e.getCause() != null)
                msg += " (" + e.getCause() + ")";
            status = error(msg);
        } catch (InvalidPathException e) {
            String msg = "Invalid path: " + e.getInput() + "; " + e.getMessage();
            if (e.getCause() != null)
                msg += " (" + e.getCause() + ")";
            status = error(msg);
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
                    msgPW.println("WARNING: failed to clean up files after test");
                    if (!agents.isEmpty()) {
                        msgPW.println("WARNING: closing agent(s)");
                    }
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
        pw.println(jdk.getAbsoluteHomeDirectory());
        String v = jdk.getVersionText(opts, pw::println);
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
    public static Set<File> getSourceFiles(RegressionParameters p, TestDescription td) {
        Consumer<String> logger = System.err::println;
        try {
            RegressionScript tmp = new RegressionScript();
            // init the script enough to parse the actions
            tmp.params = p;
            tmp.td= td;
            tmp.locations = new Locations(p, td, logger);
            tmp.modules = new Modules(p, td);
            tmp.defaultModules = p.getTestJDK().getDefaultModules(p, logger);
            tmp.systemModules = p.getTestJDK().getSystemModules(p, logger);
            String actions = td.getParameter("run");
            LinkedList<Action> actionList = tmp.parseActions(actions, false);
            Set<File> files = new TreeSet<>();
            while (! actionList.isEmpty()) {
                Action action = actionList.remove();
                Set<File> a = action.getSourceFiles();
                if (a != null)
                    files.addAll(a);
            }
            return files;
        } catch (Expr.Fault
                | Locations.Fault
                | Modules.Fault
                | ParseException
                | TestSuite.Fault e) {
            return Collections.emptySet();
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
    LinkedList<Action> parseActions(String actions, boolean stopOnError)
            throws ParseActionsException, ParseException, TestSuite.Fault, Expr.Fault {
        LinkedList<Action> actionList = new LinkedList<>();
        String[] runCmds = StringUtils.splitTerminator(LINESEP, actions);
        populateActionTable();

        Expr.Context exprContext = params.getExprContext();
        Map<String,String> testProps = getTestProperties();
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
            List<String> args = new ArrayList<>(Arrays.asList(tokens).subList(2, tokens.length));
            // [-debug, Foo.java] (everything after the big options token)
            Class<?> c = null;
            try {
                c = actionTable.get(verb);
                if (c == null) {
                    if (stopOnError)
                        throw new ParseActionsException(BAD_ACTION + verb);
                    continue;
                }
                Action action = (Action) (c.getDeclaredConstructor().newInstance());
                action.init(opts, processArgs(args, exprContext, testProps), getReason(tokens), this);
                actionList.add(action);
            } catch (IllegalAccessException e) {
                if (stopOnError)
                    throw new ParseActionsException(ILLEGAL_ACCESS_INIT + c);
            } catch (ReflectiveOperationException e) {
                if (stopOnError)
                    throw new ParseActionsException(CANT_INSTANTIATE + c + NOT_EXT_ACTION);
            }
        }

        return actionList;

    }

    boolean enablePreview() {
        String ep = td.getParameter("enablePreview");
        return ep != null && ep.equals("true");
    }

    boolean disablePreview() {
        String ep = td.getParameter("enablePreview");
        return ep != null && ep.equals("false");
    }

    private List<String> processArgs(List<String> args, Expr.Context c, Map<String,String> testProps)
            throws TestSuite.Fault, Expr.Fault, ParseException {
        if (!testSuite.getAllowSmartActionArgs(td))
            return args;

        boolean fast = true;
        for (String arg : args) {
            fast = fast && (!arg.contains("${"));
        }
        if (fast) {
            return args;
        }
        List<String> newArgs = new ArrayList<>();
        for (String arg : args) {
            newArgs.add(evalNames(arg, c, testProps));
        }
        return newArgs;
    }

    private static final Pattern namePattern = Pattern.compile("\\$\\{([A-Za-z0-9._]+)}");

    private static String evalNames(String arg, Expr.Context c, Map<String,String> testProps)
            throws Expr.Fault, ParseException {
        Matcher m = namePattern.matcher(arg);
        StringBuilder sb = null;
        // Note that '\' may appear in the replacement value for paths on Windows,
        // and so, in the following loop, avoid using Matcher::appendReplacement,
        // which interprets '\' and `$' in the replacement string.
        // Instead, use explicit operations to append the literal replacement value.
        int pos = 0;
        while (m.find(pos)) {
            if (sb == null) {
                sb = new StringBuilder();
            }
            String name = m.group(1);
            String value = testProps.containsKey(name) ? testProps.get(name) : c.get(name);
            if ("null".equals(value)) {
                throw new ParseException("unset property " + name);
            }
            sb.append(arg, pos, m.start());
            sb.append(value);
            pos = m.end();
        }
        if (sb == null) {
            return arg;
        } else {
            sb.append(arg.substring(pos));
            return sb.toString();
        }
    }

    //---------- methods for timing --------------------------------------------

    /**
     * Get the timeout to be used for a test.  Since the timeout for regression
     * tests is on a per-action basis rather than on a per-test basis, this
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
     * Returns the timeout to be used for an action.
     *
     * If no debug options have been set, the result will be the given value,
     * or a default value if the given value is negative, scaled by the timeout factor.
     *
     * If debug options have been set, the result will be 0, meaning "no timeout".
     *
     * @param time the value of the timeout specified in the action,
     *             or -1 if no value was specified
     * @return     the timeout, in seconds
     */
    protected int getActionTimeout(int time) {
        final int DEFAULT_ACTION_TIMEOUT = 120; // seconds
        return isTimeoutsEnabled()
                ? (int) ((time < 0 ? DEFAULT_ACTION_TIMEOUT : time) * getTimeoutFactor())
                : 0;
    }

    protected float getTimeoutFactor() {
        if (cacheJavaTestTimeoutFactor == -1) {
            // not synchronized, so in worst case may be set more than once
            float value = 1; // default
            try {
                // The timeout factor is available as both an integer (for backward compatibility)
                // and a floating point number.
                // Use [1] to get the floating point timeout factor
                String f = (regEnv == null ? null : regEnv.lookup("javatestTimeoutFactor")[1]);
                if (f != null)
                    value = Float.parseFloat(f);
            } catch (TestEnvironment.Fault
                     | NumberFormatException e) {
            }
            cacheJavaTestTimeoutFactor = value;
        }
        return cacheJavaTestTimeoutFactor;
    }

    private static float cacheJavaTestTimeoutFactor = -1;

    /**
     * Returns whether timeouts are (generally) enabled.
     *
     * @return {@code true} if timeouts are enabled, and {@code false} otherwise
     */
    protected boolean isTimeoutsEnabled() {
        // for now, timeouts are always enabled, unless debug options have been specified for the test
        return getTestDebugOptions().isEmpty();
    }

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
        StringBuilder sb = new StringBuilder();
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
     * have, return a map of name=value pairs.  Otherwise, return an empty map.
     *
     * @return     A map containing the tunneled environment variables.
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
    Path getNativeDir() {
        return params.getNativeDir();
    }

    /**
     * Returns the version of jtreg required by this test suite.
     */
    Version getRequiredVersion() {
        return params.getTestSuite().getRequiredVersion();
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

    Pattern getIgnoreRefLinesPattern() throws TestRunException {
        try {
            return params.getRefIgnoreLinesPattern();
        } catch (PatternSyntaxException e) {
            // this exception will only occur at most once per test run
            throw new TestRunException(e.getMessage(), e);
        }
    }

    String getTestQuery() {
        String testName = testResult.getTestName();
        return params.getTestQuery(testName);
    }

    //----------------------- computing paths ---------------------------------

    Path absTestWorkFile(String name) {
        return locations.absTestWorkFile(name);
    }

    Path absTestSrcDir() {
        return locations.absTestSrcDir();
    } // absTestSrcDir()

    Path absTestClsDir() {
        return locations.absTestClsDir();
    } // absTestClsDir()

    Path absTestScratchDir() {
        return scratchDirectory.dir.toPath().toAbsolutePath();
    } // absTestScratchDir()

    Path absTestClsTopDir() {
        return locations.absBaseClsDir();
    } // absTestClsTopDir()

    private boolean useBootClassPath(Path classdir) throws TestClassException {
        try {
            String rel = locations.absBaseClsDir().toUri().relativize(classdir.toFile().toURI()).getPath();
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
        if (libLocn == null || libLocn.isTest()) {
            if (multiModule) {
                msp.append(locations.absTestSrcDir());
            } else {
                Path testSrcDir = locations.absTestSrcDir(module);
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
                sp.append(libLocn.absSrcDir.resolve(module));
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
                if (needJUnit) params.getJUnitPath().asList().forEach(mp::append);
                if (needTestNG) params.getTestNGPath().asList().forEach(mp::append);
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
                if (needJUnit) params.getJUnitPath().asList().forEach(mp::append);
                if (needTestNG) params.getTestNGPath().asList().forEach(mp::append);
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

    boolean useBootClassPath() {
        return useBootClassPath;
    }

    boolean usePatchModules() {
        return usePatchModules;
    }

    boolean hasTestPatchMods() {
        Path testModulesDir = locations.absTestPatchDir();
        if (Files.isDirectory(testModulesDir)) {
            for (Path f : FileUtils.listFiles(testModulesDir)) {
                if (Files.isDirectory(f))
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
        Path testModulesDir = locations.absTestModulesDir();
        if (Files.isDirectory(testModulesDir)) {
            for (Path f : FileUtils.listFiles(testModulesDir)) {
                if (Files.isDirectory(f))
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

    SummaryReporter getTestNGSummaryReporter() {
        return SummaryReporter.forTestNG(workDir);
    }

    SummaryReporter getJUnitSummaryReporter() {
        return SummaryReporter.forJUnit(workDir);
    }

    Lock getLockIfRequired() {
        return testSuite.needsExclusiveAccess(td) ? Lock.get(params) : null;
    }

    int getNextSerial() {
        return nextSerial++;
    }

    private int nextSerial = 0;

    PrintWriter getMessageWriter() {
        return msgPW;
    }

    //--------------------------------------------------------------------------

    TimeoutHandlerProvider getTimeoutHandlerProvider() throws TestRunException {

        try {
            return params.getTimeoutHandlerProvider();
        } catch (MalformedURLException e) {
            throw new TestRunException("Can't get timeout handler provider", e);
        }
    }

    String getTestThreadFactory() {
        return params.getTestThreadFactory();
    }

    String getTestThreadFactoryPath() {
        return params.getTestThreadFactoryPath();
    }

    //--------------------------------------------------------------------------

    JDK getTestJDK() {
        return params.getTestJDK();
    }

    JDK_Version getTestJDKVersion() {
        return getTestJDK().getVersion(params, msgPW::println);
    }

    Path getJavaProg() {
        return params.getTestJDK().getJavaProg();
    }

    //--------------------------------------------------------------------------

    JDK getCompileJDK() {
        return params.getCompileJDK();
    }

    JDK_Version getCompileJDKVersion() {
        return getCompileJDK().getVersion(params, msgPW::println);
    }

    Path getJavacProg() {
        return params.getCompileJDK().getJavacProg();
    }

    //--------------------------------------------------------------------------

    // Get the standard properties to be set for tests

    Map<String, String> getTestProperties()  {
        // initialize the properties with standard properties common to all tests
        Map<String, String> p = new LinkedHashMap<>(params.getBasicTestProperties());
        // add test-specific properties
        String testName = testResult.getTestName();
        p.put("test.name", testName);
        String testQuery = params.getTestQuery(testName);
        if (testQuery != null) {
            p.put("test.query", testQuery);
        }
        Verbose verbose = params.getVerbose();
        if (verbose != null) {
            p.put("test.verbose", verbose.toString());
        }
        p.put("test.file", locations.absTestFile().toString());
        p.put("test.src", locations.absTestSrcDir().toString());
        p.put("test.src.path", toString(locations.absTestSrcPath()));
        p.put("test.classes", locations.absTestClsDir().toString());
        p.put("test.class.path", toString(locations.absTestClsPath()));
        if (getExecMode() == ExecMode.AGENTVM) {
            // The following will be added to java.class.path on the test VM
            // and is not for general use
            SearchPath path = new SearchPath()
                    .append(locations.absTestClsDir())
                    .append(locations.absTestSrcDir())
                    .append(locations.absLibClsList(LibLocn.Kind.PACKAGE))
                    .append(locations.absLibSrcJarList());
            p.put("test.class.path.prefix", path.toString());
        }
        if (!modules.isEmpty())
            p.put("test.modules", modules.toString());
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
        if (enablePreview()) {
            p.put("test.enable.preview", "true");
        }
        if (disablePreview()) {
            p.put("test.enable.preview", "false");
        }
        p.put("test.root", getTestRootDir().getPath());
        return Collections.unmodifiableMap(p);
    }
    // where
    private String toString(List<Path> files) {
        return files.stream()
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
    }
//    // where
//    private String toString(List<File> files) {
//        return files.stream()
//                .map(File::getPath)
//                .collect(Collectors.joining(File.pathSeparator));
//    }

    File getTestRootDir() {
        return params.getTestSuite().getRootDir();
    }

    //--------------------------------------------------------------------------

    /*
     * Get an agent for a VM with the given VM options.
     */
    Agent getAgent(JDK jdk, SearchPath classpath, List<String> testVMOpts,
                   String testThreadFactory, String testThreadFactoryPath) throws Agent.Fault {
        JDKOpts vmOpts = new JDKOpts();
        vmOpts.addAll("-classpath", classpath.toString());
        vmOpts.addAll(testVMOpts);

        if (testThreadFactory != null) {
            // Add property to differ agents with and without MainWrapper
            vmOpts.add("-D" + MainWrapper.TEST_THREAD_FACTORY + "=" + testThreadFactory);
        }

        if (params.getTestJDK().hasModules()) {
            vmOpts.addAllPatchModules(new SearchPath(params.getWorkDirectory().getFile("patches").toPath()));
        }

        /*
         * A script only uses one agent at a time, and only one, maybe two,
         * different agents overall, for actions that use agentVM mode (i.e.
         * CompileAction and MainAction.) Therefore, use a simple list to
         * record the agents that the script has already obtained for use.
         */
        for (Agent agent: agents) {
            if (agent.matches(absTestScratchDir().toFile(), jdk, vmOpts.toList())) {
                return agent;
            }
        }

        Map<String, String> envVars = new HashMap<>(getEnvVars());
        // some tests are inappropriately relying on the CLASSPATH environment
        // variable being set, so ensure it is set. See equivalent code in MainAction
        // and Main.execChild. Note we cannot set exactly the same classpath as
        // for othervm, because we should not include test-specific info
        SearchPath cp = new SearchPath().append(jdk.getJDKClassPath()).append(getJavaTestClassPath());
        envVars.put("CLASSPATH", cp.toString());

        Agent.Pool p = Agent.Pool.instance(params);
        Agent agent = p.getAgent(absTestScratchDir().toFile(), jdk, vmOpts.toList(), envVars,
                testThreadFactory, testThreadFactoryPath);
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
        if (!agents.isEmpty()) {
            Agent.Pool pool = Agent.Pool.instance(params);
            for (Agent agent: agents) {
                pool.save(agent);
            }
        }
    }

    List<Agent> agents = new ArrayList<>();

    //--------------------------------------------------------------------------

    boolean useWindowsSubsystemForLinux() {
        return params.useWindowsSubsystemForLinux();
    }

    //----------internal classes-----------------------------------------------

    void saveScratchFile(Path file, Path dest) {
        scratchDirectory.retainFile(file.toFile(), dest.toFile());
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

    private static String getVersion() {
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
    private static String version;

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
    private PrintWriter msgPW;
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
