/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javatest.Status;
import com.sun.javatest.TestResult;
import com.sun.javatest.regtest.agent.ActionHelper;
import com.sun.javatest.regtest.agent.Flags;
import com.sun.javatest.regtest.agent.SearchPath;
import com.sun.javatest.regtest.config.ExecMode;
import com.sun.javatest.regtest.config.Modules;
import com.sun.javatest.regtest.config.OS;
import com.sun.javatest.regtest.config.ParseException;
import com.sun.javatest.regtest.util.FileUtils;
import com.sun.javatest.regtest.util.StringUtils;


/**
 * Action is an abstract base class providing the ability to control the
 * behavior of each step in a JDK test description.  This class requires that
 * all derived classes implement the <em>init</em> method (where arguments are
 * processed and other initializations occur) and the <em>run</em> method (where
 * the actual work for the action occurs.  In addition to these methods, the
 * Action abstract class contains a variety of protected methods for parsing and
 * logging.  All static strings used in Action implementations are also defined
 * here.
 */
public abstract class Action extends ActionHelper {
    /**
     * The null constructor.
     */
    public Action() {
    } // Action()

    /**
     * Get the user-visible name of this action.
     * @return the user-visible name of this action.
     */
    public abstract String getName();

    /**
     * This method does initial processing of the options and arguments for the
     * action.  Processing is determined by the requirements of run() which is
     * determined by the tag specification.
     *
     * @param opts The options for the action.
     * @param args The arguments for the actions.
     * @param reason Indication of why this action was invoked.
     * @param script The script.
     * @exception  ParseException If the options or arguments are not expected
     *             for the action or are improperly formated.
     */
    public void init(Map<String,String> opts, List<String> args, String reason,
            RegressionScript script)
            throws ParseException {
        this.opts = opts;
        this.args = args;
        this.reason = reason;
        this.script = script;
    }

    /**
     * The method that does the work of the action.  The necessary work for the
     * given action is defined by the tag specification.
     *
     * @return     The result of the action.
     * @exception  TestRunException If an unexpected error occurs while running
     *             the test.
     */
    public abstract Status run() throws TestRunException;

    /**
     * Get any source files directly referenced by this action.
     * @return the source files used by this action.
     **/
    public Set<File> getSourceFiles() {
        return null;
    }

    /**
     * Get the set of modules directly referenced in this action.
     * @return the set of modules used by this action.
     */
    public Set<String> getModules() {
        return Collections.emptySet();
    }

    protected Map<String, String> getEnvVars(boolean nativeCode) {
        Map<String, String> envVars = script.getEnvVars();
        if (nativeCode) {
            Path nativeDir = script.getNativeDir();
            if (nativeDir != null) {
                envVars = new LinkedHashMap<>(envVars);
                String libPathName;
                OS os = OS.current();
                switch (os.family) {
                    case "aix":
                    case "os400":
                        libPathName = "LIBPATH";
                        break;
                    case "mac":
                        libPathName = "DYLD_LIBRARY_PATH";
                        break;
                    case "windows":
                        libPathName = "PATH";
                        break;
                    default:
                        libPathName = "LD_LIBRARY_PATH";
                        break;
                }
                String libPath = envVars.get(libPathName);
                if (libPath == null) {
                    envVars.put(libPathName, nativeDir.toString());
                } else {
                    envVars.put(libPathName, libPath + File.pathSeparator + nativeDir);
                }
                envVars = Collections.unmodifiableMap(envVars);
            }
        }

        return envVars;
    }

    static synchronized void mkdirs(File dir) {
        dir.mkdirs();
    }

    public File getArgFile() {
        Path f = script.absTestWorkFile(getName() + "." + script.getNextSerial() + ".jta");
        FileUtils.createDirectories(f.getParent());
        return f.toFile();
    }

   //------------------- parsing -----------------------------------------------

