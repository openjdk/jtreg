/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.javatest.Status;
import com.sun.javatest.regtest.agent.JDK_Version;
import com.sun.javatest.regtest.agent.TestNGRunner;
import com.sun.javatest.regtest.config.Locations;
import com.sun.javatest.regtest.config.Locations.LibLocn;
import com.sun.javatest.regtest.config.ParseException;

/**
 * This class implements the implicit "testng" action for TestNG tests.
 *
 * @see Action
 */
public class TestNGAction extends MainAction {
    public static final String NAME = "testng";

    /**
     * {@inheritdoc}
     * @return "testng"
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * This method does initial processing of the options and arguments for the
     * action.  Processing is determined by the requirements of run().
     *
     * Verify arguments are not of length 0 and separate them into the options
     * to java, the classname, and the parameters to the named class.
     *
     * Verify that the options are valid for the "testng" action.
     *
     * @param opts The options for the action.
     * @param args The arguments for the actions.
     * @param reason Indication of why this action was invoked.
     * @param script The script.
     * @exception  ParseException If the options or arguments are not expected
     *             for the action or are improperly formated.
     */
    @Override
    public void init(Map<String,String> opts, List<String> args, String reason,
                     RegressionScript script)
        throws ParseException
    {
//        if (args.length == 0)
//            throw new ParseException(TESTNG_NO_CLASSNAME);
        userSpecified = reason.startsWith(SREASON_USER_SPECIFIED);
        init(opts, args, reason, script, TestNGRunner.class.getName());

//        if (getMainArgs().size() != 0)
//            throw new ParseException(TESTNG_BAD_MAIN_ARG);

    } // init()

    boolean userSpecified = false;

    // cache results?
    @Override
    protected Status build() throws TestRunException {
        if (userSpecified) {
            return super.build();
        } else {
            JDK_Version v = script.getCompileJDKVersion();
            Map<String,String> buildOpts = new HashMap<>();
            if (v.compareTo(JDK_Version.V1_6) >= 0) {
                buildOpts.put("implicit", "none");
            }
            Locations locations = script.locations;
            Set<String> buildArgs = new LinkedHashSet<>(script.getLibBuildArgs());
            if (buildArgs.isEmpty()) {
                buildArgs.addAll(listModules(locations.absLibSrcList(LibLocn.Kind.SYS_MODULE)));
                buildArgs.addAll(listModules(locations.absLibSrcList(LibLocn.Kind.USER_MODULE)));
                buildArgs.addAll(listClasses(locations.absLibSrcList(LibLocn.Kind.PACKAGE)));
            }
            try {
                File testSrcDir = locations.absTestSrcDir();
                switch (locations.getDirKind(testSrcDir)) {
                    case PACKAGE:
                        buildArgs.addAll(listClasses(Collections.singletonList(testSrcDir)));
                        break;
                    case SYS_MODULE:
                    case USER_MODULE:
                        buildArgs.addAll(listModules(Collections.singletonList(testSrcDir)));
                        break;
                }
            } catch (Locations.Fault e) {
                return Status.error(e.getMessage());
            }
            BuildAction ba = new BuildAction();
            return ba.build(buildOpts, new ArrayList<>(buildArgs), SREASON_ASSUMED_BUILD, script);
        }
    }

    private List<String> listClasses(List<File> roots) {
        List<String> classes = new ArrayList<>();
        for (File root: roots)
            listClasses(root, null, classes);
        return classes;
    }

    private void listClasses(File dir, String pkg, List<String> classes) {
        for (File f: dir.listFiles()) {
            String f_name = f.getName();
            if (f.isDirectory())
                listClasses(f, pkg == null ? f_name : pkg + "." + f_name, classes);
            else if (f_name.endsWith(".java")) {
                String c_name = f_name.substring(0, f_name.length() - 5);
                classes.add(pkg == null ? c_name : pkg + "." + c_name);
            }
        }
    }

    private Set<String> listModules(List<File> roots) {
        Set<String> modules = new LinkedHashSet<>();
        for (File root: roots) {
            for (File f: root.listFiles()) {
                if (f.isDirectory())
                    modules.add(f.getName() + "/*");
            }
        }
        return modules;
    }

    private static final File TESTNG_RESULTS_XML = new File("testng-results.xml");

    @Override
    public void endAction(Status s) {
        super.endAction(s);
        if (script.isCheck())
            return;
        script.getTestNGReporter().add(script.getTestResult(), section);
        String jtrPath = script.getTestResult().getWorkRelativePath();
        String tngPath = jtrPath.replaceAll("\\.jtr$", ".testng-results.xml");
        script.saveScratchFile(TESTNG_RESULTS_XML, new File(tngPath));
    }


}
