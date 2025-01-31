/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.tool;

import java.awt.EventQueue;
import java.awt.Frame;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import javax.swing.Timer;

import com.sun.interview.Interview;
import com.sun.javatest.AllTestsFilter;
import com.sun.javatest.Harness;
import com.sun.javatest.InterviewParameters;
import com.sun.javatest.JavaTestSecurityManager;
import com.sun.javatest.Keywords;
import com.sun.javatest.ParameterFilter;
import com.sun.javatest.ProductInfo;
import com.sun.javatest.Status;
import com.sun.javatest.StatusFilter;
import com.sun.javatest.TestDescription;
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
import com.sun.javatest.regtest.BadArgs;
import com.sun.javatest.regtest.Main;
import com.sun.javatest.regtest.Main.Fault;
import com.sun.javatest.regtest.agent.JDK_Version;
import com.sun.javatest.regtest.agent.SearchPath;
import com.sun.javatest.regtest.config.ExecMode;
import com.sun.javatest.regtest.config.GroupManager;
import com.sun.javatest.regtest.config.IgnoreKind;
import com.sun.javatest.regtest.config.JDK;
import com.sun.javatest.regtest.config.OS;
import com.sun.javatest.regtest.config.RegressionKeywords;
import com.sun.javatest.regtest.config.RegressionParameters;
import com.sun.javatest.regtest.config.RegressionTestSuite;
import com.sun.javatest.regtest.config.TestManager;
import com.sun.javatest.regtest.exec.Agent;
import com.sun.javatest.regtest.exec.Lock;
import com.sun.javatest.regtest.report.BasicObserver;
import com.sun.javatest.regtest.report.ElapsedTimeHandler;
import com.sun.javatest.regtest.report.RegressionReporter;
import com.sun.javatest.regtest.report.TestStats;
import com.sun.javatest.regtest.report.Verbose;
import com.sun.javatest.regtest.report.VerboseHandler;
import com.sun.javatest.regtest.report.XMLWriter;
import com.sun.javatest.regtest.util.NaturalComparator;
import com.sun.javatest.tool.Desktop;
import com.sun.javatest.util.BackupPolicy;
import com.sun.javatest.util.I18NResourceBundle;
import com.sun.javatest.util.StringArray;

import static com.sun.javatest.regtest.Main.EXIT_BAD_ARGS;
import static com.sun.javatest.regtest.Main.EXIT_EXCEPTION;
import static com.sun.javatest.regtest.Main.EXIT_FAULT;
import static com.sun.javatest.regtest.Main.EXIT_NO_TESTS;
import static com.sun.javatest.regtest.Main.EXIT_OK;
import static com.sun.javatest.regtest.Main.EXIT_TEST_ERROR;
import static com.sun.javatest.regtest.Main.EXIT_TEST_FAILED;
import static com.sun.javatest.regtest.tool.Option.ArgType.*;


/**
 * Main entry point to be used to access jtreg.
 */
@SuppressWarnings("removal") // Security Manager and related APIs
public class Tool {

