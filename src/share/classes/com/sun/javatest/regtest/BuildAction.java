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

import com.sun.javatest.TestResult;
import com.sun.javatest.Status;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class implements the "build" action as described by the JDK tag
 * specification.
 *
 * @author Iris A Garcia
 * @see Action
 */
public class BuildAction extends Action
{
    /**
     * A method used by sibling classes to run both the init() and run()
     * method of BuildAction.
     *
     * @param opts The options for the action.
     * @param reason Indication of why this action was invoked.
     * @param args The arguments for the actions.
     * @param script The script.
     * @return     The result of the action.
     * @see #init
     * @see #run
     */
    public Status build(String[][] opts, String[] args, String reason,
                        RegressionScript script) throws TestRunException
    {
        init(opts, args, reason, script);
        return run();
    } // build()

    /**
     * This method does initial processing of the options and arguments for the
     * action.  Processing is determined by the requirements of run().
     *
     * Verify that the options are of length 0 and that there is at least one
     * argument.
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
            throw new ParseException(BUILD_UNEXPECT_OPT);

        if (args.length == 0)
            throw new ParseException(BUILD_NO_CLASSNAME);

        for (int i = 0; i < args.length; i++) {
            String currArg = args[i];
            if ((currArg.indexOf(File.separatorChar) != -1)
                || (currArg.indexOf('/') != -1))
                throw new ParseException(BUILD_BAD_CLASSNAME + currArg);
        }

        this.args = args;
        this.opts = opts;
    } // init()

    @Override
    public File[] getSourceFiles() {
        List<File> l = new ArrayList<File>();
        for (int i = 0; i < args.length; i++) {
            // the argument to build is a classname
            String currFN = args[i].replace('.', File.separatorChar) + ".java";
            try {
                File javaSrc = script.locateJavaSrc(currFN);
                l.add(javaSrc);
            } catch (TestRunException ignore) {
            }
        }
        return l.toArray(new File[l.size()]);
    }

    /**
     * The method that does the work of the action.  The necessary work for the
     * given action is defined by the tag specification.
     *
     * Each named class will be compiled if its corresponding class file doesn't
     * exist or is older than its source file.  The class name is fully
     * qualified as necessary and the ".java" extension is added before
     * compilation.
     *
     * Build is allowed to search anywhere in the library-list.  Compile is
     * allowed to search only in the directory containing the defining file of
     * the test.  Thus, compile will always absolutify by adding the directory
     * path of the defining file to the passed filename.  Build must pass an
     * absolute filename to handle files found in the library-list.
     *
     * @return     The result of the action.
     * @exception  TestRunException If an unexpected error occurs while running
     *             the test.
     */
    public Status run() throws TestRunException {
        Status status = null;

        section = startAction("build", args, reason);

        // step 1: see which files need compiling, and group them according
        // to the value of the -d flag that will be required
        PrintWriter pw = section.getMessageWriter();
        long now = System.currentTimeMillis();
        Map<File,List<File>> filesToCompile = new LinkedHashMap<File,List<File>>();
        for (int i = 0; i < args.length; i++) {
            // the argument to build is a classname
            String currFN = args[i].replace('.', File.separatorChar) + ".java";
            File sf = script.locateJavaSrc(currFN);
            File cf = script.locateJavaCls(currFN);
            if (sf.lastModified() > now) {
                pw.println(String.format(BUILD_FUTURE_SOURCE, sf,
                        DateFormat.getDateTimeInstance().format(new Date(sf.lastModified()))));
                pw.println(BUILD_FUTURE_SOURCE_2);
            }
            if (!cf.exists() || !cf.canRead()
                    || (cf.lastModified() < sf.lastModified())) {
                File destDir = script.absTestClsDestDir(sf);
                List<File> filesForDest = filesToCompile.get(destDir);
                if (filesForDest == null) {
                    filesForDest = new ArrayList<File>();
                    filesToCompile.put(destDir, filesForDest);
                }
                filesForDest.add(sf);
            }
        }

        // step 2: perform the compilations, if any
        if (filesToCompile.isEmpty()) {
            status = Status.passed(BUILD_UP_TO_DATE);
        } else {
            status = null;
            for (List<File> filesForDest: filesToCompile.values()) {
                File[] files = filesForDest.toArray(new File[filesForDest.size()]);
                CompileAction ca = new CompileAction();
                Status s =  ca.compile(opts, asStrings(files), SREASON_FILE_TOO_OLD, script);
                if (s.isFailed()) {
                    status = s;
                    break;
                }
            }
            if (status == null)
                status = Status.passed(BUILD_SUCC);
        }

        endAction(status, section);
        return status;
    } // run()

    private String[] asStrings(File[] files) {
        String[] strings = new String[files.length];
        for (int i = 0; i < files.length; i++)
            strings[i] = files[i].getPath();
        return strings;
    }

    //----------member variables------------------------------------------------

    private String[]   args;
    private String[][] opts;

    private TestResult.Section section;
}
