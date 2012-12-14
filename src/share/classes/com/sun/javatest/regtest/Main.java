/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.types.Commandline;

import com.sun.javatest.CompositeFilter;
import com.sun.javatest.Keywords;
import com.sun.javatest.Harness;
import com.sun.javatest.InterviewParameters;
import com.sun.javatest.JavaTestSecurityManager;
import com.sun.javatest.Parameters;
import com.sun.javatest.ProductInfo;
import com.sun.javatest.Status;
import com.sun.javatest.TestEnvironment;
import com.sun.javatest.TestFilter;
import com.sun.javatest.TestResult;
import com.sun.javatest.TestResultTable;
import com.sun.javatest.TestSuite;
import com.sun.javatest.WorkDirectory;
import com.sun.javatest.exec.ExecToolManager;
import com.sun.javatest.httpd.HttpdServer;
import com.sun.javatest.httpd.PageGenerator;
import com.sun.javatest.report.Report;
import com.sun.javatest.tool.Desktop;
import com.sun.javatest.tool.Startup;
import com.sun.javatest.util.BackupPolicy;
import com.sun.javatest.util.I18NResourceBundle;

import static com.sun.javatest.regtest.Option.ArgType.*;

/**
 * JavaTest entry point to be used to access regression extensions.
 */
public class Main {

    /**
     * Exception to report a problem while executing in Main.
     */
    public static class Fault extends Exception {
        static final long serialVersionUID = -6780999176737139046L;
        Fault(I18NResourceBundle i18n, String s, Object... args) {
            super(i18n.getString(s, args));
        }
    }

    public static final String MAIN = "main";           // main set of options
    public static final String SELECT = "select";       // test selection options
    public static final String JDK = "jdk";             // specify JDK to use
    public static final String MODE = "mode";           // sameVM or otherVM
    public static final String VERBOSE = "verbose";     // verbose controls
    public static final String DOC = "doc";             // help or doc info

    Option[] options = {
        new Option(OPT, VERBOSE, "verbose", "v", "verbose") {
            @Override
            public String[] getChoices() {
                String[] values = new String[Verbose.values().length];
                int i = 0;
                for (String s: Verbose.values())
                    values[i++] = s;
                return values;
            }
            public void process(String opt, String arg) throws BadArgs {
                if (arg == null)
                    verbose = Verbose.DEFAULT;
                else {
                    verbose = Verbose.decode(arg);
                    if (verbose == null)
                        throw new BadArgs(i18n, "main.unknownVerbose", arg);
                }
                childArgs.add(opt);
            }
        },

        new Option(NONE, VERBOSE, "verbose", "v1") {
            public void process(String opt, String arg) {
                verbose = Verbose.SUMMARY;
                childArgs.add(opt);
            }
        },

        new Option(NONE, VERBOSE, "verbose", "va") {
            public void process(String opt, String arg) {
                verbose = Verbose.ALL;
                childArgs.add(opt);
            }
        },

        new Option(NONE, VERBOSE, "verbose", "vp") {
            public void process(String opt, String arg) {
                verbose = Verbose.PASS;
                childArgs.add(opt);
            }
        },

        new Option(NONE, VERBOSE, "verbose", "vf") {
            public void process(String opt, String arg) {
                verbose = Verbose.FAIL;
                childArgs.add(opt);
            }
        },

        new Option(NONE, VERBOSE, "verbose", "ve") {
            public void process(String opt, String arg) {
                verbose = Verbose.ERROR;
                childArgs.add(opt);
            }
        },

        new Option(NONE, VERBOSE, "verbose", "vt") {
            public void process(String opt, String arg) {
                verbose = Verbose.TIME;
                childArgs.add(opt);
            }
        },

        new Option(NONE, DOC, "", "t", "tagspec") {
            public void process(String opt, String arg) {
                if (help == null)
                    help = new Help(options);
                help.setTagSpec(true);
            }
        },

        new Option(NONE, DOC, "", "n", "relnote") {
            public void process(String opt, String arg) {
                if (help == null)
                    help = new Help(options);
                help.setReleaseNotes(true);
            }
        },

        new Option(OLD, MAIN, "", "w", "workDir") {
            public void process(String opt, String arg) {

                workDirArg = new File(arg);
                childArgs.add("-w:" + workDirArg.getAbsolutePath());
            }
        },

        new Option(OPT, MAIN, "", "retain") {
            @Override
            public String[] getChoices() {
                return new String[] { "none", "pass", "fail", "error", "all", "file-pattern" };
            }
            public void process(String opt, String arg) throws BadArgs {
                if (arg != null)
                    arg = arg.trim();
                if (arg == null || arg.length() == 0)
                    retainArgs = Collections.singletonList("all");
                else
                    retainArgs = Arrays.asList(arg.split(","));
                if (retainArgs.contains("none") && retainArgs.size() > 1) {
                    throw new BadArgs(i18n, "main.badRetainNone", arg);
                }
                childArgs.add(opt);
            }
        },

        new Option(OLD, MAIN, "", "r", "reportDir") {
            public void process(String opt, String arg) {
                reportDirArg = new File(arg);
                childArgs.add("-r:" + reportDirArg.getAbsolutePath());
            }
        },

        new Option(NONE, MAIN, "ro-nr", "ro", "reportOnly") {
            public void process(String opt, String arg) {
                reportOnlyFlag = true;
            }
        },

        new Option(NONE, MAIN, "ro-nr", "nr", "noreport") {
            public void process(String opt, String arg) {
                noReportFlag = true;
            }
        },

        new Option(STD, MAIN, "", "timeout", "timeoutFactor") {
            public void process(String opt, String arg) {
                timeoutFactorArg = arg;
                childArgs.add(opt);
            }
        },

        new Option(STD, MAIN, "", "dir") {
            public void process(String opt, String arg) {
                baseDirArg = new File(arg);
                childArgs.add("-dir:" + baseDirArg.getAbsolutePath());
            }
        },

        new Option(OPT, MAIN, "", "allowSetSecurityManager") {
            @Override
            public String[] getChoices() {
                return new String[] { "yes", "no", "on", "off", "true", "false" };
            }
            public void process(String opt, String arg) {
                boolean b = (arg == null || Arrays.asList("yes", "on", "true").contains(arg));
                allowSetSecurityManagerFlag = b;
                childArgs.add(opt);
            }
        },

        new Option(STD, SELECT, "", "status") {
            @Override
            public String[] getChoices() {
                return new String[] { "pass", "fail", "notRun", "error" };
            }
            public void process(String opt, String arg) {
                priorStatusValuesArg = arg.toLowerCase();
                childArgs.add(opt);
            }
        },

        new Option(STD, SELECT, "", "exclude", "Xexclude") {
            public void process(String opt, String arg) {
                File f = new File(arg);
                excludeListArgs.add(f);
                childArgs.add("-exclude:" + f.getAbsolutePath());
            }
        },

        new Option(NONE, MAIN, null, "startHttpd") {
            public void process(String opt, String arg) {
                httpdFlag = true;
                childArgs.add(opt);
            }
        },

        new Option(OLD, MAIN, "", "o", "observer") {
            public void process(String opt, String arg) {
                observerClassName = arg;
                childArgs.add(opt);
                childArgs.add(arg);
            }
        },

        new Option(OLD, MAIN, "", "od", "observerDir", "op", "observerPath") {
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                observerPathArg = new ArrayList<File>();
                for (String f: arg.split(File.pathSeparator)) {
                    if (f.length() == 0)
                        continue;
                    observerPathArg.add(new File(f));
                }
                childArgs.add("-op:" + filesToAbsolutePath(pathToFiles(arg)));
            }
        },

        new Option(NONE, MAIN, null, "g", "gui") {
            public void process(String opt, String arg) {
                guiFlag = true;
                childArgs.add(opt);
            }
        },

        new Option(NONE, MAIN, null, "c", "check") {
            public void process(String opt, String arg) {
                checkFlag = true;
            }
        },

