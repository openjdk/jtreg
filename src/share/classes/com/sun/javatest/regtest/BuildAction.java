/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.javatest.Status;
import com.sun.javatest.regtest.Locations.ClassLocn;

import static com.sun.javatest.regtest.RStatus.*;

/**
 * This class implements the "build" action as described by the JDK tag
 * specification.
 *
 * @author Iris A Garcia
 * @see Action
 */
public class BuildAction extends Action
{
    public static final String NAME = "build";

    /**
     * {@inheritdoc}
     * @return "build"
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * A method used by sibling classes to run both the init() and run()
     * method of BuildAction.
     *
     * @param opts The options for the action.
     * @param reason Indication of why this action was invoked.
     * @param args The arguments for the actions.
     * @param script The script.
     * @return     The result of the action.
     * @throws TestRunException if an error occurs during the work
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
    @Override
    public void init(String[][] opts, String[] args, String reason,
                     RegressionScript script)
        throws ParseException
    {
        super.init(opts, args, reason, script);

        for (String[] opt : opts) {
            if (opt[0].equals("implicit") && opt[1].equals("none")) {
                implicitOpt = "-implicit:none";
                continue;
            }
            throw new ParseException(BUILD_UNEXPECT_OPT);
        }

        if (args.length == 0)
            throw new ParseException(BUILD_NO_CLASSNAME);

        for (String currArg : args) {
            if ((currArg.indexOf(File.separatorChar) != -1)
                    || (currArg.indexOf('/') != -1))
                throw new ParseException(BUILD_BAD_CLASSNAME + currArg);
        }
    } // init()

    @Override
    public Set<File> getSourceFiles() {
        Set<File> files = new LinkedHashSet<File>();
        for (String arg: args) {
            // the arguments to build are classnames or package names with wildcards
            try {
                for (ClassLocn cl: script.locations.locateClasses(arg)) {
                    files.add(cl.absSrcFile);
                }
            } catch (TestRunException ignore) {
            }
        }
        return files;
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
     * @return  The result of the action.
     * @throws  TestRunException If an unexpected error occurs while running
     *          the test.
     */
    public Status run() throws TestRunException {
        Status status;

        startAction();

        // step 1: see which files need compiling, and group them according
        // to the value of the -d flag that will be required
        PrintWriter pw = section.getMessageWriter();
        long now = System.currentTimeMillis();
        Map<File, List<File>> filesToCompile = new LinkedHashMap<File, List<File>>();
        for (String arg: args) {
            // the argument to build is a classname or package name with wildcards
            for (ClassLocn cl: script.locations.locateClasses(arg)) {
                if (cl.absSrcFile.lastModified() > now) {
                    pw.println(String.format(BUILD_FUTURE_SOURCE, cl.absSrcFile,
                            DateFormat.getDateTimeInstance().format(new Date(cl.absSrcFile.lastModified()))));
                    pw.println(BUILD_FUTURE_SOURCE_2);
                }
                if (!cl.isUpToDate()) {
                    File destDir = cl.lib.absClsDir;
                    List<File> filesForDest = filesToCompile.get(destDir);
                    if (filesForDest == null) {
                        filesForDest = new ArrayList<File>();
                        filesToCompile.put(destDir, filesForDest);
                    }
                    filesForDest.add(cl.absSrcFile);
                }
            }
        }

        // step 2: perform the compilations, if any
        if (filesToCompile.isEmpty()) {
            status = passed(BUILD_UP_TO_DATE);
        } else {
            status = null;
            for (Map.Entry<File,List<File>> e: filesToCompile.entrySet()) {
                File destDir = e.getKey();
                List<File> filesForDest = e.getValue();
                CompileAction ca = new CompileAction();
                String[][] compOpts = { };
                // RFE:  For now we just compile dir at a time in isolation
                // A better solution would be to put other dirs on source path
                // and use -implicit:none
                List<String> compileArgs = new ArrayList<String>();
                if (IGNORE_SYMBOL_FILE)
                    compileArgs.add("-XDignore.symbol.file=true");
                if (implicitOpt != null)
                    compileArgs.add(implicitOpt);
                compileArgs.addAll(asStrings(filesForDest));
                String[] compArgs = compileArgs.toArray(new String[compileArgs.size()]);
                Status s =  ca.compile(destDir, compOpts, compArgs, SREASON_FILE_TOO_OLD, script);
                if (!s.isPassed()) {
                    status = s;
                    break;
                }
            }
            if (status == null)
                status = passed(BUILD_SUCC);
        }

        endAction(status);
        return status;
    } // run()

    private List<String> asStrings(List<File> files) {
        List<String> strings = new ArrayList<String>();
        int i = 0;
        for (File f: files)
            strings.add(f.getPath());
        return strings;
    }

    //----------member variables------------------------------------------------

    private String implicitOpt;
    private static final boolean IGNORE_SYMBOL_FILE = true;
}
