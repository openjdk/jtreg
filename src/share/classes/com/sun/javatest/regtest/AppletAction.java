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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.javatest.Status;

import static com.sun.javatest.regtest.RStatus.*;

/**
 * This class implements the "applet" action as described by the JDK tag
 * specification.
 *
 * @author Iris A Garcia
 * @see Action
 */
public class AppletAction extends Action
{
    public static final String NAME = "applet";

    /**
     * {@inheritdoc}
     * @return "applet"
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * This method does initial processing of the options and arguments for the
     * action.  Processing is determined by the requirements of run().
     *
     * Verify that the options are valid for the "applet" action.
     *
     * Verify that there is at least one argument.  This is assumed to be the
     * html file name.
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

        if (args.length != 1)
            throw new ParseException(APPLET_ONE_ARG_REQ);

        for (int i = 0; i < opts.length; i++) {
            String optName  = opts[i][0];
            String optValue = opts[i][1];

            if (optName.equals("fail")) {
                reverseStatus = parseFail(optValue);
            } else if (optName.equals("timeout")) {
                timeout  = parseTimeout(optValue);
            } else if (optName.equals("manual")) {
                manual = parseAppletManual(optValue);
            } else if (optName.equals("othervm")) {
                othervm = true;
            } else if (optName.equals("policy")) {
                overrideSysPolicy = true;
                policyFN = parsePolicy(optValue);
            } else if (optName.equals("java.security.policy")) {
                String name = optValue;
                if (optValue.startsWith("=")) {
                    overrideSysPolicy = true;
                    name = optValue.substring(1, optValue.length());
                }
                policyFN = parsePolicy(name);
            } else if (optName.equals("secure")) {
                secureFN = parseSecure(optValue);
            } else {
                throw new ParseException(APPLET_BAD_OPT + optName);
            }
        }

        if (manual.equals("unset")) {
            if (timeout < 0)
                timeout = script.getActionTimeout(0);
        } else {
            if (timeout >= 0)
                // can't have both timeout and manual
                throw new ParseException(PARSE_TIMEOUT_MANUAL);
            timeout = 0;
        }

        if (!othervm) {
            if (policyFN != null)
                throw new ParseException(PARSE_POLICY_OTHERVM);
            if (secureFN != null)
                throw new ParseException(PARSE_SECURE_OTHERVM);
        }

        htmlFN   = args[0];
    } // init()

    @Override
    public Set<File> getSourceFiles() {
        return Collections.singleton(new File(script.absTestSrcDir(), htmlFN));
    }

    /**
     * The method that does the work of the action.  The necessary work for the
     * given action is defined by the tag specification.
     *
     * Run the applet described by the first "<applet>" html tag in the given
     * html file.  Equivalent to "appletviewer <html-file>".
     *
     * Note that currently, this action assumes that the JVM supports multiple
     * processes.
     *
     * @return     The result of the action.
     * @exception  TestRunException If an unexpected error occurs while running
     *             the test.
     */
    public Status run() throws TestRunException {
        Status status;

        htmlFileContents = new HTMLFileContents(htmlFN);

        // TAG-SPEC:  "The named <class> will be compiled on demand, just as
        // though an "@run build <class>" action had been inserted before
        // this action."
        clsName = htmlFileContents.getAppletAttrs().get("code");
        if (clsName.endsWith(".class"))
            clsName = clsName.substring(0, clsName.lastIndexOf(".class"));
        String[][] buildOpts = {};
        String[]   buildArgs = {clsName};
        BuildAction ba = new BuildAction();
        if (!(status = ba.build(buildOpts, buildArgs, SREASON_ASSUMED_BUILD, script)).isPassed())
            return status;

        startAction();

        if (script.isCheck()) {
            // If we're only running check on the contents of the test
            // desciption and we got this far, we can just return success.
            // Everything after this point is preparation to run the actual test
            // and the test itself.
            status = passed(CHECK_PASS);
        } else {
//          if (othervm)
                status = runOtherJVM();
//          else
//              status = runSameJVM();
        }

        endAction(status);
        return status;
    } // run()

