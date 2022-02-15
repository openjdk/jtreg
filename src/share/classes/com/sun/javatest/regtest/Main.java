/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.types.Commandline;

import com.sun.javatest.Harness;
import com.sun.javatest.regtest.tool.AntOptionDecoder;
import com.sun.javatest.regtest.tool.Tool;
import com.sun.javatest.util.I18NResourceBundle;

/**
 * Main entry point to be used to access the Regression Test Harness for JDK: jtreg.
 */
public class Main {

    /**
     * Standard entry point.
     * Only returns if GUI mode is initiated; otherwise, it calls System.exit with an
     * appropriate exit code.
     * @param args An array of options and arguments, such as might be supplied on the command line.
     */
    public static void main(String[] args) {
        Tool.main(args);
    } // main()

    /**
     * Exception to report a problem while running the test harness.
     */
    public static class Fault extends Exception {
        static final long serialVersionUID = -6780999176737139046L;
        public Fault(I18NResourceBundle i18n, String s, Object... args) {
            super(i18n.getString(s, args));
        }
    }

    /** Execution OK. */
    public static final int EXIT_OK = 0;
    /** No tests found. */
    public static final int EXIT_NO_TESTS = 1;
    /** One or more tests failed. */
    public static final int EXIT_TEST_FAILED = 2;
    /** One or more tests had an error. */
    public static final int EXIT_TEST_ERROR = 3;
    /** Bad user args. */
    public static final int EXIT_BAD_ARGS = 4;
    /** Other fault occurred. */
    public static final int EXIT_FAULT = 5;
    /** Unexpected exception occurred. */
    public static final int EXIT_EXCEPTION = 6;


    public Main() {
        this(new PrintWriter(System.out, true), new PrintWriter(System.err, true));
    }

    public Main(PrintWriter out, PrintWriter err) {
        tool = new Tool(out, err);
    }

    /**
     * Decode command line args and perform the requested operations.
     * @param args An array of args, such as might be supplied on the command line.
     * @throws BadArgs if problems are found with any of the supplied args
     * @throws Main.Fault if a serious error occurred during execution
     * @throws Harness.Fault if exception problems are found while trying to run the tests
     * @throws InterruptedException if the harness is interrupted while running the tests
     * @return an exit code: 0 for success, greater than 0 for an error
     */
    public int run(String[] args) throws
            BadArgs, Fault, Harness.Fault, InterruptedException {
        return tool.run(args);
    }

    private Tool tool;

    //---------- Ant Invocation ------------------------------------------------

    /**
     * Ant task to invoke jtreg.
     */
    public static class Ant extends MatchingTask {
        private final Tool tool = new Tool();
        private File jdk;
        private File dir;
        private File reportDir;
        private File workDir;
        private File nativeDir;
        private boolean wsl;
        private String concurrency;
        private String status;
        private String vmOption;
        private String vmOptions;
        private String javacOption;
        private String javacOptions;
        private String javaOption;
        private String javaOptions;
        private String verbose;
        private boolean agentVM;
        private boolean sameVM;
        private boolean otherVM;
        private Boolean failOnError;  // yes, no, or unset
        private String resultProperty;
        private String failureProperty;
        private String errorProperty;
        private final List<Commandline.Argument> args = new ArrayList<>();

        public void setDir(File dir) {
            this.dir = dir;
        }

        public void setReportDir(File reportDir) {
            this.reportDir = reportDir;
        }

        public void setWorkDir(File workDir) {
            this.workDir = workDir;
        }

        public void setNativeDir(File nativeDir) {
            this.nativeDir = nativeDir;
        }

        public void setWSL(boolean wsl) {
            this.wsl = wsl;
        }

        public void setJDK(File jdk) {
            this.jdk = jdk;
        }

        public void setConcurrency(String concurrency) {
            this.concurrency = concurrency;
        }

        // Should rethink this, and perhaps allow nested vmoption elements.
        // On the other hand, users can give nested <args> for vmoptions too.
        public void setVMOption(String vmOpt) {
            this.vmOption = vmOpt;
        }

        public void setVMOptions(String vmOpts) {
            this.vmOptions = vmOpts;
        }

