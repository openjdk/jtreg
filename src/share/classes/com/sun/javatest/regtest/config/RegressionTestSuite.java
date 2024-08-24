/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.PrintWriter;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.sun.javatest.InterviewParameters;
import com.sun.javatest.Script;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestEnvironment;
import com.sun.javatest.TestFinder;
import com.sun.javatest.TestSuite;
import com.sun.javatest.WorkDirectory;
import com.sun.javatest.regtest.exec.RegressionScript;
import com.sun.javatest.regtest.tool.Version;
import com.sun.javatest.util.BackupPolicy;
import com.sun.javatest.util.I18NResourceBundle;

import com.sun.javatest.regtest.tool.RegressionContextManager;


public final class RegressionTestSuite extends TestSuite
{
    static Map<File, SoftReference<RegressionTestSuite>> cache;

    public static RegressionTestSuite open(File testSuiteRoot,
            TestFinder.ErrorHandler errHandler) throws Fault {
        if (cache == null)
            cache = new HashMap<>();
        SoftReference<RegressionTestSuite> ref = cache.get(testSuiteRoot);
        RegressionTestSuite ts = (ref == null) ? null : ref.get();
        if (ts == null) {
            ts = new RegressionTestSuite(testSuiteRoot, errHandler);
            cache.put(testSuiteRoot, new SoftReference<>(ts));
        }
        return ts;
    }

    /**
     * Creates a {@code RegressionTestSuite} object for the test suite identified by a given path.
     * @param testSuiteRoot the root directory of the test suite
     * @param errHandler a handler that can be used to report any problems encountered by
     *      the test suite's test handler
     * @throws Fault if there are problems reading the {@code TEST.ROOT} file.
     */
    public RegressionTestSuite(File testSuiteRoot, TestFinder.ErrorHandler errHandler) throws Fault {
        super(testSuiteRoot, createTSInfo(), RegressionTestSuite.class.getClassLoader());
        properties = new TestProperties(getRootDir(), errHandler);
        this.errHandler = errHandler;
        setTestFinder(createTestFinder());
    }

    private static Map<String,String> createTSInfo() {
        Map<String, String> map = new HashMap<>();
        map.put(TestSuite.TM_CONTEXT_NAME, RegressionContextManager.class.getName());
        return map;
    }


    @Override
    public String getName() {
        return getPath(); // better than nothing, could pick up name from TEST.ROOT
    }

    @Override
    protected TestFinder createTestFinder() throws Fault {
        try {
            TestFinder f = new RegressionTestFinder(properties, errHandler);
            f.init(new String[] { }, getRoot(), null);
            return f;
        } catch (TestFinder.Fault e) {
            throw new Error();
        }
    }

    @Override
    public boolean getTestRefreshBehavior(int event) {
        switch (event) {
            case CLEAR_CHANGED_TEST:
            case DELETE_NONTEST_RESULTS:
                return true;
            default:
                return super.getTestRefreshBehavior(event);
        }
    }


    @Override
    public Script createScript(TestDescription td, String[] exclTestCases, TestEnvironment scriptEnv,
            WorkDirectory workDir,
            BackupPolicy backupPolicy) throws Fault {
        Script s = new RegressionScript();

        // generic script init
        s.initTestDescription(td);
        s.initExcludedTestCases(exclTestCases);
        s.initTestEnvironment(scriptEnv);
        s.initWorkDir(workDir);
        s.initBackupPolicy(backupPolicy);
        s.initClassLoader(getClassLoader());

        return s;
    }

    public interface ParametersFactory {
        RegressionParameters create(RegressionTestSuite ts) throws TestSuite.Fault;
    }

    private static ParametersFactory factory;

    public static void setParametersFactory(ParametersFactory factory) {
        RegressionTestSuite.factory = factory;
    }

    /**
     * {@inheritDoc}
     *
     * If no {@link #setParametersFactory(ParametersFactory) factory} has been set,
     * any errors that may be reported while using the {@link JDK} and related classes
     * will be written to {@link System#err}.
     */
    @Override
    public RegressionParameters createInterview() throws TestSuite.Fault {
        try {
            return (factory != null) ? factory.create(this) // expected case
                    : new RegressionParameters("regtest", this, System.err::println); // fallback
        } catch (InterviewParameters.Fault e) {
            throw new TestSuite.Fault(i18n, "suite.cantCreateInterview", e.getMessage());
        }
    }



    @Override
    public URL[] getFilesForTest(TestDescription td) {
        Set<URL> urls = new LinkedHashSet<>();

        // always start with the file containing the test description
        try {
            urls.add(td.getFile().toURI().toURL());
        } catch (MalformedURLException e) {
            // ignore any bad URLs
        }

        try {
            RegressionParameters params = createInterview();
            Set<File> files = RegressionScript.getSourceFiles(params, td);
            for (File file: files) {
                try {
                    urls.add(file.toURI().toURL());
                } catch (MalformedURLException e) {
                }
            }
        } catch (Fault ignore) {
        }
        return urls.toArray(new URL[urls.size()]);
    }

    public GroupManager getGroupManager(PrintWriter out) throws IOException {
        GroupManager g = new GroupManager(out, getRootDir().toPath(), properties.getGroupFiles());
        RegressionTestFinder tf = (RegressionTestFinder) getTestFinder();
        g.setAllowedExtensions(tf.getAllowedExtensions());
        g.setIgnoredDirectories(tf.getIgnoredDirectories());
        return g;
    }

    // defined in JT Harness 4.2, needs to be overridden
    // because default impl broken for jtreg (NPE)
    @Override
    public boolean needServices() {
        return false;
    }

    public ExecMode getDefaultExecMode() {
        return properties.getDefaultExecMode();
    }

    public boolean useBootClassPath(String rootRelativePath) throws TestSuite.Fault {
        return properties.useBootClassPath(new File(getRootDir(), rootRelativePath));
    }

    public boolean useOtherVM(TestDescription td) throws TestSuite.Fault {
        return properties.useOtherVM(td.getFile());
    }

    /**
     * {@return true if the test is configured to run exclusively, false otherwise}
     * @param td the test description
     */
    public boolean needsExclusiveAccess(TestDescription td) {
        return properties.needsExclusiveAccess(td.getFile());
    }

    public Version getRequiredVersion() {
        return properties.getRequiredVersion();
    }

    public Set<String> getLibDirs(TestDescription td) throws TestSuite.Fault {
        return properties.getLibDirs(td.getFile());
    }

    public Set<String> getLibBuildArgs(TestDescription td) throws TestSuite.Fault {
        return properties.getLibBuildArgs(td.getFile());
    }

    public Set<File> getExternalLibRoots(TestDescription td) throws TestSuite.Fault {
        return properties.getExternalLibs(td.getFile());
    }

    public Set<String> getDefaultModules(TestDescription td) throws TestSuite.Fault {
        return properties.getModules(td.getFile());
    }

    public ExtraPropDefns getExtraPropDefns() {
        return properties.getExtraPropDefns();
    }

    public int getMaxOutputSize(TestDescription td) throws TestSuite.Fault {
        return properties.getMaxOutputSize(td.getFile());
    }

    public boolean getAllowSmartActionArgs(TestDescription td) throws TestSuite.Fault {
        return properties.getAllowSmartActionArgs(td.getFile());
    }

    public boolean getEnablePreview(TestDescription td) throws TestSuite.Fault {
        return properties.getEnablePreview(td.getFile());
    }

    private final TestFinder.ErrorHandler errHandler;
    private final TestProperties properties;
    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(RegressionTestSuite.class);
}