    //----------internal methods------------------------------------------------

    private Status runOtherJVM() throws TestRunException {
        // WRITE ARGUMENT FILE
        File appArgFileName = new File(script.absTestClsDir(),
                clsName + RegressionScript.WRAPPEREXTN);
        FileWriter fw;
        try {
            fw = new FileWriter(appArgFileName);
            fw.write(clsName + "\0");
            fw.write(script.absTestSrcDir() + "\0");
            fw.write(script.absTestClsDir() + "\0");
            fw.write(script.getTestClassPath() + "\0");
            fw.write(manual + "\0");
            fw.write(htmlFileContents.getBody() + "\0");
            fw.write(toString(htmlFileContents.getAppletParams()) + "\0");
            fw.write(toString(htmlFileContents.getAppletAttrs()) + "\0");
            fw.close();
        } catch (IOException e) {
            return error(APPLET_CANT_WRITE_ARGS);
        } catch (SecurityException e) {
            // shouldn't happen since JavaTestSecurityManager allows file ops
            return error(APPLET_SECMGR_FILEOPS);
        }

        // CONSTRUCT THE COMMAND LINE

        // TAG-SPEC:  "The source and class directories of a test are made
        // available to main and applet actions via the system properties
        // "test.src" and "test.classes", respectively"
        List<String> command = new ArrayList<String>(6);
        Map<String, String> env = new LinkedHashMap<String, String>();
        SearchPath cp = new SearchPath().append(script.getJavaTestClassPath()).append(script.getTestClassPath());
        command.add(script.getJavaProg());
        command.add("-classpath");
        command.add(cp.toString());

        List<String> vmOpts = new ArrayList<String>();
        vmOpts.addAll(script.getTestVMOptions());
        vmOpts.addAll(script.getTestJavaOptions());
        command.addAll(vmOpts);

        for (Map.Entry<String, String> e: script.getTestProperties().entrySet()) {
            command.add("-D" + e.getKey() + "=" + e.getValue());
        }

        String headless = System.getProperty("java.awt.headless");
        if (headless != null)
            command.add("-Djava.awt.headless=" + headless);

        // input methods use lots of memory
        boolean mx = false;
        for (int i = 0; i < vmOpts.size() && !mx; i++) {
            String opt = vmOpts.get(i);
            if (opt.startsWith("-mx") || opt.startsWith("-Xmx"))
                mx = true;
        }
        if (!mx)
            command.add("-mx128m");

        String newPolicyFN;
        if (policyFN != null) {
            // add pemission to read JTwork/classes by adding a grant entry
            newPolicyFN = addGrantEntry(policyFN);
            String cmd = overrideSysPolicy
                            ? "-Djava.security.policy==" + newPolicyFN
                            : "-Djava.security.policy=" + newPolicyFN;
            command.add(cmd);
        }

        if (secureFN != null)
            command.add("-Djava.security.manager=" + secureFN);
        else if (policyFN != null)
            command.add("-Djava.security.manager=default");
//      command.add("-Djava.security.debug=all");

        command.add("com.sun.javatest.regtest.AppletWrapper");
        command.add(appArgFileName.getPath());

        // convert from List to String[]
        String[] cmdArgs = command.toArray(new String[command.size()]);

        env.putAll(script.getEnvVars());

        Status status;
        PrintWriter sysOut = section.createOutput("System.out");
        PrintWriter sysErr = section.createOutput("System.err");
        try {
            if (showCmd)
                showCmd("applet", cmdArgs, section);

            recorder.exec(cmdArgs, env);

            // RUN THE APPLET WRAPPER CLASS
            ProcessCommand cmd = new ProcessCommand();
            cmd.setExecDir(script.absTestScratchDir());

            // Set the exit codes and their associated strings.  Note that we
            // require the use of a non-zero exit code for a passed test so
            // that we have a chance of detecting whether the test itself has
            // illegally called System.exit(0).
            cmd.setStatusForExit(Status.exitCodes[Status.PASSED],
                                 passed(EXEC_PASS));
            cmd.setStatusForExit(Status.exitCodes[Status.FAILED],
                                 failed(EXEC_FAIL));
            cmd.setDefaultStatus(failed(UNEXPECT_SYS_EXIT));

            // allow only one applet to run at a time, we don't want the tester
            // to be inundated with applet tests
            synchronized(appletLock) {
                TimeoutHandler timeoutHandler =
                    TimeoutHandlerProvider.createHandler(script, section);
                status = normalize(cmd.exec(cmdArgs, env, sysOut, sysErr,
                                            (long)timeout * 1000, timeoutHandler));
            }
        } finally {
            if (sysOut != null) sysOut.close();
            if (sysErr != null) sysErr.close();
        }

        // EVALUATE RESULTS

        if (!status.isError()
            && !status.getReason().startsWith(UNEXPECT_SYS_EXIT)) {
            // Dynamically construct the return status

            // an empty reason indicates that the test ran to completion
                // (either pass or user selected "fail")
            String sr;
            if (status.getReason().equals("")) {
                boolean uEval   = manual.equals("yesno");
                boolean manualp = (manual.equals("yesno") || manual.equals("done"));
                String uEvalString = uEval ? APPLET_USER_EVAL : "";
                sr = manualp ? (APPLET_MANUAL_TEST + uEvalString + ": ") : "";
            } else {
                sr = "";
            }

            boolean ok = status.isPassed();
            int st = status.getType();
            if (ok && reverseStatus) {
                sr += EXEC_PASS_UNEXPECT;
                st = Status.FAILED;
            } else if (ok && !reverseStatus) {
                sr += EXEC_PASS;
            } else if (!ok && reverseStatus) {
                sr += EXEC_FAIL_EXPECT;
                st = Status.PASSED;
            } else { /* !ok && !reverseStatus */
                sr += EXEC_FAIL;
            }
            if ((st == Status.FAILED) && !status.getReason().equals("")
                && !status.getReason().equals(EXEC_PASS))
                sr += ": " + status.getReason();
            status = createStatus(st, sr);
        }

        return status;
    } // runOtherJVM()

