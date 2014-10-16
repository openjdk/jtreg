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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javatest.TestDescription;
import com.sun.javatest.TestResult;
import com.sun.javatest.TestSuite;
import com.sun.javatest.finder.CommentStream;
import com.sun.javatest.finder.HTMLCommentStream;
import com.sun.javatest.finder.JavaCommentStream;
import com.sun.javatest.finder.ShScriptCommentStream;
import com.sun.javatest.finder.TagTestFinder;
import com.sun.javatest.util.I18NResourceBundle;

/**
  * This is a specific implementation of the TagTestFinder which is to be used
  * for JDK regression testing.  It follows the test-tag specifications as given
  * in http://openjdk.java.net/jtreg/tag-spec.txt.
  *
  * A test description consists of a single block comment in either Java files
  * or shell-script files.  A file may contain multiple test descriptions.
  *
  * @author Iris A Garcia
  * @see com.sun.javatest.TestFinder
  * @see com.sun.javatest.finder.TagTestFinder
  */
public class RegressionTestFinder extends TagTestFinder
{
    /**
      * Constructs the list of file names to exclude for pruning in the search
      * for files to examine for test descriptions.  This constructor also sets
      * the allowable comment formats.
      * @param properties the test suite properties manager
      */
    public RegressionTestFinder(TestProperties properties) {
        this.properties = properties;
        this.checkBugID = properties.checkBugID;

        Set<String> rootValidKeys = properties.validKeys;
        validTagNames = getValidTagNames(rootValidKeys != null);

        exclude(excludeNames);
        addExtension(".sh", ShScriptCommentStream.class);
        addExtension(".html", HTMLCommentStream.class);

        baseContext = new RegressionContext();
    }

    @SuppressWarnings("unchecked")
    Set<String> getAllowedExtensions() {
        return ((HashMap) getField("extensionTable")).keySet();
    }

    @SuppressWarnings("unchecked")
    Set<String> getIgnoredDirectories() {
        return ((HashMap) getField("excludeList")).keySet();
    }

