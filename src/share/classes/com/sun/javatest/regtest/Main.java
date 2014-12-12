/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
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
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.types.Commandline;

import com.sun.javatest.Harness;
import com.sun.javatest.InterviewParameters;
import com.sun.javatest.JavaTestSecurityManager;
import com.sun.javatest.Keywords;
import com.sun.javatest.ProductInfo;
import com.sun.javatest.Status;
import com.sun.javatest.TestEnvironment;
import com.sun.javatest.TestFilter;
import com.sun.javatest.TestFinder;
import com.sun.javatest.TestResult;
import com.sun.javatest.TestResultTable;
import com.sun.javatest.TestSuite;
import com.sun.javatest.WorkDirectory;
import com.sun.javatest.exec.ExecToolManager;
import com.sun.javatest.httpd.HttpdServer;
import com.sun.javatest.httpd.PageGenerator;
import com.sun.javatest.regtest.Help.VersionHelper;
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

    List<Option> options = Arrays.asList(
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
                help.setTagSpec(true);
            }
        },

        new Option(NONE, DOC, "", "n", "relnote") {
            public void process(String opt, String arg) {
                help.setReleaseNotes(true);
            }
        },

        new Option(OLD, MAIN, "", "w", "workDir") {
            public void process(String opt, String arg) {
                workDirArg = new File(arg);
                childArgs.add("-w:" + getNormalizedFile(workDirArg));
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
                childArgs.add("-r:" + getNormalizedFile(reportDirArg));
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

        new Option(STD, MAIN, "ro-nr", "show") {
            public void process(String opt, String arg) {
                noReportFlag = true;
                showStream = arg;
            }
        },

        new Option(STD, MAIN, "", "timeout", "timeoutFactor") {
            public void process(String opt, String arg) {
                timeoutFactorArg = arg;
                childArgs.add(opt);
            }
        },

        new Option(STD, MAIN, "", "tl", "timelimit") {
            public void process(String opt, String arg) {
                timeLimitArg = arg;
                childArgs.add(opt);
            }
        },

        new Option(STD, MAIN, "", "conc", "concurrency") {
            public void process(String opt, String arg) {
                concurrencyArg = arg;
                childArgs.add(opt);
            }
        },

        new Option(OPT, MAIN, "", "xml") {
            public void process(String opt, String arg) {
                xmlFlag = true;
                xmlVerifyFlag = "verify".equals(arg);
                childArgs.add(opt);
            }
        },

        new Option(STD, MAIN, "", "dir") {
            public void process(String opt, String arg) {
                baseDirArg = new File(arg);
                childArgs.add("-dir:" + getNormalizedFile(baseDirArg));
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

        new Option(STD, SELECT, null, "exclude", "Xexclude") {
            public void process(String opt, String arg) {
                File f = getNormalizedFile(new File(arg));
                excludeListArgs.add(f);
                childArgs.add("-exclude:" + f);
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

        new Option(STD, MAIN, "", "th", "timeoutHandler") {
            public void process(String opt, String arg) {
                TimeoutHandlerProvider.setClassName(arg);
                childArgs.add("-th:" + arg);
            }
        },

        new Option(STD, MAIN, "", "thd", "timeoutHandlerDir") {
            public void process(String opt, String arg) throws BadArgs {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                ArrayList<File> timeoutHandlerPathArg = new ArrayList<File>();
                for (String f: arg.split(File.pathSeparator)) {
                    if (f.length() == 0)
                        continue;
                    timeoutHandlerPathArg.add(new File(f));
                }
                try {
                    TimeoutHandlerProvider.setClassPath(timeoutHandlerPathArg);
                } catch (MalformedURLException ex) {
                    throw new BadArgs(i18n, ex.toString());
                }
                childArgs.add("-thd:" + filesToAbsolutePath(pathToFiles(arg)));
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

        new Option(NONE, MAIN, null, "l", "listtests") {
            public void process(String opt, String arg) {
                listTestsFlag = true;
            }
        },

        new Option(NONE, MAIN, null, "showGroups") {
            public void process(String opt, String arg) {
                showGroupsFlag = true;
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

        new Option(STD, MAIN, "", "lock") {
            public void process(String opt, String arg) throws BadArgs {
                File f = getNormalizedFile(new File(arg));
                try {
                    if (!(f.exists() ? f.isFile() && f.canRead() : f.createNewFile()))
                        throw new BadArgs(i18n, "main.badLockFile", arg);
                } catch (IOException e) {
                    throw new BadArgs(i18n, "main.cantCreateLockFile", arg);
                }
                exclusiveLockArg = f;
                childArgs.add("-lock:" + f);
            }
        },

        new Option(STD, MAIN, "", "nativepath") {
            public void process(String opt, String arg) throws BadArgs {
                if (arg.contains(File.pathSeparator))
                    throw new BadArgs(i18n, "main.nativePathMultiplePath", arg);
                File f = new File(arg);
                if (!f.exists())
                    throw new BadArgs(i18n, "main.nativePathNotExist", arg);
                if (!f.isDirectory())
                    throw new BadArgs(i18n, "main.nativePathNotDir", arg);
                nativeDirArg = f;
                childArgs.add("-nativepath:" + getNormalizedFile(nativeDirArg));
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
                testJDK = com.sun.javatest.regtest.JDK.of(arg);
            }
        },

        new Option(OLD, JDK, "", "compilejdk") {
            public void process(String opt, String arg) {
                compileJDK = com.sun.javatest.regtest.JDK.of(arg);
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
                help.setCommandLineHelpQuery(arg);
            }
        },

        new Option(REST, DOC, "help", "onlineHelp") {
            public void process(String opt, String arg) {
                help.setOnlineHelpQuery(arg);
            }
        },

        new Option(NONE, DOC, "help", "version") {
            public void process(String opt, String arg) {
                help.setVersionFlag(true);
            }
        },

        new Option(FILE, MAIN, null) {
            public void process(String opt, String arg) {
                if (groupPtn.matcher(arg).matches())
                    testGroupArgs.add(arg);
                else
                    testFileArgs.add(new File(arg));
                childArgs.add(arg);
            }

            Pattern groupPtn = System.getProperty("os.name").matches("(?i)windows.*")
                    ? Pattern.compile("(|[^A-Za-z]|.{2,}):[A-Za-z0-9_,]+")
                    : Pattern.compile(".*:[A-Za-z0-9_,]+");
        }
    );

    //---------- Ant Invocation ------------------------------------------------

    public static class Ant extends MatchingTask {
        private Main m = new Main();
        private File jdk;
        private File dir;
        private File reportDir;
        private File workDir;
        private File nativeDir;
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

        public void setNativeDir(File nativeDir) {
            this.nativeDir = nativeDir;
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
                AntOptionDecoder decoder = new AntOptionDecoder(m.options);
                decoder.process("concurrency", concurrency);
                decoder.process("dir", dir);
                decoder.process("reportDir", reportDir);
                decoder.process("workDir", workDir);
                decoder.process("nativeDir", nativeDir);
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

                if (m.testFileArgs.isEmpty() && dir != null) {
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
        } catch (TestManager.NoTests e) {
            err.println(i18n.getString("main.error", e.getMessage()));
            exit(EXIT_NO_TESTS);
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
            e.printStackTrace(System.err);
            exit(EXIT_EXCEPTION);
        } catch (Exception e) {
            err.println(i18n.getString("main.unexpectedException", e.toString()));
            e.printStackTrace(System.err);
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
        javatest_jar = findJar("javatest.jar", "lib/javatest.jar", com.sun.javatest.Harness.class);
        if (javatest_jar != null)
            System.setProperty("javatestClassDir", javatest_jar.getPath());

        jtreg_jar = findJar("jtreg.jar", "lib/jtreg.jar", getClass());
        if (jtreg_jar != null) {
            jcovManager = new JCovManager(jtreg_jar.getParentFile());
            if (jcovManager.isJCovInstalled()) {
                options = new ArrayList<Option>(options);
                options.addAll(jcovManager.options);
            }
        }

        help = new Help(options);
        if (jcovManager != null && jcovManager.isJCovInstalled()) {
            help.addVersionHelper(new VersionHelper() {
                public void showVersion(PrintWriter out) {
                    out.println(jcovManager.version());
                }
            });
        }
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
        if (args.length > 0)
            new OptionDecoder(options).decodeArgs(expandAtFiles(args));
        else {
            help = new Help(options);
            help.setCommandLineHelpQuery(null);
        }
        return run();
    }

    private int run() throws BadArgs, Fault, Harness.Fault, InterruptedException {
        findSystemJarFiles();

        if (help.isEnabled()) {
            guiFlag = help.show(out);
            return EXIT_OK;
        }

        File baseDir;
        if (baseDirArg == null) {
            baseDir = new File(System.getProperty("user.dir"));
        } else {
            if (!baseDirArg.exists())
                throw new Fault(i18n, "main.cantFindFile", baseDirArg);
            baseDir = baseDirArg.getAbsoluteFile();
        }

        String antFileList = System.getProperty(JAVATEST_ANT_FILE_LIST);
        if (antFileList != null)
            antFileArgs.addAll(readFileList(new File(antFileList)));

        TestManager testManager = new TestManager(out, baseDir);
        testManager.addTests(testFileArgs, false);
        testManager.addTests(antFileArgs, true);
        testManager.addGroups(testGroupArgs);

        if (testManager.isEmpty())
            throw testManager.new NoTests();

        boolean multiRun = testManager.isMultiRun();

        for (RegressionTestSuite ts: testManager.getTestSuites()) {
            Version requiredVersion = ts.getRequiredVersion();
            Version currentVersion = Version.getCurrent();
            if (requiredVersion.compareTo(currentVersion) > 0) {
                throw new Fault(i18n, "main.requiredVersion", ts.getPath(), requiredVersion.version, requiredVersion.build, currentVersion.version, currentVersion.build);
            }
        }

        if (execMode == null) {
            Set<ExecMode> modes = EnumSet.noneOf(ExecMode.class);
            for (RegressionTestSuite ts: testManager.getTestSuites()) {
                ExecMode m = ts.getDefaultExecMode();
                if (m != null)
                    modes.add(m);
            }
            switch (modes.size()) {
                case 0:
                    execMode = ExecMode.OTHERVM;
                    break;
                case 1:
                    execMode = modes.iterator().next();
                    break;
                default:
                    throw new Fault(i18n, "main.cantDetermineExecMode");
            }
        }

        if (execMode == ExecMode.SAMEVM && !testJavaOpts.isEmpty())
            throw new Fault(i18n, "main.cant.mix.samevm.java.options");

        if (execMode == ExecMode.SAMEVM && compileJDK != null)
            throw new Fault(i18n, "main.cant.mix.samevm.compile.jdk.options");

        if (execMode == ExecMode.SAMEVM && concurrencyArg != null)
            throw new Fault(i18n, "main.cant.mix.samevm.concurrency.options");

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
            testJDK = com.sun.javatest.regtest.JDK.of(f);
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

        if (workDirArg == null) {
            workDirArg = new File("JTwork");
            childArgs.add(0, "-w:" + getNormalizedFile(workDirArg));
        }

        if (reportDirArg == null && !noReportFlag) {
            reportDirArg = new File("JTreport");
            childArgs.add(0, "-r:" + getNormalizedFile(reportDirArg));
        }

        makeDir(workDirArg, false);
        testManager.setWorkDirectory(workDirArg);

        if (showGroupsFlag) {
            showGroups(testManager);
            return EXIT_OK;
        }

        if (listTestsFlag) {
            listTests(testManager);
            return EXIT_OK;
        }

        makeDir(new File(workDirArg, "scratch"), true);

        if (!noReportFlag) {
            makeDir(reportDirArg, false);
            testManager.setReportDirectory(reportDirArg);
        }

        if (allowSetSecurityManagerFlag) {
            switch (execMode) {
                case AGENTVM:
                    initPolicyFile();
                    Agent.Pool.instance().setSecurityPolicy(policyFile);
                    break;
                case SAMEVM:
                    initPolicyFile();
                    break;
                case OTHERVM:
                    break;
            }
        }

        if (jcovManager.isEnabled()) {
            jcovManager.setTestJDK(testJDK);
            jcovManager.setWorkDir(getNormalizedFile(workDirArg));
            jcovManager.setReportDir(getNormalizedFile(reportDirArg));
            boolean isChild = (System.getProperty("javatest.child") != null);
            if (!isChild) {
                jcovManager.instrumentClasses();
                final String XBOOTCLASSPATH_P = "-Xbootclasspath/p:";
                final String XMS = "-Xms";
                final String defaultInitialHeap = "20m";
                String insert = jcovManager.instrClasses + File.pathSeparator
                                + jcovManager.jcov_network_saver_jar;
                boolean found_Xbootclasspath_p = false;
                boolean found_Xms = false;
                for (int i = 0; i < testVMOpts.size(); i++) {
                    String opt = testVMOpts.get(i);
                    if (opt.startsWith(XBOOTCLASSPATH_P)) {
                        opt = opt.substring(0, XBOOTCLASSPATH_P.length())
                                + insert + File.pathSeparator
                                + opt.substring(XBOOTCLASSPATH_P.length());
                        testVMOpts.set(i, opt);
                        found_Xbootclasspath_p = true;
                        break;
                    } else if (opt.startsWith(XMS))
                        found_Xms = true;
                }
                if (!found_Xbootclasspath_p)
                    testVMOpts.add(XBOOTCLASSPATH_P + insert);
                if (!found_Xms)
                    testVMOpts.add(XMS + defaultInitialHeap);
                jcovManager.startGrabber();
                testVMOpts.add("-Djcov.port=" + jcovManager.grabberPort);

                if (JCovManager.showJCov)
                    System.err.println("Modified VM opts: " + testVMOpts);
            }
        }

        try {

            if (!isThisVMOK())
                return execChild();

            Harness.setClassDir(ProductInfo.getJavaTestClassDir());

            // Allow keywords to begin with a numeric
            Keywords.setAllowNumericKeywords(true);

            if (httpdFlag)
                startHttpServer();

            if (multiRun && guiFlag)
                throw new Fault(i18n, "main.onlyOneTestSuiteInGuiMode");

            testStats = new TestStats();

            for (RegressionTestSuite ts: testManager.getTestSuites()) {

                if (multiRun && (verbose != null && verbose.multiRun))
                    out.println("Running tests in " + ts.getRootDir());

                RegressionParameters params = createParameters(testManager, ts);

                checkLockFiles(params.getWorkDirectory().getRoot(), "start");

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

                if (guiFlag) {
                    showTool(params);
                    return EXIT_OK;
                } else {
                    try {
                        boolean quiet = (multiRun && !(verbose != null && verbose.multiRun));
                        testStats.addAll(batchHarness(params, quiet));
                    } finally {
                        checkLockFiles(params.getWorkDirectory().getRoot(), "done");
                    }
                }
            }

            if (multiRun) {
                testStats.showResultStats(out);
                RegressionReporter r = new RegressionReporter(workDirArg, reportDirArg, out);
                r.report(testManager);
                if (!reportOnlyFlag)
                    out.println("Results written to " + canon(workDirArg));
            }

            return (testStats.counts[Status.ERROR] > 0 ? EXIT_TEST_ERROR
                    : testStats.counts[Status.FAILED] > 0 ? EXIT_TEST_FAILED
                    : testStats.counts[Status.PASSED] == 0 ? EXIT_NO_TESTS
                    : errors != 0 ? EXIT_FAULT
                    : EXIT_OK);

        } finally {

            if (jcovManager.isEnabled()) {
                jcovManager.stopGrabber();
                if (jcovManager.results.exists()) {
                    jcovManager.writeReport();
                    out.println("JCov report written to " + canon(new File(jcovManager.report, "index.html")));
                } else {
                    out.println("Note: no jcov results found; no report generated");
                }
            }

        }
    }

    /**
     * Show the expansion (to files and directories) of the groups given
     * for the test suites on the command line.  If a test suite is given
     * without qualifying with a group, all groups in that test suite are
     * shown.
     * No filters (like keywords, status, etc) are taken into account.
     */
    void showGroups(TestManager testManager) throws Fault {
        for (RegressionTestSuite ts : testManager.getTestSuites()) {
            out.println(i18n.getString("main.tests.suite", ts.getRootDir()));
            try {
                Set<String> selected = testManager.getGroups(ts);
                GroupManager gm = ts.getGroupManager(out);
                Set<String> gset = new TreeSet<String>(new NaturalComparator(false));
                if (selected.isEmpty())
                    gset.addAll(gm.getGroups());
                else {
                    for (String g : gm.getGroups()) {
                        if (selected.contains(g))
                            gset.add(g);
                    }
                }
                if (gset.isEmpty()) {
                    out.println(i18n.getString("main.groups.nogroups"));
                } else {
                    for (String g: gset) {
                        out.print(g);
                        out.print(":");
                        Set<String> fset = new TreeSet<String>(new NaturalComparator(false));
                        for (File f : gm.getFiles(g))
                            fset.add(ts.getRootDir().toURI().relativize(f.toURI()).getPath());
                        for (String f: fset) {
                            out.print(" ");
                            out.print(f);
                        }
                        out.println();
                    }
                }
            } catch (IOException e) {
                throw new Fault(i18n, "main.cantReadGroups", ts.getRootDir(), e);
            }
        }
    }
    /**
     * Show the set of tests defined by the parameters on the command line.
     * Filters (like keywords and status) are taken into account.
     */

    void listTests(TestManager testManager) throws BadArgs, Fault {
        int total = 0;
        for (RegressionTestSuite ts: testManager.getTestSuites()) {
            int count = 0;
            out.println(i18n.getString("main.tests.suite", ts.getRootDir()));
            RegressionParameters params = createParameters(testManager, ts);
            for (Iterator<TestResult> iter = getResultsIterator(params); iter.hasNext(); ) {
                TestResult tr = iter.next();
                out.println(tr.getTestName());
                count++;
            }
            out.println(i18n.getString("main.tests.found", count));
            total += count;
        }
        if (testManager.isMultiRun())
            out.println(i18n.getString("main.tests.total", total));
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
    private static String[] expandAtFiles(String[] args) throws Fault {
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

    private static void loadCmdFile(String name, List<String> args) throws Fault {
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

    private static List<File> readFileList(File file) throws Fault {
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
        return testStats.counts;
    }

    private boolean isThisVMOK() {
        if (reportOnlyFlag || checkFlag || listTestsFlag
                || showStream != null || execMode != ExecMode.SAMEVM)
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
            SearchPath jcp = new SearchPath(System.getProperty("java.class.path"));
            SearchPath cpa = filesToAbsolutePath(classPathAppendArg);
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

        File childJDKHome = getNormalizedFile(testJDK.getAbsoluteFile());
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
        if (junit_jar.exists())
            classpath.add(junit_jar);
        if (testng_jar.exists())
            classpath.add(testng_jar);
        classpath.addAll(classPathAppendArg);
        c.add(filesToAbsolutePath(classpath).toString());

        c.addAll(testVMOpts);
        if (nativeDirArg != null)
            c.add("-Djava.library.path=" + nativeDirArg.getAbsolutePath());

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

        for (Map.Entry<?, ?> e: System.getProperties().entrySet()) {
            String name = (String) e.getKey();
            if (name.startsWith("javatest.") || name.startsWith("jtreg."))
                c.add("-D" + name + "=" + e.getValue());
        }

        c.add("-Djavatest.child=true");

        if (junit_jar != null)
            c.add("-Djunit.jar=" + junit_jar.getPath());

        if (testng_jar != null)
            c.add("-Dtestng.jar=" + testng_jar.getPath());

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
                SearchPath cp = new SearchPath().append(javatest_jar, jtreg_jar, testJDK.getToolsJar());

                ProcessBuilder pb = new ProcessBuilder();
                pb.environment().clear();
                pb.environment().putAll(getEnvVars());
                pb.environment().put("CLASSPATH", cp.toString());
                p = pb
                    .command(cmd)
                    .directory(execDir)
                    .start();
            } catch (IOException e) {
                err.println("cannot start child VM: " + e);
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
        if (javatest_jar == null) {
            javatest_jar = findJar("javatest.jar", "lib/javatest.jar", com.sun.javatest.Harness.class);
            if (javatest_jar == null)
                throw new Fault(i18n, "main.cantFind.javatest.jar");
        }

        if (jtreg_jar == null) {
            jtreg_jar = findJar("jtreg.jar", "lib/jtreg.jar", getClass());
            if (jtreg_jar == null)
                throw new Fault(i18n, "main.cantFind.jtreg.jar");
        }

        String junit_jar_prop = System.getProperty("junit.jar");
        if (junit_jar_prop != null)
            junit_jar = new File(junit_jar_prop);
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
        // no convenient version info for junit.jar

        String testng_jar_prop = System.getProperty("testng.jar");
        if (testng_jar_prop != null)
            testng_jar = new File(testng_jar_prop);
        else {
            try {
                testng_jar = findJar("testng.jar", "lib/testng.jar", org.testng.annotations.Test.class);
            } catch (NoClassDefFoundError ex) {
            }
        }
        if (testng_jar == null) {
            // Leave a place-holder for the optional jar.
            testng_jar = new File(jtreg_jar.getParentFile(), "testng.jar");
        }
        help.addJarVersionHelper("TestNG", testng_jar, "META-INF/maven/org.testng/testng/pom.properties");
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
                for (File f: Arrays.asList(jtreg_jar, javatest_jar)) {
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

    private void makeDir(File dir, boolean quiet) throws Fault {
        // FIXME: I18N
        if (dir.isDirectory())
            return;
        if (!quiet)
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

    private static SearchPath filesToAbsolutePath(List<File> files) {
        SearchPath p = new SearchPath();
        for (File f: files) {
            p.append(getNormalizedFile(f));
        }
        return p;
    }

    /**
     * Create a RegressionParameters object based on the values set up by decodeArgs.
     * @return a RegressionParameters object
     */
    private RegressionParameters createParameters(
            TestManager testManager, RegressionTestSuite testSuite)
            throws BadArgs, Fault
    {
        try {
            RegressionParameters rp = testSuite.createInterview();

            WorkDirectory workDir = testManager.getWorkDirectory(testSuite);
            rp.setWorkDirectory(workDir);

            // JT Harness 4.3+ requires a config file to be set
            rp.setFile(workDir.getFile("config.jti"));

            rp.setRetainArgs(retainArgs);

            rp.setTests(testManager.getTests(testSuite));

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
                    int c;
                    if (concurrencyArg.equals("auto"))
                        c = Runtime.getRuntime().availableProcessors();
                    else
                        c = Integer.parseInt(concurrencyArg);
                    rp.setConcurrency(c);
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

            File rd = testManager.getReportDirectory(testSuite);
            if (rd != null)
                rp.setReportDir(rd);

            if (exclusiveLockArg != null)
                rp.setExclusiveLock(exclusiveLockArg);

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

            if (junit_jar != null)
                rp.setJUnitJar(junit_jar);

            if (testng_jar != null)
                rp.setTestNGJar(testng_jar);

            if (timeLimitArg != null) {
                try {
                    rp.setTimeLimit(Integer.parseInt(timeLimitArg));
                } catch (NumberFormatException e) {
                    throw new BadArgs(i18n, "main.badTimeLimit");
                }
            }

            if (nativeDirArg != null)
                rp.setNativeDir(nativeDirArg);

            return rp;
        } catch (TestSuite.Fault f) {
            // TODO: fix bad string -- need more helpful resource here
            throw new Fault(i18n, "main.cantOpenTestSuite", testSuite.getRootDir(), f);
        }
    }

    private static File canon(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return getNormalizedFile(file);
        }
    }

    private static Harness.Observer getObserver(List<File> observerPath, String observerClassName)
            throws Fault {
        try {
            Class<?> observerClass;
            if (observerPath == null)
                observerClass = Class.forName(observerClassName);
            else {
                URL[] urls = new URL[observerPath.size()];
                int u = 0;
                for (File f: observerPath) {
                    try {
                        urls[u++] = f.toURI().toURL();
                    } catch (MalformedURLException ignore) {
                    }
                }
                ClassLoader loader = new URLClassLoader(urls);
                observerClass = loader.loadClass(observerClassName);
            }
            return observerClass.asSubclass(Harness.Observer.class).newInstance();
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

    /**
     * Run the harness in batch mode, using the specified parameters.
     */
    private TestStats batchHarness(RegressionParameters params, boolean quiet)
            throws Fault, Harness.Fault, InterruptedException {
        boolean reportRequired =
                !noReportFlag && !Boolean.getBoolean("javatest.noReportRequired");

        try {
            TestStats stats = new TestStats();
            boolean ok;
            ElapsedTimeHandler elapsedTimeHandler = null;

            if (reportOnlyFlag) {
                for (Iterator<TestResult> iter = getResultsIterator(params); iter.hasNext(); ) {
                    TestResult tr = iter.next();
                    stats.add(tr);
                }
                ok = stats.isOK();
            } else if (showStream != null) {
                TestResult tr = null;
                for (Iterator<TestResult> iter = getResultsIterator(params); iter.hasNext(); ) {
                    if (tr != null) {
                        out.println("More than one test specified");
                        tr = null;
                        break;
                    }
                    tr = iter.next();
                }
                if (tr == null) {
                    ok = false;
                } else if (tr.getStatus().isNotRun()) {
                    out.println("Test has not been run");
                    ok = false;
                } else {
                    try {
                        // work around bug CODETOOLS-7900214 -- force the sections to be reloaded
                        tr.getProperty("sections");
                        for (int i = 0; i < tr.getSectionCount(); i++) {
                            TestResult.Section s = tr.getSection(i);
                            String text = s.getOutput(showStream);
                            // need to handle internal newlines properly
                            if (text != null) {
                                out.println("### Section " + s.getTitle());
                                out.println(text);
                            }
                        }
                        ok = true;
                    } catch (TestResult.Fault f) {
                        out.println("Cannot reload test results: " + f.getMessage());
                        ok = false;
                    }
                }
                quiet = true;
            } else {
                // Set backup parameters; in time this might become more versatile.
                BackupPolicy backupPolicy = createBackupPolicy();

                Harness h = new Harness();
                h.setBackupPolicy(backupPolicy);

                if (xmlFlag) {
                    out.println("XML output " +
                            (xmlVerifyFlag ? "with verification to " : " to ") +
                            params.getWorkDirectory().getPath());
                    h.addObserver(new XMLWriter.XMLHarnessObserver(xmlVerifyFlag, out, err));
                }
                if (observerClassName != null)
                    h.addObserver(getObserver(observerPathArg, observerClassName));

                if (verbose != null)
                    new VerboseHandler(verbose, out, err).register(h);

                stats.register(h);

                h.addObserver(new BasicObserver() {
                    @Override
                    public void error(String msg) {
                        Main.this.error(msg);
                    }
                });
                params.getWorkDirectory().getTestResultTable().getTestFinder().setErrorHandler(
                        new TestFinder.ErrorHandler() {
                    public void error(String msg) {
                        Main.this.error(msg);
                    }
                });

                if (reportRequired) {
                    elapsedTimeHandler = new ElapsedTimeHandler();
                    elapsedTimeHandler.register(h);
                }

                ok = h.batch(params);

                Agent.Pool.instance().flush();
                Alarm.finished();
                Lock.get(params).close();
            }

            if (!quiet)
                stats.showResultStats(out);

            if (reportRequired) {
                RegressionReporter r = new RegressionReporter(workDirArg, reportDirArg, out);
                r.report(params, elapsedTimeHandler, stats, quiet);
            }

            if (!reportOnlyFlag && !quiet)
                out.println("Results written to " + params.getWorkDirectory().getPath());

            // report a brief msg to System.err as well, in case System.out has
            // been redirected.
            if (!ok && !quiet)
                err.println(i18n.getString("main.testsFailed"));

            return stats;
        } finally {
            out.flush();
            err.flush();
        }
    }

    @SuppressWarnings("unchecked")
    private Iterator<TestResult> getResultsIterator(InterviewParameters params) {
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
            date = i18n.getString("main.noDate");

        PageGenerator.setSWBuildDate(date);
        PageGenerator.setSWVersion(ProductInfo.getVersion());

        thr.start();
    }

    private Map<String, String> getEnvVars() {

        Map<String, String> envVars = new TreeMap<String, String>();
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
        for (Map.Entry<String, String> e: System.getenv().entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k.startsWith("JTREG_")) {
                envVars.put(k, v);
            }
        }

        return envVars;
    }

    private void addEnvVars(Map<String, String> table, String list) {
        addEnvVars(table, list.split(","));
    }

    private void addEnvVars(Map<String, String> table, String[] list) {
        addEnvVars(table, Arrays.asList(list));
    }

    private void addEnvVars(Map<String, String> table, List<String> list) {
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
                    table.put(s, value);
            } else if (eq > 0) {
                String name = s.substring(0, eq);
                String value = s.substring(eq + 1);
                table.put(name, value);
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
                // use URI to avoid encoding issues, e.g. Program%20Files
                URI uri = getClass().getClassLoader().getResource(className).toURI();
                if (uri.getScheme().equals("jar")) {
                    String ssp = uri.getRawSchemeSpecificPart();
                    int sep = ssp.lastIndexOf("!");
                    uri = new URI(ssp.substring(0, sep));
                }
                if (uri.getScheme().equals("file"))
                    return new File(uri.getPath());
            } catch (URISyntaxException ignore) {
                ignore.printStackTrace(System.err);
            }
        }

        return null;
    }

    private void error(String msg) {
        err.println(i18n.getString("main.error", msg));
        errors++;
    }
    int errors;

    /**
     * Call System.exit, taking care to get permission from the
     * JavaTestSecurityManager, if it is installed.
     */
    private static void exit(int exitCode) {
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

    private static File getNormalizedFile(File f) {
        return new File(f.getAbsoluteFile().toURI().normalize());
    }

    //----------member variables-----------------------------------------------

    private PrintWriter out;
    private PrintWriter err;

    // this first group of args are the "standard" JavaTest args
    private File workDirArg;
    private List<String> retainArgs;
    private List<File> excludeListArgs = new ArrayList<File>();
    private String keywordsExprArg;
    private String concurrencyArg;
    private String timeoutFactorArg;
    private String priorStatusValuesArg;
    private File reportDirArg;
    private List<String> testGroupArgs = new ArrayList<String>();
    private List<File> testFileArgs = new ArrayList<File>();
    // TODO: consider making this a "pathset" to detect redundant specification
    // of directories and paths within them.
    private List<File> antFileArgs = new ArrayList<File>();

    // these args are jtreg extras
    private File baseDirArg;
    private ExecMode execMode;
    private JDK compileJDK;
    private JDK testJDK;
    private boolean guiFlag;
    private boolean reportOnlyFlag;
    private boolean noReportFlag;
    private String showStream;
    private boolean allowSetSecurityManagerFlag = true;
    private static Verbose  verbose;
    private boolean httpdFlag;
    private String timeLimitArg;
    private String observerClassName;
    private List<File> observerPathArg;
    private List<String> testCompilerOpts = new ArrayList<String>();
    private List<String> testJavaOpts = new ArrayList<String>();
    private List<String> testVMOpts = new ArrayList<String>();
    private boolean checkFlag;
    private boolean listTestsFlag;
    private boolean showGroupsFlag;
    private List<String> envVarArgs = new ArrayList<String>();
    private IgnoreKind ignoreKind;
    private List<File> classPathAppendArg = new ArrayList<File>();
    private File nativeDirArg;
    private boolean jitFlag = true;
    private Help help;
    private boolean xmlFlag;
    private boolean xmlVerifyFlag;
    private File exclusiveLockArg;

    File jtreg_home;
    File javatest_jar;
    File jtreg_jar;
    File junit_jar;
    File testng_jar;
    File policyFile;

    JCovManager jcovManager;

    // the list of args to be passed down to a  child VM
    private List<String> childArgs = new ArrayList<String>();

    private TestStats testStats;

    private static final String AUTOMATIC = "!manual";
    private static final String MANUAL    = "manual";

    private static final String[] DEFAULT_UNIX_ENV_VARS = {
        "DISPLAY", "GNOME_DESKTOP_SESSION_ID", "HOME", "LANG",
        "LC_ALL", "LC_CTYPE", "LPDEST", "PRINTER", "TZ", "XMODIFIERS"
    };

    private static final String[] DEFAULT_WINDOWS_ENV_VARS = {
        "SystemDrive", "SystemRoot", "windir", "TMP", "TEMP"
    };

    private static final String JAVATEST_ANT_FILE_LIST = "javatest.ant.file.list";

    private static boolean debugChild = Boolean.getBoolean("javatest.regtest.debugChild");

    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(Main.class);
}
