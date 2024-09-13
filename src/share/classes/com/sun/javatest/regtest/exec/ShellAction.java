/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.sun.javatest.Status;
import com.sun.javatest.regtest.TimeoutHandler;
import com.sun.javatest.regtest.config.Locations;
import com.sun.javatest.regtest.config.Modules;
import com.sun.javatest.regtest.config.OS;
import com.sun.javatest.regtest.config.ParseException;
import com.sun.javatest.regtest.util.StringUtils;

import static com.sun.javatest.regtest.RStatus.*;

/**
 * This class implements the "shell" action as described by the JDK tag
 * specification.
 *
 * @see Action
 */
public class ShellAction extends Action
{
    public static final String NAME = "shell";

    /**
     * {@inheritDoc}
     * @return "shell"
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * This method does initial processing of the options and arguments for the
     * action.  Processing is determined by the requirements of run().
     *
     * Verify arguments are of length 0.
     *
     * Verify that the options are valid for the "shell" action and separate the
     * shell filename from the shell arguments.
     *
     * @param opts The options for the action.
     * @param args The arguments for the actions.
     * @param reason Indication of why this action was invoked.
     * @param script The script.
     * @exception  ParseException If the options or arguments are not expected
     *             for the action or are improperly formated.
     */
    @Override
    public void init(Map<String,String> opts, List<String> args, String reason,
                     RegressionScript script)
        throws ParseException
    {
        super.init(opts, args, reason, script);

        if (args.isEmpty())
            throw new ParseException(SHELL_NO_SCRIPT_NAME);

        for (Map.Entry<String,String> e: opts.entrySet()) {
            String optName  = e.getKey();
            String optValue = e.getValue();

            switch (optName) {
                case "fail":
                    reverseStatus = parseFail(optValue);
                    break;
                case "timeout":
                    timeout  = parseTimeout(optValue);
                    break;
                case "manual":
                    manual = parseShellManual(optValue);
                    break;
                default:
                    throw new ParseException(SHELL_BAD_OPTION + optName);
            }
        }

        if (manual.equals("unset")) {
            if (timeout < 0)
                timeout = script.getActionTimeout(-1);
        } else {
            if (timeout >= 0)
                // can't have both timeout and manual
                throw new ParseException(PARSE_TIMEOUT_MANUAL);
            timeout = 0;
        }

        // the first argument is the name of the shell script, the rest are
        // arguments to the shell script
        shellFN = args.get(0);
        //shellArgs = "";
//        shellArgs = new ArrayList();
//      for (int i = 1; i < args.length; i++) {
//          //shellArgs += " " + args[i];
//          shellArgs.add(args[i]);
//      }
        // support simple unescaped ' characters,
        // as in: @run shell test.sh abc 'def ghi jkl' mno
        shellArgs = new ArrayList<>();
        StringBuilder curr = null;
        for (int i = 1; i < args.size(); i++) {
            if (curr == null)
                curr = new StringBuilder(args.get(i));
            else
                curr.append(" ").append(args.get(i));
            if (isEvenQuotes(curr)) {
                shellArgs.add(curr.toString().replace("'", ""));
                curr = null;
            }
        }
        if (curr != null)
            shellArgs.add(curr.toString());
    } // init()
    // where
        private static boolean isEvenQuotes(StringBuilder s) {
            int n = 0;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == '\'')
                    n++;
            }
            return (n % 2 == 0);
        }

    @Override
    public Set<File> getSourceFiles() {
        return Set.of(script.absTestSrcDir().resolve(shellFN).toFile());
    }

    @Override
    protected boolean supportsExclusiveAccess() {
        return true;
    }

    /**
     * The method that does the work of the action.  The necessary work for the
     * given action is defined by the tag specification.
     *
     * Invoke the Bourne shell to run the shell filename with the provided
     * arguments.  The shell filename is fully qualified as necessary and all
     * environment variables are set according to the tag specification.
     *
     * A "shell" action passes if the script exits with an exit code of 0.  It
     * fails otherwise.
     *
     * Note that this action inherently assumes that the JVM supports multiple
     * processes.
     *
     * @return     The result of the action.
     * @exception  TestRunException If an unexpected error occurs while running
     *             the test.
     */
    @Override
    public Status run() throws TestRunException {
        Status status;

        startAction(false);

        File shellFile = script.absTestSrcDir().resolve(shellFN).toFile();
        if (!shellFile.exists())
            throw new TestRunException(CANT_FIND_SRC + shellFile);

        // If we're only running checks on the contents of the test description
        // and we got this far, we can just set a successful status. Everything
        // after this point is preparation to run the actual test.
        if (script.isCheck()) {
            status = passed(CHECK_PASS);
        } else {
            mkdirs(script.absTestClsDir().toFile());

            // CONSTRUCT THE COMMAND LINE

            // TAG-SPEC:  "The source, class, and Java home directories are made
            // available to shell-action scripts via the environment variables
            // TESTSRC, TESTCLASSES, and TESTJAVA."
            Map<String, String> env = new LinkedHashMap<>();
            env.putAll(getEnvVars(true));
            Locations locations = script.locations;
            env.put("TESTFILE", fixupSep(locations.absTestFile()));
            env.put("TESTSRC", fixupSep(locations.absTestSrcDir()));
            env.put("TESTSRCPATH", fixupSep(locations.absTestSrcPath()));
            env.put("TESTCLASSES" , fixupSep(locations.absTestClsDir()));
            env.put("TESTCLASSPATH", fixupSep(locations.absTestClsPath()));
            env.put("COMPILEJAVA", fixupSep(script.getCompileJDK().getAbsolutePath()));
            env.put("TESTJAVA", fixupSep(script.getTestJDK().getAbsolutePath()));
            List<String> vmOpts = script.getTestVMOptions();
            env.put("TESTVMOPTS", fixupSep(StringUtils.join(vmOpts, " ")));
            List<String> toolVMOpts = script.getTestToolVMOptions();
            env.put("TESTTOOLVMOPTS", fixupSep(StringUtils.join(toolVMOpts, " ")));
            List<String> compilerOpts = script.getTestCompilerOptions();
            env.put("TESTJAVACOPTS", fixupSep(StringUtils.join(compilerOpts, " ")));
            List<String> javaOpts = script.getTestJavaOptions();
            env.put("TESTJAVAOPTS", fixupSep(StringUtils.join(javaOpts, " ")));
            env.put("TESTTIMEOUTFACTOR", String.valueOf(script.getTimeoutFactor()));
            env.put("TESTROOT", script.getTestRootDir().getPath());
            Modules modules = script.getModules();
            if (!modules.isEmpty())
                env.put("TESTMODULES", modules.toString());
            Path nativeDir = script.getNativeDir();
            if (nativeDir != null) {
                env.put("TESTNATIVEPATH", nativeDir.toAbsolutePath().toString());
            }
            if (script.enablePreview()) {
                env.put("TESTENABLEPREVIEW", "true");
            }
            if (script.disablePreview()) {
                env.put("TESTENABLEPREVIEW", "false");
            }
            String testQuery = script.getTestQuery();
            if (testQuery != null) {
                env.put("TESTQUERY", testQuery);
            }

            List<String> command = new ArrayList<>();
            if (script.useWindowsSubsystemForLinux()) {
                Path java_exe = script.getTestJDK().getHomeDirectory().resolve("bin").resolve("java.exe");
                env.put("NULL", "/dev/null");
                if (Files.exists(java_exe)) {
                    // invoking a Windows binary: use standard Windows separator characters
                    env.put("EXE_SUFFIX", ".exe");
                    env.put("FS", "/");
                    env.put("PS", File.pathSeparator);
                    env.put("WSLENV", getWSLENV(env, true));
                } else {
                    // invoking a Linux binary: use Linux separator characters
                    env.put("FS", "/");
                    env.put("PS", ";");
                    env.put("WSLENV", getWSLENV(env, false));
                }
                command.add("wsl.exe");
                command.add("sh");
                command.add(getWSLPath(shellFile));
            } else {
                // Set up default values for env vars; then override as needed
                String FS = File.separator;
                String PS = File.pathSeparator;
                String NULL = "/dev/null";
                if (OS.current().family.equals("windows")) {
                    if (System.getenv("PATH").contains("/cygwin")) {
                        // override values for Cygwin
                        FS = "/";
                    } else {
                        // override values for MKS (now mostly unsupported)
                        NULL = "NUL";
                    }
                }
                env.put("FS", FS);
                env.put("PS", PS);
                env.put("NULL", NULL);
                command.add("sh");
                command.add(shellFile.getPath());
            }
            command.addAll(shellArgs);

            // PASS TO PROCESSCOMMAND
            PrintWriter sysOut = section.createOutput("System.out");
            PrintWriter sysErr = section.createOutput("System.err");
            try {
                if (showCmd)
                    showCmd("shell", command, section);
                recorder.exec(command, env);

                TimeoutHandler timeoutHandler =
                        script.getTimeoutHandlerProvider().createHandler(this.getClass(), script, section);

                // RUN THE SHELL SCRIPT
                ProcessCommand cmd = new ProcessCommand()
                    .setMessageWriter(section.getMessageWriter())
                    .setExecDir(script.absTestScratchDir().toFile())
                    .setCommand(command)
                    .setEnvironment(env)
                    .setStreams(sysOut, sysErr)
                    .setTimeout(timeout, TimeUnit.SECONDS)
                    .setTimeoutHandler(timeoutHandler);

                status = normalize(cmd.exec());

            } finally {
                if (sysOut != null) sysOut.close();
                if (sysErr != null) sysErr.close();
            }

            // EVALUATE RESULTS

            if (!status.isError()) {
                boolean ok = status.isPassed();
                int st = status.getType();
                String sr;

                if (ok && reverseStatus) {
                    sr = EXEC_PASS_UNEXPECT;
                    st = Status.FAILED;
                } else if (ok && !reverseStatus) {
                    sr = EXEC_PASS;
                } else if (!ok && reverseStatus) {
                    sr = EXEC_FAIL_EXPECT;
                    st = Status.PASSED;
                } else { /* !ok && !reverseStatus */
                    sr = EXEC_FAIL;
                }
                if ((st == Status.FAILED) && !status.getReason().equals("")
                    && !status.getReason().equals(EXEC_PASS))
                    sr += ": " + status.getReason();
                status = createStatus(st, sr);
            }
        }

        endAction(status);
        return status;
    } // run()
        // where
        private String fixupSep(List<Path> files) {
            StringBuilder sb = new StringBuilder();
            for (Path f: files) {
                if (sb.length() > 0) sb.append(File.pathSeparator);
                sb.append(fixupSep(f));
            }
            return sb.toString();
        }
    private String fixupSep(Path f) {
        return fixupSep(f.toString());
    }
    private String fixupSep(File f) {
        return fixupSep(f.getPath());
    }
        private String fixupSep(String s) {
            return (sep == null ? s : s.replace(File.separator, sep));
        }
        private static final String sep = getSeparator();
        private static String getSeparator() {
            return (File.separatorChar == '\\'
                ? System.getProperty("javatest.shell.separator", "/")
                : null);
        }

    private String parseShellManual(String value) throws ParseException {
        if (value != null)
            throw new ParseException(SHELL_MANUAL_NO_VAL + value);
        else
            value = "novalue";
        return value;
    } // parseShellManual()

    private String getWSLENV(Map<String, String> env, boolean targetIsWindows) {
         StringBuilder sb = new StringBuilder();
         String sep = "";
         for (String name : env.keySet()) {
             String suffix;
             switch (name) {
                 case "COMPILEJAVA":
                 case "TESTJAVA":
                 case "TESTROOT":
                     suffix = "/p";
                     break;

                 case "TESTSRC":
                 case "TESTCLASSES":
                     suffix = targetIsWindows ? "" : "/p";
                     break;

                 case "TESTSRCPATH":
                 case "TESTCLASSPATH":
                 case "TESTNATIVEPATH":
                     suffix = targetIsWindows ? "" : "/l";
                     break;

                 default:
                     if (name.equalsIgnoreCase("PATH")) {
                         continue;
                     }
                     suffix = "";
             }
             sb.append(sep).append(name).append(suffix);
             sep = ":";
         }
         return sb.toString();
    }

    private String getWSLPath(File file) {
        Path path = file.toPath().toAbsolutePath();
        char driveLetter = Character.toLowerCase(path.getRoot().toString().charAt(0));
        StringBuilder result = new StringBuilder();
        result.append("/mnt/").append(driveLetter);
        for (Path pathElement : path) {
            result.append("/").append(pathElement);
        }
        return result.toString();
    }

    //----------member variables------------------------------------------------

    private String shellFN;
    private List<String> shellArgs;

    private boolean reverseStatus = false;
    private int     timeout = -1;
    private String  manual  = "unset"; // or "novalue"
}
