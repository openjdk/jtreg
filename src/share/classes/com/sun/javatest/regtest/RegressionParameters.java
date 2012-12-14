/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.sun.interview.Interview;
import com.sun.interview.Question;
import com.sun.javatest.InterviewParameters;
import com.sun.javatest.TestEnvironment;
import com.sun.javatest.Parameters;
import com.sun.javatest.ProductInfo;
import com.sun.javatest.Status;
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

    public void setTests(List<String> tests) {
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

    // The following (load and save) are an interim consequence of not using proper
    // interview questions to store configurations values.
    // The critical values to preserve are any that may be set by direct setXYZ methods
    // below.
    // A better solution is to migrate to using interview questions where possible,
    // and potentially allow the values to be modified via the Config Editor

    private static final String ENVVARS = ".envVars";
    private static final String CHECK = ".check";
    private static final String JDK = ".jdk";
    private static final String EXEC_MODE = ".execMode";
    private static final String TEST_VM_OPTIONS = ".testVMOpts";
    private static final String TEST_COMPILER_OPTIONS = ".testCompilerOpts";
    private static final String TEST_JAVA_OPTIONS = ".testJavaOpts";
    private static final String IGNORE = ".ignore";
    private static final String RETAIN_ARGS = ".retain";
    private static final String JUNIT = ".junit";

    @Override
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

        v = (String) data.get(prefix + JDK);
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
    }

    @SuppressWarnings("unchecked")
    @Override
    public void save(Map data) {
        save0((Map<String,String>) data);
        super.save(data);
    }

    private void save0(Map<String,String> data) {
        String prefix = getTag();

        if (envVars != null)
            data.put(prefix + ENVVARS, StringArray.join(envVars, "\n"));

        data.put(prefix + CHECK, String.valueOf(check));
        data.put(prefix + EXEC_MODE, String.valueOf(execMode));
        data.put(prefix + IGNORE, String.valueOf(ignoreKind));

        if (testJDK != null)
            data.put(prefix + JDK, testJDK.getPath());

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

    private List<String> retainArgs;
    private Set<Integer> retainStatusSet = new HashSet<Integer>(4);
    private Pattern retainFilesPattern;

}
