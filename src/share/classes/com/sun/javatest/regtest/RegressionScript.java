/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
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

import static com.sun.javatest.regtest.RStatus.*;

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
    public Status run(String[] argv, TestDescription td, TestEnvironment env) {
        if (!(env instanceof RegressionEnvironment))
            throw new AssertionError();

        long started = System.currentTimeMillis();

        regEnv = (RegressionEnvironment) env;
        params = regEnv.params;
        testSuite = (RegressionTestSuite) params.getTestSuite();

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

        PrintWriter msgPW = testResult.getTestCommentWriter();

        try {
            locations = new Locations(regEnv, td);

            // defaultExecMode may still be overridden in individual actions with /othervm
            defaultExecMode = testSuite.useOtherVM(td) ? ExecMode.OTHERVM : params.getExecMode();
            useBootClassPath = testSuite.useBootClassPath(td.getRootRelativePath());

            LinkedList<Action> actionList = parseActions(actions, true);

            needJUnit = false;
            for (Action a: actionList) {
                if (a instanceof JUnitAction)
                    needJUnit = true;
            }
            if (needJUnit && !params.isJUnitAvailable()) {
                throw new TestRunException("JUnit not available: see the FAQ or online help for details");
            }

            needTestNG = false;
            for (Action a: actionList) {
                if (a instanceof TestNGAction)
                    needTestNG = true;
            }
            if (needTestNG && !params.isTestNGAvailable()) {
                throw new TestRunException("TestNG not available: see the FAQ or online help for details");
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
        } catch (InterruptedException e) {
            status = error("Interrupted! " + e.getLocalizedMessage());
        } catch (ScratchDirectory.Fault e) {
            String msg = e.getLocalizedMessage();
            if (e.getCause() != null)
                msg += " (" + e.getCause() + ")";
            status = error(msg);
        } catch (TestSuite.Fault e) {
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
            if (params.isRetainEnabled()) {
                String errmsg = null;
                try {
                    if (scratchDirectory != null) {
                        scratchDirectory.retainFiles(status, msgPW);
                    } else {
                        errmsg = "No scratch directory";
                    }
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
                RegressionEnvironment e = (RegressionEnvironment) p.getEnv();
                e.put("testClassDir", "/NO/CLASSES/");
                locations = new Locations(e, td);
            }
            String actions = td.getParameter("run");
            LinkedList<Action> actionList = parseActions(actions, false);
            Set<File> files = new TreeSet<File>();
            while (! actionList.isEmpty()) {
                Action action = actionList.remove();
                Set<File> a = action.getSourceFiles();
                if (a != null)
                    files.addAll(a);
            }
            return files;
        } catch (TestRunException e) {
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
        LinkedList<Action> actionList = new LinkedList<Action>();
        String[] runCmds = StringArray.splitTerminator(LINESEP, actions);
        populateActionTable();

        for (String runCmd : runCmds) {
            // e.g. reason compile/fail/ref=Foo.ref -debug Foo.java
            // where "reason" indicates why the action should run
            String[] tokens = StringArray.splitWS(runCmd);
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
     *             zero, then the default timeout will be returned.
     * @return     The timeout in seconds.
     */
    protected int getActionTimeout(int time) {
        if (time == 0)
            time = 120;
        return (int) (time * getTimeoutFactor());
    }

    protected synchronized float getTimeoutFactor() {
        if (cacheJavaTestTimeoutFactor == -1) {
            cacheJavaTestTimeoutFactor = 1; // default
            try {
                // use [1] to get the floating point timeout factor
                String f = (regEnv == null ? null : regEnv.lookup("javatestTimeoutFactor")[1]);
                if (f != null)
                    cacheJavaTestTimeoutFactor = Float.parseFloat(f);
            } catch (TestEnvironment.Fault e) {
            } catch (NumberFormatException e) {
            }
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

    //----------------------- computing paths ---------------------------------

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

    SearchPath getTestClassPath() throws TestClassException {
        return getTestClassPath(false);
    }

    SearchPath getTestClassPath(boolean testOnBootClassPath) throws TestClassException {
        return getTestClassPaths(testOnBootClassPath)[0];
    } // getTestClassPath()

    SearchPath getTestBootClassPath(boolean testOnBootClassPath) throws TestClassException {
        return getTestClassPaths(testOnBootClassPath)[1];
    } // getTestBootClassPath()

    private SearchPath[] getTestClassPaths(boolean testOnBootClassPath) throws TestClassException {
        SearchPath cp = new SearchPath();
        SearchPath bcp = new SearchPath();
        JDK jdk = getTestJDK();
        if (testOnBootClassPath) {
            bcp.append(locations.absTestClsDir());
            bcp.append(locations.absClsLibList());
            bcp.append(locations.absSrcJarLibList());
            bcp.append(jdk.getJDKClassPath());
            bcp.append(getCPAPPEND());
        } else {
            cp.append(locations.absTestClsDir());
            cp.append(locations.absTestSrcDir()); // required??
            for (File lib: locations.absClsLibList())
                (useBootClassPath(lib) ? bcp : cp).append(lib);
            cp.append(locations.absSrcJarLibList());
            cp.append(jdk.getJDKClassPath());
            cp.append(getCPAPPEND());
        }

        return new SearchPath[] { cp, bcp };
    }

    private SearchPath getCPAPPEND() {
        // handle cpa option to jtreg
        Map<String, String> envVars = getEnvVars();
        String cpa = envVars.get("CPAPPEND");
        if (cpa != null) {
            // the cpa we were passed always uses '/' as FILESEP, make
            // sure to use the proper one for the platform
            return new SearchPath(cpa.replace('/', File.separatorChar));
        }
        return new SearchPath();
    }

    private boolean useBootClassPath(File classdir) throws TestClassException {
        try {
            String rel = locations.absBaseClsDir().toURI().relativize(classdir.toURI()).getPath();
            return testSuite.useBootClassPath(rel);
        } catch (TestSuite.Fault f) {
            throw new TestClassException(f.toString());
        }
    }

    private SearchPath cacheCompileClassPath;
    SearchPath getCompileClassPath() throws TestClassException {
        if (cacheCompileClassPath == null) {
            cacheCompileClassPath = new SearchPath();
            JDK jdk = getCompileJDK();

            cacheCompileClassPath.append(locations.absTestClsDir());
            cacheCompileClassPath.append(locations.absTestSrcDir()); // required??
            cacheCompileClassPath.append(locations.absClsLibList());
            cacheCompileClassPath.append(locations.absSrcJarLibList());
            cacheCompileClassPath.append(jdk.getJDKClassPath());

            if (needJUnit)
                cacheCompileClassPath.append(params.getJUnitJar());

            if (needTestNG)
                cacheCompileClassPath.append(params.getTestNGJar());

            // handle cpa option to jtreg
            Map<String, String> envVars = getEnvVars();
            String cpa = envVars.get("CPAPPEND");
            if (cpa != null) {
                // the cpa we were passed always uses '/' as FILESEP, make
                // sure to use the proper one for the platform
                cpa = cpa.replace('/', File.separatorChar);
                cacheCompileClassPath.append(cpa);
            }

        }
        return cacheCompileClassPath;
    } // getCompileClassPath()

    // necessary only for JDK1.2 and above
    private SearchPath cacheCompileSourcePath;

    /**
     * Returns the fully-qualified directory name where the source resides.
     *
     * @param fileName The exact name of the file to locate.
     * @param dirList  A list of directories in which to search. The list will
     *             contain the directory of the defining file of the test
     *             followed by the library list.
     */
    SearchPath getCompileSourcePath() {
        if (cacheCompileSourcePath == null) {
            cacheCompileSourcePath = new SearchPath();
            cacheCompileSourcePath.append(locations.absTestSrcDir());
            cacheCompileSourcePath.append(locations.absSrcLibList());
        }
        return cacheCompileSourcePath;
    } // getCompileSourcePath()

    boolean useBootClassPath() {
        return useBootClassPath;
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

    File getJUnitJar() {
        return params.getJUnitJar();
    }

    boolean isTestNGRequired() {
        return needTestNG;
    }

    File getTestNGJar() {
        return params.getTestNGJar();
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

    JDK getTestJDK() {
        return params.getTestJDK();
    }

    JDK.Version getTestJDKVersion() {
        return getTestJDK().getVersion(params);
    }

    String getJavaProg() {
        return params.getTestJDK().getJavaProg().getPath();
    }

    //--------------------------------------------------------------------------

    JDK getCompileJDK() {
        return params.getCompileJDK();
    }

    JDK.Version getCompileJDKVersion() {
        return getCompileJDK().getVersion(params);
    }

    String getJavacProg() {
        return params.getCompileJDK().getJavacProg().getPath();
    }

    //--------------------------------------------------------------------------

    // Get the standard properties to be set for tests

    Map<String, String> getTestProperties() throws TestClassException {
        Map<String, String> p = new LinkedHashMap<String, String>();
        // The following will be added to javac.class.path on the test JVM
        switch (getExecMode()) {
            case AGENTVM:
            case SAMEVM:
                SearchPath path = new SearchPath()
                    .append(locations.absTestClsDir())
                    .append(locations.absTestSrcDir())
                    .append(locations.absClsLibList());
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
        File nativeDir = getNativeDir();
        if (nativeDir != null)
            p.put("test.nativepath", nativeDir.getAbsolutePath());
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
    Agent getAgent(JDK jdk, SearchPath classpath, List<String> testVMOpts) throws IOException {
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

        Map<String, String> envVars = new HashMap<String, String>();
        envVars.putAll(getEnvVars());
        // some tests are inappropriately relying on the CLASSPATH environment
        // variable being set, so ensure it is set. See equivalent code in MainAction
        // and Main.execChild. Note we cannot set exactly the same classpath as
        // for othervm, because we should not include test-specific info
        SearchPath cp = new SearchPath(getJavaTestClassPath()).append(jdk.getToolsJar());
        envVars.put("CLASSPATH", cp.toString());

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

    static final String WRAPPEREXTN = ".jta";

    private static final String LINESEP  = System.getProperty("line.separator");

    private static final String
        CANT_INSTANTIATE      = "Unable to instantiate: ",
        NOT_EXT_ACTION        = " does not extend Action",
        ILLEGAL_ACCESS_INIT   = "Illegal access to init method: ",
        BAD_ACTION            = "Bad action for script: ",
        ADD_BAD_SUBTYPE       = "Class must be a subtype of ";

    //----------member variables-----------------------------------------------

    private final Map<String, Class<?>> actionTable = new HashMap<String, Class<?>>();
    private TestResult testResult;

    private RegressionEnvironment regEnv;
    private RegressionParameters params;
    private RegressionTestSuite testSuite;
    private boolean useBootClassPath;
    private ExecMode defaultExecMode;
    private boolean needJUnit;
    private boolean needTestNG;
    private ScratchDirectory scratchDirectory;
    Locations locations;

}
