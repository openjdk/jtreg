/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.sun.interview.Interview;
import com.sun.interview.Question;
import com.sun.javatest.ExcludeList;
import com.sun.javatest.InterviewParameters;
import com.sun.javatest.Parameters;
import com.sun.javatest.ProductInfo;
import com.sun.javatest.Status;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestEnvironment;
import com.sun.javatest.TestFilter;
import com.sun.javatest.TestSuite;
import com.sun.javatest.interview.BasicInterviewParameters;

public class RegressionParameters
    extends BasicInterviewParameters
    implements Parameters.EnvParameters
{
    public RegressionParameters(String tag, TestSuite testsuite) throws InterviewParameters.Fault {
        super(tag, testsuite);

        setTitle("jtreg Configuration Editor"); // I18N
        setEdited(false);
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

    public Parameters.EnvParameters getEnvParameters() {
        return this;
    }

    protected Question getEnvFirstQuestion() {
        return getEnvSuccessorQuestion();
    }

    //---------------------------------------------------------------------

    @Override
    public TestFilter getRelevantTestFilter() {
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
                        if (Integer.parseInt(maxTimeoutValue) > timeLimit)
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
     * @inheritDoc
     *
     * Use a caching filter partly for performance reasons, and partly to make
     * it easier to calculate the number of rejected tests. Simply counting
     * the number of true/false results is insufficient because the filter
     * may be called more than once per test. An alternative approach is to use
     * an ObservableTestFilter, and count the number of of true/false results in
     * between Harness.Observer.startingTestRun and Harness.Observer.finishedTestRun.
     * But that doesn't work correctly since the readAheadTestIterator in Harness
     * is created before the notification to Harness.Observer.startingTestRun.
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

        final Set<String> excludedPlatforms = new HashSet<String>();
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
                            if (bugIds.length > 0 && !bugIds[0].matches("[1-9][0-9,]*"))
                                platforms = bugIds;
                        }

                        if (platforms.length == 0)
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
     * @inheritDoc
     *
     * Use a caching filter partly for performance reasons, and partly to make
     * it easier to analze the rejected tests.
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

    private static CachingTestFilter UNSET = new CachingTestFilter(new TestFilter() {
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
    private static final String TIMELIMIT = ".timeLimit";
    private static final String REPORTDIR = ".reportDir";
    private static final String EXCLUSIVE_LOCK = ".exclLock";

    @Override @SuppressWarnings("rawtypes")
    public void load(Map data, boolean checkChecksum) throws Interview.Fault {
        super.load(data, checkChecksum);
        String prefix = getTag();

        String v;

        v = (String) data.get(prefix + ENVVARS);
        if (v != null)
            setEnvVars(StringArray.splitSeparator("\n", v));

        v = (String) data.get(prefix + CHECK);
        if (v != null)
            setCheck(v.equals("true"));

        v = (String) data.get(prefix + EXEC_MODE);
        if (v != null)
            setExecMode(ExecMode.valueOf(v));

        v = (String) data.get(prefix + IGNORE);
        if (v != null)
            setIgnoreKind(IgnoreKind.valueOf(v));

        v = (String) data.get(prefix + COMPILE_JDK);
        if (v != null)
            setCompileJDK(new JDK(v));

        v = (String) data.get(prefix + TEST_JDK);
        if (v != null)
            setTestJDK(new JDK(v));

        v = (String) data.get(prefix + TEST_VM_OPTIONS);
        if (v != null)
            setTestVMOptions(Arrays.asList(StringArray.splitSeparator("\n", v)));

        v = (String) data.get(prefix + TEST_COMPILER_OPTIONS);
        if (v != null)
            setTestCompilerOptions(Arrays.asList(StringArray.splitSeparator("\n", v)));

        v = (String) data.get(prefix + TEST_JAVA_OPTIONS);
        if (v != null)
            setTestJavaOptions(Arrays.asList(StringArray.splitSeparator("\n", v)));

        v = (String) data.get(prefix + RETAIN_ARGS);
        if (v != null)
            setRetainArgs(Arrays.asList(StringArray.splitSeparator("\n", v)));

        v = (String) data.get(prefix + JUNIT);
        if (v != null)
            setJUnitJar(new File(v));

        v = (String) data.get(prefix + TIMELIMIT);
        if (v != null)
            setTimeLimit(Integer.parseInt(v));

        v = (String) data.get(prefix + REPORTDIR);
        if (v != null)
            setReportDir(new File(v));

        v = (String) data.get(prefix + EXCLUSIVE_LOCK);
        if (v != null)
            setExclusiveLock(new File(v));
    }

    @Override @SuppressWarnings({"unchecked", "rawtypes"})
    public void save(Map data) {
        save0((Map<String, String>) data);
        super.save(data);
    }

    private void save0(Map<String, String> data) {
        String prefix = getTag();

        if (envVars != null)
            data.put(prefix + ENVVARS, StringArray.join(envVars, "\n"));

        data.put(prefix + CHECK, String.valueOf(check));
        data.put(prefix + EXEC_MODE, String.valueOf(execMode));
        data.put(prefix + IGNORE, String.valueOf(ignoreKind));

        if (testJDK != null)
            data.put(prefix + TEST_JDK, testJDK.getPath());

        if (compileJDK != null)
            data.put(prefix + COMPILE_JDK, compileJDK.getPath());

        if (retainArgs != null)
            data.put(prefix + RETAIN_ARGS, StringUtils.join(retainArgs, "\n"));

        if (testVMOpts != null)
            data.put(prefix + TEST_VM_OPTIONS, StringUtils.join(testVMOpts, "\n"));

        if (testCompilerOpts != null)
            data.put(prefix + TEST_COMPILER_OPTIONS, StringUtils.join(testCompilerOpts, "\n"));

        if (testJavaOpts != null)
            data.put(prefix + TEST_JAVA_OPTIONS, StringUtils.join(testJavaOpts, "\n"));

        if (junitJar != null)
            data.put(prefix + JUNIT, junitJar.getPath());

        if (timeLimit > 0)
            data.put(prefix + TIMELIMIT, String.valueOf(timeLimit));

        if (reportDir != null)
            data.put(prefix + REPORTDIR, reportDir.getPath());

        if (exclusiveLock != null)
            data.put(prefix + EXCLUSIVE_LOCK, exclusiveLock.getPath());
    }

    //---------------------------------------------------------------------

    String[] getEnvVars() {
        if (envVars == null) {
            String envVarStr = System.getProperty("envVars");
            if ((envVarStr != null) && (envVarStr.length() != 0))
                envVars = StringArray.splitSeparator(",", envVarStr);
            else
                envVars = new String[0];
        }
        return envVars;
    }

    void setEnvVars(String[] envVars) {
        this.envVars = (envVars == null ? new String[0] : envVars);
    }

    private String[] envVars;

    //---------------------------------------------------------------------

    boolean isCheck() {
        return check;
    }

    void setCheck(boolean check) {
        this.check = check;
    }

    private boolean check;

    //---------------------------------------------------------------------

    void setExecMode(ExecMode execMode) {
        this.execMode = execMode;
    }

    ExecMode getExecMode() {
        return execMode;
    }

    ExecMode execMode;

    //---------------------------------------------------------------------

    void setIgnoreKind(IgnoreKind ignoreKind) {
        ignoreKind.getClass(); // null-check
        this.ignoreKind = ignoreKind;
    }

    IgnoreKind getIgnoreKind() {
        return ignoreKind;
    }

    IgnoreKind ignoreKind = IgnoreKind.ERROR; // non-null default

    //---------------------------------------------------------------------

    void setTimeLimit(int timeLimit) {
        this.timeLimit = timeLimit;
    }

    int getTimeLimit() {
        return timeLimit;
    }

    int timeLimit;

    //---------------------------------------------------------------------

    void setCompileJDK(JDK compileJDK) {
        compileJDK.getClass(); // null check
        this.compileJDK = compileJDK;
    }

    JDK getCompileJDK() {
        return compileJDK;
    }

    private JDK compileJDK;

    //---------------------------------------------------------------------

    void setTestJDK(JDK testJDK) {
        testJDK.getClass(); // null check
        this.testJDK = testJDK;
    }

    JDK getTestJDK() {
        return testJDK;
    }

    private JDK testJDK;

    //---------------------------------------------------------------------

    void setJUnitJar(File junitJar) {
        junitJar.getClass(); // null check
        this.junitJar = junitJar;
    }

    File getJUnitJar() {
        if (junitJar == null) {
            File jtClsDir = ProductInfo.getJavaTestClassDir();
            junitJar = new File(jtClsDir.getParentFile(), "junit.jar");
        }
        return junitJar;
    }

    private File junitJar;

    boolean isJUnitAvailable() {
        if (junitJarExists == null)
            junitJarExists = getJUnitJar().exists();
        return junitJarExists;
    }

    private Boolean junitJarExists;

    //---------------------------------------------------------------------

    Path getJavaTestClassPath() {
        if (javaTestClassPath == null) {
            File jtClsDir = ProductInfo.getJavaTestClassDir();
            javaTestClassPath = new Path(jtClsDir);

            if (jtClsDir.getName().equals("javatest.jar")) {
                File installDir = jtClsDir.getParentFile();
                // append jtreg.jar to the path
                javaTestClassPath.append(new File(installDir, "jtreg.jar"));
            }
        }
        return javaTestClassPath;
    }

    private Path javaTestClassPath;

    //---------------------------------------------------------------------

    List<String> getTestVMOptions() {
        if (testVMOpts == null)
            testVMOpts = new ArrayList<String>();
        return testVMOpts;
    }

    void setTestVMOptions(List<String> testVMOpts) {
        this.testVMOpts = testVMOpts;
    }

    private List<String> testVMOpts;

    /**
     * Return the set of VM options each prefixed by -J, as required by JDK tools.
     */
    List<String> getTestToolVMOptions() {
        List<String> testToolVMOpts = new ArrayList<String>();
        for (String s: getTestVMOptions())
            testToolVMOpts.add("-J" + s);
        return testToolVMOpts;
    }

    /**
     * Return the set of VM options and Java options, for use by the java command.
     */
    List<String> getTestVMJavaOptions() {
        if (testVMOpts == null || testVMOpts.isEmpty())
            return getTestJavaOptions();
        if (testJavaOpts == null || testJavaOpts.isEmpty())
            return getTestVMOptions();
        List<String> opts = new ArrayList<String>();
        opts.addAll(getTestVMOptions());
        opts.addAll(getTestJavaOptions());
        return opts;
    }

    //---------------------------------------------------------------------

    List<String> getTestCompilerOptions() {
        if (testCompilerOpts == null)
            testCompilerOpts = new ArrayList<String>();
        return testCompilerOpts;
    }

    void setTestCompilerOptions(List<String> testCompilerOpts) {
        this.testCompilerOpts = testCompilerOpts;
    }

    private List<String> testCompilerOpts;

    //---------------------------------------------------------------------

    List<String> getTestJavaOptions() {
        if (testJavaOpts == null)
            testJavaOpts = new ArrayList<String>();
        return testJavaOpts;
    }

    void setTestJavaOptions(List<String> testJavaOpts) {
        this.testJavaOpts = testJavaOpts;
    }

    private List<String> testJavaOpts;

    //---------------------------------------------------------------------

    List<String> getRetainArgs() {
        return retainArgs;
    }

    void setRetainArgs(List<String> retainArgs) {
        this.retainArgs = retainArgs;

        retainStatusSet.clear();
        if (retainArgs == null) {
            // equivalent to "none"
            retainFilesPattern = null;
            return;
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

    boolean isRetainEnabled() {
        return (retainArgs != null);
    }

    Set<Integer> getRetainStatus() {
        return retainStatusSet;
    }

    Pattern getRetainFilesPattern() {
        return retainFilesPattern;
    }

    //---------------------------------------------------------------------

    void setReportDir(File reportDir) {
        reportDir.getClass(); // null check
        this.reportDir = reportDir;
    }

    File getReportDir() {
        return reportDir;
    }

    private File reportDir;

    //---------------------------------------------------------------------

    void setExclusiveLock(File exclusiveLock) {
        exclusiveLock.getClass(); // null check
        this.exclusiveLock = exclusiveLock;
    }

    File getExclusiveLock() {
        return exclusiveLock;
    }

    private File exclusiveLock;

    //---------------------------------------------------------------------

    private List<String> retainArgs;
    private Set<Integer> retainStatusSet = new HashSet<Integer>(4);
    private Pattern retainFilesPattern;

}
