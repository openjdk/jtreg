/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.config;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.sun.interview.Interview;
import com.sun.interview.Question;
import com.sun.javatest.CompositeFilter;
import com.sun.javatest.ExcludeList;
import com.sun.javatest.InterviewParameters;
import com.sun.javatest.Parameters;
import com.sun.javatest.ProductInfo;
import com.sun.javatest.Status;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestEnvironment;
import com.sun.javatest.TestFilter;
import com.sun.javatest.interview.BasicInterviewParameters;
import com.sun.javatest.regtest.agent.JDK_Version;
import com.sun.javatest.regtest.agent.SearchPath;
import com.sun.javatest.regtest.exec.TimeoutHandlerProvider;
import com.sun.javatest.regtest.util.StringUtils;

import static com.sun.javatest.regtest.util.StringUtils.join;


public class RegressionParameters
    extends BasicInterviewParameters
    implements Parameters.EnvParameters
{
    public RegressionParameters(String tag, RegressionTestSuite testSuite) throws InterviewParameters.Fault {
        super(tag, testSuite);

        setTitle("jtreg Configuration Editor"); // I18N
        setEdited(false);
    }

    //---------------------------------------------------------------------

    @Override
    public RegressionTestSuite getTestSuite() {
        return (RegressionTestSuite) super.getTestSuite();
    }

    //---------------------------------------------------------------------

    public void setTests(Collection<String> tests) {
        setTests(tests == null ? null : tests.toArray(new String[tests.size()]));
    }

    public void setTests(String[] tests) {
        MutableTestsParameters mtp =
            (MutableTestsParameters) getTestsParameters();
        mtp.setTests(tests);
    }

    public void setKeywordsExpr(String expr) {
        MutableKeywordsParameters mkp =
            (MutableKeywordsParameters) getKeywordsParameters();
        mkp.setKeywords(MutableKeywordsParameters.EXPR, expr);
    }

    /**
     * This method is to workaround an earlier workaround
     * (in {@link BasicInterviewParameters#getParameters})
     * for the max concurrency.
     * @return  the maximum permitted concurrency
     */
    @Override
    protected int getMaxConcurrency() {
        return Parameters.ConcurrencyParameters.MAX_CONCURRENCY;
    }

    public void setConcurrency(int conc) {
        MutableConcurrencyParameters mcp =
            (MutableConcurrencyParameters) getConcurrencyParameters();
        mcp.setConcurrency(conc);
    }

    public void setTimeoutFactor(float tfac) {
        MutableTimeoutFactorParameters mtfp =
            (MutableTimeoutFactorParameters) getTimeoutFactorParameters();
        mtfp.setTimeoutFactor(tfac);
    }

    public void setExcludeLists(File[] files) {
        MutableExcludeListParameters mep =
            (MutableExcludeListParameters) getExcludeListParameters();
        mep.setExcludeFiles(files);
    }

    public void setPriorStatusValues(boolean[] b) {
        MutablePriorStatusParameters mpsp =
            (MutablePriorStatusParameters) getPriorStatusParameters();
        mpsp.setPriorStatusValues(b);
    }

    //---------------------------------------------------------------------

    @Override
    public TestEnvironment getEnv() {
        try {
            return new RegressionEnvironment(this);
        }
        catch (com.sun.javatest.TestEnvironment.Fault e) {
            return null;
        }
    }

    @Override
    public Parameters.EnvParameters getEnvParameters() {
        return this;
    }

    @Override
    protected Question getEnvFirstQuestion() {
        return getEnvSuccessorQuestion();
    }

    //---------------------------------------------------------------------

    /* Although a filter can throw TestFilter.Fault, currently all such
     * exceptions are silently ignored!
     * The following map provides a way of recording whether a problem
     * was encountered by a filter.
     */
    public Map<TestDescription, String> filterFaults = new HashMap<>();

    /* A RegressionContext is used by various filters, but initializing it may throw an
     * exception. Therefore, it should be initialized explicitly, and the exception
     * handled, rather than lazily, in circumstances where the exception might be ignored.
     */
    private Expr.Context exprContext;

    public void initExprContext() throws JDK.Fault {
        exprContext = new RegressionContext(this);
    }

    @Override
    public TestFilter getRelevantTestFilter() {
        if (relevantTestFilter == UNSET) {
            List<TestFilter> filters = new ArrayList<>();
            TestFilter mf = getModulesFilter();
            if (mf != null)
                filters.add(mf);

            TestFilter rf = getRequiresFilter();
            filters.add(rf);

            TestFilter tlf = getTimeLimitFilter();
            if (tlf != null)
                filters.add(tlf);

            TestFilter f = new CompositeFilter(filters.toArray(new TestFilter[filters.size()]));
            relevantTestFilter = new CachingTestFilter(f);
        }
        return relevantTestFilter;

    }

    CachingTestFilter relevantTestFilter = UNSET;

    TestFilter getModulesFilter() {
        JDK jdk = getTestJDK();
        if (jdk == null || jdk.getVersion(this).compareTo(JDK_Version.V9) == -1)
            return null;

        final Set<String> availModules = jdk.getSystemModules(this);
        if (availModules.isEmpty())
            return null;

        return new TestFilter() {
            @Override
            public String getName() {
                return "ModulesFilter";
            }

            @Override
            public String getDescription() {
                return "Select tests for which all required modules are available";
            }

            @Override
            public String getReason() {
                return "A required module is not available";
            }

            @Override
            public boolean accepts(TestDescription td) {
                String reqdModules = td.getParameter("modules");
                if (reqdModules == null)
                    return true;

                for (String m: reqdModules.split(" ")) {
                    if (m.length() == 0)
                        continue;
                    int slash = m.indexOf("/");
                    String name = slash == -1 ? m : m.substring(0, slash);
                    if (!availModules.contains(name))
                        return false;
                }

                return true;
            }
        };
    }

    TestFilter getRequiresFilter() {
        return new TestFilter() {
            @Override
            public String getName() {
                return "RequiresFilter";
            }

            @Override
            public String getDescription() {
                return "Select tests that satisfy a given set of platform requirements";
            }

            @Override
            public String getReason() {
                return "The platform does not meet the specified requirements";
            }

            @Override
            public boolean accepts(TestDescription td) {
                try {
                    String requires = td.getParameter("requires");
                    if (requires == null)
                        return true;
                    return Expr.parse(requires, exprContext).evalBoolean(exprContext);
                } catch (Expr.Fault ex) {
                    filterFaults.put(td, "Error evaluating expression: " + ex.getMessage());
                    // While it may seem more obvious to return false in this case,
                    // that would make it easier to overlook the fault since the
                    // test will have been quietly filtered out.
                    // By returning true, we give downstream code the opportunity
                    // to check whether a filter fault occurred, and to report
                    // the error back to the user.
                    return true;
                }
            }
        };
    }

    TestFilter getTimeLimitFilter() {
        if (timeLimit <= 0)
            return null;

        return new TestFilter() {

            @Override
            public String getName() {
                return "TestLimitFilter";
            }

            @Override
            public String getDescription() {
                return "Select tests that do not exceed a specified timeout value";
            }

            @Override
            public String getReason() {
                return "Test declares a timeout which exceeds the requested time limit";
            }

            @Override
            public boolean accepts(TestDescription td) throws TestFilter.Fault {
                String maxTimeoutValue = td.getParameter("maxTimeout");
                if (maxTimeoutValue != null) {
                    try {
                        int maxTimeout = Integer.parseInt(maxTimeoutValue);
                        if (maxTimeout == 0 || maxTimeout > timeLimit)
                            return false;
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
                return true;
            }

        };
    }

    /**
     * {@inheritDoc}
     *
     * Use a caching filter partly for performance reasons, and partly to make
     * it easier to calculate the number of rejected tests. Simply counting
     * the number of true/false results is insufficient because the filter
     * may be called more than once per test. An alternative approach is to use
     * an ObservableTestFilter, and count the number of of true/false results in
     * between Harness.Observer.startingTestRun and Harness.Observer.finishedTestRun.
     * But that doesn't work correctly since the readAheadTestIterator in Harness
     * is created before the notification to Harness.Observer.startingTestRun.
     *
     * @return a filter based on the current exclude list(s)
     */
    @Override
    public CachingTestFilter getExcludeListFilter() {
        // warning: os.arch depends on JVM; it ideally should come from the test jdk
        OS os = OS.current();

        // On JPRT, we see the following various types of values for these properties
        //    os.arch              amd64
        //    os.arch              i386
        //    os.arch              sparc
        //    os.arch              x86
        //    os.name              Linux
        //    os.name              SunOS
        //    os.name              Windows 2003
        //    os.name              Windows XP
        //    os.version           2.6.27.21-78.2.41.fc9.i686
        //    os.version           2.6.27.21-78.2.41.fc9.x86_64
        //    os.version           5.1
        //    os.version           5.10
        //    os.version           5.2
        // On a Mac, we see the following types of values
        //    os.arch              x86_64
        //    os.arch              universal
        //    os.name              Darwin
        //    os.name              Mac OS X
        //    os.version           10.6.7
        //    os.version           10.7.4

        // ProblemList.txt uses the following encoding
        //    generic-all   Problems on all platforms
        //    generic-ARCH  Where ARCH is one of: sparc, sparcv9, x64, i586, etc.
        //    OSNAME-all    Where OSNAME is one of: solaris, linux, windows
        //    OSNAME-ARCH   Specific on to one OSNAME and ARCH, e.g. solaris-x64
        //    OSNAME-REV    Specific on to one OSNAME and REV, e.g. solaris-5.8

        final Set<String> excludedPlatforms = new HashSet<>();
        for (String p: Arrays.asList(os.name, os.name.replaceAll("\\s", ""), os.family, "generic")) {
            for (String q: Arrays.asList(null, os.arch, os.simple_arch, os.version, os.simple_version, "all")) {
                String ep = (q == null) ? p : p + "-" + q;
                excludedPlatforms.add(ep.toLowerCase());
            }
        }

        // System.err.println("excluded platforms: " + excludedPlatforms);


        if (excludeListFilter == UNSET) {
            final ExcludeList el = getExcludeList();
            if (el == null)
                excludeListFilter = null;
            else {
                excludeListFilter = new CachingTestFilter(new TestFilter() {
                    @Override
                    public String getName() {
                        return "jtregExcludeListFilter";
                    }

                    @Override
                    public String getDescription() {
                        return "Select tests which are not excluded on any exclude list";
                    }

                    @Override
                    public String getReason() {
                        return "Test has been excluded by an exclude list";
                    }

                    @Override
                    public boolean accepts(TestDescription td) throws TestFilter.Fault {
                        ExcludeList.Entry e = el.getEntry(td.getRootRelativeURL());
                        if (e == null)
                            return true;

                        String[] platforms = e.getPlatforms();
                        if (platforms.length == 0 || (platforms.length == 1 && platforms[0].length() == 0)) {
                            // allow for old ProblemList.txt format
                            String[] bugIds = e.getBugIdStrings();
                            if (bugIds.length > 0 && !bugIds[0].matches("0|([1-9][0-9,]*)"))
                                platforms = bugIds;
                        }

                        if (platforms.length == 0 || (platforms.length == 1 && platforms[0].length() == 0))
                            return false;

                        for (String p: platforms) {
                            if (excludedPlatforms.contains(p.toLowerCase()))
                                return false;
                        }

                        return true;
                    }
                });
            }
        }
        return excludeListFilter;
    }

    private CachingTestFilter excludeListFilter = UNSET;

    /**
     * {@inheritDoc}
     *
     * Use a caching filter partly for performance reasons, and partly to make
     * it easier to analyze the rejected tests.
     *
     * @return a filter based on the keywords given on the command line.
     */
    @Override
    public CachingTestFilter getKeywordsFilter() {
        if (keywordsFilter == UNSET) {
            TestFilter f = super.getKeywordsFilter();
            keywordsFilter = (f == null) ? null : new CachingTestFilter(f);
        }
        return keywordsFilter;
    }

    private CachingTestFilter keywordsFilter = UNSET;

    private static final CachingTestFilter UNSET = new CachingTestFilter(new TestFilter() {
        @Override
        public String getName() {
            throw new IllegalStateException();
        }
        @Override
        public String getDescription() {
            throw new IllegalStateException();
        }
        @Override
        public String getReason() {
            throw new IllegalStateException();
        }
        @Override
        public boolean accepts(TestDescription td) throws TestFilter.Fault {
            throw new IllegalStateException();
        }
    });

    //---------------------------------------------------------------------

    // The following (load and save) are an interim consequence of not using proper
    // interview questions to store configurations values.
    // The critical values to preserve are any that may be set by direct setXYZ methods
    // below.
    // A better solution is to migrate to using interview questions where possible,
    // and potentially allow the values to be modified via the Config Editor

    private static final String ENVVARS = ".envVars";
    private static final String CHECK = ".check";
    private static final String COMPILE_JDK = ".compilejdk";
    private static final String TEST_JDK = ".testjdk";
    private static final String EXEC_MODE = ".execMode";
    private static final String TEST_VM_OPTIONS = ".testVMOpts";
    private static final String TEST_COMPILER_OPTIONS = ".testCompilerOpts";
    private static final String TEST_JAVA_OPTIONS = ".testJavaOpts";
    private static final String IGNORE = ".ignore";
    private static final String RETAIN_ARGS = ".retain";
    private static final String JUNIT = ".junit";
    private static final String TESTNG = ".testng";
    private static final String ASMTOOLS = ".asmtools";
    private static final String TIMELIMIT = ".timeLimit";
    private static final String REPORTDIR = ".reportDir";
    private static final String EXCLUSIVE_LOCK = ".exclLock";
    private static final String NATIVEDIR = ".nativeDir";
    private static final String TIMEOUT_HANDLER = ".timeoutHandler";
    private static final String TIMEOUT_HANDLER_PATH = ".timeoutHandlerPath";
    private static final String TIMEOUT_HANDLER_TIMEOUT = ".timeoutHandlerTimeout";

    @Override @SuppressWarnings({"rawtypes", "unchecked"})
    public void load(Map data, boolean checkChecksum) throws Interview.Fault {
        super.load(data, checkChecksum);
        load0((Map<String,String>) data, checkChecksum);
    }

    private void load0(Map<String,String> data, boolean checkChecksum) throws Interview.Fault {
        String prefix = getTag();

        String v;

        v = data.get(prefix + ENVVARS);
        if (v != null)
            setEnvVars(deserializeEnv(v, "\n"));

        v = data.get(prefix + CHECK);
        if (v != null)
            setCheck(v.equals("true"));

        v = data.get(prefix + EXEC_MODE);
        if (v != null)
            setExecMode(ExecMode.valueOf(v));

        v = data.get(prefix + IGNORE);
        if (v != null)
            setIgnoreKind(IgnoreKind.valueOf(v));

        v = data.get(prefix + COMPILE_JDK);
        if (v != null)
            setCompileJDK(JDK.of(v));

        v = data.get(prefix + TEST_JDK);
        if (v != null)
            setTestJDK(JDK.of(v));

        v = data.get(prefix + TEST_VM_OPTIONS);
        if (v != null && v.length() > 0)
            setTestVMOptions(Arrays.asList(StringUtils.splitSeparator("\n", v)));

        v = data.get(prefix + TEST_COMPILER_OPTIONS);
        if (v != null && v.length() > 0)
            setTestCompilerOptions(Arrays.asList(StringUtils.splitSeparator("\n", v)));

        v = data.get(prefix + TEST_JAVA_OPTIONS);
        if (v != null && v.length() > 0)
            setTestJavaOptions(Arrays.asList(StringUtils.splitSeparator("\n", v)));

        v = data.get(prefix + RETAIN_ARGS);
        if (v != null && v.length() > 0)
            setRetainArgs(Arrays.asList(StringUtils.splitSeparator("\n", v)));

        v = data.get(prefix + JUNIT);
        if (v != null)
            setJUnitPath(new SearchPath(v));

        v = data.get(prefix + TESTNG);
        if (v != null)
            setTestNGPath(new SearchPath(v));

        v = data.get(prefix + ASMTOOLS);
        if (v != null)
            setAsmToolsPath(new SearchPath(v));

        v = data.get(prefix + TIMELIMIT);
        if (v != null)
            setTimeLimit(Integer.parseInt(v));

        v = data.get(prefix + REPORTDIR);
        if (v != null)
            setReportDir(new File(v));

        v = data.get(prefix + EXCLUSIVE_LOCK);
        if (v != null)
            setExclusiveLock(new File(v));

        v = data.get(prefix + NATIVEDIR);
        if (v != null)
            setNativeDir(new File(v));

        v = data.get(prefix + TIMEOUT_HANDLER);
        if (v != null)
            setTimeoutHandler(v);

        v = data.get(prefix + TIMEOUT_HANDLER_PATH);
        if (v != null)
            setTimeoutHandlerPath(v);

        v = data.get(prefix + TIMEOUT_HANDLER_TIMEOUT);
        if (v != null)
            setTimeoutHandlerTimeout(v);
    }

    @Override @SuppressWarnings({"unchecked", "rawtypes"})
    public void save(Map data) {
        save0((Map<String, String>) data);
        super.save(data);
    }

    private void save0(Map<String, String> data) {
        String prefix = getTag();

        if (envVars != null)
            data.put(prefix + ENVVARS, serializeEnv(envVars, "\n"));

        data.put(prefix + CHECK, String.valueOf(check));
        data.put(prefix + EXEC_MODE, String.valueOf(execMode));
        data.put(prefix + IGNORE, String.valueOf(ignoreKind));

        if (testJDK != null)
            data.put(prefix + TEST_JDK, testJDK.getPath());

        if (compileJDK != null)
            data.put(prefix + COMPILE_JDK, compileJDK.getPath());

        if (retainArgs != null && retainArgs.size() > 0)
            data.put(prefix + RETAIN_ARGS, join(retainArgs, "\n"));

        if (testVMOpts != null && testVMOpts.size() > 0)
            data.put(prefix + TEST_VM_OPTIONS, join(testVMOpts, "\n"));

        if (testCompilerOpts != null && testCompilerOpts.size() > 0)
            data.put(prefix + TEST_COMPILER_OPTIONS, join(testCompilerOpts, "\n"));

        if (testJavaOpts != null && testJavaOpts.size() > 0)
            data.put(prefix + TEST_JAVA_OPTIONS, join(testJavaOpts, "\n"));

        if (junitPath != null)
            data.put(prefix + JUNIT, junitPath.toString());

        if (testngPath != null)
            data.put(prefix + TESTNG, testngPath.toString());

        if (asmToolsPath != null)
            data.put(prefix + ASMTOOLS, asmToolsPath.toString());

        if (timeLimit > 0)
            data.put(prefix + TIMELIMIT, String.valueOf(timeLimit));

        if (reportDir != null)
            data.put(prefix + REPORTDIR, reportDir.getPath());

        if (exclusiveLock != null)
            data.put(prefix + EXCLUSIVE_LOCK, exclusiveLock.getPath());

        if (nativeDir != null)
            data.put(prefix + NATIVEDIR, nativeDir.getPath());

        if (timeoutHandlerClassName != null)
            data.put(prefix + TIMEOUT_HANDLER, timeoutHandlerClassName);

        if (timeoutHandlerPath != null) {
            StringBuilder sb = new StringBuilder();
            String sep = "";
            for (File file: timeoutHandlerPath) {
                sb.append(sep).append(file);
                sep = File.pathSeparator;
            }
            data.put(prefix + TIMEOUT_HANDLER_PATH, sb.toString());
        }

        if (timeoutHandlerTimeout != -1) {  // -1: default; 0: no timeout; >0: timeout in seconds
            data.put(prefix + TIMEOUT_HANDLER_TIMEOUT, String.valueOf(timeoutHandlerTimeout));
        }
    }

    //---------------------------------------------------------------------

    private Map<String, String> deserializeEnv(String envString, String sep) {
        Map<String, String> env;
        if ((envString != null) && (envString.length() != 0)) {
            env = new LinkedHashMap<>();
            String[] envArr = StringUtils.splitSeparator(sep, envString);
            for (String e : envArr) {
                String[] split = StringUtils.splitSeparator("=", e);
                env.put(split[0], split[1]);
            }
            return Collections.unmodifiableMap(env);
        } else {
            return Collections.emptyMap();
        }
    }

    private String serializeEnv(Map<String, String> env, String sep) {
        StringBuilder envString = new StringBuilder();
        int cnt = env.size();
        for(Map.Entry<String, String> var : env.entrySet()) {
            envString
                .append(var.getKey())
                .append("=")
                .append(var.getValue());
            cnt--;
            if (cnt != 0) {
                envString.append(sep);
            }
        }
        return envString.toString();
    }

    public Map<String, String> getEnvVars() {
        if (envVars == null) {
            String envVarStr = System.getProperty("envVars");
            envVars = deserializeEnv(envVarStr, ",");
        }
        return envVars;
    }

    public void setEnvVars(Map<String, String> envVars) {
        if (envVars == null) {
            this.envVars = Collections.emptyMap();
        } else {
            this.envVars = Collections.unmodifiableMap(new LinkedHashMap<>(envVars));
        }
    }

    private Map<String, String> envVars;

    //---------------------------------------------------------------------

    public boolean isCheck() {
        return check;
    }

    public void setCheck(boolean check) {
        this.check = check;
    }

    private boolean check;

    //---------------------------------------------------------------------

    public void setExecMode(ExecMode execMode) {
        this.execMode = execMode;
    }

    public ExecMode getExecMode() {
        return execMode;
    }

    ExecMode execMode;

    //---------------------------------------------------------------------

    public void setIgnoreKind(IgnoreKind ignoreKind) {
        ignoreKind.getClass(); // null-check
        this.ignoreKind = ignoreKind;
    }

    public IgnoreKind getIgnoreKind() {
        return ignoreKind;
    }

    IgnoreKind ignoreKind = IgnoreKind.ERROR; // non-null default

    //---------------------------------------------------------------------

    public void setTimeLimit(int timeLimit) {
        this.timeLimit = timeLimit;
    }

    int getTimeLimit() {
        return timeLimit;
    }

    int timeLimit;

    //---------------------------------------------------------------------

    public void setCompileJDK(JDK compileJDK) {
        compileJDK.getClass(); // null check
        this.compileJDK = compileJDK;
    }

    public JDK getCompileJDK() {
        return compileJDK;
    }

    private JDK compileJDK;

    //---------------------------------------------------------------------

    public void setTestJDK(JDK testJDK) {
        testJDK.getClass(); // null check
        this.testJDK = testJDK;
    }

    public JDK getTestJDK() {
        return testJDK;
    }

    private JDK testJDK;

    //---------------------------------------------------------------------

    public void setJUnitPath(SearchPath junitPath) {
        junitPath.getClass(); // null check
        this.junitPath = junitPath;
    }

    public SearchPath getJUnitPath() {
        return junitPath;
    }

    private SearchPath junitPath;

    public boolean isJUnitAvailable() {
        return (junitPath != null) && !junitPath.isEmpty();
    }

    //---------------------------------------------------------------------

    public void setTestNGPath(SearchPath testngPath) {
        testngPath.getClass(); // null check
        this.testngPath = testngPath;
    }

    public SearchPath getTestNGPath() {
        return testngPath;
    }

    private SearchPath testngPath;

    public boolean isTestNGAvailable() {
        return (testngPath != null) && !testngPath.isEmpty();
    }

    //---------------------------------------------------------------------

    public void setAsmToolsPath(SearchPath asmToolsPath) {
        asmToolsPath.getClass(); // null check
        this.asmToolsPath = asmToolsPath;
    }

    public SearchPath getAsmToolsPath() {
        return asmToolsPath;
    }

    private SearchPath asmToolsPath;

    //---------------------------------------------------------------------

    public SearchPath getJavaTestClassPath() {
        if (javaTestClassPath == null) {
            File jtClsDir = ProductInfo.getJavaTestClassDir();
            javaTestClassPath = new SearchPath(jtClsDir);

            if (jtClsDir.getName().equals("javatest.jar")) {
                File installDir = jtClsDir.getParentFile();
                // append jtreg.jar to the path
                javaTestClassPath.append(new File(installDir, "jtreg.jar"));
            }
        }
        return javaTestClassPath;
    }

    private SearchPath javaTestClassPath;

    //---------------------------------------------------------------------

    public List<String> getTestVMOptions() {
        if (testVMOpts == null)
            testVMOpts = Collections.emptyList();
        return testVMOpts;
    }

    public void setTestVMOptions(List<String> testVMOpts) {
        this.testVMOpts = Collections.unmodifiableList(new ArrayList<>(testVMOpts));
    }

    private List<String> testVMOpts;

    /**
     * Returns the set of VM options each prefixed by -J, as required by JDK tools.
     * @return the set of VM options
     */
    public List<String> getTestToolVMOptions() {
        List<String> testToolVMOpts = new ArrayList<>();
        for (String s: getTestVMOptions())
            testToolVMOpts.add("-J" + s);
        return Collections.unmodifiableList(testToolVMOpts);
    }

    /**
     * Returns the set of VM options and Java options, for use by the java command.
     * @return the set of VM and Java options
     */
    public List<String> getTestVMJavaOptions() {
        if ((testVMOpts == null || testVMOpts.isEmpty()) && nativeDir == null)
            return getTestJavaOptions();
        if ((testJavaOpts == null || testJavaOpts.isEmpty()) && nativeDir == null)
            return getTestVMOptions();
        List<String> opts = new ArrayList<>();
        opts.addAll(getTestVMOptions());
        opts.addAll(getTestJavaOptions());
        if (nativeDir != null)
            opts.add("-Djava.library.path=" + nativeDir.getAbsolutePath());

        return Collections.unmodifiableList(opts);
    }

    //---------------------------------------------------------------------

    public List<String> getTestCompilerOptions() {
        if (testCompilerOpts == null)
            testCompilerOpts = Collections.emptyList();
        return testCompilerOpts;
    }

    public void setTestCompilerOptions(List<String> testCompilerOpts) {
        this.testCompilerOpts = Collections.unmodifiableList(new ArrayList<>(testCompilerOpts));
    }

    private List<String> testCompilerOpts;

    //---------------------------------------------------------------------

    public List<String> getTestJavaOptions() {
        if (testJavaOpts == null)
            testJavaOpts = Collections.emptyList();
        return testJavaOpts;
    }

    public void setTestJavaOptions(List<String> testJavaOpts) {
        this.testJavaOpts = Collections.unmodifiableList(new ArrayList<>(testJavaOpts));
    }

    private List<String> testJavaOpts;

    //---------------------------------------------------------------------

    public List<String> getTestDebugOptions() {
        if (testDebugOpts == null)
            testDebugOpts = Collections.emptyList();
        return testDebugOpts;
    }

    public void setTestDebugOptions(List<String> testJavaOpts) {
        this.testDebugOpts = Collections.unmodifiableList(new ArrayList<>(testJavaOpts));
    }

    private List<String> testDebugOpts;

    //---------------------------------------------------------------------

    List<String> getRetainArgs() {
        return retainArgs;
    }

    public void setRetainArgs(List<String> retainArgs) {

        retainStatusSet.clear();
        if (retainArgs == null) {
            // equivalent to "none"
            retainFilesPattern = null;
            return;
        } else {
            this.retainArgs = Collections.unmodifiableList(new ArrayList<>(retainArgs));
        }

        StringBuilder sb = new StringBuilder();

        for (String arg: retainArgs) {
            if (arg.equals("all")) {
                retainStatusSet.add(Status.PASSED);
                retainStatusSet.add(Status.FAILED);
                retainStatusSet.add(Status.ERROR);
            } else if (arg.equals("pass")) {
                retainStatusSet.add(Status.PASSED);
            } else if (arg.equals("fail")) {
                retainStatusSet.add(Status.FAILED);
            } else if (arg.equals("error")) {
                retainStatusSet.add(Status.ERROR);
            } else if (arg.equals("none")) {
                // can only appear by itself
                // no further action required
            } else if (arg.length() > 0) {
                if (sb.length() > 0)
                    sb.append("|");
                boolean inQuote = false;
                for (int i = 0; i < arg.length(); i++) {
                    char c = arg.charAt(i);
                    switch (c) {
                        case '*':
                            if (inQuote) {
                                sb.append("\\E");
                                inQuote = false;
                            }
                            sb.append(".*");
                            break;
                        default:
                            if (!inQuote) {
                                sb.append("\\Q");
                                inQuote = true;
                            }
                            sb.append(c);
                            break;
                    }
                }
                if (inQuote)
                    sb.append("\\E");
            }
        }

        retainFilesPattern = (sb.length() == 0 ? null : Pattern.compile(sb.toString()));
    }

    public boolean isRetainEnabled() {
        return (retainArgs != null);
    }

    public Set<Integer> getRetainStatus() {
        return retainStatusSet;
    }

    public Pattern getRetainFilesPattern() {
        return retainFilesPattern;
    }

    //---------------------------------------------------------------------

    public void setReportDir(File reportDir) {
        reportDir.getClass(); // null check
        this.reportDir = reportDir;
    }

    public File getReportDir() {
        return reportDir;
    }

    private File reportDir;

    //---------------------------------------------------------------------

    public void setExclusiveLock(File exclusiveLock) {
        exclusiveLock.getClass(); // null check
        this.exclusiveLock = exclusiveLock;
    }

    public File getExclusiveLock() {
        return exclusiveLock;
    }

    private File exclusiveLock;

    //---------------------------------------------------------------------

    public void setNativeDir(File nativeDir) {
        this.nativeDir = nativeDir;
    }

    public File getNativeDir() {
        return nativeDir;
    }

    private File nativeDir;

    //---------------------------------------------------------------------

    public void setTimeoutHandler(String timeoutHandlerClassName) {
        timeoutHandlerClassName.getClass(); // null check
        this.timeoutHandlerClassName = timeoutHandlerClassName;
    }

    String getTimeoutHandler() {
        return timeoutHandlerClassName;
    }

    private String timeoutHandlerClassName;

    void setTimeoutHandlerPath(String timeoutHandlerPath) {
        timeoutHandlerPath.getClass(); // null check
        this.timeoutHandlerPath = new ArrayList<>();
        for (String f: timeoutHandlerPath.split(File.pathSeparator)) {
            if (f.length() > 0) {
                this.timeoutHandlerPath.add(new File(f));
            }
        }
    }

    public void setTimeoutHandlerPath(List<File> timeoutHandlerPath) {
        timeoutHandlerPath.getClass(); // null check
        this.timeoutHandlerPath = timeoutHandlerPath;
    }

    List<File> getTimeoutHandlerPath() {
        return timeoutHandlerPath;
    }

    private List<File> timeoutHandlerPath;


    public void setTimeoutHandlerTimeout(long timeout) {
        this.timeoutHandlerTimeout = timeout;
    }

    private void setTimeoutHandlerTimeout(String timeout) {
        this.timeoutHandlerTimeout = Long.parseLong(timeout);
    }

    long getTimeoutHandlerTimeout() {
        return timeoutHandlerTimeout;
    }

    private long timeoutHandlerTimeout;

    // Ideally, this method would be better on a "shared execution context" object
    public TimeoutHandlerProvider getTimeoutHandlerProvider() throws MalformedURLException {
        if (timeoutHandlerProvider == null) {
            timeoutHandlerProvider = new TimeoutHandlerProvider();
            timeoutHandlerProvider.setClassName(timeoutHandlerClassName);
            if (timeoutHandlerPath != null && !timeoutHandlerPath.isEmpty())
                timeoutHandlerProvider.setClassPath(timeoutHandlerPath);
            if (timeoutHandlerTimeout != -1)
                timeoutHandlerProvider.setTimeout(timeoutHandlerTimeout);
        }
        return timeoutHandlerProvider;
    }

    private TimeoutHandlerProvider timeoutHandlerProvider;

    //---------------------------------------------------------------------

    private List<String> retainArgs;
    private final Set<Integer> retainStatusSet = new HashSet<>(4);
    private Pattern retainFilesPattern;
}