    /**
     * This method parses the <em>timeout</em> action option used by several
     * actions.  It verifies that the value of the timeout is a valid number.
     *
     * @param value The proposed value of the timeout.
     * @return     An integer representation of the passed value for the
     *             timeout scaled by the timeout factor.
     * @exception  ParseException If the string does not have a valid
     *             interpretation as a number.
     */
    protected int parseTimeout(String value) throws ParseException {
        if (value == null)
            throw new ParseException(PARSE_TIMEOUT_NONE);
        try {
            return script.getActionTimeout(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            throw new ParseException(PARSE_TIMEOUT_BAD_INT + value);
        }
    } // parseTimeout()

    /**
     * This method parses the <em>fail</em> action option used by several
     * actions.  It verifies that there is no associated value for the option.
     *
     * @param value The proposed value of the fail.
     * @return     True if there is no associated value.
     * @exception  ParseException If there is an associated value.
     */
    protected boolean parseFail(String value) throws ParseException {
        if (value != null)
            throw new ParseException(PARSE_FAIL_UEXPECT + value);
        return true;
    } // parseFail()

    /**
     * This method parses the <em>module</em> action option used by some
     * actions.
     *
     * @param value The proposed value of the module
     * @return     True if the value is a legal identifier.
     * @exception  ParseException If there is an associated value.
     */
    protected String parseModule(String value) throws ParseException {
        if (value == null)
            throw new ParseException(PARSE_MODULE_NONE);
        if (!isQualifiedName(value))
            throw new ParseException(PARSE_MODULE_INVALID + value);
        return value;
    }

    private boolean isQualifiedName(String name) {
        boolean beginIdent = true;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (beginIdent) {
                if (!Character.isJavaIdentifierStart(ch)) {
                    return false;
                }
                beginIdent = false;
            } else {
                if (ch == '.') {
                    beginIdent = true;
                } else if (!Character.isJavaIdentifierPart(ch)) {
                    return false;
                }
            }
        }
        return !beginIdent;
    }

    //--------------------------------------------------------------------------

    /**
     * Add a grant entry to the policy file so that jtreg and other libraries can read
     * JTwork/classes.
     * The remaining entries in the policy file should remain the same.
     *
     * @param fileName The absolute name of the original policy file.
     * @return     A string indicating the absolute name of the modified policy
     *             file.
     * @throws TestRunException if a problem occurred adding this grant entry.
     */
    protected File addGrantEntries(File fileName) throws TestRunException {
        return addGrantEntries(fileName, null);
    }

    /**
     * Add a grant entry to the policy file so that jtreg and other libraries can read
     * JTwork/classes. An entry is added for the argFile, if one is given.
     *
     * The remaining entries in the policy file should remain the same.
     *
     * @param fileName the absolute name of the original policy file
     * @param argFile  an additional file to be granted permissions
     * @return     a string indicating the absolute name of the modified policy file
     * @throws TestRunException if a problem occurred adding this grant entry.
     */
    protected File addGrantEntries(File fileName, File argFile) throws TestRunException {
        File newPolicy = script.absTestScratchDir().resolve(fileName.getName() + "_new").toFile();

        try {
            try (FileWriter fw = new FileWriter(newPolicy)) {
                fw.write("// The following grant entries were added by jtreg.  Do not edit." + LINESEP);
                fw.write("grant {" + LINESEP);
                fw.write("    permission java.io.FilePermission \""
                        + script.absTestClsTopDir().toString().replace(FILESEP, "{/}")
                        + "${/}-\", \"read\";" + LINESEP);
                if (argFile != null) {
                    fw.write("    permission java.io.FilePermission \""
                            + argFile.getPath().replace(FILESEP, "{/}")
                            + "\", \"read\";" + LINESEP);
                }
                fw.write("};" + LINESEP);

                List<Path> libs = new ArrayList<>();
                libs.addAll(script.getJavaTestClassPath().asList());
                if (script.isJUnitRequired()) {
                    libs.addAll(script.getJUnitPath().asList());
                }
                if (script.isTestNGRequired()) {
                    libs.addAll(script.getTestNGPath().asList());
                }
                for (Path lib : libs) {
                    fw.write("grant codebase \"" + lib.toUri() + "\" {" + LINESEP);
                    fw.write("    permission java.security.AllPermission;" + LINESEP);
                    fw.write("};" + LINESEP);
                }
                fw.write(LINESEP);

                fw.write("// original policy file:" + LINESEP);
                fw.write("// " + fileName + LINESEP);

                try (BufferedReader in = new BufferedReader(new FileReader(fileName))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        fw.write(line + LINESEP);
                    }
                }
            }
        } catch (IOException e) {
            throw new TestRunException(POLICY_WRITE_PROB + newPolicy);
        } catch (SecurityException e) {
            throw new TestRunException(POLICY_SM_PROB + newPolicy);
        }