    Object getField(String name) {
        try {
            Field f = TagTestFinder.class.getDeclaredField(name);
            try {
                f.setAccessible(true);
                return f.get(this);
            } finally {
                f.setAccessible(false);
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace(System.err);
            return null;
        } catch (SecurityException e) {
            e.printStackTrace(System.err);
            return null;
        } catch (IllegalArgumentException e) {
            e.printStackTrace(System.err);
            return null;
        } catch (IllegalAccessException e) {
            e.printStackTrace(System.err);
            return null;
        }
    }

    @Override
    protected void setRoot(File testSuiteRoot) throws Fault {
        super.setRoot(canon(testSuiteRoot));
    }

    @Override
    protected void scanFile(File file) {
        // filter out SCCS leftovers
        if (file.getName().startsWith(","))
            return;

        try {
            File tngRoot = properties.getTestNGRoot(file);
            if (tngRoot != null) {
                scanTestNGFile(tngRoot, file);
            } else {
                super.scanFile(file);
            }
        } catch (TestSuite.Fault e) {
            error(i18n, "finder.cant.read.test.properties", new Object[] { e });
        }
    }

    protected void scanTestNGFile(File tngRoot, File file) throws TestSuite.Fault {
        if (isTestNGTest(file)) {
            BufferedReader in = null;
            try {
                in = new BufferedReader(new FileReader(file));
                Map<String,String> tagValues = readTestNGComments(file, in);
                if (tagValues == null) {
                    tagValues = new HashMap<String,String>();
                    // could read more of file looking for annotations like @Test, @Factory
                    // to guess whether this is really a test file or not
                }
                String p = tngRoot.toURI().relativize(file.toURI()).getPath();
                String className = p.substring(0, p.length() - 5).replace("/", ".");
                tagValues.put("packageRoot", getRootDir().toURI().relativize(tngRoot.toURI()).getPath());
                tagValues.put("testngClass", className);
                tagValues.put("library", StringUtils.join(properties.getLibDirs(file), " "));
                foundTestDescription(tagValues, file, /*line*/0);
            } catch (IOException e) {
                error(i18n, "finder.ioError", file);
            } finally {
                try {
                    if (in != null) in.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private Map<String,String> readTestNGComments(File file, BufferedReader in) throws IOException {
        CommentStream cs = new JavaCommentStream();
        cs.init(in);
        cs.setFastScan(true);

        Map<String, String> tagValues = null;
        String comment;
        int index = 1;
        while ((comment = cs.readComment()) != null) {
            @SuppressWarnings("unchecked")
            Map<String, String> tv = parseComment(comment, file);
            if (tv.isEmpty())
                continue;

            if (tagValues == null) {
                tagValues = tv;
            } else {
                tv.put("error", PARSE_MULTIPLE_COMMENTS_NOT_ALLOWED);
                tv.put("id", String.valueOf(index++));
                foundTestDescription(tv, file, /*line*/0);
            }

            // The "test" marker can now be removed so that we don't waste
            // space unnecessarily.  We need to do the remove *after* the
            // isEmpty() check because of the potential to interfere with
            // defaults based on file extension. (i.e. The TD /* @test */
            // still needs to evaluate to a valid test description.)
            tagValues.remove("test");
        }
        return tagValues;

    }

    protected boolean isTestNGTest(File file) {
        // for now, ignore comments and annotations, and
        // assume *.java is a test
        return file.getName().endsWith(".java");
    }

    private File canon(File f) {
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return new File(f.getAbsoluteFile().toURI().normalize());
        }
    }

    @Override @SuppressWarnings({"unchecked", "rawtypes"})
    protected Map<String, String> normalize(Map tv) {
        return normalize0((Map<String, String>) tv);
    }

    private Map<String, String> normalize0(Map<String, String> tagValues) {
        Map<String, String> newTagValues = new HashMap<String, String>();
        String fileName = getCurrentFile().getName();
        String baseName = fileName.substring(0, fileName.lastIndexOf("."));
        boolean testNG = tagValues.containsKey("testngClass");

        // default values
        newTagValues.put("title", " ");
        newTagValues.put("source", fileName);

        if (testNG) {
            if (tagValues.get("run") != null) {
                tagValues.put("error", PARSE_BAD_RUN);
            }
            String className = tagValues.get("testngClass");
            newTagValues.put("run", Action.REASON_ASSUMED_ACTION + " testng "
                             + className + LINESEP);
        } else if (fileName.endsWith(".sh")) {
            newTagValues.put("run", Action.REASON_ASSUMED_ACTION + " shell "
                             + fileName + LINESEP);
        } else if (fileName.endsWith(".java")) { // we have a ".java" file
            newTagValues.put("run", Action.REASON_ASSUMED_ACTION + " main "
                             + baseName + LINESEP);
        } else { // we have a ".html" file
            newTagValues.put("run", Action.REASON_ASSUMED_ACTION + " applet "
                             + fileName + LINESEP);
        }

        // translate between JDK tags and JavaTest tags; for required
        // JavaTest fields, make sure that we make a reasonable assumption
        for (Map.Entry<? extends String, ? extends String> e: tagValues.entrySet()) {
            String name  = e.getKey();
            String value = e.getValue();
            if (name.equals("summary")) {
                // the title is the first sentence of the provided summary
                name = "title";
                int pos = 0;
            loop:
                while (true) {
                    pos = value.indexOf(".", pos);
                    if (pos == -1 || pos + 1 == value.length())
                        break;
                    switch (value.charAt(pos + 1)) {
                    case ' ':
                    case '\n':
                    case '\r':
                    case '\t':
                    case '\f':
                    case '\b':
                        value = value.substring(0, pos + 1);
                        break loop;
                    }
                    pos++;
                }
            } else if (name.equals("bug") || name.equals("key"))  {
                // force some keywords
                name = "keywords";
                String oldValue = newTagValues.get("keywords");
                if (oldValue != null)
                    value = oldValue + " " + value;
            } else if (name.equals("test")) {
                // TagTestFinder.scanFile() removes the "test" name/value pair,
                // so I don't think that we'll ever get here.  3/13

                // If we run into an @test, we have a regression test.
                // Add "regtest" to the list of keywords.  The script
                // will be triggered off this keyword.
                name = "keywords";
                String oldValue = newTagValues.get("keywords");
                if (oldValue != null)
                    value = oldValue + " regtest";
                else
                    value = "regtest";
            }
//          System.out.println("--- NAME: " + name + " VALUE: " + value);
            newTagValues.put(name, value);
        }

        String value = newTagValues.get("run");

        // force more key words based on actions
        String origKeywords = newTagValues.get("keywords");
        String addKeywords  = "";

        if (match(value, OTHERVM_OPTION) || match(value, BOOTCLASSPATH_OPTION))
            addKeywords += " othervm";

        if (match(value, MANUAL_OPTION))
            addKeywords += " manual";

        if (match(value, NATIVE_OPTION))
            addKeywords += " native";

        if (match(value, SHELL_ACTION))
            addKeywords += " shell";

        if (match(value, JUNIT_ACTION))
            addKeywords += " junit";

        if (match(value, DRIVER_ACTION))
            addKeywords += " driver";

        if (match(value, IGNORE_ACTION))
            addKeywords += " ignore";

        if (testNG)
            addKeywords += " testng";

        if (!addKeywords.equals("")) {
            if (origKeywords == null)
                newTagValues.put("keywords", addKeywords.trim());
            else
                newTagValues.put("keywords", origKeywords + addKeywords);
        }

        if (rejectTrailingBuild) {
            int sep = value.lastIndexOf(LINESEP, value.length() - 1 - LINESEP.length()); // ignore final LINESEP
            String lastLine = value.substring(sep == -1 ? 0 : sep + LINESEP.length());
            if (lastLine.startsWith(Action.REASON_USER_SPECIFIED + " build")) {
                newTagValues.put("error", PARSE_RUN_ENDS_WITH_BUILD);
            }
        }

        int maxTimeout = -1;
        // The following pattern is slightly sloppy since it runs the risk of
        // false positives in the args to a test; if necessary the pattern could
        // require matching on the possible action names as well.
        Pattern p = Pattern.compile("/timeout=([0-9]+)(?:/| )");
        Matcher m = p.matcher(value);
        while (m.find()) {
            int t = Integer.parseInt(m.group(1));
            if (t > maxTimeout)
                maxTimeout = t;
        }
        if (maxTimeout > 0)
            newTagValues.put("maxTimeout", String.valueOf(maxTimeout));

        /*
        for (Map.Entry<String,String> e: newTagValues.entrySet()) {
            System.out.println("NAME: " + e.getKey() + " VALUE: " + e.getValue());
//          if (name.equals("keywords"))
//              System.out.println(currFile + " " + "`" + value + "'");
        }
        */

        return newTagValues;
    }

    private static boolean match(CharSequence cs, Pattern p) {
        return p.matcher(cs).matches();
    }

    /**
     * Make sure that the provide name-value pair is of the proper format as
     * described in the tag-spec.
     *
     * @param entries   The map of the entries being read
     * @param name      The name of the entry that has been read
     * @param value     The value of the entry that has been read
     */
    @Override @SuppressWarnings({"unchecked", "rawtypes"})
    protected void processEntry(Map entries, String name, String value)
    {
        Map<String, String> tagValues = (Map<String, String>) entries;

        // check for valid tag name, don't produce error message for the
        // the SCCS sequence '%' 'W' '%'
        if (name.startsWith("(#)"))
            return;

        // translate the shorthands into run actions
        if (name.startsWith(COMPILE)
            || name.startsWith(CLEAN)
            || name.startsWith(BUILD)
            || name.startsWith(IGNORE)) {
            value = name + " " + value;
            name = RUN;
        }

        try {
            if (!validTagNames.contains(name)) {
                parseError(tagValues, PARSE_TAG_BAD + name);
            } else if (name.equals(RUN)) {
                processRun(tagValues, value);
            } else if (name.equals(BUG)) {
                processBug(tagValues, value);
            } else if (name.equals(REQUIRES)) {
                processRequires(tagValues, value);
            } else if (name.equals(KEY)) {
                processKey(tagValues, value);
            } else if (name.equals(LIBRARY)) {
                processLibrary(tagValues, value);
            } else
                tagValues.put(name, value);
        } catch (TestSuite.Fault e) {
            reportError(tagValues, e.getMessage());
        }
    }

    @Override
    protected void foundTestDescription(TestDescription td) {
        String wrp = TestResult.getWorkRelativePath(td);
        TestDescription other = paths.get(wrp);
        if (other != null && !td.getRootRelativeURL().equals(other.getRootRelativeURL())) {
            error(i18n, "finder.jtrClash",
                    new Object[] { td.getFile(), other.getFile() });
            return;
        }

        super.foundTestDescription(td);
        paths.put(wrp, td);

    }
    Map<String, TestDescription> paths = new HashMap<String, TestDescription>();

    //-----internal routines----------------------------------------------------

    //---------- parsing -------------------------------------------------------

    private void parseError(Map<String, String> tagValues, String value) {
        // TODO: The use of "Exception" in the following message is
        // temporarily retained for backward compatibility.
        // "Error" would be a better word.
        reportError(tagValues, "Parse Exception: " + value);
    }

    private void reportError(Map<String, String> tagValues, String value) {
        // for now, just record first error
        if (tagValues.get(ERROR) == null)
            tagValues.put(ERROR, value);
    }

    /**
     * Create the "run" action entry by adding a reason code to the
     * user-provided action.  Each action is separated by LINESEP.
     *
     * @param tagValues The map of all of the current tag values.
     * @param value     The value of the entry currently being processed.
     */
    private void processRun(Map<String, String> tagValues, String value)
    {
        String oldValue = tagValues.get(RUN);
        StringBuilder sb = new StringBuilder();
        if (oldValue != null)
            sb.append(oldValue);
        sb.append(Action.REASON_USER_SPECIFIED)
                .append(" ")
                .append(value)
                .append(LINESEP);
        tagValues.put(RUN, sb.toString());
    }

    /**
     * Verify that all bugs are properly formatted.
     * Each provided bugid must match one of the following patterns:
     * Sun bug number: 7 digits
     * OpenJDK JIRA number: 7 digits or PROJECTNAME- 7 digits
     * Oracle internal bug number: 8 digits beginning 14
     *
     * @param tagValues The map of all of the current tag values.
     * @param value     The value of the entry currently being processed.
     */
    private void processBug(Map<String, String> tagValues, String value) {
        if (value.trim().length() == 0) {
            parseError(tagValues, PARSE_BUG_EMPTY);
            return;
        }

        StringBuilder newValue = new StringBuilder();
        if (tagValues.get(BUG) != null)
            newValue.append(tagValues.get(BUG));
        for (String bugid : StringArray.splitWS(value)) {
            // bugid checking can be switched on and off with an
            // environment var. that the testsuite finds
            if (checkBugID && !bugIdPattern.matcher(bugid).matches()) {
                parseError(tagValues, PARSE_BUG_INVALID + bugid);
                continue;
            }
            if (newValue.length() > 0)
                newValue.append(" ");
            newValue.append("bug").append(bugid);
        }

        if (newValue.length() > 0)
            tagValues.put(BUG, newValue.toString());
    }
    private static final Pattern bugIdPattern = Pattern.compile("(([A-Z]+-)?[0-9]{7})|(14[0-9]{6})");

    /**
     * Validate @requires, and combine multiple instances.
     *
     * @param tagValues The map of all of the current tag values.
     * @param value     The value of the entry currently being processed.
     */
    private void processRequires(Map<String, String> tagValues, String value) throws TestSuite.Fault {
        if (value.trim().length() == 0) {
            parseError(tagValues, PARSE_REQUIRES_EMPTY);
            return;
        }

        try {
            final Set<String> validPropNames = properties.getValidRequiresProperties(getCurrentFile());
            Expr.Context c = new RegressionContext(baseContext, validPropNames);
            Expr.parse(value, c);
        } catch (Expr.Fault f) {
            parseError(tagValues, PARSE_REQUIRES_SYNTAX + f.getMessage());
            return;
        }

        String oldValue = tagValues.get(REQUIRES);
        if (oldValue == null) {
            tagValues.put(REQUIRES, value);
        } else {
            tagValues.put(REQUIRES, "(" + oldValue + ") & (" + value + ")");
        }
    }

    /**
     * Verify that the provided set of keys are allowed for the current
     * test-suite.  The set of keys is stored in the system property
     * <code>env.regtest.key</code>.
     *
     * @param tagValues The map of all of the current tag values.
     * @param value     The value of the entry currently being processed.
     * @return    A string which contains the new value for the "key" tag.
     */
    private void processKey(Map<String, String> tagValues, String value)
            throws TestSuite.Fault {
        if (value.trim().length() == 0) {
            parseError(tagValues, PARSE_KEY_EMPTY);
            return;
        }

        // make sure that the provided keys are all valid
        Set<String> validKeys = properties.getValidKeys(getCurrentFile());
        StringBuilder newValue = new StringBuilder();
        for (String key: StringArray.splitWS(value)) {
            if (!validKeys.contains(key)) {
                parseError(tagValues, PARSE_KEY_BAD + key);
                continue;
            }
            if (newValue.length() > 0)
                newValue.append(" ");
            newValue.append(key);
        }

        if (newValue.length() > 0)
            tagValues.put(KEY, newValue.toString());
    }

    /**
     * Create the library-directory list.  Pathnames are prepended left to
     * right.
     *
     * @param tagValues The map of all of the current tag values.
     * @param value     The value of the entry currently being processed.
     * @return    A string which contains the new value for the "library" tag.
     */
    private void processLibrary(Map<String, String> tagValues, String value) {
        String newValue;
        if (tagValues.get(RUN) == null) {
            // we haven't seen a "run" action yet
            if (value.trim().length() != 0) {
                // multiple library tags allowed, prepend the new stuff
                String oldValue = tagValues.get(LIBRARY);
                if (oldValue != null)
                    newValue = value.trim() + " " + oldValue;
                else
                    newValue = value.trim();
                tagValues.put(LIBRARY, newValue);
            } else {
                // found an empty library tag
                parseError(tagValues, PARSE_LIB_EMPTY);
            }
        } else {
            // found library tag after the first @run tag
            parseError(tagValues, PARSE_LIB_AFTER_RUN);
        }
    }

    private Set<String> getValidTagNames(boolean allowKey) {
        Set<String> tags = new HashSet<String>();
        // JDK specific tags
        tags.add(TEST);
        tags.add(BUG);
        tags.add(SUMMARY);
        tags.add(AUTHOR);
        tags.add(LIBRARY);
        tags.add(CLEAN);
        tags.add(COMPILE);
        tags.add(IGNORE);
        tags.add(RUN);
        tags.add(BUILD);
        tags.add(REQUIRES);

        // @key allowed only if TEST.ROOT contains a non-empty entry for
        // "key".  This is handled by the testsuite object.
        if (allowKey) {
            tags.add(KEY);
        }

        return tags;
    }

    //----------misc statics----------------------------------------------------

    public static final String TEST    = "test";
    public static final String AUTHOR  = "author";
    public static final String BUG     = "bug";
    public static final String BUILD   = "build";
    public static final String CLEAN   = "clean";
    public static final String COMPILE = "compile";
    public static final String ERROR   = "error";
    public static final String IGNORE  = "ignore";
    public static final String KEY     = "key";
    public static final String LIBRARY = "library";
    public static final String REQUIRES = "requires";
    public static final String RUN     = "run";
    public static final String SUMMARY = "summary";

    private static final String LINESEP = System.getProperty("line.separator");

    static final String[] excludeNames = {
        "SCCS", "Codemgr_wsdata", ".hg", "RCS", ".svn",
        "DeletedFiles", "DELETED-FILES", "deleted_files",
        "TemporarilyRemoved"
    };

    // These are all of the error messages using in the finder.
    protected static final String
        PARSE_TAG_BAD         = "Invalid tag: ",
        PARSE_BUG_EMPTY       = "No value provided for `@bug'",
        PARSE_BUG_INVALID     = "Invalid or unrecognized bugid: ",
        PARSE_KEY_EMPTY       = "No value provided for `@key'",
        PARSE_KEY_BAD         = "Invalid key: ",
        PARSE_LIB_EMPTY       = "No value provided for `@library'",
        PARSE_LIB_AFTER_RUN   = "`@library' must appear before first `@run'",
        PARSE_BAD_RUN         = "Explicit action tag not allowed",
        PARSE_REQUIRES_EMPTY  = "No expression for @requires",
        PARSE_REQUIRES_SYNTAX = "Syntax error in @requires expression: ",
        PARSE_RUN_ENDS_WITH_BUILD = "No action after @build",
        PARSE_MULTIPLE_COMMENTS_NOT_ALLOWED
                              = "Multiple test descriptions not allowed";

    private static final Pattern
        BOOTCLASSPATH_OPTION = getOptionPattern("bootclasspath"),
        OTHERVM_OPTION =       getOptionPattern("othervm"),
        MANUAL_OPTION  =       getOptionPattern("manual"),
        NATIVE_OPTION  =       getOptionPattern("native"),
        SHELL_ACTION   =       getActionPattern("shell"),
        JUNIT_ACTION   =       getActionPattern("junit"),
        DRIVER_ACTION  =       getActionPattern("driver"),
        IGNORE_ACTION  =       getActionPattern("ignore");

    private static Pattern getActionPattern(String name) {
        return Pattern.compile("(?s).*\\Q" + Action.REASON_USER_SPECIFIED + " " + name + "\\E\\b.*");
    }

    private static Pattern getOptionPattern(String name) {
        return Pattern.compile("(?s).*/" + name + "[/= \t].*");
    }

    //----------member variables------------------------------------------------

    private final Set<String> validTagNames;
    private final TestProperties properties;
    private final boolean checkBugID;
    private final RegressionContext baseContext;

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(RegressionTestFinder.class);
    private static final boolean rejectTrailingBuild =
            !Boolean.getBoolean("javatest.regtest.allowTrailingBuild");
}