        // deprecated
        new Option(NONE, MAIN, "ignore", "noignore") {
            public void process(String opt, String arg) {
                ignoreKind = IgnoreKind.RUN;
                childArgs.add(opt);
            }
        },

        new Option(STD, MAIN, "ignore", "ignore") {
            @Override
            public String[] getChoices() {
                String[] values = new String[IgnoreKind.values().length];
                int i = 0;
                for (IgnoreKind k: IgnoreKind.values())
                    values[i++] = k.toString().toLowerCase();
                return values;
            }
            public void process(String opt, String arg) throws BadArgs {
                for (IgnoreKind k: IgnoreKind.values()) {
                    if (arg.equalsIgnoreCase(k.toString())) {
                        if (k == IgnoreKind.QUIET)
                            keywordsExprArg = combineKeywords(keywordsExprArg, "!ignore");
                        ignoreKind = k;
                        childArgs.add(opt);
                        return;
                    }
                }
                throw new BadArgs(i18n, "main.unknownIgnore", arg);
            }
        },

        new Option(OLD, MAIN, null, "e") {
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                envVarArgs.addAll(Arrays.asList(arg.split(",")));
            }
        },

        new Option(NONE, SELECT, "a-m", "a", "automatic", "automagic") {
            public void process(String opt, String arg) {
                keywordsExprArg = combineKeywords(keywordsExprArg, AUTOMATIC);
                childArgs.add(opt);
            }
        },

        new Option(NONE, SELECT, "a-m", "m", "manual") {
            public void process(String opt, String arg) {
                keywordsExprArg = combineKeywords(keywordsExprArg, MANUAL);
                childArgs.add(opt);
            }
        },

        new Option(NONE, SELECT, "shell-noshell", "shell") {
            public void process(String opt, String arg) {
                keywordsExprArg = combineKeywords(keywordsExprArg, "shell");
                childArgs.add(opt);
            }
        },

        new Option(NONE, SELECT, "shell-noshell", "noshell") {
            public void process(String opt, String arg) {
                keywordsExprArg = combineKeywords(keywordsExprArg, "!shell");
                childArgs.add(opt);
            }
        },

        new Option(STD, SELECT, null, "bug") {
            public void process(String opt, String arg) {
                keywordsExprArg = combineKeywords(keywordsExprArg, "bug" + arg);
                childArgs.add(opt);
            }
        },

        new Option(STD, SELECT, null, "k", "keywords") {
            public void process(String opt, String arg) {
                keywordsExprArg = combineKeywords(keywordsExprArg, "(" + arg + ")");
                childArgs.add(opt);
            }
        },

        new Option(NONE, MODE, "svm-ovm", "ovm", "othervm") {
            public void process(String opt, String arg) {
                execMode = ExecMode.OTHERVM;
                childArgs.add(opt);
            }
        },

        new Option(NONE, MODE, "svm-ovm", "s", "svm", "samevm") {
            public void process(String opt, String arg) {
                // allow backdoor way to treat samevm optioms as meaning agentvm
                if (isTrue(System.getenv("JTREG_USE_AGENTVM_FOR_SAMEVM")))
                    execMode = ExecMode.AGENTVM;
                else
                    execMode = ExecMode.SAMEVM;
                childArgs.add(opt);
            }
        },

        new Option(NONE, MODE, "svm-ovm", "avm", "agentvm") {
            public void process(String opt, String arg) {
                execMode = ExecMode.AGENTVM;
                childArgs.add(opt);
            }
        },

        new Option(OLD, JDK, "", "jdk", "testjdk") {
            public void process(String opt, String arg) {
                testJDK = new JDK(arg);
            }
        },

        new Option(OLD, JDK, "", "compilejdk") {
            public void process(String opt, String arg) {
                compileJDK = new JDK(arg);
            }
        },

        new Option(STD, JDK, "", "cpa", "classpathappend") {
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                for (String f: arg.split(File.pathSeparator)) {
                    if (f.length() == 0)
                        continue;
                    classPathAppendArg.add(new File(f));
                }
                // needed in child for @compile
                childArgs.add("-cpa:" + filesToAbsolutePath(pathToFiles(arg)));
            }
        },

        new Option(NONE, JDK, "jit-nojit", "jit") {
            public void process(String opt, String arg) {
                jitFlag = true;
            }
        },

        new Option(NONE, JDK, "jit-nojit", "nojit") {
            public void process(String opt, String arg) {
                jitFlag = false;
            }
        },

        new Option(WILDCARD, JDK, null, "Xrunjcov") {
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(NONE, JDK, null, "classic", "green", "native", "hotspot", "client", "server", "d32", "d64") {
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(OPT, JDK, null, "enableassertions", "ea", "disableassertions", "da") {
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(NONE, JDK, null, "enablesystemassertions", "esa", "disablesystemassertions", "dsa") {
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(WILDCARD, JDK, null, "XX", "Xms", "Xmx") {
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(WILDCARD, JDK, null, "Xint", "Xmixed", "Xcomp") {
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(STD, JDK, null, "Xbootclasspath") {
            public void process(String opt, String arg) {
                testVMOpts.add("-Xbootclasspath:" + filesToAbsolutePath(pathToFiles(arg)));
            }
        },

        new Option(STD, JDK, null, "Xbootclasspath/a") {
            public void process(String opt, String arg) {
                testVMOpts.add("-Xbootclasspath/a:" + filesToAbsolutePath(pathToFiles(arg)));
            }
        },

        new Option(STD, JDK, null, "Xbootclasspath/p") {
            public void process(String opt, String arg) {
                testVMOpts.add("-Xbootclasspath/p:" + filesToAbsolutePath(pathToFiles(arg)));
            }
        },

        new Option(WILDCARD, JDK, null, "X") {
            public void process(String opt, String arg) {
                // This is a change in spec. Previously. -X was used to tunnel
                // options to jtreg, with the only supported value being -Xexclude.
                // Now, -exclude is supported, and so we treat -X* as a VM option.
                testVMOpts.add(opt);
            }
        },

        new Option(WILDCARD, JDK, null, "D") {
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(STD, JDK, null, "vmoption") {
            public void process(String opt, String arg) {
                if (arg.length() > 0)
                    testVMOpts.add(arg);
            }
        },

        new Option(STD, JDK, null, "vmoptions") {
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                testVMOpts.addAll(Arrays.asList(arg.split("\\s+")));
            }
        },

        new Option(STD, JDK, null, "agentlib") {
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(STD, JDK, null, "agentpath") {
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(STD, JDK, null, "javaagent") {
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(STD, JDK, null, "javacoption") {
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                testCompilerOpts.add(arg);
                childArgs.add(opt);
            }
        },

        new Option(STD, JDK, null, "javacoptions") {
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                testCompilerOpts.addAll(Arrays.asList(arg.split("\\s+")));
                childArgs.add(opt);
            }
        },

        new Option(STD, JDK, null, "javaoption") {
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                testJavaOpts.add(arg);
                childArgs.add(opt);
            }
        },

        new Option(STD, JDK, null, "javaoptions") {
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                testJavaOpts.addAll(Arrays.asList(arg.split("\\s+")));
                childArgs.add(opt);
            }
        },

        new Option(REST, DOC, "help", "h", "help", "usage") {
            public void process(String opt, String arg) {
                if (help == null)
                    help = new Help(options);
                help.setCommandLineHelpQuery(arg);
            }
        },

        new Option(REST, DOC, "help", "onlineHelp") {
            public void process(String opt, String arg) {
                if (help == null)
                    help = new Help(options);
                help.setOnlineHelpQuery(arg);
            }
        },

        new Option(NONE, DOC, "help", "version") {
            public void process(String opt, String arg) {
                if (help == null)
                    help = new Help(options);
                help.setVersionFlag(true);
            }
        },

        new Option(FILE, MAIN, null) {
            public void process(String opt, String arg) {
                File f= new File(arg);
                testFileArgs.add(f);
                childArgs.add(arg);
            }
        }
    };

    //---------- Ant Invocation ------------------------------------------------

    public static class Ant extends MatchingTask {
        private Main m = new Main();
        private File jdk;
        private File dir;
        private File reportDir;
        private File workDir;
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
        private List<Commandline.Argument> args = new ArrayList<Commandline.Argument>();

        public void setDir(File dir) {
            this.dir = dir;
        }

        public void setReportDir(File reportDir) {
            this.reportDir = reportDir;
        }

        public void setWorkDir(File workDir) {
            this.workDir = workDir;
        }

        public void setJDK(File jdk) {
            this.jdk = jdk;
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
            java.util.Hashtable<?,?> properties = p.getProperties();
            for (Map.Entry<?,?> e: properties.entrySet()) {
                String key = (String) e.getKey();
                if (key.startsWith("javatest."))
                    System.setProperty(key, (String) e.getValue());
            }

            try {
                AntOptionDecoder decoder = new AntOptionDecoder(m.options);
                decoder.process("dir", dir);
                decoder.process("reportDir", reportDir);
                decoder.process("workDir", workDir);
                decoder.process("jdk", jdk);
                decoder.process("verbose", verbose);
                decoder.process("agentVM", agentVM);
                decoder.process("sameVM", sameVM);
                decoder.process("otherVM", otherVM);
                decoder.process("vmoption", vmOption);
                decoder.process("vmoptions", vmOptions);
                decoder.process("javaoption", javaOption);
                decoder.process("javaoptions", javaOptions);
                decoder.process("javacoption", javacOption);
                decoder.process("javacoptions", javacOptions);
                decoder.process("status", status);

                if (args.size() > 0) {
                    List<String> allArgs = new ArrayList<String>();
                    for (Commandline.Argument a: args)
                        allArgs.addAll(Arrays.asList(a.getParts()));
                    decoder.decodeArgs(allArgs);
                }

                if (m.testFileArgs.size() == 0 && dir != null) {
                    DirectoryScanner s = getDirectoryScanner(dir);
                    addPaths(dir, s.getIncludedFiles());
                }

                int rc = m.run();

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

            } catch (BadArgs e) {
                throw new BuildException(e.getMessage(), e);
            } catch (Fault e) {
                throw new BuildException(e.getMessage(), e);
            } catch (Harness.Fault e) {
                throw new BuildException(e.getMessage(), e);
            } catch (InterruptedException e) {
                throw new BuildException(i18n.getString("main.interrupted"), e);
            }
        }

        private void addPaths(File dir, String[] paths) {
            if (paths != null) {
                for (String p: paths)
                    m.antFileArgs.add(new File(dir, p));
            }
        }
    }

    //---------- Command line invocation support -------------------------------

    /** Execution OK. */
    public static final int EXIT_OK = 0;
    /** One of more tests failed. */
    public static final int EXIT_TEST_FAILED = 1;
    /** One or more tests had an error. */
    public static final int EXIT_TEST_ERROR = 2;
    /** Bad user args. */
    public static final int EXIT_BAD_ARGS = 3;
    /** Other fault occurred. */
    public static final int EXIT_FAULT = 4;
    /** Unexpected exception occurred. */
    public static final int EXIT_EXCEPTION = 5;

    /**
     * Standard entry point. Only returns if GUI mode is initiated; otherwise, it calls System.exit
     * with an appropriate exit code.
     * @param args An array of args, such as might be supplied on the command line.
     */
    public static void main(String[] args) {
        PrintWriter out = new PrintWriter(System.out, true);
        PrintWriter err = new PrintWriter(System.err, true);
        Main m = new Main(out, err);
        try {
            int rc;
            try {
                rc = m.run(args);
            } finally {
                out.flush();
                err.flush();
            }

            if (!(m.guiFlag && rc == EXIT_OK)) {
                // take care not to exit if GUI might be around,
                // and take care to ensure JavaTestSecurityManager will
                // permit the exit
                exit(rc);
            }
        } catch (Harness.Fault e) {
            err.println(i18n.getString("main.error", e.getMessage()));
            exit(EXIT_FAULT);
        } catch (Fault e) {
            err.println(i18n.getString("main.error", e.getMessage()));
            exit(EXIT_FAULT);
        } catch (BadArgs e) {
            err.println(i18n.getString("main.badArgs", e.getMessage()));
            new Help(m.options).showCommandLineHelp(out);
            exit(EXIT_BAD_ARGS);
        } catch (InterruptedException e) {
            err.println(i18n.getString("main.interrupted"));
            exit(EXIT_EXCEPTION);
        } catch (SecurityException e) {
            err.println(i18n.getString("main.securityException", e.getMessage()));
            e.printStackTrace();
            exit(EXIT_EXCEPTION);
        } catch (Exception e) {
            err.println(i18n.getString("main.unexpectedException", e.toString()));
            e.printStackTrace();
            exit(EXIT_EXCEPTION);
        }
    } // main()

    public Main() {
        this(new PrintWriter(System.out, true), new PrintWriter(System.err, true));
    }

    public Main(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;

        // FIXME: work around bug 6466752
        File jt_jar = findJar("javatest.jar", "lib/javatest.jar", com.sun.javatest.Harness.class);
        if (jt_jar != null)
            System.setProperty("javatestClassDir", jt_jar.getPath());
    }

    /**
     * Decode command line args and perform the requested operations.
     * @param args An array of args, such as might be supplied on the command line.
     * @throws BadArgs if problems are found with any of the supplied args
     * @throws Harness.Fault if exception problems are found while trying to run the tests
     * @throws InterruptedException if the harness is interrupted while running the tests
     */
    public final int run(String[] args) throws
            BadArgs, Fault, Harness.Fault, InterruptedException {
        new OptionDecoder(options).decodeArgs(expandAtFiles(args));
        return run();
    }

    private int run() throws BadArgs, Fault, Harness.Fault, InterruptedException {
        if (help != null) {
            guiFlag = help.show(out);
            return EXIT_OK;
        }

        if (execMode == ExecMode.SAMEVM && !testJavaOpts.isEmpty())
            throw new Fault(i18n, "main.cant.mix.samevm.java.options");

        if (execMode == ExecMode.SAMEVM && compileJDK != null)
            throw new Fault(i18n, "main.cant.mix.samevm.compile.jdk.options");

        if (testJDK == null) {
            String s = null;
            if (execMode != ExecMode.SAMEVM)
                s = System.getenv("JAVA_HOME");
            if (s == null || s.length() == 0) {
                s = System.getProperty("java.home");
                if (s == null || s.length() == 0)
                    throw new BadArgs(i18n, "main.jdk.not.set");
            }
            File f = new File(s);
            if (compileJDK == null
                    && f.getName().toLowerCase().equals("jre")
                    && f.getParentFile() != null)
                f = f.getParentFile();
            testJDK = new JDK(f);
        }

        if (jitFlag == false) {
            if (execMode == ExecMode.SAMEVM)
                testVMOpts.add("-Djava.compiler=");
            else
                envVarArgs.add("JAVA_COMPILER=");
        }

        if (classPathAppendArg.size() > 0) {
            // TODO: store this separately in RegressionParameters, instead of in envVars
            // Even in sameVM mode, this needs to be stored stored in CPAPPEND for use
            // by script.testClassPath for @compile -- however, note that in sameVM mode
            // this means this value will be on JVM classpath and in URLClassLoader args
            // (minor nit)
            envVarArgs.add("CPAPPEND=" + filesToAbsolutePath(classPathAppendArg));
        }

        if (compileJDK != null && !compileJDK.exists())
            throw new Fault(i18n, "main.compile.jdk.not.found", compileJDK);

        if (!testJDK.exists())
            throw new Fault(i18n, "main.test.jdk.not.found", testJDK);

        File baseDir;
        if (baseDirArg == null) {
            baseDir = new File(System.getProperty("user.dir"));
        } else {
            if (!baseDirArg.exists())
                throw new Fault(i18n, "main.cantFindFile", baseDirArg);
            baseDir = baseDirArg.getAbsoluteFile();
        }

        List<File> absTestFileArgs = new ArrayList<File>();

        for (File t: testFileArgs) {
            if (!t.isAbsolute())
                t = new File(baseDir, t.getPath());
            if (!t.exists())
                throw new Fault(i18n, "main.cantFindFile", t);
            absTestFileArgs.add(t);
        }

        testFileArgs = absTestFileArgs;

        String antFileList = System.getProperty(JAVATEST_ANT_FILE_LIST);
        if (antFileList != null)
            antFileArgs.addAll(readFileList(new File(antFileList)));

        if (testSuiteArg == null) {
            File t;
            if (testFileArgs.size() > 0)
                t = testFileArgs.iterator().next();
            else if (antFileArgs.size() > 0)
                t = antFileArgs.iterator().next();
            else
                throw new BadArgs(i18n, "main.noTestSuiteOrTests");

            testSuiteArg = getTestSuite(t);
            if (testSuiteArg == null)
                throw new Fault(i18n, "main.cantDetermineTestSuite", t);
        }

        if (workDirArg == null) {
            workDirArg = new File("JTwork");
            childArgs.add(0, "-w:" + workDirArg.getAbsolutePath());
        }

        if (reportDirArg == null && !noReportFlag) {
            reportDirArg = new File("JTreport");
            childArgs.add(0, "-r:" + reportDirArg.getAbsolutePath());
        }

        if (!noReportFlag)
            makeDir(reportDirArg);

        makeDir(workDirArg);
        makeDir(new File(workDirArg, "scratch"));

        findSystemJarFiles();

        if (allowSetSecurityManagerFlag) {
            switch (execMode) {
                case AGENTVM:
                    initPolicyFile();
                    Agent.Pool.instance().setSecurityPolicy(policyFile);
                    break;
                case SAMEVM:
                    initPolicyFile();
                case OTHERVM:
            }
        }

        if (!isThisVMOK())
            return execChild();

        RegressionParameters params = createParameters();

        checkLockFiles(params.getWorkDirectory().getRoot(), "start");

        Harness.setClassDir(ProductInfo.getJavaTestClassDir());

        // Allow keywords to begin with a numeric
        Keywords.setAllowNumericKeywords(true);

        // Before we install our own security manager (which will restrict access
        // to the system properties), take a copy of the system properties.
        TestEnvironment.addDefaultPropTable("(system properties)", System.getProperties());

        // TODO: take SecurityManager into account for isThisVMOK
        if (execMode == ExecMode.SAMEVM) {
            RegressionSecurityManager.install();
            SecurityManager sm = System.getSecurityManager();
            if (sm instanceof RegressionSecurityManager) {
                RegressionSecurityManager rsm = (RegressionSecurityManager) sm;
                rsm.setAllowPropertiesAccess(true);
                if (allowSetSecurityManagerFlag)
                    rsm.setAllowSetSecurityManager(true);
                // experimental
                rsm.setAllowSetIO(true);
            }
        }

        if (httpdFlag)
            startHttpServer();

        if (guiFlag) {
            showTool(params);
            return EXIT_OK;
        } else {
            try {
                return batchHarness(params);
            } finally {
                checkLockFiles(params.getWorkDirectory().getRoot(), "done");
            }
        }
    }

    /**
     * Process Win32-style command files for the specified command line
     * arguments and return the resulting arguments. A command file argument
     * is of the form '@file' where 'file' is the name of the file whose
     * contents are to be parsed for additional arguments. The contents of
     * the command file are parsed using StreamTokenizer and the original
     * '@file' argument replaced with the resulting tokens. Recursive command
     * files are not supported. The '@' character itself can be quoted with
     * the sequence '@@'.
     */
    private static String[] expandAtFiles(String[] args)
    throws Fault {
        List<String> newArgs = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.length() > 1 && arg.charAt(0) == '@') {
                arg = arg.substring(1);
                if (arg.charAt(0) == '@') {
                    newArgs.add(arg);
                } else {
                    loadCmdFile(arg, newArgs);
                }
            } else {
                newArgs.add(arg);
            }
        }
        return newArgs.toArray(new String[newArgs.size()]);
    }

    private static void loadCmdFile(String name, List<String> args)
    throws Fault {
        Reader r;
        try {
            r = new BufferedReader(new FileReader(name));
        } catch (FileNotFoundException e) {
            throw new Fault(i18n, "main.cantFindFile", name);
        } catch (IOException e) {
            throw new Fault(i18n, "main.cantOpenFile", name, e);
        }
        try {
            StreamTokenizer st = new StreamTokenizer(r);
            st.resetSyntax();
            st.wordChars(' ', 255);
            st.whitespaceChars(0, ' ');
            st.commentChar('#');
            st.quoteChar('"');
            st.quoteChar('\'');
            while (st.nextToken() != StreamTokenizer.TT_EOF) {
                args.add(st.sval);
            }
        } catch (IOException e) {
            throw new Fault(i18n, "main.cantRead", name, e);
        } finally {
            try {
                r.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private static List<File> readFileList(File file)
    throws Fault {
        BufferedReader r;
        try {
            r = new BufferedReader(new FileReader(file));
        } catch (FileNotFoundException e) {
            throw new Fault(i18n, "main.cantFindFile", file);
        } catch (IOException e) {
            throw new Fault(i18n, "main.cantOpenFile", file, e);
        }
        try {
            List<File> list = new ArrayList<File>();
            String line;
            while ((line = r.readLine()) != null)
                list.add(new File(line));
            return list;
        } catch (IOException e) {
            throw new Fault(i18n, "main.cantRead", file, e);
        } finally {
            try {
                r.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private static void writeFileList(File file, List<File> list) throws Fault {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(file));
            for (File f: list) {
                String p = f.getPath();
                out.write(p);
                out.newLine();
            }
            out.close();
        } catch (IOException e) {
            throw new Fault(i18n, "main.cantWrite", file, e);
        }
    }

    public int[] getTestStats() {
        return testStats;
    }

    private boolean isThisVMOK() {
        if (reportOnlyFlag || checkFlag || execMode != ExecMode.SAMEVM)
            return true;

        // sameVM tests can use this VM if
        // - the current directory is the required scratch directory
        // - the current VM is the required test VM
        // - there are no outstanding VM options
        // - there is no classpath append
        // - don't allow setSecurityManager

        File scratchDir = canon(new File(workDirArg, "scratch"));
        File currDir = canon(new File(""));
        if (!currDir.equals(scratchDir)) {
            if (debugChild)
                System.err.println("dir mismatch: " + currDir + " " + scratchDir);
            return false;
        }

        File currJDKHome = canon(new File(System.getProperty("java.home")));
        if (currJDKHome.getName().toLowerCase().equals("jre"))
            currJDKHome = currJDKHome.getParentFile();
        if (!currJDKHome.equals(testJDK.getCanonicalFile())) {
            if (debugChild)
                System.err.println("jdk mismatch: " + currJDKHome + " " + testJDK + " (" + testJDK.getCanonicalFile() + ")");
            return false;
        }

        boolean isChild = (System.getProperty("javatest.child") != null);

        if (!isChild && !testVMOpts.isEmpty()) {
            if (debugChild)
                System.err.println("need VM opts: " + testVMOpts);
            return false;
        }

        if (classPathAppendArg.size() > 0) {
            if (debugChild)
                System.err.println("need classPathAppend: " + classPathAppendArg);
            Path jcp = new Path(System.getProperty("java.class.path"));
            Path cpa = filesToAbsolutePath(classPathAppendArg);
            if (!(jcp.contains(cpa)))
                return false;
        }

        if (!isChild && allowSetSecurityManagerFlag) {
            if (debugChild)
                System.err.println("need policy file for setSecurityManager");
            return false;
        }

        return true;
    }

    // TODO use @file for args?
    private int execChild() throws Fault {
        if (System.getProperty("javatest.child") != null)
            throw new AssertionError();

        File childJDKHome = testJDK.getAbsoluteFile();
        File childJava = new File(new File(childJDKHome, "bin"), "java");
        File childTools  = new File(new File(childJDKHome, "lib"), "tools.jar");
        File scratchDir = canon(new File(workDirArg, "scratch"));

        List<String> c = new ArrayList<String>();
        c.add(childJava.getPath());

        c.add("-classpath");
        List<File> classpath = new ArrayList<File>();
        classpath.add(jtreg_jar);
        if (childTools.exists())
            classpath.add(childTools);
        classpath.addAll(classPathAppendArg);
        c.add(filesToAbsolutePath(classpath).toString());

        c.addAll(testVMOpts);

        if (allowSetSecurityManagerFlag)
            c.add("-Djava.security.policy=" + policyFile.toURI());

        // Tunnel Ant file args separately from command line tests, so that
        // they can be treated specially in the child VM:  invalid files
        // specified by the user on the command line give an error;
        // invalid files given as part of an Ant fileset are ignored.
        if (antFileArgs.size() > 0) {
            try {
                File file = File.createTempFile("jtreg.", ".tmp", scratchDir);
                writeFileList(file, antFileArgs);
                c.add("-D" + JAVATEST_ANT_FILE_LIST + "=" + file);
            } catch (IOException e) {
                throw new Fault(i18n, "main.cantWriteTempFile", e);
            }
        }

        for (Map.Entry<?,?> e: System.getProperties().entrySet()) {
            String name = (String) e.getKey();
            if (name.startsWith("javatest."))
                c.add("-D" + name + "=" + e.getValue());
        }

        c.add("-Djavatest.child=true");

        if (junit_jar != null)
            c.add("-Djunit.jar=" + junit_jar.getPath());

        c.add(Main.class.getName());

        for (String o: testVMOpts)
            c.add("-vmoption:" + o);

        if (baseDirArg == null)
            c.add("-dir:" + System.getProperty("user.dir"));

        c.addAll(childArgs);

        String[] cmd = c.toArray(new String[c.size()]);
        File execDir = scratchDir;

        if (debugChild) {
            System.err.println("Starting JavaTest child");
            System.err.println("Dir " + execDir + "; Command " + c);
        }

        Runtime r = Runtime.getRuntime();
        Process p = null;

        try {
            try {
                // strictly speaking, we do not need to set the CLASSPATH for the child VM,
                // but we do it to maximize the consistency between sameVM and otherVM env.
                // See similar code in MainAction for otherVM tests.
                // Note the CLASSPATH will not be exactly the same as for otherVM tests,
                // because it will not have (and cannot have) the test-specific values.
                Path cp = new Path().append(javatest_jar, jtreg_jar, testJDK.getToolsJar());

                String[] env = getEnvVars();
                String[] env_cp = new String[env.length + 1];
                System.arraycopy(env, 0, env_cp, 0, env.length);
                env_cp[env_cp.length - 1] = ("CLASSPATH=" + cp);

                p = r.exec(cmd, env_cp, execDir);
            } catch (IOException e) {
                err.println("cannot start child VM");
                return EXIT_FAULT;
            }

            InputStream childOut = p.getInputStream(); // output stream from process
            StreamCopier childOutCopier = new StreamCopier(childOut, out);
            childOutCopier.start();
            InputStream childErr = p.getErrorStream();
            StreamCopier childErrCopier = new StreamCopier(childErr, err);
            childErrCopier.start();

            OutputStream childIn = p.getOutputStream();  // input stream to process
            if (childIn != null)
                childIn.close();

            // wait for the stream copiers to complete
            childOutCopier.waitUntilDone();
            childErrCopier.waitUntilDone();

            // wait for the process to complete;
            int exitCode = p.waitFor();
            p = null;

            if (debugChild) {
                System.err.println("JavaTest child process: rc=" + exitCode);
            }

            childOut.close();
            childErr.close();

            return exitCode;

        } catch (IOException e) {
            // TODO handle exception
            return EXIT_EXCEPTION;
        } catch (InterruptedException e) {
            // TODO handle exception
            return EXIT_EXCEPTION;
        } finally {
            if (p != null)
                p.destroy();
        }
    }

    void findSystemJarFiles() throws Fault {
        javatest_jar = findJar("javatest.jar", "lib/javatest.jar", com.sun.javatest.Harness.class);
        if (javatest_jar == null)
            throw new Fault(i18n, "main.cantFind.javatest.jar");

        jtreg_jar = findJar("jtreg.jar", "lib/jtreg.jar", getClass());
        if (jtreg_jar == null)
            throw new Fault(i18n, "main.cantFind.jtreg.jar");

        String s = System.getProperty("junit.jar");
        if (s != null)
            junit_jar = new File(s);
        else {
            try {
                junit_jar = findJar("junit.jar", "lib/junit.jar", org.junit.runner.JUnitCore.class);
            } catch (NoClassDefFoundError ex) {
            }
        }
        if (junit_jar == null) {
            // Leave a place-holder for the optional jar.
            junit_jar = new File(jtreg_jar.getParentFile(), "junit.jar");
        }
    }

    void initPolicyFile() throws Fault {
        // Write a policy file into the work directory granting all permissions
        // to jtreg.
        // Note: don't use scratch directory, which is cleared before tests run
        File pfile = new File(workDirArg, "jtreg.policy");
        try {
            BufferedWriter pout = new BufferedWriter(new FileWriter(pfile));
            try {
                String LINESEP = System.getProperty("line.separator");
                for (File f: new File[] { jtreg_jar, javatest_jar }) {
                    pout.write("grant codebase \"" + f.toURI().toURL() + "\" {" + LINESEP);
                    pout.write("    permission java.security.AllPermission;" + LINESEP);
                    pout.write("};" + LINESEP);
                }
            } finally {
                pout.close();
            }
            policyFile = pfile;
        } catch (IOException e) {
            throw new Fault(i18n, "main.cantWritePolicyFile", e);
        }
    }

    /**
     * A thread to copy an input stream to an output stream
     */
    static class StreamCopier extends Thread {
        /**
         * Create one.
         * @param from  the stream to copy from
         * @param to    the stream to copy to
         */
        StreamCopier(InputStream from, PrintWriter to) {
            super(Thread.currentThread().getName() + "_StreamCopier_" + (serial++));
            in = new BufferedReader(new InputStreamReader(from));
            out = to;
        }

        /**
         * Set the thread going.
         */
        @SuppressWarnings("deprecation")
        @Override
        public void run() {
            //System.out.println("Copying stream");
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    out.println(line);
                }
            } catch (IOException e) {
            }
//          //System.out.println("Stream copied");
            synchronized (this) {
                done = true;
                notifyAll();
            }
        }

        public synchronized boolean isDone() {
            return done;
        }

        /**
         * Blocks until the copy is complete, or until the thread is interrupted
         */
        public synchronized void waitUntilDone() throws InterruptedException {
            boolean interrupted = false;

            // poll interrupted flag, while waiting for copy to complete
            while (!(interrupted = Thread.interrupted()) && !done)
                wait(1000);

            if (interrupted)
                throw new InterruptedException();
        }

        private BufferedReader in;
        private PrintWriter out;
        private boolean done;
        private static int serial;

    }

    private File getTestSuite(File test) {
        File f = canon(test);
        if (f.isFile())
            f = f.getParentFile();
        while (f != null) {
            if (new File(f, "TEST.ROOT").exists())
                return f;
            f = f.getParentFile();
        }
        // TODO try and default from work directory
        return null;
    }

    private String getEnvVar(String name) {
        for (String arg: envVarArgs) {
            if (arg.startsWith(name + "="))
                return (StringArray.splitEqual(arg))[1];
        }
        return null;
    }

    private void makeDir(File dir) throws Fault {
        // FIXME: I18N
        if (dir.isDirectory())
            return;
        out.println("Directory \"" + dir + "\" not found: creating");
        dir.mkdirs();
        if (!dir.isDirectory()) {
            throw new Fault(i18n, "main.cantCreateDir", dir);
        }
    }

    private static List<File> pathToFiles(String path) {
        List<File> files = new ArrayList<File>();
        for (String f: path.split(File.pathSeparator)) {
            if (f.length() > 0)
                files.add(new File(f));
        }
        return files;
    }

    private static Path filesToAbsolutePath(List<File> files) {
        Path p = new Path();
        for (File f: files) {
            p.append(f.getAbsolutePath());
        }
        return p;
    }

    /**
     * Create a RegressionParameters object based on the values set up by decodeArgs.
     * @return a RegressionParameters object
     */
    private RegressionParameters createParameters() throws BadArgs, Fault {
        File ts = testSuiteArg;
        File wd = workDirArg;

        try {
            // create a canonTestFile suite and work dir.
            RegressionTestSuite testSuite = new RegressionTestSuite(ts);
            RegressionParameters rp = (RegressionParameters) (testSuite.createInterview());

            WorkDirectory workDir;
            if (WorkDirectory.isWorkDirectory(wd))
                workDir = WorkDirectory.open(wd, testSuite);
            else
                workDir = WorkDirectory.convert(wd, testSuite);
            rp.setWorkDirectory(workDir);

            rp.setRetainArgs(retainArgs);

            // set up the tests mode, and if specified tests are used, pass in the canonTestFile list.
            // ensure the tests parameters are root-relative
            File root = testSuite.getRoot();
            List<String> tests = new ArrayList<String>();

            if (testFileArgs != null) {
                // In the command line, the canonTestFile args are filenames, probably absolute,
                // and possibly with non-canonical file separators (e.g. / on Windows).
                // In the interview, they should be URIs -- relative to root, and using '/'
                for (File testFile: testFileArgs) {
                    File canonTestFile = canon(testFile);
                    if (canonTestFile.equals(root))
                        tests = null; // means all test suite
                    else {
                        String test = getRelativePath(root, canonTestFile);
                        if (test == null) // cant make relative
                            throw new Fault(i18n, "main.testNotInTestSuite", testFile);
                        if (tests != null) // no need to add if all tests already included
                            tests.add(test);
                    }
                }
            }

            // no need to scan ant tests if all test suite selected (i.e. tests == null)
            if (tests != null && antFileArgs != null && antFileArgs.size() > 0) {
                TestResultTable trt = workDir.getTestResultTable();
                for (File antFile: antFileArgs) {
                    File canonAntFile = canon(antFile);
                    if (canonAntFile.equals(root))
                        tests = null; // means all test suite
                    else {
                        String test = getRelativePath(root, canonAntFile);
                        if (test == null) // cant make relative
                            throw new Fault(i18n, "main.testNotInTestSuite", antFile);
                        if (tests != null && trt.validatePath(test)) { // no need to add if all tests already included
                            tests.add(test);
                        }
                    }
                }
            }

            if (tests != null && tests.size() > 0)
                rp.setTests(tests);

            if (keywordsExprArg != null)
                rp.setKeywordsExpr(keywordsExprArg);
            rp.setExcludeLists(excludeListArgs.toArray(new File[excludeListArgs.size()]));
            if (priorStatusValuesArg == null || priorStatusValuesArg.length() == 0)
                rp.setPriorStatusValues((boolean[]) null);
            else {
                boolean[] b = new boolean[Status.NUM_STATES];
                b[Status.PASSED]  = (priorStatusValuesArg.indexOf("pass") != -1);
                b[Status.FAILED]  = (priorStatusValuesArg.indexOf("fail") != -1);
                b[Status.ERROR]   = (priorStatusValuesArg.indexOf("erro") != -1);
                b[Status.NOT_RUN] = (priorStatusValuesArg.indexOf("notr") != -1);
                rp.setPriorStatusValues(b);
            }

            if (concurrencyArg != null) {
                try {
                    rp.setConcurrency(Integer.parseInt(concurrencyArg));
                } catch (NumberFormatException e) {
                    throw new BadArgs(i18n, "main.badConcurrency");
                }
            }

            if (timeoutFactorArg != null) {
                try {
                    rp.setTimeoutFactor(Float.parseFloat(timeoutFactorArg));
                } catch (NumberFormatException e) {
                    throw new BadArgs(i18n, "main.badTimeoutFactor");
                }
            }

            if (!rp.isValid())
                throw new Fault(i18n, "main.badParams", rp.getErrorMessage());

            for (String o: testVMOpts) {
                if (o.startsWith("-Xrunjcov")) {
                    if (!testVMOpts.contains("-XX:+EnableJVMPIInstructionStartEvent"))
                        testVMOpts.add("-XX:+EnableJVMPIInstructionStartEvent");
                    break;
                }
            }

            if (testVMOpts.size() > 0)
                rp.setTestVMOptions(testVMOpts);

            if (testCompilerOpts.size() > 0)
                rp.setTestCompilerOptions(testCompilerOpts);

            if (testJavaOpts.size() > 0)
                rp.setTestJavaOptions(testJavaOpts);

            rp.setCheck(checkFlag);
            rp.setExecMode(execMode);
            rp.setEnvVars(getEnvVars());
            rp.setCompileJDK((compileJDK != null) ? compileJDK : testJDK);
            rp.setTestJDK(testJDK);
            if (ignoreKind != null)
                rp.setIgnoreKind(ignoreKind);

//            String junit_jar = System.getProperty("junit.jar");
            if (junit_jar != null)
                rp.setJUnitJar(junit_jar);

            return rp;
        } catch (TestSuite.Fault f) {
            f.printStackTrace();
            // TODO: fix bad string -- need more helpful resource here
            throw new Fault(i18n, "main.cantOpenTestSuite", ts, f);
        } catch (WorkDirectory.Fault e) {
            throw new Fault(i18n, "main.cantRead", wd.getName(), e);
        } catch (IOException e) {
            throw new Fault(i18n, "main.cantRead", wd.getName(), e);
        }
    }
    // where
    private File canon(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    private String getRelativePath(File base, File f) {
        StringBuilder sb = new StringBuilder();
        for ( ; f != null; f = f.getParentFile()) {
            if (f.equals(base))
                return sb.toString();
            if (sb.length() > 0)
                sb.insert(0, '/');
            sb.insert(0, f.getName());
        }
        return null;
    }

    /**
     * Initialize the harness.  If we are in verbose mode, add our own observer.
     */
    private Harness createHarness() throws Fault {

        // Set backup parameters; in time this might become more versatile.
        BackupPolicy backupPolicy = createBackupPolicy();

        Harness h = new Harness();
        h.setBackupPolicy(backupPolicy);

        if (observerClassName != null) {
            try {
                Class observerClass;
                if (observerPathArg == null)
                    observerClass = Class.forName(observerClassName);
                else {
                    URL[] urls = new URL[observerPathArg.size()];
                    int u = 0;
                    for (File f: observerPathArg) {
                        try {
                            urls[u++] = f.toURI().toURL();
                        } catch (MalformedURLException ignore) {
                        }
                    }
                    ClassLoader loader = new URLClassLoader(urls);
                    observerClass = loader.loadClass(observerClassName);
                }
                Harness.Observer observer = (Harness.Observer)(observerClass.newInstance());
                h.addObserver(observer);
            } catch (ClassCastException e) {
                throw new Fault(i18n, "main.obsvrType",
                        new Object[] {Harness.Observer.class.getName(), observerClassName});
            } catch (ClassNotFoundException e) {
                throw new Fault(i18n, "main.obsvrNotFound", observerClassName);
            } catch (IllegalAccessException e) {
                throw new Fault(i18n, "main.obsvrFault", e);
            } catch (InstantiationException e) {
                throw new Fault(i18n, "main.obsvrFault", e);
            }
        }

        // add our own observer for verbose
        if (verbose != null) {
            Harness.Observer observer = new RegressionObserver(verbose, out, err);
            h.addObserver(observer);
        }

        return h;
    } // createHarness()

    /**
     * Run the harness in batch mode, using the specified parameters.
     */
    private int batchHarness(InterviewParameters params)
            throws Fault, Harness.Fault, InterruptedException {
        try {
            boolean ok;

            if (reportOnlyFlag) {
                testStats = new int[Status.NUM_STATES];
                for (Iterator iter = getResultsIterator(params); iter.hasNext(); ) {
                    TestResult tr = (TestResult) (iter.next());
                    testStats[tr.getStatus().getType()]++;
                }
                ok = (testStats[Status.FAILED] == 0 && testStats[Status.ERROR] ==0);
            } else {
                Harness harness = createHarness();
                harness.addObserver(new BatchObserver());
                ok = harness.batch(params);
                Agent.Pool.instance().close();
                Alarm.finished();
            }

            showResultStats(testStats);

            boolean reportRequired =
                    !noReportFlag && !Boolean.getBoolean("javatest.noReportRequired");
            List<String> reportKinds =
                    Arrays.asList(System.getProperty("javatest.report.kinds", "html text").split("[ ,]+"));
            String backups = System.getProperty("javatest.report.backups"); // default: none
            if (reportRequired) {
                try {
                    if (Thread.interrupted()) {
                        // It is important to ensure the interrupted bit is cleared before writing
                        // a report, because Report.writeReport checks if the interrupted bit is set,
                        // and will stop writing the report. This typically manifests itself as
                        // writing the HTML files but /not/ writing the text/summary.txt file.
                        out.println("WARNING: interrupt status cleared prior to writing report");
                    }

                    Report r = new Report();
                    Report.Settings s = new Report.Settings(params);
                    if (reportKinds.contains("html")) {
                        s.setEnableHtmlReport(true);
                        s.setHtmlMainReport(true, true);
                    }
                    if (reportKinds.contains("text")) {
                        s.setEnablePlainReport(true);
                    }
                    if (reportKinds.contains("xml")) {
                        s.setEnableXmlReport(true);
                    }
                    s.setFilter(new CompositeFilter(params.getFilters()));
                    if (backups == null)
                        s.setEnableBackups(false);
                    else {
                        try {
                            s.setBackupLevels(Integer.parseInt(backups));
                            s.setEnableBackups(true);
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                    r.writeReport(s, reportDirArg);
                    fixupReports(workDirArg, reportDirArg);
                    File report = new File(reportDirArg, "report.html"); // std through version 3.*
                    if (!report.exists())
                        report = new File(new File(reportDirArg, "html"), "report.html"); // version 4.*
                    if (report.exists())
                        out.println("Report written to " + report);
                } catch (IOException e) {
                    out.println("Error while writing report: " + e);
                } catch (SecurityException e) {
                    out.println("Error while writing report: " + e);
                }
            }

            if (!reportOnlyFlag)
                out.println("Results written to " + params.getWorkDirectory().getPath());

            // report a brief msg to System.err as well, in case System.out has
            // been redirected.
            if (!ok)
                err.println(i18n.getString("main.testsFailed"));

            return (testStats[Status.ERROR] > 0 ? EXIT_TEST_ERROR :
                testStats[Status.FAILED] > 0 ? EXIT_TEST_FAILED :
                    EXIT_OK);
        } finally {
            out.flush();
            err.flush();
        }
    }

    private Iterator getResultsIterator(InterviewParameters params) {
        TestResultTable trt = params.getWorkDirectory().getTestResultTable();
        trt.waitUntilReady();

        String[] tests = params.getTests();
        TestFilter[] filters = params.getFilters();
        if (tests == null)
            return trt.getIterator(filters);
        else
            return trt.getIterator(tests, filters);
    }

    private void showTool(final InterviewParameters params) throws BadArgs {
        Startup startup = new Startup();

        try {
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    // build a gui for the tool and run it...
                    Desktop d = new Desktop();
                    ExecToolManager m = (ExecToolManager) (d.getToolManager(ExecToolManager.class));
                    if (m == null)
                        throw new AssertionError("Cannot find ExecToolManager");
                    m.startTool(params);
                    d.setVisible(true);
                }
            });
        } finally {
            startup.disposeLater();
        }
    } // showTool()

    private void showResultStats(int[] stats) {
        int p = stats[Status.PASSED];
        int f = stats[Status.FAILED];
        int e = stats[Status.ERROR];
        int nr = stats[Status.NOT_RUN];

        String msg;
        if (p + f + e + nr == 0)
            msg = i18n.getString("main.noTests");
        else {
            msg = i18n.getString("main.tests",
                    new Object[] {
                new Integer(p),
                new Integer((p > 0) && (f + e + nr > 0) ? 1 : 0),
                new Integer(f),
                new Integer((f > 0) && (e + nr > 0) ? 1 : 0),
                new Integer(e),
                new Integer((e > 0) && (nr > 0) ? 1 : 0),
                new Integer(nr)
            });
        }
        out.println(msg);
    }

    private BackupPolicy createBackupPolicy() {
        return new BackupPolicy() {
            public int getNumBackupsToKeep(File file) {
                return numBackupsToKeep;
            }
            public boolean isBackupRequired(File file) {
                if (ignoreExtns != null) {
                    for (int i = 0; i < ignoreExtns.length; i++) {
                        if (file.getPath().endsWith(ignoreExtns[i]))
                            return false;
                    }
                }
                return true;
            }
            private int numBackupsToKeep = Integer.getInteger("javatest.backup.count", 5).intValue();
            private String[] ignoreExtns = StringArray.split(System.getProperty("javatest.backup.ignore", ".jtr"));
        };
    }

    private void startHttpServer() {
        // start the http server
        // do this as early as possible, since objects may check
        // HttpdServer.isActive() and decide whether or not to
        // register their handlers
        HttpdServer server = new HttpdServer();
        Thread thr = new Thread(server);

        PageGenerator.setSWName(ProductInfo.getName());

        // format the date for i18n
        DateFormat df = DateFormat.getDateInstance(DateFormat.LONG);
        Date dt = ProductInfo.getBuildDate();
        String date;
        if (dt != null)
            date = df.format(dt);
        else
            date = i18n.getString("main.nobDate");

        PageGenerator.setSWBuildDate(date);
        PageGenerator.setSWVersion(ProductInfo.getVersion());

        thr.start();
    }

    private String[] getEnvVars() {

        Map<String,String> envVars = new TreeMap<String,String>();
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("windows")) {
            addEnvVars(envVars, DEFAULT_WINDOWS_ENV_VARS);
            // TODO PATH? MKS? Cygwin?
            addEnvVars(envVars, "PATH"); // accept user's path, for now
        } else {
            addEnvVars(envVars, DEFAULT_UNIX_ENV_VARS);
            addEnvVars(envVars, "PATH=/bin:/usr/bin");
        }
        addEnvVars(envVars, envVarArgs);
        for (Map.Entry<String,String> e: System.getenv().entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k.startsWith("JTREG_")) {
                envVars.put(k, k + "=" + v);
            }
        }

        return envVars.values().toArray(new String[envVars.size()]);
    }

    private void addEnvVars(Map<String,String> table, String list) {
        addEnvVars(table, list.split(","));
    }

    private void addEnvVars(Map<String,String> table, String[] list) {
        addEnvVars(table, Arrays.asList(list));
    }

    private void addEnvVars(Map<String,String> table, List<String> list) {
        if (list == null)
            return;

        for (String s: list) {
            s = s.trim();
            if (s.length() == 0)
                continue;
            int eq = s.indexOf("=");
            if (eq == -1) {
                String value = System.getenv(s);
                if (value != null)
                    table.put(s, s + "=" + value);
            } else if (eq > 0) {
                String name = s.substring(0, eq);
                table.put(name, s);
            }
        }
    }

    private static String combineKeywords(String kw1, String kw2) {
        return (kw1 == null ? kw2 : kw1 + " & " + kw2);
    }

    private File findJar(String jarProp, String pathFromHome, Class<?> c) {
        if (jarProp != null) {
            String v = System.getProperty(jarProp);
            if (v != null)
                return new File(v);
        }

        if (pathFromHome != null) {
            String v = System.getProperty("jtreg.home");
            if (v != null)
                return new File(v, pathFromHome);
        }

        if (c != null)  {
            try {
                String className = c.getName().replace(".", "/") + ".class";
                // use URI to avoid encoding issues, e.g. Progtram%20Files
                URI uri = getClass().getClassLoader().getResource(className).toURI();
                if (uri.getScheme().equals("jar")) {
                    String ssp = uri.getRawSchemeSpecificPart();
                    int sep = ssp.lastIndexOf("!");
                    uri = new URI(ssp.substring(0, sep));
                    if (uri.getScheme().equals("file"))
                        return new File(uri.getPath());
                }
            } catch (URISyntaxException ignore) {
                ignore.printStackTrace();
            }
        }

        return null;
    }

    /* If the work dir and report dir are equal or close to each other in the
     * file system, rewrite HTML files in the report directory, replacing
     * absolute paths for the work directory with relative paths.
     */
    private void fixupReports(File work, File report) {
        File workParent = work.getParentFile();
        String canonWorkPath;
        try {
            canonWorkPath = work.getCanonicalPath();
        } catch (IOException e) {
            canonWorkPath = work.getAbsolutePath();
        }

        File reportParent = report.getParentFile();
        File reportHtmlDir = new File(report, "html");

        if (equal(work, report)) {
            fixupReportFiles(report,        canonWorkPath, ".");
            fixupReportFiles(reportHtmlDir, canonWorkPath, "..");
        } else if (equal(report, workParent)) {
            fixupReportFiles(report,        canonWorkPath, work.getName());
            fixupReportFiles(reportHtmlDir, canonWorkPath, "../" + work.getName());
        } else if (equal(work, reportParent)) {
            fixupReportFiles(report,        canonWorkPath, work.getName());
            fixupReportFiles(reportHtmlDir, canonWorkPath, "../" + work.getName());
        } else if (equal(workParent, reportParent)) {
            fixupReportFiles(report,        canonWorkPath, "../" + work.getName());
            fixupReportFiles(reportHtmlDir, canonWorkPath, "../../" + work.getName());
        }
    }

    /* Rewrite html files in the given directory, replacing hrefs to the old path
     * with references to the new path. */
    private void fixupReportFiles(File dir, String oldPath, String newPath) {
        String dirPath;
        try {
            dirPath = dir.getCanonicalPath();
        } catch (IOException e) {
            dirPath = dir.getAbsolutePath();
        }

        for (File f: dir.listFiles()) {
            if (f.getName().endsWith(".html")) {
                try {
                    write(f, read(f)
                            .replace("href=\"" + oldPath + "/", "href=\"" + newPath + "/")
                            .replace("href=\"" + oldPath + "\"", "href=\"" + newPath + "\"")
                            .replace("href=\"" + dirPath + "\"", "href=\".\""));
                } catch (IOException e) {
                    out.println("Error while updating report: " + e);
                }
            }
        }
    }

    private String read(File f) throws IOException {
        byte[] bytes = new byte[(int) f.length()];
        DataInputStream fIn = new DataInputStream(new FileInputStream(f));
        try {
            fIn.readFully(bytes);
            return new String(bytes);
        } finally {
            fIn.close();
        }
    }

    private void write(File f, String s) throws IOException {
        FileOutputStream fOut = new FileOutputStream(f);
        try {
            fOut.write(s.getBytes());
        } finally {
            fOut.close();
        }
    }

    private static <T> boolean equal(T t1, T t2) {
        return (t1 == null ? t2 == null : t1.equals(t2));
    }

    /**
     * Call System.exit, taking care to get permission from the
     * JavaTestSecurityManager, if it is installed.
     */
    private static final void exit(int exitCode) {
        // If our security manager is installed, it won't allow a call of
        // System.exit unless we ask it nicely, pretty please, thank you.
        SecurityManager sc = System.getSecurityManager();
        if (sc instanceof JavaTestSecurityManager)
            ((JavaTestSecurityManager) sc).setAllowExit(true);
        System.exit(exitCode);
    }

    private static boolean isTrue(String s) {
        return (s != null) &&
                (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes"));
    }

    // This is almost completely dead code; testStats appears unused
    // so the only use here is error handling, which perhaps can be
    // folded into RegressionObserver
    private class BatchObserver implements Harness.Observer {
        public void startingTestRun(Parameters params) {
            testStats = new int[Status.NUM_STATES];
        }

        public void startingTest(TestResult tr) { }

        public void finishedTest(TestResult tr) {
            testStats[tr.getStatus().getType()]++;
        }

        public void stoppingTestRun() { }

        public void finishedTesting() { }

        public void finishedTestRun(boolean allOK) { }

        public void error(String msg) {
            err.println(i18n.getString("main.error", msg));
        }
    }

    private void checkLockFiles(File workDir, String msg) {
//      String jc = System.getProperty("javatest.child");
//      File jtData = new File(workDir, "jtData");
//      String[] children = jtData.list();
//      if (children != null) {
//          for (String c: children) {
//              if (c.endsWith(".lck"))
//                  err.println("Warning: " + msg + (jc == null ? "" : ": [" + jc + "]") + ": found lock file: " + c);
//          }
//      }
    }

    //----------member variables-----------------------------------------------

    private PrintWriter out;
    private PrintWriter err;

    // this first group of args are the "standard" JavaTest args
    private File testSuiteArg;
    private File workDirArg;
    private List<String> retainArgs;
    private List<File> excludeListArgs = new ArrayList<File>();
    private String keywordsExprArg;
    private String concurrencyArg; // not currently exposed in any way
    private String timeoutFactorArg;
    private String priorStatusValuesArg;
    private File reportDirArg;
    private List<File> testFileArgs = new ArrayList<File>();
    // TODO: consider making this a "pathset" to detect redundant specification
    // of directories and paths within them.
    private List<File> antFileArgs = new ArrayList<File>();

    // these args are jtreg extras
    private File baseDirArg;
    private ExecMode execMode = ExecMode.OTHERVM;
    private JDK compileJDK;
    private JDK testJDK;
    private boolean guiFlag;
    private boolean reportOnlyFlag;
    private boolean noReportFlag;
    private boolean allowSetSecurityManagerFlag = true;
    private static Verbose  verbose;
    private boolean httpdFlag;
    private String observerClassName;
    private List<File> observerPathArg;
    private List<String> testCompilerOpts = new ArrayList<String>();
    private List<String> testJavaOpts = new ArrayList<String>();
    private List<String> testVMOpts = new ArrayList<String>();
    private boolean checkFlag;
    private List<String> envVarArgs = new ArrayList<String>();
    private IgnoreKind ignoreKind;
    private List<File> classPathAppendArg = new ArrayList<File>();
    private boolean jitFlag = true;
    private Help help;

    File javatest_jar;
    File jtreg_jar;
    File junit_jar;
    File policyFile;

    // the list of args to be passed down to a  child VM
    private List<String> childArgs = new ArrayList<String>();

    private int[] testStats;

    private static final String AUTOMATIC = "!manual";
    private static final String MANUAL    = "manual";

    private static final String[] DEFAULT_UNIX_ENV_VARS = {
        "DISPLAY", "HOME", "LANG", "LC_ALL", "LC_TYPE", "LPDEST", "PRINTER", "TZ", "XMODIFIERS"
    };

    private static final String[] DEFAULT_WINDOWS_ENV_VARS = {
        "SystemDrive", "SystemRoot", "windir", "TMP", "TEMP"
    };

    private static final String JAVATEST_ANT_FILE_LIST = "javatest.ant.file.list";

    private static boolean debugChild = Boolean.getBoolean("javatest.regtest.debugChild");

    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(Main.class);
}