        return newPolicy;
    } // addGrantEntries()

    /**
     * This method parses the <em>policy</em> action option used by several
     * actions.  It verifies that the indicated policy file exists in the
     * directory containing the defining file of the test.
     *
     * @param value The proposed filename for the policy file.
     * @return     a file representing the absolute name of the policy file for
     *             the test.
     * @exception  ParseException If the passed filename is null, the empty
     *             string, or does not exist.
     */
    protected File parsePolicy(String value) throws ParseException {
        if ((value == null) || value.equals(""))
            throw new ParseException(MAIN_NO_POLICY_NAME);
        File policyFile = script.absTestSrcDir().resolve(value).toFile();
        if (!policyFile.exists())
            throw new ParseException(MAIN_CANT_FIND_POLICY + policyFile);
        return policyFile;
    } // parsePolicy()

    /**
     * This method parses the <em>secure</em> action option used to provide the
     * name of a subclass to be installed as the security manager.  No
     * verification of the existence of the .class is done.
     *
     * @param value The proposed class name for the security manager.
     * @return    A string indicating the absolute name of the security manager
     *            class.
     * @exception ParseException If the passed classname is null, the empty
     *            string
     */
    protected String parseSecure(String value) throws ParseException {
        if ((value == null) || value.equals(""))
            throw new ParseException(MAIN_NO_SECURE_NAME);
        return value;
    } // parseSecure()

    //----------logging methods-------------------------------------------------

    /**
     * Set up a recording area for the action.  The initial contents of the
     * default message area are set and will be of the form:
     * <pre>
     * command: action [command_args]
     * reason: [reason_string]
     * </pre>
     * @param initConfig whether or not to initialize a configuration section
     */
    protected void startAction(boolean initConfig) {
        long exclusiveAccessWaitMillis = 0;
        // if the RegressionScript isn't meant to only check the test description,
        // then before starting the action, we check if the RegressionScript
        // requires a exclusiveAccess lock and if it does, we acquire it.
        if (supportsExclusiveAccess() && !script.isCheck()) {
            exclusiveAccessLock = script.getLockIfRequired();
            if (exclusiveAccessLock != null) {
                long startNanos = System.nanoTime();
                exclusiveAccessLock.lock();
                exclusiveAccessWaitMillis = Duration.ofNanos(
                        System.nanoTime() - startNanos).toMillis();
            }
        }
        Date startDate = new Date();
        startTime = startDate.getTime();
        String name = getName();
        section = script.getTestResult().createSection(name);

        PrintWriter pw = section.getMessageWriter();
        pw.println(LOG_COMMAND + name + " " + StringUtils.join(args, " "));
        pw.println(LOG_REASON + reason);

        recorder = new ActionRecorder(this);
        if (initConfig) {
            configWriter = section.createOutput("configuration");
        }
        if (exclusiveAccessLock != null) {
            // log the time spent (in seconds) waiting for exclusiveAccess
            pw.println(LOG_EXCLUSIVE_ACCESS_TIME + ((double) exclusiveAccessWaitMillis / 1000.0));
        }
        pw.println(LOG_STARTED + startDate);
    }

    /**
     * Set the status for the passed action. After this call, the recording area
     * for the action becomes immutable.
     *
     * @param status The final status of the action.
     */
    protected void endAction(Status status) {
        try {
            Date endDate = new Date();
            long elapsedTime = endDate.getTime() - startTime;
            PrintWriter pw = section.getMessageWriter();
            pw.println(LOG_FINISHED + endDate);
            pw.println(LOG_ELAPSED_TIME + ((double) elapsedTime / 1000.0));
            recorder.close();
            section.setStatus(status);
        } finally {
            if (exclusiveAccessLock != null) {
                exclusiveAccessLock.unlock();
            }
        }
    }

    /**
     * {@return true if the action can run a {@code RegressionScript}
     *          that has been configured to run exclusively, false otherwise}
     */
    protected boolean supportsExclusiveAccess() {
        return false;
    }

    //----------workarounds-------------------------------------------------------

    /**
     * This method pushes the full, constructed command for the action to the
     * log.  The constructed command contains the action and its arguments
     * modified to run in another process.  The command may also contain
     * additional things necessary to run the action according to spec.  This
     * may include things such as a modified classpath, absolute names of files,
     * and environment variables.
     *
     * Used primarily for debugging purposes.
     *
     * @param action The name of the action currently being processed.
     * @param cmdArgs An array of the command to pass to ProcessCommand.
     * @param section The section of the result file for this action.
     * @see com.sun.javatest.lib.ProcessCommand#run
     */
    protected void showCmd(String action, String[] cmdArgs, TestResult.Section section) {
        showCmd(action, List.of(cmdArgs), section);
    }

    protected void showCmd(String action, List<String> cmdArgs, TestResult.Section section) {
        PrintWriter pw = section.getMessageWriter();
        pw.println(LOG_JT_COMMAND + action);
        for (String s: cmdArgs)
            pw.print("'" + s + "' ");
        pw.println();
    } // showCmd()

    // this has largely been superseded by the default show mode code
    protected void showMode(String action, ExecMode mode, TestResult.Section section) {
        PrintWriter pw = section.getMessageWriter();
        pw.println("MODE: " + mode);
    }

    protected void showMode(ExecMode mode) {
        showMode(mode, null);
    }

    protected void showMode(ExecMode mode, Set<String> reasons) {
        PrintWriter pw = section.getMessageWriter();
        pw.print("Mode: " + mode.name().toLowerCase());
        if (reasons != null && !reasons.isEmpty()) {
            pw.print(" ");
            pw.print(reasons);
        }
        pw.println();
    }

    /**
     * Given a string, change "\\" into "\\\\" for windows platforms.  This method
     * must be called exactly once before the string is used to start a new
     * process.
     *
     * @param s    The string to translate.
     * @return     For Windows systems, a modified string.  For all other
     *             systems including i386 (win32 sparc and Linux), the same
     *             string.
     */
    String[] quoteBackslash(String[] s) {
        String bs = "\\";
        String[] retVal = new String[s.length];
        if (System.getProperty("file.separator").equals(bs)) {
            for (int i = 0; i < s.length; i++) {
                String victim = s[i];
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < victim.length(); j++) {
                    String c = String.valueOf(victim.charAt(j));
                    sb.append(c);
                    if (c.equals(bs))
                        sb.append(c);
                }
                retVal[i] = sb.toString();
            }
        } else
            retVal = s;

        return retVal;
    } // quoteBackslash()

    /**
     * Single quote the given string.  This method should be used if the string
     * contains characters which should not be interpreted by the shell.
     *
     * @param s    The string to translate.
     * @return     The same string, surrounded by "'".
     */
    String singleQuoteString(String s) {
        StringBuilder b = new StringBuilder();
        b.append("'").append(s).append("'");
        return(b.toString());
    } // singleQuoteString()

    //--------------------------------------------------------------------------

    protected static <T> List<T> join(List<T> l1, List<T> l2) {
        List<T> result = new ArrayList<>();
        result.addAll(l1);
        result.addAll(l2);
        return result;
    }

    //--------------------------------------------------------------------------

    Set<String> getModules(SearchPath pp) {
        if (pp == null)
            return Collections.emptySet();

        Set<String> results = new LinkedHashSet<>();
        for (Path element : pp.asList()) {
            if (Files.isRegularFile(element)) {
                getModule(element, results);
            } else if (Files.isDirectory(element)) {
                for (Path file : FileUtils.listFiles(element)) {
                    getModule(file, results);
                }
            }
        }
        return results;
    }

    private void getModule(Path file, Set<String> results) {
        if (isModule(file)) {
            results.add(file.getFileName().toString());
        } else if (file.getFileName().toString().endsWith(".jar")) {
            results.add(getAutomaticModuleName(file));
        }
    }

    private boolean isModule(Path f) {
        if (Files.isDirectory(f)) {
            if (script.systemModules.contains(f.getFileName().toString())) {
                return true;
            }
            if (Files.exists(f.resolve("module-info.class")))
                return true;
            if (Files.exists(f.resolve("module-info.java")))
                return true;
        }
        return false;
    }

    private static final Map<Path, String> automaticNames = new ConcurrentHashMap<>();

    // see java.lang.module.ModulePath.deriveModuleDescriptor
    // See ModuleFinder.of for info on determining automatic module names
    //    https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/module/ModuleFinder.html#of(java.nio.file.Path...)
    private String getAutomaticModuleName(Path f) {
        // Step 0: see if already cached
        String cached = automaticNames.get(f);
        if (cached != null) {
            return cached;
        }

        // Step 1: check for Automatic-Module-Name in thge main jar file manifest
        try (JarFile jar = new JarFile(f.toFile())) {
            Manifest mf = jar.getManifest();
            Attributes attrs = mf.getMainAttributes();
            String amn = attrs.getValue("Automatic-Module-Name");
            if (amn != null) {
                automaticNames.put(f, amn);
                return amn;
            }
        } catch (IOException e) {
            script.getMessageWriter().println("Problem reading jar manifest to get Automatic-Module-Name: " + f + " " + e);
        }

        // Step 2: infer the name from the jar file name
        String fn = f.getFileName().toString();

        // drop .jar
        String mn = fn.substring(0, fn.length()-4);
        String vs = null;

        // find first occurrence of -${NUMBER}. or -${NUMBER}$
        Matcher matcher = Pattern.compile("-(\\d+(\\.|$))").matcher(mn);
        if (matcher.find()) {
            int start = matcher.start();
            // drop tail (ignore version info)
            mn = mn.substring(0, start);
        }

        // finally clean up the module name
        mn =  mn.replaceAll("[^A-Za-z0-9]", ".")  // replace non-alphanumeric
                .replaceAll("(\\.)(\\1)+", ".")   // collapse repeating dots
                .replaceAll("^\\.", "")           // drop leading dots
                .replaceAll("\\.$", "");          // drop trailing dots

        return mn;
    }

    //----------module exports----------------------------------------------------

    protected List<String> getExtraModuleConfigOptions(Modules.Phase phase) {
        if (!script.getTestJDK().hasModules())
            return Collections.emptyList();

        Modules modules = script.getModules();

        boolean needAddExports = false;
        Set<String> addModules = null;
        for (Modules.Entry e: modules) {
            String m = e.moduleName;
            if (e.needAddExports()) {
                needAddExports = true;
            }
            if (addModules == null) {
                addModules = new LinkedHashSet<>();
            }
            addModules.add(m);
        }
        if (!needAddExports && addModules == null) {
            return Collections.emptyList();
        }

        List<String> list = new ArrayList<>();
        if (addModules != null) {
            list.add("--add-modules");
            list.add(StringUtils.join(addModules, ","));
        }

        for (Modules.Entry e: modules) {
            if (e.packageName != null) {
                if (e.addExports) {
                    list.add("--add-exports");
                    list.add(e.moduleName + "/" + e.packageName + "=ALL-UNNAMED");
                }
                if (e.addOpens && (phase == Modules.Phase.DYNAMIC)) {
                    list.add("--add-opens");
                    list.add(e.moduleName + "/" + e.packageName + "=ALL-UNNAMED");
                }
            }
        }

        PrintWriter pw = section.getMessageWriter();
        pw.println("Additional options from @modules: " + StringUtils.join(list, " "));

        return list;
    }

    protected boolean includesOption(String option, String arg, List<String> options) {
        boolean seenOption = false;
        for (String opt: options) {
            if (opt.equals(option + "=" + arg)) {
                return true;
            } else if (opt.equals(option)) {
                seenOption = true;
            } else if (seenOption && opt.equals(arg)) {
                return true;
            } else {
                seenOption = false;
            }
        }
        return false;
    }

    //----------misc statics----------------------------------------------------

    protected static final String FILESEP  = System.getProperty("file.separator");
    protected static final String LINESEP  = System.getProperty("line.separator");

    // This is a hack to deal with the fact that the implementation of
    // Runtime.exec() for Windows stringifies the arguments.
    protected static final String EXECQUOTE = (System.getProperty("os.name").startsWith("Windows") ? "\"" : "");

    public static final String
        REASON_ASSUMED_ACTION = "ASSUMED_ACTION",
        REASON_USER_SPECIFIED = "USER_SPECIFIED",
        REASON_ASSUMED_BUILD  = "ASSUMED_BUILD",
        REASON_FILE_TOO_OLD   = "FILE_OUT_OF_DATE";

    protected static final String
        SREASON_ASSUMED_ACTION= "Assumed action based on file name: run ",
        SREASON_USER_SPECIFIED= "User specified action: run ",
        SREASON_ASSUMED_BUILD = "Named class compiled on demand",
        SREASON_FILE_TOO_OLD  = ".class file out of date or does not exist";

    // These are all of the error messages used in all actions.
    protected static final String
        PARSE_TIMEOUT_NONE    = "No timeout value",
        PARSE_TIMEOUT_BAD_INT = "Bad integer specification: ",
        PARSE_FAIL_UEXPECT    = "Unexpected value for `fail': ",
        PARSE_MODULE_NONE     = "No module name",
        PARSE_MODULE_INVALID  = "Invalid module name",

        // policy and security manager
        PARSE_BAD_OPT_JDK     = "Option not allowed using provided test JDK: ",
        PARSE_NO_POLICY_NAME  = "No policy file name",
        PARSE_CANT_FIND_POLICY= "Can't find policy file: ",
        PARSE_NO_SECURE_NAME  = "No security manager file name",
        PARSE_POLICY_OTHERVM  = "`/policy' and `/java.security.policy` require use of `/othervm'",
        PARSE_SECURE_OTHERVM  = "`/secure' requires use of `/othervm'",
        PARSE_TIMEOUT_MANUAL  = "`/manual' disables use of `/timeout'",

        POLICY_WRITE_PROB     = "Problems writing new policy file: ",
        POLICY_SM_PROB        = "Unable to create new policy file: ",

        LOG_COMMAND           = "command: ",
        LOG_RESULT            = " result: ",
        LOG_JT_COMMAND        = "JavaTest command: ",
        LOG_REASON            = "reason: ",
        LOG_ELAPSED_TIME      = "elapsed time (seconds): ",
        LOG_EXCLUSIVE_ACCESS_TIME = "exclusiveAccess wait time (seconds): ",
        LOG_STARTED = "started: ",
        LOG_FINISHED = "finished: ",
        //LOG_JDK               = "JDK under test: ",

        // COMMON
        // used in:  shell, main, applet
        EXEC_FAIL             = "Execution failed",
        EXEC_FAIL_EXPECT      = "Execution failed as expected",
        EXEC_PASS_UNEXPECT    = "Execution passed unexpectedly",
        CHECK_PASS            = "Test description appears acceptable",

        // used in:  compile, main
        AGENTVM_CANT_GET_VM      = "Cannot get VM for test",
        AGENTVM_IO_EXCEPTION     = "Agent communication error: %s; check console log for any additional details",
        AGENTVM_EXCEPTION        = "Agent error: %s; check console log for any additional details",

        CANT_FIND_SRC         = "Can't find source file: ",

        // applet
        APPLET_ONE_ARG_REQ    = "`applet' requires exactly one file argument",
        APPLET_BAD_VAL_MANUAL = "Bad value for `manual' option: ",
        APPLET_BAD_OPT        = "Bad option for applet: ",
        APPLET_CANT_FIND_HTML = "Can't find HTML file: ",
        APPLET_HTML_READ_PROB = "Problem reading HTML file: ",
        APPLET_MISS_ENDBODY   = "No </body> tag in ",
        APPLET_MISS_APPLET    = "No <applet> tag in ",
        APPLET_MISS_ENDAPPLET = "No </applet> tag in ",
        APPLET_MISS_REQ_ATTRIB= " missing required attribute ",
        APPLET_ARCHIVE_USUPP  = "`archive' not supported in file: ",
        APPLET_MISS_REQ_PARAM = "Missing required name or value for param in <param> tag",
        APPLET_CANT_WRITE_ARGS= "Can't write `applet' argument file",
        APPLET_SECMGR_FILEOPS = "Unable to create applet argument file",

        APPLET_USER_EVAL      = ", user evaluated",
        APPLET_MANUAL_TEST    = "Manual test",

        // build
        BUILD_UNEXPECT_OPT    = "Unexpected options for `build'",
        BUILD_NO_CLASSNAME    = "No classname(s) provided for `build'",
        BUILD_BAD_CLASSNAME   = "Bad classname provided for `build': ",
        BUILD_NO_COMP_NEED    = "No need to compile: ",
        BUILD_UP_TO_DATE      = "All files up to date",
        BUILD_SUCC            = "Build successful",
        BUILD_LIB_LIST        = " in directory-list: ",
        BUILD_FUTURE_SOURCE   = "WARNING: file %s has a modification time in the future: %s",
        BUILD_FUTURE_SOURCE_2 = "Unexpected results may occur",

        // clean
        CLEAN_SUCC            = "Clean successful",
        CLEAN_UNEXPECT_OPT    = "Unexpected option(s) for `clean'",
        CLEAN_NO_CLASSNAME    = "No classname(s) provided for `clean'",
        CLEAN_BAD_CLASSNAME   = "Bad classname provided for `clean': ",
        CLEAN_RM_FAILED       = "`clean' unable to delete file: ",
        CLEAN_SECMGR_PROB     = "Problem deleting directory contents: ",

        // compile
        COMPILE_NO_CLASSNAME  = "No classname provided for `compile'",
        COMPILE_NO_DOT_JAVA   = "No classname ending with `.java' found",
        COMPILE_BAD_OPT       = "Bad option for compile: ",
        COMPILE_OPT_DISALLOW  = "Compile option not allowed: ",
        COMPILE_NO_REF_NAME   = "No reference file name",
        COMPILE_CANT_FIND_REF = "Can't find reference file: ",
        COMPILE_CANT_READ_REF = "Can't read reference file: ",
        COMPILE_GOLD_FAIL     = "Output does not match reference file: ",
        COMPILE_GOLD_LINE     = ", line ",
        COMPILE_GOLD_READ_PROB= "Problem reading reference file: ",
        COMPILE_MODULES_UEXPECT    = "Unexpected value for `modules': ",

        COMPILE_CANT_CREATE_ARG_FILE = "Can't create `compile' argument file",
        COMPILE_CANT_WRITE_ARGS  = "Can't write `compile' argument file",
        COMPILE_SECMGR_FILEOPS   = "Unable to create `compile' argument file",

        COMPILE_PASS_UNEXPECT = "Compilation passed unexpectedly",
        COMPILE_PASS          = "Compilation successful",
        COMPILE_FAIL_EXPECT   = "Compilation failed as expected",
        COMPILE_FAIL          = "Compilation failed",
        COMPILE_CANT_RESET_SECMGR= "Cannot reset security manager",
        COMPILE_CANT_RESET_PROPS = "Cannot reset system properties",

        // ignore
        IGNORE_UNEXPECT_OPTS  = "Unexpected option(s) for `ignore'",
        IGNORE_TEST_IGNORED   = "Test ignored",
        IGNORE_TEST_IGNORED_C = "Test ignored: ",
        IGNORE_TEST_SUPPRESSED   = "@ignore suppressed by command line option",
        IGNORE_TEST_SUPPRESSED_C = "@ignore suppressed by command line option: ",

        // junit
        JUNIT_NO_CLASSNAME     = "No class provided for `junit'",
        JUNIT_BAD_MAIN_ARG     = "Bad argument provided for class in `junit'",

        // driver
        DRIVER_NO_CLASSNAME    = "No class provided for `driver'",
        DRIVER_UNEXPECT_VMOPT  = "VM options not allowed",
        DRIVER_BAD_OPT         = "Bad option for driver: ",

        // main
        MAIN_NO_CLASSNAME     = "No class provided for `main'",
        MAIN_MANUAL_NO_VAL    = "Arguments to `manual' option not supported: ",
        MAIN_BAD_OPT          = "Bad option for main: ",
        MAIN_CANT_FIND_SECURE = "Can't find security manager file name: ",
        MAIN_BAD_OPT_JDK      = "Option not allowed using provided test JDK: ",
        MAIN_NO_POLICY_NAME   = "No policy file name",
        MAIN_CANT_FIND_POLICY = "Can't find policy file: ",
        MAIN_POLICY_OTHERVM   = "`/policy' requires use of `/othervm'",
        MAIN_NO_SECURE_NAME   = "No security manager file name",
        MAIN_SECURE_OTHERVM   = "`/secure' requires use of `/othervm'",
        MAIN_UNEXPECT_VMOPT   = ": vm option(s) found, need to specify /othervm",
        MAIN_POLICY_WRITE_PROB= "Problems writing new policy file: ",
        MAIN_POLICY_SM_PROB   = "Unable to create new policy file: ",
        MAIN_CANT_RESET_SECMGR= "Cannot reset security manager",
        MAIN_CANT_RESET_PROPS = "Cannot reset system properties",
        MAIN_NO_NATIVES       = "Use -nativepath to specify the location of native code",

        //    runOtherJVM
        MAIN_CANT_WRITE_ARGS  = "Can't write `main' argument file",
        MAIN_SECMGR_FILEOPS   = "Unable to create `main' argument file",

        // shell
        SHELL_NO_SCRIPT_NAME  = "No script name provided for `shell'",
        SHELL_MANUAL_NO_VAL   = "Arguments to `manual' option not supported: ",
        SHELL_BAD_OPTION      = "Bad option for shell: ";

    //----------member variables------------------------------------------------

    protected /*final*/ Map<String,String> opts;
    protected /*final*/ List<String> args;
    protected /*final*/ String reason;
    protected /*final*/ RegressionScript script;

    protected /*final*/ TestResult.Section section;
    protected /*final*/ ActionRecorder recorder;
    protected /*final*/ PrintWriter configWriter;
    private long startTime;
    // used when the action's RegressionScript is configured to
    // run in exclusiveAccess.dir
    private Lock exclusiveAccessLock;

    protected static final boolean showCmd = Flags.get("showCmd");
    protected static final boolean showMode = Flags.get("showMode");
    protected static final boolean showJDK = Flags.get("showJDK");
}


