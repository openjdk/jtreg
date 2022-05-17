/*
 * Copyright (c) 1998, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.javatest.Status;
import com.sun.javatest.regtest.config.Locations;
import com.sun.javatest.regtest.config.Locations.ClassLocn;
import com.sun.javatest.regtest.config.ParseException;

import javax.lang.model.SourceVersion;

import static com.sun.javatest.regtest.RStatus.error;
import static com.sun.javatest.regtest.RStatus.passed;

/**
 * This class implements the "clean" action as described by the JDK tag
 * specification.
 *
 * @see Action
 */
public class CleanAction extends Action
{
    public static final String NAME = "clean";

    /**
     * {@inheritDoc}
     * @return "clean"
     */
    @Override
    public String getName() {
        return NAME;
    }

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
     *             for the action or are improperly formatted.
     */
    @Override
    public void init(Map<String,String> opts, List<String> args, String reason,
                     RegressionScript script)
        throws ParseException
    {
        super.init(opts, args, reason, script);

        if (!opts.isEmpty())
            throw new ParseException(CLEAN_UNEXPECT_OPT);

        if (args.isEmpty())
            throw new ParseException(CLEAN_NO_CLASSNAME);

        for (String arg : args) {
            // allow "clean default package" marker
            if ("*".equals(arg))
                continue;
            // allow qualified class name with optional "clean any package" pattern
            String name = arg.endsWith(".*") ? arg.substring(0, arg.length() - 2) : arg;
            if (SourceVersion.isName(name))
                continue;
            // detected a syntactically invalid class name
            throw new ParseException(CLEAN_BAD_CLASSNAME + arg);
        }
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
        Status status = passed(CLEAN_SUCC);

        startAction(false);

        if (script.isCheck()) {
            status = passed(CHECK_PASS);
        } else {
            for (String arg : args) {
                // NOTE -- should probably clean library-compiled classes
                // as well.

                if (arg.equals("*"))
                    // clean default package
                    arg = ".*";
                if (arg.endsWith(".*")) {
                    // clean any package
                    String path = arg.substring(0, arg.length() -2);
                    path = path.replace('.', File.separatorChar);
                    File dir = script.absTestClsDir().toFile();
                    if (!path.equals(""))
                        dir = new File(dir, path);

                    recorder.exec("for f in " + dir + "/*; do\n"
                            + "  if [ -f $f ]; then rm $f ; fi\n"
                            + "done");

                    try {
                        if (dir.isDirectory()) {
                            File[] files = dir.listFiles();
                            if (files != null) {
                                for (File f : files) {
                                    // don't complain about not being able to clean
                                    // subpackages
                                    if (!f.delete() && !f.isDirectory())
                                        throw new TestRunException(CLEAN_RM_FAILED + f);
                                }
                            }
                        }
                    } catch (SecurityException e) {
                        // shouldn't happen as JavaTestSecurityManager allows file ops
                        throw new TestRunException(CLEAN_SECMGR_PROB + dir);
                    }
                } else {
                    // clean class file
                    File victim = script.absTestClsDir().resolve(
                                           arg.replace('.', File.separatorChar) + ".class").toFile();
                    recorder.exec("rm -f " + victim);
                    if (victim.exists() && !victim.delete())
                        return error(CLEAN_RM_FAILED + victim);
                }
            }
        }

        endAction(status);
        return status;
    } // run()

    @Override
    public Set<File> getSourceFiles() {
        Set<File> files = new LinkedHashSet<>();
        for (String arg: args) {
            // the arguments to clean are class names or package names with wildcards
            try {
                for (ClassLocn cl: script.locations.locateClasses(arg)) {
                    if (Files.exists(cl.absSrcFile))
                        files.add(cl.absSrcFile.toFile());
                }
            } catch (Locations.Fault ignore) {
            }
        }
        return files;
    }

    //----------member variables------------------------------------------------
}