    /**
     * Standard entry point. Only returns if GUI mode is initiated; otherwise, it calls System.exit
     * with an appropriate exit code.
     * @param args An array of args, such as might be supplied on the command line.
     */
    public static void main(String[] args) {
        checkJavaOSVersion();

        PrintWriter out = new PrintWriter(System.out, true);
        PrintWriter err = new PrintWriter(System.err, true);
        Tool m = new Tool(out, err);
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
        } catch (TestManager.NoTests e) {
            err.println(i18n.getString("main.error", e.getMessage()));
            exit(EXIT_NO_TESTS);
        } catch (Harness.Fault | Fault e) {
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
        } catch (RuntimeException | Error e) {
            err.println(i18n.getString("main.unexpectedException", e.toString()));
            e.printStackTrace(System.err);
            exit(EXIT_EXCEPTION);
        }
    } // main()

    private static void checkJavaOSVersion() {
        String osName = System.getProperty("os.name");
        if (osName != null && osName.equals("Mac OS X")) {
            var command = List.of("sw_vers", "-productVersion");
            try {
                String expectVersion;
                Process p = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start();
                try (InputStream in = p.getInputStream();
                    BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
                    expectVersion = r.lines().collect(Collectors.joining());
                }
                p.waitFor();
                int rc = p.exitValue();
                if (rc != 0) {
                    System.err.println("Error getting OS version: "
                            + String.join(" ", command) + ": rc=" + rc);
                    System.exit(99);
                }

                checkJavaOSVersion(expectVersion);

            } catch (IOException | InterruptedException e) {
                System.err.println("Error getting OS version: "
                        + String.join(" ", command) + ": " + e);
                System.exit(99);
            }
        }
    }

    private static void checkJavaOSVersion(String expectVersion) {
        String osVersion = System.getProperty("os.version");
        if (!osVersion.startsWith(expectVersion)) {
            System.err.println("The version of JDK you are using to run jtreg does not report the OS version correctly.");
            System.err.println("    java.home:    " + System.getProperty("java.home"));
            System.err.println("    java.version: " + System.getProperty("java.version"));
            System.err.println("    os.version:   " + osVersion + "  (expected: " + expectVersion + ")");
            System.err.println("Use a more recent update of this version of JDK, or a newer version of JDK.");
            System.exit(1);
        }
    }

    /**
     * Call System.exit, taking care to get permission from the
     * JavaTestSecurityManager, if it is installed.
     */
    @SuppressWarnings("static")
    private static void exit(int exitCode) {
        // If our security manager is installed, it won't allow a call of
        // System.exit unless we ask it nicely, pretty please, thank you.
        SecurityManager sc = System.getSecurityManager();
        if (sc instanceof JavaTestSecurityManager) {
            JavaTestSecurityManager.setAllowExit(true);
        }
        System.exit(exitCode);
    }

    public static final String MAIN = "main";           // main set of options
    public static final String SELECT = "select";       // test selection options
    public static final String JDK = "jdk";             // specify JDK to use
    public static final String MODE = "mode";           // agentM or otherVM
    public static final String VERBOSE = "verbose";     // verbose controls
    public static final String DOC = "doc";             // help or doc info
    public static final String TIMEOUT = "timeout";     // timeout-related options
    public static final String AGENT_POOL = "pool";     // agent pool related options

    public List<Option> options = List.of(new Option(OPT, VERBOSE, "verbose", "-v", "-verbose") {
            @Override
            public String[] getChoices() {
                String[] values = new String[Verbose.values().length];
                int i = 0;
                for (String s: Verbose.values())
                    values[i++] = s;
                return values;
            }
            @Override
            public void process(String opt, String arg) throws BadArgs {
                if (arg == null)
                    verbose = Verbose.DEFAULT;
                else {
                    verbose = Verbose.decode(arg);
                    if (verbose == null)
                        throw new BadArgs(i18n, "main.unknownVerbose", arg);
                }
            }
        },

        new Option(NONE, VERBOSE, "verbose", "-v1") {
            @Override
            public void process(String opt, String arg) {
                verbose = Verbose.SUMMARY;
            }
        },

        new Option(NONE, VERBOSE, "verbose", "-va") {
            @Override
            public void process(String opt, String arg) {
                verbose = Verbose.ALL;
            }
        },

        new Option(NONE, VERBOSE, "verbose", "-vp") {
            @Override
            public void process(String opt, String arg) {
                verbose = Verbose.PASS;
            }
        },

        new Option(NONE, VERBOSE, "verbose", "-vf") {
            @Override
            public void process(String opt, String arg) {
                verbose = Verbose.FAIL;
            }
        },

        new Option(NONE, VERBOSE, "verbose", "-ve") {
            @Override
            public void process(String opt, String arg) {
                verbose = Verbose.ERROR;
            }
        },

        new Option(NONE, VERBOSE, "verbose", "-vt") {
            @Override
            public void process(String opt, String arg) {
                verbose = Verbose.TIME;
            }
        },

        new Option(NONE, DOC, "", "-t", "-tagspec") {
            @Override
            public void process(String opt, String arg) {
                help.setTagSpec(true);
            }
        },

        new Option(NONE, DOC, "", "-n", "-relnote") {
            @Override
            public void process(String opt, String arg) {
                help.setReleaseNotes(true);
            }
        },

        new Option(OLD, MAIN, "", "-w", "-workDir") {
            @Override
            public void process(String opt, String arg) {
                workDirArg = Path.of(arg);
            }
        },

        new Option(OPT, MAIN, "", "-retain") {
            @Override
            public String[] getChoices() {
                return new String[] { "none", "lastRun", "pass", "fail", "error", "all", "file-pattern" };
            }
            @Override
            public void process(String opt, String arg) throws BadArgs {
                if (arg != null)
                    arg = arg.trim();
                if (arg == null || arg.length() == 0)
                    retainArgs = Collections.singletonList("all");
                else
                    retainArgs = List.of(arg.split(","));
                if (retainArgs.contains("none") && retainArgs.size() > 1) {
                    throw new BadArgs(i18n, "main.badRetainNone", arg);
                }
                if (retainArgs.contains("lastRun") && retainArgs.size() > 1) {
                    throw new BadArgs(i18n, "main.badRetainLastRun", arg);
                }
            }
        },

        new Option(OLD, MAIN, "", "-r", "-reportDir") {
            @Override
            public void process(String opt, String arg) {
                reportDirArg = Path.of(arg);
            }
        },

        new Option(NONE, MAIN, "ro-nr", "-ro", "-reportOnly") {
            @Override
            public void process(String opt, String arg) {
                reportOnlyFlag = true;
            }
        },

        new Option(NONE, MAIN, "ro-nr", "-nr", "-noreport") {
            @Override
            public void process(String opt, String arg) {
                reportMode = ReportMode.NONE;
            }
        },

        new Option(STD, MAIN, "ro-nr", "-show") {
            @Override
            public void process(String opt, String arg) {
                reportMode = ReportMode.NONE;
                showStream = arg;
            }
        },

        new Option(STD, MAIN, "ro-nr", "-report") {
            @Override
            public String[] getChoices() {
                String[] values = new String[ReportMode.values().length];
                int i = 0;
                for (ReportMode m: ReportMode.values())
                    values[i++] = m.toString().toLowerCase(Locale.US).replace("_", "-");
                return values;
            }

            @Override
            public void process(String opt, String arg) throws BadArgs {
                switch (arg) {
                    case "none":
                        reportMode = ReportMode.NONE;
                        break;
                    case "executed":
                        reportMode = ReportMode.EXECUTED;
                        break;
                    case "all-executed":
                        reportMode = ReportMode.ALL_EXECUTED;
                        break;
                    case "files":
                        reportMode = ReportMode.FILES;
                        break;
                    case "all":
                        reportMode = ReportMode.ALL;
                        break;
                    default:
                        throw new BadArgs(i18n, "main.badReportOption", arg);
                }
            }
        },

        new Option(STD, TIMEOUT, "", "-timeout", "-timeoutFactor") {
            @Override
            public void process(String opt, String arg) throws BadArgs {
                try {
                    timeoutFactorArg = Float.parseFloat(arg);
                } catch (NumberFormatException e) {
                    throw new BadArgs(i18n, "main.badTimeoutFactor");
                }
            }
        },

        new Option(STD, TIMEOUT, "", "-tl", "-timelimit") {
            @Override
            public void process(String opt, String arg) {
                timeLimitArg = arg;
            }
        },

        new Option(GNU, AGENT_POOL, null, "--max-pool-size") {
            @Override
            public void process(String opt, String arg) throws BadArgs {
                try {
                    maxPoolSize = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    throw new BadArgs(i18n, "main.badMaxPoolSize", arg);
                }
            }

        },

        new Option(GNU, AGENT_POOL, null, "--pool-idle-timeout") {
            @Override
            public void process(String opt, String arg) throws BadArgs {
                try {
                    poolIdleTimeout = Duration.ofMillis((long) (1000 * Float.parseFloat(arg)));
                } catch (NumberFormatException e) {
                    throw new BadArgs(i18n, "main.badPoolIdleTimeout", arg);
                }

            }
        },

        new Option(GNU, AGENT_POOL, null, "--agent-attempts") {
            @Override
            public void process(String opt, String arg) throws BadArgs {
                int numTimes;
                try {
                    numTimes = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    throw new BadArgs(i18n, "main.badAgentSelAttempt", arg);
                }
                if (numTimes < 1) {
                    throw new BadArgs(i18n, "main.badAgentSelAttempt", numTimes);
                }
                numAgentSelectionAttempt = numTimes;
            }
        },

        new Option(STD, MAIN, "", "-conc", "-concurrency") {
            @Override
            public void process(String opt, String arg) {
                concurrencyArg = arg;
            }
        },

        new Option(OPT, MAIN, "", "-xml") {
            @Override
            public void process(String opt, String arg) {
                xmlFlag = true;
                xmlVerifyFlag = "verify".equals(arg);
            }
        },

        new Option(STD, MAIN, "", "-dir") {
            @Override
            public void process(String opt, String arg) {
                baseDirArg = Path.of(arg);
            }
        },

        new Option(OPT, MAIN, "", "-allowSetSecurityManager") {
            @Override
            public String[] getChoices() {
                return new String[] { "yes", "no", "on", "off", "true", "false" };
            }
            @Override
            public void process(String opt, String arg) {
                boolean b = (arg == null || List.of("yes", "on", "true").contains(arg));
                allowSetSecurityManagerFlag = b;
            }
        },

        new Option(STD, SELECT, "", "-status") {
            @Override
            public String[] getChoices() {
                return new String[] { "pass", "fail", "notRun", "error" };
            }
            @Override
            public void process(String opt, String arg) {
                priorStatusValuesArg = arg.toLowerCase();
            }
        },

        new Option(STD, SELECT, null, "-exclude", "-Xexclude") {
            @Override
            public void process(String opt, String arg) {
                File f = getNormalizedFile(new File(arg));
                excludeListArgs.add(f.toPath());
            }
        },

        new Option(STD, SELECT, null, "-match") {
            @Override
            public void process(String opt, String arg) {
                File f = getNormalizedFile(new File(arg));
                matchListArgs.add(f.toPath());
            }
        },

        new Option(NONE, MAIN, null, "-startHttpd") {
            @Override
            public void process(String opt, String arg) {
                httpdFlag = true;
            }
        },

        new Option(OLD, MAIN, "", "-o", "-observer") {
            @Override
            public void process(String opt, String arg) {
                observerClassName = arg;
            }
        },

       new Option(OLD, MAIN, "", "-ttf", "-testThreadFactory") {
            @Override
            public void process(String opt, String arg) {
                testThreadFactory = arg;
            }
        },

        new Option(STD, MAIN, "", "-ttfp", "-testThreadFactoryPath") {
            @Override
            public void process(String opt, String arg) {
                testThreadFactoryPathArg = arg;
            }
        },

        new Option(OLD, MAIN, "", "-od", "-observerDir", "-op", "-observerPath") {
            @Override
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                observerPathArg = new ArrayList<>();
                for (String f: arg.split(File.pathSeparator)) {
                    if (f.length() == 0)
                        continue;
                    observerPathArg.add(Path.of(f));
                }
            }
        },

        new Option(STD, TIMEOUT, "", "-th", "-timeoutHandler") {
            @Override
            public void process(String opt, String arg) {
                timeoutHandlerClassName = arg;
            }
        },

        new Option(STD, TIMEOUT, "", "-thd", "-timeoutHandlerDir") {
            @Override
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                timeoutHandlerPathArg = new ArrayList<>();
                for (String f: arg.split(File.pathSeparator)) {
                    if (f.length() == 0)
                        continue;
                    timeoutHandlerPathArg.add(Path.of(f));
                }
            }
        },

        new Option(STD, TIMEOUT, "", "-thtimeout", "-timeoutHandlerTimeout") {
            @Override
            public void process(String opt, String arg) throws BadArgs {
                try {
                    timeoutHandlerTimeoutArg = Long.parseLong(arg);
                } catch (NumberFormatException e) {
                    throw new BadArgs(i18n, "main.badTimeoutHandlerTimeout");
                }
            }
        },

        new Option(NONE, MAIN, null, "-g", "-gui") {
            @Override
            public void process(String opt, String arg) {
                guiFlag = true;
            }
        },

        new Option(NONE, MAIN, null, "-c", "-check") {
            @Override
            public void process(String opt, String arg) {
                checkFlag = true;
            }
        },

        new Option(NONE, MAIN, null, "-l", "-listtests") {
            @Override
            public void process(String opt, String arg) {
                listTestsFlag = true;
            }
        },

        new Option(NONE, MAIN, null, "-showGroups") {
            @Override
            public void process(String opt, String arg) {
                showGroupsFlag = true;
            }
        },

        new Option(NONE, MAIN, null, "-i", "--verify-exclude") {
            @Override
            public void process(String opt, String arg) {
                verifyExcludeListsFlag = true;
            }
        },

        // deprecated
        new Option(NONE, MAIN, "ignore", "-noignore") {
            @Override
            public void process(String opt, String arg) {
                ignoreKind = IgnoreKind.RUN;
            }
        },

        new Option(STD, MAIN, "ignore", "-ignore") {
            @Override
            public String[] getChoices() {
                String[] values = new String[IgnoreKind.values().length];
                int i = 0;
                for (IgnoreKind k: IgnoreKind.values())
                    values[i++] = k.toString().toLowerCase();
                return values;
            }
            @Override
            public void process(String opt, String arg) throws BadArgs {
                for (IgnoreKind k: IgnoreKind.values()) {
                    if (arg.equalsIgnoreCase(k.toString())) {
                        if (k == IgnoreKind.QUIET)
                            extraKeywordExpr = combineKeywords(extraKeywordExpr, "!ignore");
                        ignoreKind = k;
                        return;
                    }
                }
                throw new BadArgs(i18n, "main.unknownIgnore", arg);
            }
        },

        new Option(OLD, MAIN, null, "-e") {
            @Override
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                envVarArgs.addAll(List.of(arg.split(",")));
            }
        },

        new Option(STD, MAIN, "", "-lock") {
            @Override
            public void process(String opt, String arg) throws BadArgs {
                File f = getNormalizedFile(new File(arg));
                try {
                    if (!(f.exists() ? f.isFile() && f.canRead() : f.createNewFile()))
                        throw new BadArgs(i18n, "main.badLockFile", arg);
                } catch (IOException e) {
                    throw new BadArgs(i18n, "main.cantCreateLockFile", arg);
                }
                exclusiveLockArg = f.toPath();
            }
        },

        new Option(STD, MAIN, "", "-nativepath") {
            @Override
            public void process(String opt, String arg) throws BadArgs {
                if (arg.contains(File.pathSeparator))
                    throw new BadArgs(i18n, "main.nativePathMultiplePath", arg);
                File f = new File(arg);
                if (!f.exists())
                    throw new BadArgs(i18n, "main.nativePathNotExist", arg);
                if (!f.isDirectory())
                    throw new BadArgs(i18n, "main.nativePathNotDir", arg);
                nativeDirArg = f.toPath();
            }
        },

        new Option(NONE, MAIN, "wsl-cygwin", "-wsl") {
            @Override
            public void process(String opt, String arg) throws BadArgs {
                if (!isWindows()) {
                    throw new BadArgs(i18n, "main.windowsOnly", opt);
                }
                if (isCygwinDetected()) {
                    out.println(i18n.getString("main.warn.wsl.specified.found.cygwin"));
                }
                useWindowsSubsystemForLinux = true;
            }
        },

        new Option(NONE, MAIN, "wsl-cygwin", "-cygwin") {
            @Override
            public void process(String opt, String arg) throws BadArgs {
                if (!isWindows()) {
                    throw new BadArgs(i18n, "main.windowsOnly", opt);
                }
                if (isWindowsSubsystemForLinuxDetected()) {
                    out.println(i18n.getString("main.warn.cygwin.specified.found.wsl"));
                }
                useWindowsSubsystemForLinux = false;
            }
        },

        new Option(NONE, SELECT, "a-m", "-a", "-automatic", "-automagic") {
            @Override
            public void process(String opt, String arg) {
                extraKeywordExpr = combineKeywords(extraKeywordExpr, AUTOMATIC);
            }
        },

        new Option(NONE, SELECT, "a-m", "-m", "-manual") {
            @Override
            public void process(String opt, String arg) {
                extraKeywordExpr = combineKeywords(extraKeywordExpr, MANUAL);
            }
        },

        new Option(NONE, SELECT, "shell-noshell", "-shell") {
            @Override
            public void process(String opt, String arg) {
                extraKeywordExpr = combineKeywords(extraKeywordExpr, "shell");
            }
        },

        new Option(NONE, SELECT, "shell-noshell", "-noshell") {
            @Override
            public void process(String opt, String arg) {
                extraKeywordExpr = combineKeywords(extraKeywordExpr, "!shell");
            }
        },

        new Option(STD, SELECT, null, "-bug") {
            @Override
            public void process(String opt, String arg) {
                extraKeywordExpr = combineKeywords(extraKeywordExpr, "bug" + arg);
            }
        },

        new Option(STD, SELECT, null, "-k", "-keywords") {
            @Override
            public void process(String opt, String arg) {
                userKeywordExpr = combineKeywords(userKeywordExpr, '(' + arg + ')');
            }
        },

        new Option(NONE, MODE, "avm-ovm", "-ovm", "-othervm") {
            @Override
            public void process(String opt, String arg) {
                execMode = ExecMode.OTHERVM;
            }
        },

        new Option(NONE, MODE, "avm-ovm", "-avm", "-agentvm", "-s", "-svm", "-samevm") {
            @Override
            public void process(String opt, String arg) {
                execMode = ExecMode.AGENTVM;
            }
        },

        new Option(OLD, JDK, "", "-jdk", "-testjdk") {
            @Override
            public void process(String opt, String arg) {
                testJDK = com.sun.javatest.regtest.config.JDK.of(arg);
            }
        },

        new Option(OLD, JDK, "", "-compilejdk") {
            @Override
            public void process(String opt, String arg) {
                compileJDK = com.sun.javatest.regtest.config.JDK.of(arg);
            }
        },

        new Option(STD, JDK, "", "-cpa", "-classpathappend") {
            @Override
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                for (String f: arg.split(File.pathSeparator)) {
                    if (f.length() == 0)
                        continue;
                    classPathAppendArg.add(Path.of(f));
                }
            }
        },

        new Option(NONE, JDK, "jit-nojit", "-jit") {
            @Override
            public void process(String opt, String arg) {
                jitFlag = true;
            }
        },

        new Option(NONE, JDK, "jit-nojit", "-nojit") {
            @Override
            public void process(String opt, String arg) {
                jitFlag = false;
            }
        },

        new Option(NONE, JDK, null, "-classic", "-green", "-native", "-hotspot", "-client", "-server", "-d32", "-d64") {
            @Override
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(OPT, JDK, null, "-enableassertions", "-ea", "-disableassertions", "-da") {
            @Override
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(NONE, JDK, null, "-enablesystemassertions", "-esa", "-disablesystemassertions", "-dsa") {
            @Override
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(GNU, JDK, null, "--add-modules") {
            @Override
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
                testVMOpts.add(arg);
            }
        },

        new Option(GNU, JDK, null, "--limit-modules") {
            @Override
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
                testVMOpts.add(arg);
            }
        },

        new Option(WILDCARD, JDK, null, "-XX", "-Xms", "-Xmx") {
            @Override
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(WILDCARD, JDK, null, "-Xint", "-Xmixed", "-Xcomp") {
            @Override
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(STD, JDK, null, "-Xbootclasspath") {
            @Override
            public void process(String opt, String arg) {
                testVMOpts.add("-Xbootclasspath:" + filesToAbsolutePath(pathToFiles(arg)));
            }
        },

        new Option(STD, JDK, null, "-Xbootclasspath/a") {
            @Override
            public void process(String opt, String arg) {
                testVMOpts.add("-Xbootclasspath/a:" + filesToAbsolutePath(pathToFiles(arg)));
            }
        },

        new Option(STD, JDK, null, "-Xbootclasspath/p") {
            @Override
            public void process(String opt, String arg) {
                testVMOpts.add("-Xbootclasspath/p:" + filesToAbsolutePath(pathToFiles(arg)));
            }
        },

        new Option(GNU, JDK, null, "--patch-module") {
            @Override
            public void process(String opt, String arg) {
                // new style:  --patch-module <module>=<path>
                int eq = arg.indexOf("=");
                testVMOpts.add("--patch-module");
                testVMOpts.add(arg.substring(0, eq + 1)
                        + filesToAbsolutePath(pathToFiles(arg.substring(eq + 1))));
            }
        },

        new Option(WILDCARD, JDK, null, "-X") {
            @Override
            public void process(String opt, String arg) {
                // This is a change in spec. Previously. -X was used to tunnel
                // options to jtreg, with the only supported value being -Xexclude.
                // Now, -exclude is supported, and so we treat -X* as a VM option.
                testVMOpts.add(opt);
            }
        },

        new Option(WILDCARD, JDK, null, "-D") {
            @Override
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(STD, JDK, null, "-vmoption") {
            @Override
            public void process(String opt, String arg) {
                if (arg.length() > 0)
                    testVMOpts.add(arg);
            }
        },

        new Option(STD, JDK, null, "-vmoptions") {
            @Override
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                testVMOpts.addAll(List.of(arg.split("\\s+")));
            }
        },

        new Option(STD, JDK, null, "-agentlib") {
            @Override
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(STD, JDK, null, "-agentpath") {
            @Override
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(STD, JDK, null, "-javaagent") {
            @Override
            public void process(String opt, String arg) {
                testVMOpts.add(opt);
            }
        },

        new Option(STD, JDK, null, "-javacoption") {
            @Override
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                testCompilerOpts.add(arg);
            }
        },

        new Option(STD, JDK, null, "-javacoptions") {
            @Override
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                testCompilerOpts.addAll(List.of(arg.split("\\s+")));
            }
        },

        new Option(STD, JDK, null, "-javaoption") {
            @Override
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                testJavaOpts.add(arg);
            }
        },

        new Option(STD, JDK, null, "-javaoptions") {
            @Override
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                testJavaOpts.addAll(List.of(arg.split("\\s+")));
            }
        },

        new Option(STD, JDK, null, "-debug") {
            @Override
            public void process(String opt, String arg) {
                arg = arg.trim();
                if (arg.length() == 0)
                    return;
                testDebugOpts.addAll(List.of(arg.split("\\s+")));
            }
        },

        new Option(REST, DOC, "help", "--help", "-h", "-help", "-usage") {
            @Override
            public void process(String opt, String arg) {
                help.setCommandLineHelpQuery(arg);
            }
        },

        new Option(NONE, DOC, "help", "-version") {
            @Override
            public void process(String opt, String arg) {
                help.setVersionFlag(true);
            }
        },

        new Option(FILE, MAIN, null) {
            @Override
            public void process(String opt, String arg) throws BadArgs {
                if (TestManager.GroupSpec.isGroupSpec(arg)) {
                    testGroupSpecArgs.add(TestManager.GroupSpec.of(arg));
                } else if (TestManager.TestSpec.isTestSpec(arg)) {
                    testSpecArgs.add(TestManager.TestSpec.of(arg));
                } else {
                    throw new BadArgs(i18n, "main.badTestOrGroup", arg);
                }
            }
        }
    );

    //---------- Command line invocation support -------------------------------

    public Tool() {
        this(new PrintWriter(System.out, true), new PrintWriter(System.err, true));
    }

    public Tool(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;

        javatest_jar = JarManager.forClass(Harness.class);

        jtreg_jar = JarManager.forClass(getClass());

        help = new Help(options);
        if (javatest_jar != null) {
            help.addVersionHelper(o -> {
                try (JarFile jf = new JarFile(javatest_jar.toFile())) {
                    JarEntry e = jf.getJarEntry("META-INF/buildInfo.txt");
                    if (e != null) {
                        try (InputStream in = jf.getInputStream(e)) {
                            Properties p = new Properties();
                            p.load(in);
                            String v = p.getProperty("version");
                            String s = "JT Harness" + ", version " + v
                                    + " " + p.getProperty("milestone")
                                    + " " + p.getProperty("build")
                                    + " (" + p.getProperty("date") + ")";
                            o.println(s);
                        }
                    }
                } catch (IOException e) {
                }
            });
        }
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
    public final int run(String[] args) throws
            BadArgs, Main.Fault, Harness.Fault, InterruptedException {
        if (args.length > 0) {
            expandedArgs = expandAtFiles(args);
            new OptionDecoder(options).decodeArgs(expandedArgs);
        } else {
            help = new Help(options);
            help.setCommandLineHelpQuery(null);
        }
        return run();
    }

    public int run() throws BadArgs, Fault, Harness.Fault, InterruptedException {
        findSystemJarFiles();

        if (help.isEnabled()) {
            guiFlag = help.show(out);
            return EXIT_OK;
        }

        if (userKeywordExpr != null) {
            userKeywordExpr = userKeywordExpr.replace("-", "_");
            try {
                Keywords.create(Keywords.EXPR, userKeywordExpr);
            } catch (Keywords.Fault e) {
                throw new Fault(i18n, "main.badKeywords", e.getMessage());
            }
        }

        Path baseDir;
        if (baseDirArg == null) {
            baseDir = Path.of(System.getProperty("user.dir"));
        } else {
            if (!Files.exists(baseDirArg))
                throw new Fault(i18n, "main.cantFindFile", baseDirArg);
            baseDir = baseDirArg.toAbsolutePath();
        }

        // support for -show:stream file.jtr
        var jtrFiles = testSpecArgs.stream()
                .filter(s -> s.file.getFileName().toString().endsWith(".jtr"))
                .collect(Collectors.toList());
        if (!jtrFiles.isEmpty()) {
            if (jtrFiles.size() < testSpecArgs.size() || !testGroupSpecArgs.isEmpty()) {
                throw new Fault(i18n, "main.invalidCombinationTestArgs");
            } else if (jtrFiles.size() > 1) {
                throw new Fault(i18n, "main.moreThanOneTestSpecified");
            } else if (showStream == null) {
                throw new Fault(i18n, "main.noStreamSpecified");
            } else {
                var jtr = jtrFiles.get(0);
                if (jtr.id != null) {
                    throw new Fault(i18n, "main.cannotSpecifyID", jtr);
                }
                if (jtr.query != null) {
                    throw new Fault(i18n, "main.cannotSpecifyQuery", jtr);
                }
                Path p =jtr.file;
                try {
                    var tr = new TestResult(jtr.file.toFile());
                    showStream(tr);
                } catch (TestResult.Fault e) {
                    throw new Fault(i18n, "main.cannotReloadTestResult", p);
                }
            }
            return EXIT_OK;
        }

        final TestManager testManager = new TestManager(out, baseDir, Tool.this::error);
        testManager.addTestSpecs(testSpecArgs);
        testManager.addGroupSpecs(testGroupSpecArgs);

        if (testManager.isEmpty())
            throw new TestManager.NoTests();

        boolean multiRun = testManager.isMultiRun();

        for (RegressionTestSuite ts: testManager.getTestSuites()) {
            Version requiredVersion = ts.getRequiredVersion();
            Version currentVersion = Version.getCurrent();
            if (requiredVersion.compareTo(currentVersion) > 0) {
                throw new Fault(i18n, "main.requiredVersion",
                        ts.getPath(),
                        requiredVersion.getVersionBuildString(),
                        currentVersion.getVersionBuildString());
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

        if (testJDK == null) {
            String s = System.getenv("JAVA_HOME");
            if (s == null || s.length() == 0) {
                s = System.getProperty("java.home");
                if (s == null || s.length() == 0)
                    throw new BadArgs(i18n, "main.jdk.not.set");
            }
            File f = new File(s);
            if (compileJDK == null
                    && f.getName().equalsIgnoreCase("jre")
                    && f.getParentFile() != null)
                f = f.getParentFile();
            testJDK = com.sun.javatest.regtest.config.JDK.of(f.toPath());
        }

        JDK_Version testJDK_version = checkJDK(testJDK);

        if (compileJDK != null) {
            JDK_Version compileJDK_version = checkJDK(compileJDK);
            if (!compileJDK_version.equals(testJDK_version)) {
                out.println("Warning: compileJDK has a different version ("
                        + compileJDK_version + ") from testJDK ("
                        + testJDK_version + ")");
            }
        }

        if (isWindows()) {
            if (useWindowsSubsystemForLinux == null) {
                // The test for Cygwin is more specific than the test for WSL,
                // and so we give priority to Cygwin if both are detected.
                useWindowsSubsystemForLinux = !isCygwinDetected() && isWindowsSubsystemForLinuxDetected();
            }
        } else {
            useWindowsSubsystemForLinux = false;
        }

        if (!jitFlag) {
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

        if (reportMode == null) {
            reportMode = ReportMode.ALL_EXECUTED;
        }

        if (workDirArg == null) {
            workDirArg = Path.of("JTwork");
        }

        if (reportDirArg == null && reportMode != ReportMode.NONE) {
            reportDirArg = Path.of("JTreport");
        }

        makeDir(workDirArg, false);
        testManager.setWorkDirectory(workDirArg);

        // register a factory to be used to create the parameters for a test suite,
        // such that all the appropriate command-line args are taken into account
        RegressionTestSuite.setParametersFactory(ts -> {
            try {
                return createParameters(testManager, ts);
            } catch (BadArgs
                     | Fault ex) {
                throw new TestSuite.Fault(i18n, "main.cantCreateParameters", ex.getMessage());
            }
        });

        if (showGroupsFlag) {
            showGroups(testManager);
            return EXIT_OK;
        }

        if (verifyExcludeListsFlag) {
            verifyExcludeLists(testManager, baseDir);
        }

        if (listTestsFlag) {
            listTests(testManager);
            return EXIT_OK;
        }

        makeDir(workDirArg.resolve("scratch"), true);

        if (reportMode != ReportMode.NONE) {
            makeDir(reportDirArg, false);
            testManager.setReportDirectory(reportDirArg);

            if (expandedArgs != null) {
                Path reportTextDir = reportDirArg.resolve("text");
                makeDir(reportTextDir, true);
                Path cmdArgsFile = reportTextDir.resolve("cmdArgs.txt");
                // update to use try-with-resources and lambda
                try (BufferedWriter cmdArgsWriter = Files.newBufferedWriter(cmdArgsFile)) {
                    for (String arg: expandedArgs) {
                        cmdArgsWriter.append(arg);
                        cmdArgsWriter.newLine();
                    }
                } catch (IOException e) {
                    System.err.println("Error writing " + cmdArgsFile + ": " + e);
                }
            }
        }

        Harness.setClassDir(ProductInfo.getJavaTestClassDir());

        // Allow keywords to begin with a numeric
        Keywords.setAllowNumericKeywords(RegressionKeywords.allowNumericKeywords);

        if (httpdFlag)
            startHttpServer();

        if (multiRun && guiFlag)
            throw new Fault(i18n, "main.onlyOneTestSuiteInGuiMode");

        testStats = new TestStats();
        boolean foundEmptyGroup = false;

        for (RegressionTestSuite ts: testManager.getTestSuites()) {

            if (multiRun && (verbose != null && verbose.multiRun))
                out.println("Running tests in " + ts.getRootDir());

            RegressionParameters params = createParameters(testManager, ts);
            String[] tests = params.getTests();
            if (tests != null && tests.length == 0)
                foundEmptyGroup = true;

            checkLockFiles(params.getWorkDirectory().getRoot(), "start");

            switch (execMode) {
                case AGENTVM:
                    Agent.Pool p = Agent.Pool.instance(params);
                    if (allowSetSecurityManagerFlag) {
                        initPolicyFile();
                        p.setSecurityPolicy(policyFile.toFile());
                    }
                    if (timeoutFactorArg != null) {
                        p.setTimeoutFactor(timeoutFactorArg);
                    }
                    if (maxPoolSize == -1) {
                        // The default max pool size depends on the concurrency
                        // and whether there are additional VM options to be set
                        // when executing tests, as compared to when compiling tests.
                        // Also, the classpath for compile actions is typically
                        // different for compile actions and main actions.
                        int factor = 2; // (testJavaOpts.isEmpty() ? 1 : 2);
                        maxPoolSize = params.getConcurrency() * factor;
                    }
                    p.setMaxPoolSize(maxPoolSize);
                    p.setIdleTimeout(poolIdleTimeout);
                    p.setNumAgentSelectionAttempts(numAgentSelectionAttempt);
                    break;
                case OTHERVM:
                    break;
                default:
                    throw new AssertionError();
            }

            // Before we install our own security manager (which will restrict access
            // to the system properties), take a copy of the system properties.
            TestEnvironment.addDefaultPropTable("(system properties)", System.getProperties());

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
            if (verbose != null && verbose.multiRun)
                out.println();
        }

        if (multiRun) {
            if (verbose != null && verbose.multiRun) {
                out.println("Overall summary:");
            }
            testStats.showResultStats(out);
            if (reportMode != ReportMode.NONE) {
                RegressionReporter r = new RegressionReporter(out);
                r.report(testManager);
            }
            if (!reportOnlyFlag) {
                out.println("Results written to " + canon(workDirArg.toFile()));
            }
        }

        return (testStats.counts[Status.ERROR] > 0 ? EXIT_TEST_ERROR
                : testStats.counts[Status.FAILED] > 0 ? EXIT_TEST_FAILED
                : testStats.counts[Status.PASSED] == 0 && !foundEmptyGroup ? EXIT_NO_TESTS
                : errors != 0 ? EXIT_FAULT
                : EXIT_OK);
    }

    void verifyExcludeLists(TestManager testManager, Path baseDir) throws BadArgs, Fault, Harness.Fault, InterruptedException  {
        // dummy manager with an "all" spec for each suite
        TestManager dummyTestManager = new TestManager(new PrintWriter(System.out, true), baseDir, Tool.this::error);
        dummyTestManager.setWorkDirectory(testManager.getWorkDirectory()); // Without this, nullpointer happens in createparameters
        dummyTestManager.addTestSpecs(testManager.getTestSuites().stream()
            .map(ts -> TestManager.TestSpec.of(ts.getRootDir().toString()))
            .collect(Collectors.toList()));

        List<String> validTestNames = new ArrayList<String>();
        for (RegressionTestSuite ts: dummyTestManager.getTestSuites()) {
            if (verbose != null)  {
                out.println(i18n.getString("main.tests.suite", ts.getRootDir()));
                if (verbose == Verbose.ALL)  {
                    out.println(i18n.getString("main.tests.allrecognizedtests"));
                }
            }

            RegressionParameters params = createParameters(dummyTestManager, ts);
            for (Iterator<TestResult> iter = getEagerResultsIterator(params); iter.hasNext(); ) {
                TestResult tr = iter.next();
                validTestNames.add(tr.getTestName());
                if (verbose == Verbose.ALL)  {
                    out.println(tr.getTestName());
                }
            }
        }

        List<File> excludeOrMatchFiles = new ArrayList<File>();
        for (RegressionTestSuite ts: testManager.getTestSuites()) {
            RegressionParameters params = createParameters(testManager, ts);
            for (File f : params.getExcludeLists()) {
                excludeOrMatchFiles.add(f);
            }
            for (Path p : params.getMatchLists()) {
                excludeOrMatchFiles.add(p.toFile());
            }
        }

        boolean hadErrors = false;
        for(File plf : excludeOrMatchFiles) {
            ExcludeFileVerifier efv = new ExcludeFileVerifier(out);
            efv.verify(plf, validTestNames);
            hadErrors |= efv.getHadErrors();
        }

        if (hadErrors) {
            error("Cannot run because an exclude list had errors, printed above. Either resolve them or remove the exclude list.");
        }
    }

    JDK_Version checkJDK(JDK jdk) throws Fault {
        if (!jdk.exists())
            throw new Fault(i18n, "main.jdk.not.found", jdk);

        // Check that we're not trying to use a Linux JDK when running on a Windows JDK,
        // and vice versa.
        JDK jtregJDK = com.sun.javatest.regtest.config.JDK.of(System.getProperty("java.home"));
        Path jtregJava = jtregJDK.getProg("java", true);
        Path jdkJava = jdk.getProg("java", true);
        if (!jdkJava.getFileName().equals(jtregJava.getFileName())) {
            throw new Fault(i18n, "main.incompatibleJDK", jdk, jtregJDK);
        }

        JDK_Version v = jdk.getJDKVersion(new SearchPath(jtreg_jar, javatest_jar), out::println);
        if (v == null)
            throw new Fault(i18n, "main.jdk.unknown.version", jdk);
        if (v.compareTo(JDK_Version.V1_1) <= 0)
            throw new Fault(i18n, "main.jdk.unsupported.version", jdk, v.name());
        return v;
    }

    /**
     * Show the expansion (to files and directories) of the groups given
     * for the test suites on the command line.  If a test suite is given
     * without qualifying with a group, all groups in that test suite are
     * shown.
     * No filters (like keywords, status, etc.) are taken into account.
     */
    void showGroups(TestManager testManager) throws Fault {
        for (RegressionTestSuite ts : testManager.getTestSuites()) {
            out.println(i18n.getString("main.tests.suite", ts.getRootDir()));
            try {
                Set<String> selected = testManager.getGroups(ts);
                GroupManager gm = ts.getGroupManager(out);
                Set<String> gset = new TreeSet<>(new NaturalComparator(false));
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
                        try {
                            Set<Path> files = gm.getFiles(g);
                            out.print(g);
                            out.print(":");
                            Set<String> fset = new TreeSet<>(new NaturalComparator(false));
                            for (Path f : files)
                                fset.add(ts.getRootDir().toURI().relativize(f.toUri()).getPath());
                            for (String f: fset) {
                                out.print(" ");
                                out.print(f);
                            }
                            out.println();
                        } catch (GroupManager.InvalidGroup ex) {
                            out.println(i18n.getString("tm.invalidGroup", g));
                        }
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
    private static List<String> expandAtFiles(String[] args) throws Fault {
        List<String> newArgs = new ArrayList<>();
        for (String arg : args) {
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
        return newArgs;
    }

    private static void loadCmdFile(String name, List<String> args) throws Fault {
        Reader r;
        try {
            r = new BufferedReader(new FileReader(name));
        } catch (FileNotFoundException e) {
            throw new Fault(i18n, "main.cantOpenFile", name);
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

    public int[] getTestStats() {
        return testStats.counts;
    }

    void findSystemJarFiles() throws Fault {
        if (javatest_jar == null) {
            // evaluated in constructor
            throw new Fault(i18n, "main.cantFind.javatest.jar");
        }

        if (jtreg_jar == null) {
            // evaluated in constructor
            throw new Fault(i18n, "main.cantFind.jtreg.jar");
        }

        Path libDir = jtreg_jar.getParent();

        JarManager jarManager = new JarManager(libDir);

        asmtoolsPath = jarManager.getPath("asmtools");
        help.addVersionHelper(out -> {
            for (Path f : asmtoolsPath.asList()) {
                try (JarFile jf = new JarFile(f.toFile())) {
                    JarEntry e = jf.getJarEntry("org/openjdk/asmtools/util/productinfo.properties");
                    if (e != null) {
                        try (InputStream in = jf.getInputStream(e)) {
                            Properties p = new Properties();
                            p.load(in);
                            String v = p.getProperty("PRODUCT_VERSION");
                            String s = p.getProperty("PRODUCT_NAME_LONG") + ", version " + v
                                    + " " + p.getProperty("PRODUCT_MILESTONE")
                                    + " " + p.getProperty("PRODUCT_BUILDNUMBER")
                                    + " (" + p.getProperty("PRODUCT_DATE") + ")";
                            out.println(s);
                        }
                    }
                } catch (IOException e) {
                }
            }
        });

        testngPath = jarManager.getPath("testng");
        help.addPathVersionHelper("TestNG", testngPath);

        junitPath = jarManager.getPath("junit");
        help.addPathVersionHelper("JUnit", junitPath);
    }

    void initPolicyFile() throws Fault {
        // Write a policy file into the work directory granting all permissions
        // to jtreg.
        // Note: don't use scratch directory, which is cleared before tests run
        Path pfile = workDirArg.resolve("jtreg.policy");
        try (BufferedWriter pout = Files.newBufferedWriter(pfile)) {
            String LINESEP = System.getProperty("line.separator");
            for (Path f: List.of(jtreg_jar, javatest_jar)) {
                pout.write("grant codebase \"" + f.toUri().toURL() + "\" {" + LINESEP);
                pout.write("    permission java.security.AllPermission;" + LINESEP);
                pout.write("};" + LINESEP);
            }
        } catch (IOException e) {
            throw new Fault(i18n, "main.cantWritePolicyFile", e);
        }
        policyFile = pfile;
    }

    private void makeDir(Path dir, boolean quiet) throws Fault {
        // FIXME: I18N
        if (Files.isDirectory(dir))
            return;
        if (!quiet)
            out.println("Directory \"" + dir + "\" not found: creating");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            Fault f = new Fault(i18n, "main.cantCreateDir", dir);
            f.initCause(e);
            throw f;
        }
    }

    private static List<Path> pathToFiles(String path) {
        List<Path> files = new ArrayList<>();
        for (String f: path.split(File.pathSeparator)) {
            if (f.length() > 0)
                files.add(Path.of(f));
        }
        return files;
    }

    private static SearchPath filesToAbsolutePath(List<Path> files) {
        SearchPath p = new SearchPath();
        for (Path f: files) {
            p.append(getNormalizedFile(f.toFile()).toPath());
        }
        return p;
    }

    /**
     * Create a RegressionParameters object based on the values set up by decodeArgs.
     * This method is the standard way to create the parameters, taking all the
     * command-line arguments into account. It is used as the body of a factory
     * callback, used by {@link RegressionTestSuite#createInterview}.
     * @return a RegressionParameters object
     */
    private RegressionParameters createParameters(
            TestManager testManager, RegressionTestSuite testSuite)
            throws BadArgs, Fault
    {
        try {
            RegressionParameters rp = new RegressionParameters("regtest", testSuite, out::println);

            WorkDirectory workDir = testManager.getWorkDirectory(testSuite);
            rp.setWorkDirectory(workDir);

            // JT Harness 4.3+ requires a config file to be set
            rp.setFile(workDir.getFile("config.jti"));

            rp.setRetainArgs(retainArgs);

            // the tests are the tests to be executed by the harness, and do not
            // include the "query" component
            // 'null' means "all tests"
            rp.setTests(testManager.getTests(testSuite));

            // the tests that have an associated query component, included in
            // the string
            List<String> testQueries = testManager.getTestQueries(testSuite);
            if (!testQueries.isEmpty()) {
                rp.setTestQueries(testQueries);
            }

            if (userKeywordExpr != null || extraKeywordExpr != null) {
                String expr =
                        (userKeywordExpr == null) ? extraKeywordExpr
                        : (extraKeywordExpr == null) ? userKeywordExpr
                        : "(" + userKeywordExpr + ") & " + extraKeywordExpr;
                rp.setKeywordsExpr(expr);
            }

            rp.setExcludeLists(excludeListArgs.toArray(new Path[0]));
            rp.setMatchLists(matchListArgs.toArray(new Path[0]));

            if (priorStatusValuesArg == null || priorStatusValuesArg.length() == 0)
                rp.setPriorStatusValues(null);
            else {
                boolean[] b = new boolean[Status.NUM_STATES];
                b[Status.PASSED]  = priorStatusValuesArg.contains("pass");
                b[Status.FAILED]  = priorStatusValuesArg.contains("fail");
                b[Status.ERROR]   = priorStatusValuesArg.contains("erro");
                b[Status.NOT_RUN] = priorStatusValuesArg.contains("notr");
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
                    rp.setTimeoutFactor(timeoutFactorArg);
                } catch (NumberFormatException e) {
                    throw new BadArgs(i18n, "main.badTimeoutFactor");
                }
            }

            if (timeoutHandlerClassName != null) {
                rp.setTimeoutHandler(timeoutHandlerClassName);
            }

            if (timeoutHandlerPathArg != null) {
                rp.setTimeoutHandlerPath(timeoutHandlerPathArg);
            }

            if (timeoutHandlerTimeoutArg != 0) {
                rp.setTimeoutHandlerTimeout(timeoutHandlerTimeoutArg);
            }

            if (testThreadFactory != null) {
                rp.setTestThreadFactory(testThreadFactory);
            }

            if (testThreadFactoryPathArg != null) {
                rp.setTestThreadFactoryPath(testThreadFactoryPathArg);
            }

            Path rd = testManager.getReportDirectory(testSuite);
            if (rd != null)
                rp.setReportDir(rd);

            if (exclusiveLockArg != null)
                rp.setExclusiveLock(exclusiveLockArg);

            if (!rp.isValid())
                throw new Fault(i18n, "main.badParams", rp.getErrorMessage());

            if (testVMOpts.size() > 0)
                rp.setTestVMOptions(testVMOpts);

            if (testCompilerOpts.size() > 0)
                rp.setTestCompilerOptions(testCompilerOpts);

            if (testJavaOpts.size() > 0)
                rp.setTestJavaOptions(testJavaOpts);

            if (testDebugOpts.size() > 0)
                rp.setTestDebugOptions(testDebugOpts);

            rp.setCheck(checkFlag);
            rp.setExecMode(execMode);
            rp.setEnvVars(getEnvVars());
            rp.setCompileJDK((compileJDK != null) ? compileJDK : testJDK);
            rp.setTestJDK(testJDK);
            if (ignoreKind != null)
                rp.setIgnoreKind(ignoreKind);

            if (junitPath != null)
                rp.setJUnitPath(junitPath);

            if (testngPath != null)
                rp.setTestNGPath(testngPath);

            if (asmtoolsPath != null)
                rp.setAsmToolsPath(asmtoolsPath);

            if (timeLimitArg != null) {
                try {
                    rp.setTimeLimit(Integer.parseInt(timeLimitArg));
                } catch (NumberFormatException e) {
                    throw new BadArgs(i18n, "main.badTimeLimit");
                }
            }

            if (nativeDirArg != null)
                rp.setNativeDir(nativeDirArg);

            rp.setUseWindowsSubsystemForLinux(useWindowsSubsystemForLinux);

            rp.setVerbose(verbose);

            rp.initExprContext(); // will invoke/init jdk.getProperties(params)

            return rp;
        } catch (Interview.Fault f) {
            // TODO: fix bad string -- need more helpful resource here
            throw new Fault(i18n, "main.cantOpenTestSuite", testSuite.getRootDir(), f);
        } catch (JDK.Fault f) {
            throw new Fault(i18n, "main.cantGetJDKProperties", testJDK, f.getMessage());
        }
    }

    private static File canon(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return getNormalizedFile(file);
        }
    }

    private static Harness.Observer getObserver(List<Path> observerPath, String observerClassName)
            throws Fault {
        try {
            Class<?> observerClass;
            if (observerPath == null)
                observerClass = Class.forName(observerClassName);
            else {
                URL[] urls = new URL[observerPath.size()];
                int u = 0;
                for (Path f: observerPath) {
                    try {
                        urls[u++] = f.toUri().toURL();
                    } catch (MalformedURLException ignore) {
                    }
                }
                ClassLoader loader = new URLClassLoader(urls);
                observerClass = loader.loadClass(observerClassName);
            }
            return observerClass.asSubclass(Harness.Observer.class).getDeclaredConstructor().newInstance();
        } catch (ClassCastException e) {
            throw new Fault(i18n, "main.obsvrType",
                    Harness.Observer.class.getName(), observerClassName);
        } catch (ClassNotFoundException e) {
            throw new Fault(i18n, "main.obsvrNotFound", observerClassName);
        } catch (ReflectiveOperationException e) {
            throw new Fault(i18n, "main.obsvrFault", e);
        }
    }

    /**
     * Run the harness in batch mode, using the specified parameters.
     */
    private TestStats batchHarness(RegressionParameters params, boolean quiet)
            throws Fault, Harness.Fault, InterruptedException {
        boolean reportRequired =
                reportMode != ReportMode.NONE && !Boolean.getBoolean("javatest.noReportRequired");

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

//                System.err.println("TEST: " + tr.getFile());
//                System.err.println("TEST: " + params.getWorkDirectory().getFile(tr.getWorkRelativePath()));
//                System.err.println("TEST: " + params.getWorkDirectory().getFile(tr.getWorkRelativePath()).exists());

                // The following is a workaround: sometimes the TestResult object
                // incorrectly appears to be not run, because the resultFile is null.
                if (tr != null
                        && tr.getFile() == null
                        && params.getWorkDirectory().getFile(tr.getWorkRelativePath()).exists()) {
                    try {
                        tr = new TestResult(params.getWorkDirectory(), tr.getWorkRelativePath());
                    } catch (TestResult.Fault f) {
                        out.println("Cannot reload results for test " + tr.getTestName());
                    }
                }

                if (tr == null) {
                    out.println("No test specified");
                    ok = false;
                } else {
                    ok = showStream(tr);
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
                        Tool.this.error(msg);
                    }
                });

                if (reportRequired) {
                    elapsedTimeHandler = new ElapsedTimeHandler();
                    elapsedTimeHandler.register(h);
                }

                if (params.getTestJDK().hasModules()) {
                    initModuleHelper(params.getWorkDirectory());
                }

                File versionFile = params.getWorkDirectory().getSystemFile("jtreg.version");
                try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(versionFile)))) {
                    help.showVersion(out);
                } catch (IOException e) {
                    err.println(i18n.getString("main.errorReportingVersion", e));
                }

                String[] tests = params.getTests();
                ok = (tests != null && tests.length == 0) || h.batch(params);

                Agent.Pool.flush(params);
                try {
                    Agent.Logger.close(params);
                } catch (IOException e) {
                    err.println(i18n.getString("main.errorClosingAgentLog", e));
                }
                Lock.get(params).close();
            }

            if (!quiet)
                stats.showResultStats(out);

            if (reportRequired) {
                RegressionReporter r = new RegressionReporter(out);
                TestFilter tf;
                if (reportOnlyFlag) {
                    ParameterFilter pf = new ParameterFilter();
                    pf.update(params);
                    tf = pf;
                } else {
                    switch (reportMode) {
                        case NONE:
                        default:
                            throw new IllegalStateException();

                        case EXECUTED: {
                            ParameterFilter pf = new ParameterFilter();
                            pf.update(params);
                            tf = pf;
                            break;
                        }

                        case ALL_EXECUTED:
                            boolean[] statusValues = new boolean[Status.NUM_STATES];
                            statusValues[Status.PASSED] = true;
                            statusValues[Status.FAILED] = true;
                            statusValues[Status.ERROR] = true;
                            statusValues[Status.NOT_RUN] = false;
                            tf = new StatusFilter(statusValues, params.getWorkDirectory().getTestResultTable());
                            break;

                        case ALL:
                            tf = new AllTestsFilter();
                            break;

                        case FILES: {
                            try {
                                RegressionParameters reportParams = new RegressionParameters("regtest", params.getTestSuite(), out::println);
                                reportParams.setWorkDirectory(params.getWorkDirectory());
                                reportParams.setTests(params.getTests());
                                ParameterFilter pf = new ParameterFilter();
                                pf.update(reportParams);
                                tf = pf;
                                break;
                            } catch (Interview.Fault e) {
                                throw new Fault(i18n, "main.cantCreateReportParameters", e);
                            }
                        }
                    }
                }
                r.report(params, elapsedTimeHandler, stats, tf, quiet);
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

    private boolean showStream(TestResult tr) {
        if (tr.getStatus().isNotRun()) {
            out.println("Test has not been run");
            return false;
        }

        try {
            // work around bug CODETOOLS-7900214 -- force the sections to be reloaded
            tr.getProperty("sections");

            boolean allOK = true;

            var allSections = new HashMap<String, Set<String>>();
            var allStreams = new HashSet<String>();
            for (int i = 0; i < tr.getSectionCount(); i++) {
                TestResult.Section s = tr.getSection(i);
                var names = Set.of(s.getOutputNames());
                allSections.put(s.getTitle(), names);
                allStreams.addAll(names);
            }
            String section, outputName;
            int sep = showStream.indexOf("/");
            if (sep == -1) {
                section = null;
                outputName = showStream;
                if (!allStreams.contains(outputName)) {
                    out.println("# no such output stream: " + outputName);
                    allOK = false;
                }
            } else {
                section = showStream.substring(0, sep);
                outputName = showStream.substring(sep + 1);
                var outputNames = allSections.get(section);
                if (outputNames == null) {
                    out.println("# section not found: " + section);
                    allOK = false;
                } else if (!outputNames.contains(outputName)) {
                    out.println("# output stream not found: " + showStream);
                    allOK = false;
                }
            }
            for (int i = 0; i < tr.getSectionCount(); i++) {
                TestResult.Section s = tr.getSection(i);
                if (section == null || section.equals(s.getTitle())) {
                    String text = s.getOutput(outputName);
                    if (text != null) {
                        out.println("### Section " + s.getTitle());
                        text.lines().forEach(out::println);
                    }
                }
            }
            return allOK;
        } catch (TestResult.Fault f) {
            out.println("Cannot reload test results: " + f.getMessage());
            return false;
        }
    }

    private void initModuleHelper(WorkDirectory wd) {
        File patchDir = wd.getFile("patches");
        File javaBaseDir = new File(patchDir, "java.base");
        // can't use class constants because it's a restricted package
        String[] classes = {
            "java/lang/JTRegModuleHelper.class"
        };
        for (String c: classes) {
            File f = new File(javaBaseDir, c);
            if (!f.exists()) {
                // Eventually, this should be updated to use try-with-resources
                // and Files.copy(stream, path)
                InputStream from = null;
                OutputStream to = null;
                try {
                    from = getClass().getClassLoader().getResourceAsStream(c);
                    if (from == null) {
                        out.println("Cannot find class " + c + " to init patch directory");
                        continue;
                    }
                    f.getParentFile().mkdirs();
                    to = new FileOutputStream(f);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = from.read(buf, 0, buf.length)) != -1) {
                        to.write(buf, 0, n);
                    }
                } catch (IOException t) {
                    out.println("Cannot init module patch directory: " + t);
                } finally {
                    try {
                        if (from != null) from.close();
                        if (to != null) to.close();
                    } catch (IOException e) {
                        out.println("Cannot init module patch directory: " + e);
                    }
                }
            }
        }
    }

    private Iterator<TestResult> getResultsIterator(InterviewParameters params) {
        TestResultTable trt = params.getWorkDirectory().getTestResultTable();
        trt.waitUntilReady();

        String[] tests = params.getTests();
        TestFilter[] filters = params.getFilters();
        if (tests == null)
            return trt.getIterator(filters);
        else if (tests.length == 0)
            return Collections.emptyIterator();
        else
            return trt.getIterator(tests, filters);
    }

    // An iterator that applies no filters.
    // The intention is to help discover all "existing" tests.
    private Iterator<TestResult> getEagerResultsIterator(InterviewParameters params) {
        TestResultTable trt = params.getWorkDirectory().getTestResultTable();
        trt.waitUntilReady();
        TestFilter[] filters = null;
        return trt.getIterator(filters);
    }

    private void showTool(final InterviewParameters params) throws BadArgs {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                final Frame startup = showStartup();
                Timer t = new Timer(1000, e -> {
                    showGUI();
                    startup.dispose();
                });
                t.setRepeats(false);
                t.start();
            }

            Frame showStartup() {
                Version v = Version.getCurrent();
                String title = String.format("%s %s %s %s",
                        v.product, v.version, v.milestone, v.build);
                URL logo = getClass().getResource("jtlogo.png");
                String copyright = i18n.getString("help.copyright.txt")
                        .replace("\n", " ");
                return new Startup(title, logo, "", title, copyright).show();
            }

            void showGUI() {
                // build a gui for the tool and run it...
                Desktop d = new Desktop();
                ExecToolManager m = (ExecToolManager) (d.getToolManager(ExecToolManager.class));
                if (m == null) {
                    throw new AssertionError("Cannot find ExecToolManager");
                }
                m.startTool(params);
                d.setVisible(true);
            }
        });
    } // showTool()

    private BackupPolicy createBackupPolicy() {
        return new BackupPolicy() {
            @Override
            public int getNumBackupsToKeep(File file) {
                return numBackupsToKeep;
            }
            @Override
            public boolean isBackupRequired(File file) {
                if (ignoreExtns != null) {
                    for (String ignoreExtn : ignoreExtns) {
                        if (file.getPath().endsWith(ignoreExtn)) {
                            return false;
                        }
                    }
                }
                return true;
            }
            private final int numBackupsToKeep = Integer.getInteger("javatest.backup.count", 5);
            private final String[] ignoreExtns = StringArray.split(System.getProperty("javatest.backup.ignore", ".jtr"));
        };
    }

    private void startHttpServer() {
        // start the http server
        // do this as early as possible, since objects may check
        // HttpdServer.isActive() and decide whether to
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
        Map<String, String> envVars = new TreeMap<>();
        OS os = OS.current();
        if (os.family.equals("windows")) {
            addEnvVars(envVars, DEFAULT_WINDOWS_ENV_VARS);
            // TODO PATH? MKS? Cygwin?
            addEnvVars(envVars, "PATH"); // accept user's path, for now
        } else {
            addEnvVars(envVars, DEFAULT_UNIX_ENV_VARS);
            addEnvVarsByName(envVars, name -> name.startsWith("XDG_"));
            addEnvVars(envVars, "PATH=/bin:/usr/bin:/usr/sbin");
        }
        addEnvVars(envVars, envVarArgs);
        addEnvVarsByName(envVars, name -> name.startsWith("JTREG_"));

        return envVars;
    }

    private void addEnvVars(Map<String, String> table, String list) {
        addEnvVars(table, list.split(","));
    }

    private void addEnvVars(Map<String, String> table, String[] list) {
        addEnvVars(table, List.of(list));
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
                String value = getEnvironmentVariableOrNull(s);
                if (value != null)
                    table.put(s, value);
            } else if (eq > 0) {
                String name = s.substring(0, eq);
                String value = s.substring(eq + 1);
                table.put(name, value);
            }
        }
    }

    // This method streams over a case-sensitive set of entries, returned by System.getenv();
    // Custom name filters can be implemented in a case-insensitive manner.
    private void addEnvVarsByName(Map<String, String> table, Predicate<String> nameFilter) {
        System.getenv().entrySet().stream()
                .filter(e -> nameFilter.test(e.getKey()))
                .forEach(e -> table.put(e.getKey(), e.getValue()));
    }

    // 7903515: Use case-insensitive System.getenv(String) here.
    private static String getEnvironmentVariableOrNull(String name) {
        return System.getenv(name);
    }

    private static String combineKeywords(String kw1, String kw2) {
        return (kw1 == null ? kw2 : kw1 + " & " + kw2);
    }

    private boolean isWindows() {
        return OS.current().family.equals("windows");
    }

    /**
     * Returns whether Cygwin may be available, by examining
     * to see if "LETTER:STUFF\cygwin" is mentioned anywhere in the PATH.
     */
    private boolean isCygwinDetected() {
        if (isWindows()) {
            String PATH = System.getenv("PATH");
            return (PATH != null) && PATH.matches("(?i).*;[a-z]:[^;]*\\\\cygwin.*");
        } else {
            return false;
        }
    }

    /**
     * Returns whether Windows Subsystem for Linux may be available,
     * by examining whether wsl.exe can be found on the path.
     */
    private boolean isWindowsSubsystemForLinuxDetected() {
        if (isWindows()) {
            String PATH = System.getenv("PATH");
            if (PATH != null) {
                for (Path f : new SearchPath(PATH).asList()) {
                    if (Files.exists(f.resolve("wsl.exe"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void error(String msg) {
        err.println(i18n.getString("main.error", msg));
        errors++;
    }

    int errors;

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

    private final PrintWriter out;
    private final PrintWriter err;

    private List<String> expandedArgs;

    // this first group of args are the "standard" JavaTest args
    private Path workDirArg;
    private List<String> retainArgs;
    private List<Path> excludeListArgs = new ArrayList<>();
    private String userKeywordExpr;
    private String extraKeywordExpr;
    private String concurrencyArg;
    private Float timeoutFactorArg;
    private String priorStatusValuesArg;
    private Path reportDirArg;

    // these args are jtreg extras
    public List<TestManager.GroupSpec> testGroupSpecArgs = new ArrayList<>();
    public List<TestManager.TestSpec> testSpecArgs = new ArrayList<>();
    private Path baseDirArg;
    private ExecMode execMode;
    private JDK compileJDK;
    private JDK testJDK;
    private boolean guiFlag;
    private boolean reportOnlyFlag;
    private String showStream;
    public enum ReportMode { NONE, EXECUTED, FILES, ALL_EXECUTED, ALL }
    private ReportMode reportMode;
    private boolean allowSetSecurityManagerFlag = true;
    private static Verbose  verbose;
    private boolean httpdFlag;
    private String timeLimitArg;
    private String observerClassName;
    private List<Path> observerPathArg;
    private String timeoutHandlerClassName;
    private List<Path> timeoutHandlerPathArg;
    private long timeoutHandlerTimeoutArg = -1; // -1: default; 0: no timeout; >0: timeout in seconds
    private String testThreadFactory;
    private String testThreadFactoryPathArg;
    private int maxPoolSize = -1;
    private Duration poolIdleTimeout = Duration.ofSeconds(30);
    // number of attempts to get an agent for an action
    private int numAgentSelectionAttempt = DEFAULT_NUM_AGENT_SEL_ATTEMPT;
    private List<String> testCompilerOpts = new ArrayList<>();
    private List<String> testJavaOpts = new ArrayList<>();
    private List<String> testVMOpts = new ArrayList<>();
    private List<String> testDebugOpts = new ArrayList<>();
    private boolean checkFlag;
    private boolean listTestsFlag;
    private boolean showGroupsFlag;
    private boolean verifyExcludeListsFlag;
    private List<String> envVarArgs = new ArrayList<>();
    private IgnoreKind ignoreKind;
    private List<Path> classPathAppendArg = new ArrayList<>();
    private Path nativeDirArg;
    private Boolean useWindowsSubsystemForLinux;
    private boolean jitFlag = true;
    private Help help;
    private boolean xmlFlag;
    private boolean xmlVerifyFlag;
    private Path exclusiveLockArg;
    private List<Path> matchListArgs = new ArrayList<>();

    private final Path javatest_jar;
    private final Path jtreg_jar;
    private SearchPath junitPath;
    private SearchPath testngPath;
    private SearchPath asmtoolsPath;
    private Path policyFile;

    private TestStats testStats;

    private static final String AUTOMATIC = "!manual";
    private static final String MANUAL    = "manual";

    private static final String[] DEFAULT_UNIX_ENV_VARS = {
            "DBUS_SESSION_BUS_ADDRESS", "DESKTOP_SESSION", "DISPLAY",
            "GDMSESSION", "GNOME_DESKTOP_SESSION_ID", "GNOME_SHELL_SESSION_MODE",
            "HOME",
            "LANG", "LC_ALL", "LC_CTYPE", "LPDEST",
            "PRINTER",
            "TZ",
            "WAYLAND_DISPLAY",
            "XMODIFIERS"
    };

    private static final String[] DEFAULT_WINDOWS_ENV_VARS = {
            "SystemDrive", "SystemRoot",
            "TMP", "TEMP", "TZ",
            "windir"
    };

    // default value for agent selection attempts. we default to 1, which implies
    // by default we don't re-attempt on a failure
    private static final int DEFAULT_NUM_AGENT_SEL_ATTEMPT = 1;

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(Tool.class);
}
