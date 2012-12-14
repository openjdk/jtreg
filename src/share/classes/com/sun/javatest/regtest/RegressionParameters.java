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

import com.sun.interview.Interview;
import com.sun.interview.Question;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import com.sun.javatest.InterviewParameters;
import com.sun.javatest.TestEnvironment;
import com.sun.javatest.Parameters;
import com.sun.javatest.ProductInfo;
import com.sun.javatest.Status;
import com.sun.javatest.TestSuite;
import com.sun.javatest.interview.BasicInterviewParameters;
import com.sun.javatest.lib.ProcessCommand;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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

    public void setTimeoutFactor(int tfac) {
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
    private static final String SAME_JVM = ".sameJVM";
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

        v = (String) data.get(prefix + SAME_JVM);
        if (v != null)
            setSameJVM(v.equals("true"));

        v = (String) data.get(prefix + IGNORE);
        if (v != null)
            setIgnoreKind(IgnoreKind.valueOf(v));

        v = (String) data.get(prefix + JDK);
        if (v != null)
            setJDK(new JDK(v));

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
        data.put(prefix + SAME_JVM, String.valueOf(sameJVM));
        data.put(prefix + IGNORE, String.valueOf(ignoreKind));

        if (jdk != null)
            data.put(prefix + JDK, jdk.getPath());

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

    void setSameJVM(boolean sameJVM) {
        this.sameJVM = sameJVM;
    }

    boolean isSameJVM() {
        return sameJVM;
    }

    boolean isOtherJVM() {
        return !sameJVM;
    }

    boolean sameJVM;

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

    void setJDK(JDK jdk) {
        jdk.getClass(); // null check
        this.jdk = jdk;
    }

    JDK getJDK() {
        return jdk;
    }

    JDK jdk;

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

    //@Deprecated
    private File getJavaHome() {
        if (javaHome == null)
            initJavaHome();
        return javaHome;
    }

    private void initJavaHome() {
        String s;
        String[] ev = getEnvVars();
        s = System.getProperty("java.home");
        for (int i = 0; i < ev.length; i++) {
            if (ev[i].startsWith("TESTJAVAHOME")) {
                String jh = (StringArray.splitEqual(ev[i]))[1];
                if (!jh.equals("samevm"))
                    s = jh;
                break;
            }
        }
        javaHome = new File(s);
    }

    private File javaHome;

    //---------------------------------------------------------------------

    String getJavaVersion() {
        if (javaVersion == null) {
            final String VERSION_PROPERTY = "java.specification.version";
            String version = "unknown";
            if (isOtherJVM()) {
                // TODO: move to JDK
                Status status = null;
                // since we are trying to determine the Java version, we have to assume
                // the worst, and use CLASSPATH.
                String[] cmdArgs = new String[] {
                    "CLASSPATH=" + getJavaTestClassPath(),
                    jdk.getJavaProg().getPath(),
                    "com.sun.javatest.regtest.GetSystemProperty",
                    VERSION_PROPERTY
                };

                // PASS TO PROCESSCOMMAND
                StringWriter outSW = new StringWriter();
                StringWriter errSW = new StringWriter();

                ProcessCommand cmd = new ProcessCommand();
                //cmd.setExecDir(scratchDir());
                status = cmd.run(cmdArgs, new PrintWriter(errSW), new PrintWriter(outSW));

                // EVALUATE THE RESULTS
                if (status.isPassed()) {
                    // we sent everything to stdout
                    String[] v = StringArray.splitEqual(outSW.toString().trim());
                    if (v.length == 2 && v[0].equals(VERSION_PROPERTY))
                        version = v[1];
                }
            } else
                version = System.getProperty(VERSION_PROPERTY);

            // java.java.specification.version is not defined in JDK1.1.*
            if (version == null || version.length() == 0)
                javaVersion = "1.1";
            else
                javaVersion = version;

        }
        return javaVersion;
    }

    private String javaVersion;

    //---------------------------------------------------------------------

    String getJavaFullVersion() {
        if (javaFullVersion == null) {
            Status status = null;
            List<String> cmdArgs = new ArrayList<String>();
            cmdArgs.add(jdk.getJavaProg().getPath());
            cmdArgs.addAll(getTestVMOptions());
            cmdArgs.add("-version");

            // PASS TO PROCESSCOMMAND
            StringWriter outSW = new StringWriter();
            StringWriter errSW = new StringWriter();

            ProcessCommand cmd = new ProcessCommand();
            // no need to set execDir for "java -version"
            status = cmd.run(cmdArgs.toArray(new String[cmdArgs.size()]),
                            new PrintWriter(errSW), new PrintWriter(outSW));

            // EVALUATE THE RESULTS
            if (status.isPassed()) {
                // some JDK's send the string to stderr, others to stdout
                String version = errSW.toString().trim();
                javaFullVersion = "(" + getJDK() + ")" + LINESEP;
                if (version.length() == 0)
                    javaFullVersion += outSW.toString().trim();
                else
                    javaFullVersion += version;
            } else {
                javaFullVersion = getJDK().getPath();
            }
        }
        return javaFullVersion;
    }

    private String javaFullVersion;

    //---------------------------------------------------------------------

    // needed for JDK 1.1 only, for CLASSPATH; for JDK 1.2+ we use -classpath
    // ?? is it even needed there?
    String getStdJavaClassPath() {
        if (stdJavaClassPath == null) {
            File jh = getJavaHome();
            File jh_lib = new File(jh, "lib");

            stdJavaClassPath =
                (new File(jh, "classes") + PATHSEP +
                 new File(jh_lib, "classes") + PATHSEP +
                 new File(jh_lib, "classes.zip"));
        }
        return stdJavaClassPath;
    }

    private String stdJavaClassPath;

    //---------------------------------------------------------------------

    String getStdJDKClassPath() {
        if (stdJDKClassPath == null)
            stdJDKClassPath = getJDK().getToolsJar().getPath();
        return stdJDKClassPath;
    }

    private String stdJDKClassPath;

    //---------------------------------------------------------------------

    String getJavaTestClassPath() {
        if (javaTestClassPath == null) {
            File jtClsDir = ProductInfo.getJavaTestClassDir();
            javaTestClassPath = jtClsDir.getPath();

            int index = javaTestClassPath.indexOf("javatest.jar");
            if (index > 0) {
                String installDir = javaTestClassPath.substring(0, index);
                // append jtreg.jar to the path
                String jtregClassDir = installDir + "jtreg.jar";
                javaTestClassPath += PATHSEP + jtregClassDir;
            }
        }
        return javaTestClassPath;
    }

    private String javaTestClassPath;

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

    //---------------------------------------------------------------------

    private static final String PATHSEP  = System.getProperty("path.separator");
    private static final String LINESEP  = System.getProperty("line.separator");

}