        // Should rethink this, and perhaps allow nested javacoption elements.
        // On the other hand, users can give nested <args> for vmoptions too.
        public void setJavacOption(String javacOpt) {
            this.javacOption = javacOpt;
        }

        public void setJavacOptions(String javacOpts) {
            this.javacOptions = javacOpts;
        }

        // Should rethink this, and perhaps allow nested javaoption elements.
        // On the other hand, users can give nested <args> for vmoptions too.
        public void setJavaOption(String javaOpt) {
            this.javaOption = javaOpt;
        }

        public void setJavaOptions(String javaOpts) {
            this.javaOptions = javaOpts;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public void setVerbose(String verbose) {
            this.verbose = verbose;
        }

        public void setAgentVM(boolean yes) {
            this.agentVM = yes;
        }

        public void setSameVM(boolean yes) {
            this.sameVM = yes;
        }

        public void setOtherVM(boolean yes) {
            this.otherVM = yes;
        }

        public void setResultProperty(String name) {
            this.resultProperty = name;
        }

        public void setFailureProperty(String name) {
            this.failureProperty = name;
        }

        public void setErrorProperty(String name) {
            this.errorProperty = name;
        }

        public void setFailOnError(boolean yes) {
            this.failOnError = yes;
        }

        public void addArg(Commandline.Argument arg) {
            args.add(arg);
        }

        @Override
        public void execute() {
            Project p = getProject();

            // import javatest.* properties as system properties
            Map<?, ?> properties = p.getProperties();
            for (Map.Entry<?, ?> e: properties.entrySet()) {
                String key = (String) e.getKey();
                if (key.startsWith("javatest."))
                    System.setProperty(key, (String) e.getValue());
            }

            try {
                AntOptionDecoder decoder = new AntOptionDecoder(tool.options);
                decoder.process("-concurrency", concurrency);
                decoder.process("-dir", dir);
                decoder.process("-reportDir", reportDir);
                decoder.process("-workDir", workDir);
                decoder.process("-nativeDir", nativeDir);
                decoder.process("-wsl", wsl);
                decoder.process("-jdk", jdk);
                decoder.process("-verbose", verbose);
                decoder.process("-agentVM", agentVM);
                decoder.process("-sameVM", sameVM);
                decoder.process("-otherVM", otherVM);
                decoder.process("-vmoption", vmOption);
                decoder.process("-vmoptions", vmOptions);
                decoder.process("-javaoption", javaOption);
                decoder.process("-javaoptions", javaOptions);
                decoder.process("-javacoption", javacOption);
                decoder.process("-javacoptions", javacOptions);
                decoder.process("-status", status);

                if (args.size() > 0) {
                    List<String> allArgs = new ArrayList<>();
                    for (Commandline.Argument a: args)
                        allArgs.addAll(Arrays.asList(a.getParts()));
                    decoder.decodeArgs(allArgs);
                }

                if (tool.testFileArgs.isEmpty()
                        && tool.testFileIdArgs.isEmpty()
                        && tool.testGroupArgs.isEmpty()
                        && dir != null) {
                    DirectoryScanner s = getDirectoryScanner(dir);
                    addPaths(dir.toPath(), s.getIncludedFiles());
                }

                int rc = tool.run();

                if (resultProperty != null)
                    p.setProperty(resultProperty, String.valueOf(rc));

                if (failureProperty != null && (rc >= EXIT_TEST_FAILED))
                    p.setProperty(failureProperty, i18n.getString("main.testsFailed"));

                if (errorProperty != null && (rc >= EXIT_TEST_ERROR))
                    p.setProperty(errorProperty, i18n.getString("main.testsError"));

                if (failOnError == null)
                    failOnError = (resultProperty == null && failureProperty == null && errorProperty == null);

                if (failOnError && rc != EXIT_OK)
                    throw new BuildException(i18n.getString("main.testsFailed"));

            } catch (BadArgs | Fault | Harness.Fault e) {
                throw new BuildException(e.getMessage(), e);
            } catch (InterruptedException e) {
                throw new BuildException(i18n.getString("main.interrupted"), e);
            }
        }

        private void addPaths(Path dir, String[] paths) {
            if (paths != null) {
                for (String p: paths)
                    tool.antFileArgs.add(dir.resolve(p));
            }
        }
    }

    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(Main.class);
}