    private String parseAppletManual(String value) throws ParseException {
        if (value == null)
            return "novalue";
        if (!value.equals("yesno") &&
            !value.equals("done"))
            throw new ParseException(APPLET_BAD_VAL_MANUAL + value);
        return value;
    } // parseAppletManual()

    private static String toString(Map<String, String> d) {
        StringBuilder retVal = new StringBuilder();
        for (Map.Entry<String,String> e : d.entrySet()) {
            retVal.append(e.getKey());
            retVal.append("\034");
            retVal.append(e.getValue());
            retVal.append("\034");
        }
        return retVal.toString();
    } // toString()

    //-----internal classes-----------------------------------------------------

    /**
     * This class is a view of an HTML file that provides convenient accessor
     * methods relating to the first HTML applet tag.
     */
    private class HTMLFileContents
    {
        /**
         * @param htmlFN A string describing the relative location of the .html
         *         file.
         */
        HTMLFileContents(String htmlFN) throws TestRunException {
            // READ THE HTML FILE INTO A STRING
            String line;
            StringBuilder sb = new StringBuilder();
            //String htmlFN = script.relTestSrcDir() + FILESEP + args[0];
            htmlFN = new File(script.absTestSrcDir(), htmlFN).getPath();
            try {
                BufferedReader in = new BufferedReader(new FileReader(htmlFN));
                while ((line = in.readLine()) != null) {
                    sb.append(line);
                    sb.append(LINESEP);
                }
                in.close();
            } catch (FileNotFoundException e) {
                throw new TestRunException(APPLET_CANT_FIND_HTML + htmlFN);
            } catch (IOException e) {
                throw new TestRunException(APPLET_HTML_READ_PROB + htmlFN);
            }
            String contents = sb.toString();
            String lower = contents.toLowerCase();

            // BODY
            // If <body> exists, text will be the text between <body> and
            // </body>.
            // If <body> does not exist, this is still a valid html file, so
            // display the entire file.
            String lowerBody;
            int[] bodyPos = getTagPositions(contents, lower, "body", 0);
            if (bodyPos == null) {
                // no <body> tag, so take the entire file
                body = contents;
                lowerBody = lower;
            } else {
                int[] endBodyPos = getTagPositions(contents, lower, "/body", bodyPos[3]-1);
                if (endBodyPos == null)
                    throw new ParseException(APPLET_MISS_ENDBODY + htmlFN);
                body = contents.substring(bodyPos[3], endBodyPos[0]);
                lowerBody = lower.substring(bodyPos[3], endBodyPos[0]);
            }

            // APPLET ATTRIBUTES
            // Find the first <applet> tag and put contents into a map.
            int[] appletPos = getTagPositions(body, lowerBody, "applet", 0);
            if (appletPos == null)
                throw new ParseException(APPLET_MISS_APPLET + htmlFN);

            int[] endAppletPos = getTagPositions(body, lowerBody, "/applet", appletPos[3]-1);
            if (endAppletPos == null)
                throw new ParseException(APPLET_MISS_ENDAPPLET + htmlFN);

            appletAttrs = parseAttrs(body.substring(appletPos[1],
                                                         appletPos[2]));

            // verify that all of the required attributes are present
            String[] requiredAtts = {"code", "width", "height"};

            for (int i = 0; i <requiredAtts.length; i++) {
                if (appletAttrs.get(requiredAtts[i]) == null)
                    throw new ParseException(htmlFN + APPLET_MISS_REQ_ATTRIB
                                             + requiredAtts[i]);
            }

            // We currently do not support "archive".
            if (appletAttrs.get("archive") != null)
                throw new ParseException(APPLET_ARCHIVE_USUPP + htmlFN);

            // APPLET PARAMS
            // Put all parameters found between <applet> and </applet> into the
            // appletParams map.
            String appletBody = body.substring(appletPos[3], endAppletPos[0]);
            String lowerAppletBody = appletBody.toLowerCase();

            int startPos = 0;
            int[] paramPos;
            while ((paramPos = getTagPositions(appletBody, lowerAppletBody,
                                               "param", startPos)) != null) {
                Map<String, String> d =
                    parseAttrs(appletBody.substring(paramPos[1],
                                                          paramPos[2]));
                String name  = d.get("name");
                String value = d.get("value");
                if ((name == null) || (value == null))
                    throw new ParseException(APPLET_MISS_REQ_PARAM);
                appletParams.put(name, value);
                startPos = paramPos[3];
            }

        } // HTMLFileContents()

