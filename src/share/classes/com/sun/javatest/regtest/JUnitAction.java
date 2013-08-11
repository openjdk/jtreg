/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;
import java.util.List;

/**
 * This class implements the "junit" action, which is a variation of "main".
 *
 * @author John R. Rose
 * @see MainAction
 */
public class JUnitAction extends MainAction
{
    public static final String NAME = "junit";

    /**
     * {@inheritdoc}
     * @return "junit"
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
     * Verify that the options are valid for the "junit" action.
     *
     * @param opts The options for the action.
     * @param args The arguments for the actions.
     * @param reason Indication of why this action was invoked.
     * @param script The script.
     * @exception  ParseException If the options or arguments are not expected
     *             for the action or are improperly formated.
     */
    @Override
    public void init(String[][] opts, String[] args, String reason,
                     RegressionScript script)
        throws ParseException
    {
        if (args.length == 0)
            throw new ParseException(JUNIT_NO_CLASSNAME);

        init(opts, args, reason, script, JUnitRunner.class.getName());

        if (getClassArgs().size() != 0)
            throw new ParseException(JUNIT_BAD_MAIN_ARG);

    } // init()

    public static class JUnitRunner implements TestRunner {
        public static void main(String... args) throws Exception {
            main(null, args);
        }

        public static void main(ClassLoader loader, String... args) throws Exception {
            if (args.length != 2)
                throw new Error("wrong number of arguments");
            // String testName = args[0];  // not used
            Class<?> mainClass = (loader == null) ? Class.forName(args[1]) : loader.loadClass(args[1]);
            org.junit.runner.Result result;
            try {
                result = org.junit.runner.JUnitCore.runClasses(mainClass);
            } catch (NoClassDefFoundError ex) {
                throw new Exception(JUNIT_NO_DRIVER, ex);
            }
            if (!result.wasSuccessful()) {
                List<org.junit.runner.notification.Failure> failures = result.getFailures();
                for (Iterator<org.junit.runner.notification.Failure>
                                it = failures.iterator(); it.hasNext(); ) {
                    org.junit.runner.notification.Failure failure = it.next();
                    System.err.println("JavaTest Message: JUnit Failure: "+failure);
                }
                throw new Exception("JUnit test failure");
            }
        }
    }

}
