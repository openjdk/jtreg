/*
 * Copyright 1998-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.util.List;

import com.sun.javatest.Status;
import com.sun.javatest.TestResult;
import java.util.Iterator;

/**
 * This class implements the "junit" action, which is a variation of "main".
 *
 * @author John R. Rose
 * @see MainAction
 */
public class JUnitAction extends MainAction
{
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
    public void init(String[][] opts, String[] args, String reason,
                     RegressionScript script)
        throws ParseException
    {
        if (args.length == 0)
            throw new ParseException(JUNIT_NO_CLASSNAME);

        init(opts, args, reason, script, JUnitRunner.class.getName());

        if (getMainArgs().length() != 0)
            throw new ParseException(JUNIT_BAD_MAIN_ARG);

    } // init()

    protected String getActionName() {
        return "junit";
    }

    public static class JUnitRunner {
        public static void main(String... args) throws Exception {
            if (args.length != 1)
                throw new Error("wrong number of arguments");
            Class<?> mainClass = Class.forName(args[0]);
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