        //----------accessor methods--------------------------------------------

        String getBody() {
            return body;
        } // getBody()

        Map<String, String> getAppletParams() {
            return appletParams;
        } // getAppletParams()

        Map<String, String> getAppletAttrs() {
            return appletAttrs;
        } // getAppletAttrs()

        //----------internal methods--------------------------------------------

        /**
         * Return "important" positions used in parsing HTML tag attributes.
         *
         * <pre>
         *  f o o    = b a r
         * ^     ^    ^     ^
         * 0     1    2     3
         * </pre>
         *
         * @param attrs     A string containing attributes.
         * @return Array of four interesting positions for the first attribute
         *         in the string.
         */
        private int[] getAttrPositions(String attrs, int startPos) {
            try {
                // find the start of the name, skipping any header whitespace
                int nameStart = startPos;
                while (Character.isWhitespace(attrs.charAt(nameStart)))
                    nameStart++;

                // the name ends at the first whitespace or '='
                int nameEnd = nameStart;
                while (true) {
                    char c = attrs.charAt(nameEnd);
                    if (Character.isWhitespace(c) || (c == '='))
                        break;
                    nameEnd++;
                }

                // hop over any whitespaces to find the '='
                int valStart = nameEnd;
                while (Character.isWhitespace(attrs.charAt(valStart)))
                    valStart++;

                // verify presence of '='
                if (attrs.charAt(valStart) != '=')
                    return null;
                valStart++;
                // hop over any whitespaces after the '=' to find valStart
                while (Character.isWhitespace(attrs.charAt(valStart)))
                    valStart++;

                // find valEnd by locating the first non-quoted whitespace
                // character or the end of the string
                int theEnd = attrs.length();
                int valEnd = valStart;
                boolean inString = false;
                while (valEnd < theEnd) {
                    char c = attrs.charAt(valEnd);
                    if (!inString && Character.isWhitespace(c))
                        break;
                    if (c == '"')
                        inString = !inString;
                    if ((c == '\\') && (valEnd < (theEnd - 1)))
                        valEnd++;
                    valEnd++;
                }

                // verify that attribute is valid
                if ((nameEnd <= nameStart) || (valEnd <= valStart))
                    return null;

                int[] result = {nameStart, nameEnd, valStart, valEnd};
                return result;

            } catch (StringIndexOutOfBoundsException e) {
                return null; // input string was of invalid format
            }
        } // getAttrPositions()

