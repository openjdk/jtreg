/*
 * Copyright 1998-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.javatest.Status;
import com.sun.javatest.TestResult;

/**
 * This class implements the "clean" action as described by the JDK tag
 * specification.
 *
 * @author Iris A Garcia
 * @see Action
 */
public class CleanAction extends Action
{
    /**
     * This method does initial processing of the options and arguments for the
     * action.  Processing is determined by the requirements of run().
     *
     * Verify that the passed options and arguments are of length 0.
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
        this.script = script;
        this.reason = reason;

        if (opts.length != 0)
            throw new ParseException(CLEAN_UNEXPECT_OPT);

        if (args.length == 0)
            throw new ParseException(CLEAN_NO_CLASSNAME);

        for (int i = 0; i < args.length; i++) {
            String currArg = args[i];
            if ((currArg.indexOf(File.separatorChar) != -1)
                || (currArg.indexOf('/') != -1))
                throw new ParseException(CLEAN_BAD_CLASSNAME + currArg);
        }

        this.args = args;
    } // init()

    /**
     * The method that does the work of the action.  The necessary work for the
     * given action is defined by the tag specification.
     *
     * Remove the class files for the named classes, if they exist.  Limited
     * wildcard processing is supported.  The class name is fully qualified as
     * necessary before deletion.
     *
     * @return     The result of the action.
     * @exception  TestRunException If an unexpected error occurs while running
     *             the test.
     */
    public Status run() throws TestRunException {
        // This doesn't complain if the specified file doesn't exist
        Status status = Status.passed(CLEAN_SUCC);

        section = startAction("clean", args, reason);

        if (script.isCheck()) {
            status = Status.passed(CHECK_PASS);
        } else {
            for (int i = 0; i < args.length; i++) {

                if (args[i].equals("*"))
                    // clean default package
                    args[i] = ".*";
                if (args[i].endsWith(".*")) {
                    // clean any package
                    String path = args[i].substring(0, args[i].length() -2);
                    path = path.replace('.', File.separatorChar);
                    File dir = script.absTestClsDir();
                    if (!path.equals(""))
                        dir = new File(dir, path);

                    try {
                        if (dir.isDirectory()) {
                            File[] files = dir.listFiles();
                            for (int j = 0; j < files.length; j++) {
                                File f = files[j];
                                // don't complain about not being able to clean
                                // subpackages
                                if (!f.delete() && !f.isDirectory())
                                    throw new TestRunException(CLEAN_RM_FAILED + f);
                            }
                        }
                    } catch (SecurityException e) {
                        // shouldn't happen as JavaTestSecurityManager allows file ops
                        throw new TestRunException(CLEAN_SECMGR_PROB + dir);
                    }
                } else {
                    // clean class file
                    File victim = new File(script.absTestClsDir(),
                                           args[i].replace('.', File.separatorChar) + ".class");
                    if (victim.exists() && !victim.delete())
                        return Status.error(CLEAN_RM_FAILED + victim);
                }
            }
        }

        endAction(status, section);
        return status;
    } // run()

    //----------member variables------------------------------------------------

    private String[] args;

    private TestResult.Section section;
}
