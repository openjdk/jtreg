/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import com.sun.javatest.regtest.report.Verbose;
import com.sun.javatest.regtest.util.FileUtils;
import com.sun.javatest.regtest.util.StringUtils;
import com.sun.javatest.util.I18NResourceBundle;

import static com.sun.javatest.regtest.util.StringUtils.join;


public final class RegressionParameters
    extends BasicInterviewParameters
    implements Parameters.EnvParameters
{
    private final Consumer<String> logger;

    /**
     * Creates an object to handle the parameters for a test run.
     *
     * @param tag       a string to identify the set of parameters
     * @param testSuite the test suite
     * @param logger    an object to which to write logging messages
     *
     * @throws InterviewParameters.Fault if a problem occurs while creating this object
     */
    public RegressionParameters(String tag, RegressionTestSuite testSuite, Consumer<String> logger)
            throws InterviewParameters.Fault {
        super(tag, testSuite);
        this.logger = logger;

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
        setTests(tests == null ? null : tests.toArray(new String[0]));
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
     * (in {@link BasicInterviewParameters#getMaxConcurrency()})
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

    public void setExcludeLists(Path[] files) {
        MutableExcludeListParameters mep =
            (MutableExcludeListParameters) getExcludeListParameters();
        mep.setExcludeFiles(FileUtils.toFiles(files));
    }

    public File[] getExcludeLists() {
        MutableExcludeListParameters mep =
            (MutableExcludeListParameters) getExcludeListParameters();
        return mep.getExcludeFiles() != null
            ? mep.getExcludeFiles()
            : new File[0];
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
    public Map<String, String> filterFaults = new HashMap<>();

    /* A RegressionContext is used by various filters, but initializing it may throw an
     * exception. Therefore, it should be initialized explicitly, and the exception
     * handled, rather than lazily, in circumstances where the exception might be ignored.
     */
    private Expr.Context exprContext;

    public void initExprContext() throws JDK.Fault {
        exprContext = new RegressionContext(this, logger);
    }

    public Expr.Context getExprContext() {
        return exprContext;
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

            TestFilter mlf = getMatchListFilter();
            if (mlf != null)
                filters.add(mlf);

            // Do not cache the results of the composite filter,
            // to not affect the filter stats, which handle CompositeFilters specially.
            // Cache the individual filters.
            TestFilter f = new CompositeFilter(filters.toArray(new TestFilter[0]));
            return f;
        }
        return relevantTestFilter;

    }

    TestFilter relevantTestFilter = UNSET;

    TestFilter getModulesFilter() {
        JDK jdk = getTestJDK();
        if (jdk == null || jdk.getVersion(this, logger).compareTo(JDK_Version.V9) < 0)
            return null;

        final Set<String> availModules = jdk.getSystemModules(this, logger);
        if (availModules.isEmpty())
            return null;

        return new CachingTestFilter(
                "ModulesFilter",
                "Select tests for which all required modules are available",
                "A required module is not available") {

            private static final String MODULES = "modules";

            @Override
            protected String getCacheKey(TestDescription td) {
                return td.getParameter(MODULES);
            }

            @Override
            public boolean getCacheableValue(TestDescription td) {
                String reqdModules = td.getParameter(MODULES);
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
        return new CachingTestFilter(
                "RequiresFilter",
                "Select tests that satisfy a given set of platform requirements",
                "The platform does not meet the specified requirements") {

            private static final String REQUIRES = "requires";

            @Override
            protected String getCacheKey(TestDescription td) {
                return td.getParameter(REQUIRES);
            }

            @Override
            public boolean getCacheableValue(TestDescription td) {
                try {
                    String requires = td.getParameter(REQUIRES);
                    if (requires == null)
                        return true;
                    return Expr.parse(requires, exprContext).evalBoolean(exprContext);
                } catch (Expr.Fault ex) {
                    filterFaults.put(td.getRootRelativeURL(), "Error evaluating expression: " + ex.getMessage());
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

        return new CachingTestFilter(
                "TimeLimitFilter",
                "Select tests that do not exceed a specified timeout value",
                "Test declares a timeout which exceeds the requested time limit") {

            final String MAX_TIMEOUT = "maxTimeout";

            @Override
            protected String getCacheKey(TestDescription td) {
                return td.getParameter(MAX_TIMEOUT);
            }

            @Override
            public boolean getCacheableValue(TestDescription td) {
                String maxTimeoutValue = td.getParameter(MAX_TIMEOUT);
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


    private static class TestListWithPlatforms {

        private static Set<String> getPlatforms(OS os) {

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
            Set<String> platforms = new HashSet<>();
            for (String p: List.of(os.name, os.name.replaceAll("\\s", ""), os.family, "generic")) {
                for (String q: List.of("", os.arch, os.simple_arch, os.version, os.simple_version, "all")) {
                    String ep = q.isEmpty() ? p : p + "-" + q;
                    platforms.add(ep.toLowerCase());
                }
            }
            return platforms;
        }

        private final ExcludeList el;
        private final Set<String> osPlatforms;

        TestListWithPlatforms(ExcludeList el, OS os) {
            this.el = el;
            this.osPlatforms = getPlatforms(os);
        }

        public boolean match(TestDescription td) {
            ExcludeList.Entry e = el.getEntry(td.getRootRelativeURL());
            if (e == null) {
                return false;
            }
            String[] platforms = e.getPlatforms();
            if (platforms.length == 0 || (platforms.length == 1 && platforms[0].length() == 0)) {
                // allow for old ProblemList.txt format
                String[] bugIds = e.getBugIdStrings();
                if (bugIds.length > 0 && !bugIds[0].matches("0|([1-9][0-9,]*)"))
                    platforms = bugIds;
            }

            if (platforms.length == 0 || (platforms.length == 1 && platforms[0].length() == 0)) {
                return true;
            }

            for (String p: platforms) {
                if (osPlatforms.contains(p.toLowerCase())) {
                    return true;
                }
            }

            return false;
        }
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
        if (excludeListFilter == UNSET) {
            final ExcludeList el = getExcludeList();
            if (el == null) {
                excludeListFilter = null;
            } else {
                excludeListFilter = new CachingTestFilter(
                        "jtregExcludeListFilter",
                        "Select tests which are not excluded on any exclude list",
                        "Test has been excluded by an exclude list") {
                    final TestListWithPlatforms list = new TestListWithPlatforms(el, getTestOS());
                    @Override
                    protected String getCacheKey(TestDescription td) {
                        return td.getRootRelativeURL();
                    }

                    @Override
                    public boolean getCacheableValue(TestDescription td) {
                        return !list.match(td);
                    }
                };
            }
        }
        return excludeListFilter;
    }

    private CachingTestFilter excludeListFilter = UNSET;

    private TestFilter getMatchListFilter() {
        if (matchListFilter == UNSET) {
            List<Path> matchList = getMatchLists();
            if (matchList.isEmpty()) {
                matchListFilter = null;
            } else {
                final ExcludeList el;
                try {
                    el = new ExcludeList(matchList.stream()
                            .map(Path::toFile)
                            .toArray(File[]::new));
                } catch (ExcludeList.Fault | IOException e) {
                    throw new Error(e);
                }
                matchListFilter = new CachingTestFilter(
                        "jtregMatchListFilter",
                        "Select tests which are in a match list",
                        "Test is not in a match list") {
                    final TestListWithPlatforms list = new TestListWithPlatforms(el, getTestOS());
                    @Override
                    protected String getCacheKey(TestDescription td) {
                        return td.getRootRelativeURL();
                    }

                    @Override
                    public boolean getCacheableValue(TestDescription td) {
                        return list.match(td);
                    }
                };
            }
        }
        return matchListFilter;
    }

    private TestFilter matchListFilter = UNSET;

    /**
     * {@inheritDoc}
     *
     * Use a caching filter partly for performance reasons, and partly to make
     * it easier to analyze the rejected tests.
     *
     * @return a filter based on the keywords given on the command line.
     */
    @Override
    public KeywordsTestFilter getKeywordsFilter() {
        if (keywordsFilter == UNSET_KEYWORDS_FILTER) {
            TestFilter f = super.getKeywordsFilter();
            keywordsFilter = (f == null) ? null : new KeywordsTestFilter(f);
        }
        return keywordsFilter;
    }

    private KeywordsTestFilter keywordsFilter = UNSET_KEYWORDS_FILTER;

    public static class KeywordsTestFilter extends TestFilter {
        private final TestFilter delegate;
        public final Set<String> ignored = new HashSet<>();

        KeywordsTestFilter(TestFilter delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getDescription() {
            return delegate.getDescription();
        }

        @Override
        public String getReason() {
            return delegate.getReason();
        }

        @Override
        public boolean accepts(TestDescription td) throws Fault {
            // for standard jtreg tests, there's no point in caching the
            // evaluation of the keywords, because each test probably has a
            // unique set of keywords, by virtue of implicit keywords,
            // such as for @bug.  However, we can record the ignored tests.
            boolean ok = delegate.accepts(td);
            if (!ok && td.getKeywordTable().contains("ignore")) {
                ignored.add(td.getRootRelativeURL());
            }
            return ok;
        }
    }

    private static final CachingTestFilter UNSET = new CachingTestFilter("", "", "") {
        public String getCacheKey(TestDescription td) {
            throw new IllegalStateException();
        }
        @Override
        public boolean getCacheableValue(TestDescription td) {
            throw new IllegalStateException();
        }
    };

    private static final KeywordsTestFilter UNSET_KEYWORDS_FILTER = new KeywordsTestFilter(UNSET);

    @Override
    public TestFilter getPriorStatusFilter() {
        if (priorStatusFilter == UNSET) {
            final TestFilter psf = super.getPriorStatusFilter();
            if (psf == null) {
                priorStatusFilter = null;
            } else {
                priorStatusFilter = new CachingTestFilter(
                        "jtregPriorStatusFilter",
                        "Select tests which match a specified status",
                        "Test did not match a specified status") {
                    @Override
                    protected String getCacheKey(TestDescription td) {
                        return td.getRootRelativeURL();
                    }

                    @Override
                    public boolean getCacheableValue(TestDescription td) throws Fault {
                        return psf.accepts(td);
                    }
                };
            }
        }
        return priorStatusFilter;
    }

    private CachingTestFilter priorStatusFilter = UNSET;

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
    private static final String CUSTOM_TEST_THREAD_FACTORY = ".testThreadFactory";
    private static final String CUSTOM_TEST_THREAD_FACTORY_PATH = ".testThreadFactoryPath";
    private static final String TEST_QUERIES = ".testQueries";
    private static final String TEST_VERBOSE = ".testVerbose";

    @Override
    public void load(Map<String, String> data, boolean checkChecksum) throws Interview.Fault {
        super.load(data, checkChecksum);
        String prefix = getTag();

        try {
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
                setTestVMOptions(List.of(StringUtils.splitSeparator("\n", v)));

            v = data.get(prefix + TEST_COMPILER_OPTIONS);
            if (v != null && v.length() > 0)
                setTestCompilerOptions(List.of(StringUtils.splitSeparator("\n", v)));

            v = data.get(prefix + TEST_JAVA_OPTIONS);
            if (v != null && v.length() > 0)
                setTestJavaOptions(List.of(StringUtils.splitSeparator("\n", v)));

            v = data.get(prefix + RETAIN_ARGS);
            if (v != null && v.length() > 0)
                setRetainArgs(List.of(StringUtils.splitSeparator("\n", v)));

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
                setReportDir(Path.of(v));

            v = data.get(prefix + EXCLUSIVE_LOCK);
            if (v != null)
                setExclusiveLock(Path.of(v));

            v = data.get(prefix + NATIVEDIR);
            if (v != null)
                setNativeDir(Path.of(v));

            v = data.get(prefix + TIMEOUT_HANDLER);
            if (v != null)
                setTimeoutHandler(v);

            v = data.get(prefix + TIMEOUT_HANDLER_PATH);
            if (v != null)
                setTimeoutHandlerPath(v);

            v = data.get(prefix + TIMEOUT_HANDLER_TIMEOUT);
            if (v != null)
                setTimeoutHandlerTimeout(v);

            v = data.get(prefix + CUSTOM_TEST_THREAD_FACTORY);
            if (v != null)
                setTestThreadFactory(v);

            v = data.get(prefix + CUSTOM_TEST_THREAD_FACTORY_PATH);
            if (v != null)
                setTestThreadFactoryPath(v);

            v = data.get(prefix + TEST_QUERIES);
            if (v != null) {
                setTestQueries(List.of(StringUtils.splitSeparator("\n", v)));
            }

            v = data.get(prefix + TEST_VERBOSE);
            if (v != null) {
                setVerbose(Verbose.decode(v));
            }

        } catch (InvalidPathException e) {
            // This is unlikely to happen, but pretty serious if it does.
            // Since we only put valid paths into the parameters, there should be
            // no issue retrieving them after the save-load sequence.
            throw new Interview.Fault(i18n, "rp.badPath", e.getInput(), e.getMessage());
        }
    }

    public void save(Map<String, String> data) {
        super.save(data);

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
            data.put(prefix + REPORTDIR, reportDir.toString());

        if (exclusiveLock != null)
            data.put(prefix + EXCLUSIVE_LOCK, exclusiveLock.toString());

        if (nativeDir != null)
            data.put(prefix + NATIVEDIR, nativeDir.toString());

        if (timeoutHandlerClassName != null)
            data.put(prefix + TIMEOUT_HANDLER, timeoutHandlerClassName);

        if (timeoutHandlerPath != null) {
            StringBuilder sb = new StringBuilder();
            String sep = "";
            for (Path file: timeoutHandlerPath) {
                sb.append(sep).append(file);
                sep = File.pathSeparator;
            }
            data.put(prefix + TIMEOUT_HANDLER_PATH, sb.toString());
        }

        if (timeoutHandlerTimeout != -1) {  // -1: default; 0: no timeout; >0: timeout in seconds
            data.put(prefix + TIMEOUT_HANDLER_TIMEOUT, String.valueOf(timeoutHandlerTimeout));
        }

        if (testThreadFactory != null) {
            data.put(prefix + CUSTOM_TEST_THREAD_FACTORY, testThreadFactory);
        }

        if (testThreadFactoryPath != null) {
            data.put(prefix + CUSTOM_TEST_THREAD_FACTORY_PATH, testThreadFactoryPath);
        }

        if (testQueries != null) {
            data.put(prefix + TEST_QUERIES, join(testQueries, "\n"));
        }

        if (verbose != null) {
            data.put(prefix + TEST_VERBOSE, verbose.toString());
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
        this.ignoreKind = Objects.requireNonNull(ignoreKind);
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
        this.compileJDK = Objects.requireNonNull(compileJDK);
    }

    public JDK getCompileJDK() {
        return compileJDK;
    }

    private JDK compileJDK;

    //---------------------------------------------------------------------

    public void setTestJDK(JDK testJDK) {
        this.testJDK = Objects.requireNonNull(testJDK);
    }

    public JDK getTestJDK() {
        return testJDK;
    }

    private JDK testJDK;

    //---------------------------------------------------------------------

    public void setJUnitPath(SearchPath junitPath) {
        this.junitPath = Objects.requireNonNull(junitPath);
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
        this.testngPath = Objects.requireNonNull(testngPath);
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
        this.asmToolsPath = Objects.requireNonNull(asmToolsPath);
    }

    public SearchPath getAsmToolsPath() {
        return asmToolsPath;
    }

    private SearchPath asmToolsPath;

    //---------------------------------------------------------------------

    public SearchPath getJavaTestClassPath() {
        if (javaTestClassPath == null) {
            Path jtClsDir = ProductInfo.getJavaTestClassDir().toPath();
            javaTestClassPath = new SearchPath(jtClsDir);

            if (jtClsDir.getFileName().toString().equals("javatest.jar")) {
                Path installDir = jtClsDir.getParent();
                // append jtreg.jar or exploded directory to the search path
                Path jtreg = installDir.resolve("jtreg.jar");
                if (Files.exists(jtreg)) {
                    javaTestClassPath.append(jtreg);
                } else try {
                    // use code source location of this class instead
                    URL location = getClass().getProtectionDomain().getCodeSource().getLocation();
                    javaTestClassPath.append(Path.of(location.toURI()));
                } catch (Exception e) { // including NullPointerException and URISyntaxException
                    throw new RuntimeException("Computation of Java test class-path failed", e);
                }
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
            opts.add("-Djava.library.path=" + nativeDir.toAbsolutePath());

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
        if (retainArgs == null || retainArgs.contains("lastRun")) {
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

    public void setReportDir(Path reportDir) {
        this.reportDir = Objects.requireNonNull(reportDir);
    }

    public Path getReportDir() {
        return reportDir;
    }

    private Path reportDir;

    //---------------------------------------------------------------------

    public void setExclusiveLock(Path exclusiveLock) {
        this.exclusiveLock = Objects.requireNonNull(exclusiveLock);
    }

    public Path getExclusiveLock() {
        return exclusiveLock;
    }

    private Path exclusiveLock;

    //---------------------------------------------------------------------

    public void setNativeDir(Path nativeDir) {
        this.nativeDir = nativeDir;
    }

    public Path getNativeDir() {
        return nativeDir;
    }

    private Path nativeDir;

    //---------------------------------------------------------------------

    public void setTimeoutHandler(String timeoutHandlerClassName) {
        this.timeoutHandlerClassName = Objects.requireNonNull(timeoutHandlerClassName);
    }

    String getTimeoutHandler() {
        return timeoutHandlerClassName;
    }

    private String timeoutHandlerClassName;

    //---------------------------------------------------------------------

    void setTimeoutHandlerPath(String timeoutHandlerPath) {
        Objects.requireNonNull(timeoutHandlerPath);
        this.timeoutHandlerPath = new ArrayList<>();
        for (String f: timeoutHandlerPath.split(File.pathSeparator)) {
            if (f.length() > 0) {
                this.timeoutHandlerPath.add(Path.of(f));
            }
        }
    }

    public void setTimeoutHandlerPath(List<Path> timeoutHandlerPath) {
        this.timeoutHandlerPath = Objects.requireNonNull(timeoutHandlerPath);
    }

    List<Path> getTimeoutHandlerPath() {
        return timeoutHandlerPath;
    }

    private List<Path> timeoutHandlerPath;

    //---------------------------------------------------------------------

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
    //---------------------------------------------------------------------

    public void setTestThreadFactory(String testThreadFactory) {
        this.testThreadFactory = testThreadFactory;
    }

    public String getTestThreadFactory() {
        return testThreadFactory;
    }

    private String testThreadFactory;

    public void setTestThreadFactoryPath(String testThreadFactoryPath) {
        this.testThreadFactoryPath = Path.of(testThreadFactoryPath).toAbsolutePath().toString();
    }

    public String getTestThreadFactoryPath() {
        return testThreadFactoryPath;
    }

    private String testThreadFactoryPath;
    //---------------------------------------------------------------------

    public void setMatchLists(Path[] files) {
        this.matchLists = List.of(files);
    }

    public List<Path> getMatchLists() {
        return Collections.unmodifiableList(matchLists);
    }

    private List<Path> matchLists;

    //---------------------------------------------------------------------

    public void setUseWindowsSubsystemForLinux(boolean useWindowsSubsystemForLinux) {
        this.useWindowsSubsystemForLinux = useWindowsSubsystemForLinux;
    }

    public boolean useWindowsSubsystemForLinux() {
        return useWindowsSubsystemForLinux;
    }

    private boolean useWindowsSubsystemForLinux;

    //---------------------------------------------------------------------

    public void setVerbose(Verbose verbose) {
        this.verbose = verbose;
    }

    public Verbose getVerbose() {
        return verbose;
    }

    private Verbose verbose;

    //---------------------------------------------------------------------

    public void setTestQueries(List<String> testQueries) {
        this.testQueries = testQueries;
    }

    public List<String> getTestQueries() {
        return testQueries;
    }

    /**
     * Returns the query component for a given test, if one was specified,
     * or null if there is no query component for this test.
     *
     * @param test the name of the test
     * @return the query component associated with this test, or null
     */
    public String getTestQuery(String test) {
        // There are two common cases:
        // 1. any number of tests are being run, none of which have queries, or
        // 2. a single test is being run, which has query.
        // As such, it is probably not worth parsing testQueries into a map.
        if (testQueries != null) {
            for (String tq : testQueries) {
                int sep = tq.indexOf("?");
                if (test.equals(tq.substring(0, sep))) {
                    return tq.substring(sep + 1);
                }
            }
        }
        return null;
    }

    private List<String> testQueries;

    //---------------------------------------------------------------------

    public Pattern getRefIgnoreLinesPattern() {
        if (refIgnoreLinesPattern == UNSET_PATTERN) {
            String refIgnoreLines = System.getenv("JTREG_REF_IGNORE_LINES");
            String re;
            if (refIgnoreLines != null) {
                // User-specified list of regular expressions for lines to ignore in golden file comparison.
                re = Arrays.stream(refIgnoreLines.trim().split("\\s+"))
                        .map(s -> "(" + s + ")")
                        .collect(Collectors.joining("|"));
            } else {
                // Default regular expressions, based on VM warnings when specific powerful VM options are set.
                // Override these by setting JTREG_REF_IGNORE_LINES to either empty or alternative regex list
                Map<String, String> envVars = getEnvVars();
                re = Stream.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS")
                        .filter(envVars::containsKey)
                        .map(e -> "(Picked up " + e + ":.*)")
                        .collect(Collectors.joining("|"));
            }
            try {
                refIgnoreLinesPattern = re.isEmpty() ? null : Pattern.compile(re);
            } catch (PatternSyntaxException e) {
                refIgnoreLinesPattern = null;
                throw e;
            }
        }
        return refIgnoreLinesPattern;

    }

    private static final Pattern UNSET_PATTERN = Pattern.compile("");
    private Pattern refIgnoreLinesPattern = UNSET_PATTERN;

    //---------------------------------------------------------------------

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

    /**
     * Returns a map containing the properties that are passed to all tests and
     * other VBMs started by jtreg.
     *
     * @return the map
     */
    // Ideally, this method would be better on a "shared execution context" object
    public Map<String, String> getBasicTestProperties() {
        if (basicTestProperties == null) {
            Map<String, String> map = new LinkedHashMap<>();
            put(map, "test.vm.opts", getTestVMOptions(), v -> StringUtils.join(v, " "));
            put(map, "test.tool.vm.opts", getTestToolVMOptions(), v -> StringUtils.join(v, " "));
            put(map, "test.compiler.opts", getTestCompilerOptions(), v -> StringUtils.join(v, " "));
            put(map, "test.java.opts", getTestJavaOptions(), v -> StringUtils.join(v, " "));
            put(map, "test.jdk", getTestJDK(), JDK::getAbsolutePath);
            put(map, "compile.jdk", getCompileJDK(), JDK::getAbsolutePath);
            put(map, "test.timeout.factor", getTimeoutFactor(), String::valueOf);
            put(map, "test.nativepath", getNativeDir(), p -> p.toAbsolutePath().toString());
            put(map, "test.root", getTestSuite().getRootDir(), File::getAbsolutePath);
            basicTestProperties = map;
        }
        return basicTestProperties;
    }

    private <T> void put(Map<String, String> map, String name, T value, Function<T, String> toString) {
        if (value != null) {
            map.put(name, toString.apply(value));
        }
    }

    private Map<String, String> basicTestProperties;

    //---------------------------------------------------------------------

    public OS getTestOS() {
        // In general, and particularly when running tests, the testJDK should always be set.
        // But in some testing and reporting situations, it may not be. In these cases,
        // we default to the current platform.
        JDK jdk = getTestJDK();
        if (jdk == null) {
            return OS.current();
        } else {
            try {
                return OS.forProps(testJDK.getProperties(this, logger));
            } catch (JDK.Fault f) {
                // If it was going to happen, this exception would have been thrown
                // and caught early on, during Tool.createParameters; by now, the
                // properties should always be available.
                throw new IllegalStateException(f);
            }
        }
    }

    //---------------------------------------------------------------------

    private List<String> retainArgs;
    private final Set<Integer> retainStatusSet = new HashSet<>(4);
    private Pattern retainFilesPattern;

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(RegressionParameters.class);
}