        /**
         * Return "important" positions used in parsing non-nested HTML tags.
         *
         * <pre>
         *   <tag att=val ... >
         *  ^    ^           ^ ^
         *  0    1           2 3
         * </pre>
         *
         * @param contents The original contents of the HTML file.
         * @param lower    The lower-cased version of contents.
         * @param tagName  The HTML tag-name to find.
         * @param index    The index to start the search from.
         * @return Array of four interesting positions.
         */
        private int[] getTagPositions(String contents, String lower,
                                      String tagName, int index) {
            // !!!! assumes that "<" is to the immediate left of the tag name
            int tagStart = lower.indexOf("<" + tagName, index);
            if (tagStart == -1)
                return null;

            // !!!! doesn't properly handle '>' inside a quoted string
            int tagEnd = lower.indexOf(">", tagStart);
            if (tagEnd == -1)
                return null;

            int[] result = { tagStart, tagStart + tagName.length() + 1,
                             tagEnd, tagEnd + 1 };
            return result;
        } // getTagPositions()

        /**
         * Parse the attributes of an HTML tag.
         *
         * @param attrs     A string containing HTML attributes.
         * @return Map of HTML attributes (name/value pairs).
         */
        private Map<String, String> parseAttrs(String attrs) {
            Map<String, String> result = new HashMap<String, String>(3);

            int startPos = 0;
            int[] positions;
            while ((positions = getAttrPositions(attrs, startPos)) != null) {
                String value = attrs.substring(positions[2], positions[3]);
                if ((value.indexOf("\"") == 0)
                    && (value.lastIndexOf("\"") == value.length() - 1))
                    value = value.substring(1, value.length() - 1);
                result.put(attrs.substring(positions[0],
                                          positions[1]).toLowerCase(),
                           value);

                startPos = positions[3];
            }
            return result;
        } // parseAttrs()

        //----------member variables--------------------------------------------

        String body;
        Map<String, String> appletParams = new HashMap<String, String>(1);
        Map<String, String> appletAttrs;
    } // class HTMLFileContents

    //----------member variables------------------------------------------------

    private String  manual   = "unset";
    private boolean reverseStatus = false;
    private boolean othervm  = false;
    private int     timeout  = -1;
    private String  policyFN = null;
    private String  secureFN = null;
    private boolean overrideSysPolicy = false;
    private String htmlFN;
    private String clsName;
    private HTMLFileContents htmlFileContents;
    private static final Object appletLock = new Object();
}
