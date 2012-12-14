/*
 * Copyright 2000-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import com.sun.javatest.InterviewParameters;
import com.sun.javatest.util.BackupPolicy;
import com.sun.javatest.util.I18NResourceBundle;
import com.sun.javatest.Script;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestEnvironment;
import com.sun.javatest.TestFinder;
import com.sun.javatest.TestSuite;
import com.sun.javatest.WorkDirectory;
import java.util.Set;


public class RegressionTestSuite extends TestSuite
{
    /**
     * @throws Fault Thrown if there are problems reading TEST.ROOT.
     */
    public RegressionTestSuite(File testSuiteRoot) throws Fault {
        super(testSuiteRoot);
        setProperties();
        setTestFinder(createTestFinder());
    }

    @Override
    protected TestFinder createTestFinder() throws Fault {
        try {
            TestFinder f = new RegressionTestFinder(validKeys, checkBugID);
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

    @Override
    public InterviewParameters createInterview() throws TestSuite.Fault {
        try {
            return new RegressionParameters("regtest", this);
        }
        catch (InterviewParameters.Fault e) {
            throw new TestSuite.Fault(i18n, "suite.cantCreateInterview", e.getMessage());
        }
    }

    @Override
    public String[] getAdditionalDocNames() {
        return additionalDocNames;
    }

    private static String[] additionalDocNames = {
        "com/sun/javatest/regtest/help/jtreg.hs"
    };

    @Override
    public URL[] getFilesForTest(TestDescription td) {
        List<URL> urls = new ArrayList<URL>();

        // always include the file containing the test description
        try {
            urls.add(td.getFile().toURI().toURL());
        } catch (MalformedURLException e) {
            // ignore any bad URLs
        }

        File[] files = new RegressionScript().getSourceFiles(td);
        for (int i = 0; i < files.length; i++) {
            try {
                urls.add(files[i].toURI().toURL());
            } catch (MalformedURLException e) {
            }
        }
        return urls.toArray(new URL[urls.size()]);
    }

    private void setProperties() throws Fault {
        // get additional specified properties from TEST.ROOT
        File file = new File(getRoot(), "TEST.ROOT");
        if (file.exists()) {
            Properties testRootProps = new Properties();
            try {
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
                testRootProps.load(in);

                // add the list of valid keys
                String keys = testRootProps.getProperty("keys");
                if ((keys != null) && (keys.trim().length() > 0)) {
                    //sysProps.put("env.regtest.keys", keys);   // old way
                    validKeys = new HashSet<String>(Arrays.asList(StringArray.splitWS(keys))); // new way
                }

                // determine whether we want to enforce bugid syntax
                // the default is that we always do
                String bug = testRootProps.getProperty("checkBugID");
                if (bug != null) {
                    bug = bug.trim();
                    if (bug.equals("false")) {
                        //sysProps.put("env.regtest.checkBugID", bug);  // old
                        checkBugID = false;                             // new
                    }
                }

                in.close();
            } catch (IOException e) {
                throw new Fault(i18n, "suite.cantRead", file);
            }
        }
    } // setProperties()

    private Set<String> validKeys;
    private boolean checkBugID = true;
    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(RegressionTestSuite.class);
}
