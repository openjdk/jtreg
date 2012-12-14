/*
 * Copyright (c) 1997, 2007, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javatest.finder.TagTestFinder;
import com.sun.javatest.finder.HTMLCommentStream;
import com.sun.javatest.finder.ShScriptCommentStream;

/**
  * This is a specific implementation of the TagTestFinder which is to be used
  * for JDK regression testing.  It follows the test-tag sepcifications as given
  * in http://javaweb.eng/jdk/test/tag-spec.
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
      */
    public RegressionTestFinder(Set<String> validKeys, boolean checkBugID) {
        this.rootValidKeys = validKeys;
        this.checkBugID = checkBugID;

        validTagNames = new ValidTagNames(validKeys != null);

        exclude(excludeNames);
        addExtension(".sh", ShScriptCommentStream.class);
        addExtension(".html", HTMLCommentStream.class);
    }

    @Override
    protected void setRoot(File testSuiteRoot) throws Fault {
        super.setRoot(testSuiteRoot = canon(testSuiteRoot));
        validKeysTable = new ValidKeysTable(testSuiteRoot, rootValidKeys);
    }

    @Override protected void scanFile(File file) {
        // filter out SCCS leftovers
        if (file.getName().startsWith(","))
            return;
        super.scanFile(file);
    }

    private File canon(File f) {
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return f.getAbsoluteFile();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Map<String, String> normalize(Map tv) {
        return normalize0((Map<String,String>) tv);
    }

    private Map<String,String> normalize0(Map<String,String> tagValues) {
        File currFile = getCurrentFile();
        HashMap<String,String> newTagValues = new HashMap<String,String>();
        String fileName = currFile.getName();
        String baseName = fileName.substring(0, fileName.lastIndexOf("."));

        // default values
        newTagValues.put("title", " ");
        newTagValues.put("source", fileName);

        if (fileName.endsWith(".sh"))
            newTagValues.put("run", Action.REASON_ASSUMED_ACTION + " shell "
                             + fileName + LINESEP);
        else if (fileName.endsWith(".java")) // we have a ".java" file
            newTagValues.put("run", Action.REASON_ASSUMED_ACTION + " main "
                             + baseName + LINESEP);
        else // we have a ".html" file
            newTagValues.put("run", Action.REASON_ASSUMED_ACTION + " applet "
                             + fileName + LINESEP);

        // translate between JDK tags and JavaTest tags; for required
        // JavaTest fields, make sure that we make a reasonable assumption
        for (Map.Entry<? extends String, ? extends String> e: tagValues.entrySet()) {
            String name  = e.getKey();
            String value = e.getValue();
            if (name.equals("summary")) {
                // the title is the first sentance of the provided summary
                name = "title";
                int pos = 0;
            loop:
                while (true) {
                    pos = value.indexOf(".", pos);
                    if (pos == -1 || pos + 1 == value.length())
                        break loop;
                    switch (value.charAt(pos + 1)) {
                    case ' ':
                    case '\n':
                    case '\r':
                    case '\t':
                    case '\f':
                    case '\b':
                        value = value.substring(0, pos);
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

        // force more key words based on actions
        String value = newTagValues.get("run");

        String origKeywords = newTagValues.get("keywords");
        String addKeywords  = "";

        if (match(value, OTHERVM_OPTION))
            addKeywords += " othervm";

        if (match(value, MANUAL_OPTION))
            addKeywords += " manual";

        if (match(value, SHELL_ACTION))
            addKeywords += " shell";

        if (match(value, JUNIT_ACTION))
            addKeywords += " junit";

        if (match(value, IGNORE_ACTION))
            addKeywords += " ignore";

        if (!addKeywords.equals("")) {
            if (origKeywords == null)
                newTagValues.put("keywords", addKeywords.trim());
            else
                newTagValues.put("keywords", origKeywords + addKeywords);
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
    @SuppressWarnings("unchecked")
    @Override
    protected void processEntry(Map tv, String name, String value)
    {
        Map<String,String> tagValues = (Map<String,String>) tv;

        // check for valid tag name, don't produce error message for the
        // the SCCS sequence '%' 'W' '%'
        if (name.startsWith("(#)"))
            return;

        // translate the shorthands into run actions
        if (name.startsWith("compile")
            || name.startsWith("clean")
            || name.startsWith("build")
            || name.startsWith("ignore")) {
            value = name + " " + value;
            name = "run";
        }

        try {
            if (!validTagNames.isValid(name)) {
                throw new ParseException(PARSE_TAG_BAD + name);
            } else if (name.equals("run")) {
                value = parseRun(tagValues, value);
            } else if (name.equals("bug")) {
                value = parseBug(tagValues, value);
            } else if (name.equals("key")) {
                value = parseKey(tagValues, value);
            } else if (name.equals("library")) {
                value = parseLibrary(tagValues, value);
            }
        } catch (ParseException e) {
            name  = "error";
            value = e.getMessage();
        }

        super.processEntry(tagValues, name, value);
    }

    //-----internal routines----------------------------------------------------

    //---------- parsing -------------------------------------------------------

    /**
     * Create the "run" action entry by adding a reason code to the
     * user-provided action.  Each action is separated by LINESEP.
     *
     * @param tagValues The map of all of the current tag values.
     * @param value     The value of the entry currently being processed.
     * @exception ParseException If the value for the tag does not conform to
     *            the spec for the tag.
     * @return    A string which contains the new value for the "run" tag.
     */
    private String parseRun(Map<String,String> tagValues, String value)
        throws ParseException
    {
        String oldValue = tagValues.get("run");
        if (oldValue == null)
            return Action.REASON_USER_SPECIFIED + " " + value + LINESEP;
        else
            return oldValue + Action.REASON_USER_SPECIFIED + " " + value +
                LINESEP;
    }

    /**
     * Verify that all bugs are properly formatted.  Each provided bugid must
     * be entirely composed of exactly seven digits.
     *
     * @param tagValues The map of all of the current tag values.
     * @param value     The value of the entry currently being processed.
     * @exception ParseException If the value for the tag does not conform to
     *            the spec for the tag.
     * @return    A string which contains the new value for the "bug" tag.
     */
    private String parseBug(Map<String,String> tagValues, String value)
        throws ParseException
    {
        StringBuilder newValue = new StringBuilder();

        if (value.trim().length() != 0) {
            String[] bugids = StringArray.splitWS(value);
            for (int i = 0; i < bugids.length; i++) {
                String currBug = bugids[i];

                // bugid checking can be switched on and off with an
                // environment var. that the testsuite finds
                if (checkBugID) {
                    // bugid must be exactly 7 digits long
                    if (currBug.length() != 7)
                        throw new ParseException(PARSE_BUG_BAD_LEN + currBug);

                    // bugid must consist entirely of digits
                    if (!isDigitString(currBug))
                        throw new ParseException(PARSE_BUG_NON_DIGIT + currBug);
                }

                if (newValue.length() > 0)
                    newValue.append(" ");
                newValue.append("bug").append(currBug);
            }
        } else {
            throw new ParseException(PARSE_BUG_EMPTY);
        }
        return newValue.toString();
    }

    /**
     * Verify that the provided set of keys are allowed for the current
     * test-suite.  The set of keys is stored in the system property
     * <code>env.regtest.key</code>.
     *
     * @param tagValues The map of all of the current tag values.
     * @param value     The value of the entry currently being processed.
     * @exception ParseException If the value for the tag does not conform to
     *            the spec for the tag.
     * @return    A string which contains the new value for the "key" tag.
     */
    private String parseKey(Map<String,String> tagValues, String value)
        throws ParseException
  {
        Set<String> validKeys = validKeysTable.getValidKeys(getCurrentFile());

        // make sure that the provided keys are all valid
        if (value.trim().length() != 0) {
            String[] keys = StringArray.splitWS(value);
            for (int i = 0; i < keys.length; i++) {
                if (!validKeys.contains(keys[i])) {
                    // invalid key
                    throw new ParseException(PARSE_KEY_BAD + keys[i]);
                }
            }
            return StringArray.join(keys, " ");
        } else {
            throw new ParseException(PARSE_KEY_EMPTY);
        }
    }

    /**
     * Create the library-directory list.  Pathnames are prepended left to
     * right.
     *
     * @param tagValues The map of all of the current tag values.
     * @param value     The value of the entry currently being processed.
     * @exception ParseException If the value for the tag does not conform to
     *            the spec for the tag.
     * @return    A string which contains the new value for the "library" tag.
     */
    private String parseLibrary(Map tagValues, String value)
        throws ParseException
    {
        String newValue = "";
        if (tagValues.get("run") == null) {
            // we haven't seen a "run" action yet
            if (value.trim().length() != 0) {
                // multiple library tags allowed, prepend the new stuff
                String oldValue = (String) tagValues.get("library");
                if (oldValue != null)
                    newValue = value + " " + oldValue;
                else
                    newValue = value;
            } else {
                // found an empty library tag
                throw new ParseException(PARSE_LIB_EMPTY);
            }
        } else {
            // found library tag after the first @run tag
            throw new ParseException(PARSE_LIB_AFTER_RUN);
        }
        return newValue.trim();
    }

    /**
     * Given a string, determine whether it consists entirely of digits.
     *
     * @param s         The string to examine
     * @return          <code>true</code> the string consists entirely of
     *                  digits, <code>false</code> otherwise.
     */
    private boolean isDigitString(String s) {
        for (int i = 0; i < s.length(); i++)
            if (!Character.isDigit(s.charAt(i)))
                return false;
        return true;
    }

    //----------internal classes------------------------------------------------

    /*
     * HashMap of acceptable / valid tag names.
     */
    private static class ValidTagNames {
        public ValidTagNames(boolean allowKey) {
            validTags = new HashSet<String>(29);
            populate(allowKey);
        }

        public boolean isValid(String tag) {
            return validTags.contains(tag);
        }

        private void add(String validTag) {
            validTags.add(validTag);
        }

        /*
         * Put the acceptable / valid reserved private tags into a HashMap.
         */
        private void populate(boolean allowKey) {
            // JDK specific tags
            add("test");
            add("bug");
            add("summary");
            add("author");
            add("library");
            add("clean");
            add("compile");
            add("ignore");
            add("run");
            add("build");

            // @key allowed only if TEST.ROOT contains a non-empty entry for
            // "key".  This is handled by the testsuite object.
            if (allowKey) {
                add("key");
            }
        }

        private Set<String> validTags;
    }

    /**
     * A table giving the set of valid keys for any file in the test suite.
     * The keys are determined from TEST.ROOT and any TEST.properties
     * files in any enclosing directories up to the root directory.
     */
    private static class ValidKeysTable {
        ValidKeysTable(File rootDir, Set<String> rootKeys) {
            rootCacheEntry = new CacheEntry(rootDir, rootKeys);
        }

        /**
         * Get the set of valid keys for a particular file in the test suite.
         * A cache is kept of the value for the directory of the last file
         * read, to optimize the performance when processing a series of files
         * in the same directory.
         */
        synchronized Set<String> getValidKeys(File file) {
            if (!allowLocalKeys)
                return rootCacheEntry.keys;
            File dir = file.getParentFile();
            if (lastCacheEntry == null || !lastCacheEntry.dir.equals(dir))
                lastCacheEntry = getCacheEntry(dir);
            return lastCacheEntry.keys;
        }
        // where
        private CacheEntry lastCacheEntry;


        private CacheEntry getCacheEntry(File dir) {
            if (dir.equals(rootCacheEntry.dir))
                return rootCacheEntry;

            CacheEntry parent = getCacheEntry(dir.getParentFile());
            CacheEntry child = parent.children == null ? null : parent.children.get(dir.getName());
            if (child == null) {
                child = new CacheEntry(dir, getLocalKeys(dir, parent.keys));
                // for a full cache, add child into parent
                //    if (parent.children == null)
                //        parent.children = new HashMap<String,CacheEntry>();
                //    parent.children.put(dir.getName(), child);
                // for a small cache, just cache the current path by keeping the latest child
                parent.children = Collections.singletonMap(dir.getName(), child);
            }
            return child;
        }

        private static Set<String> getLocalKeys(File dir, Set<String> parentKeys) {
            File f = new File(dir, "TEST.properties");
            if (f.canRead()) {
                try {
                    BufferedInputStream in = new BufferedInputStream(new FileInputStream(f));
                    Properties p = new Properties();
                    p.load(in);
                    in.close();
                    String k = p.getProperty("keys");
                    if (k != null && k.trim().length() > 0) {
                        Set<String> s = new HashSet<String>(parentKeys);
                        s.addAll(Arrays.asList(StringArray.splitWS(k)));
                        return s;
                    }
                } catch (IOException ignore) {
                }
            }
            return parentKeys;
        }

        private static class CacheEntry {
            CacheEntry(File dir, Set<String> keys) {
                dir.getClass();
                this.dir = dir;
                this.keys = keys;
            }

            File dir;
            Set<String> keys;
            Map<String, CacheEntry> children;
        }

        private CacheEntry rootCacheEntry;

    }

    //----------misc statics----------------------------------------------------

    private static final String LINESEP = System.getProperty("line.separator");

    private static final String[] excludeNames = {
        "SCCS", "Codemgr_wsdata", ".hg", "RCS", ".svn",
        "DeletedFiles", "DELETED-FILES", "deleted_files",
        "TemporarilyRemoved"
    };

    // These are all of the error messages using in the finder.
    protected static final String
        PARSE_TAG_BAD         = "Invalid tag: ",
        PARSE_BUG_EMPTY       = "No value provided for `@bug'",
        PARSE_BUG_BAD_LEN     = "Unexpected length for bugid: ",
        PARSE_BUG_NON_DIGIT   = "Non-digit found in bugid: ",
        PARSE_KEY_EMPTY       = "No value provided for `@key'",
        PARSE_KEY_BAD         = "Invalid key: ",
        PARSE_LIB_EMPTY       = "No value provided for `@library'",
        PARSE_LIB_AFTER_RUN   = "`@library' must appear before first `@run'";

    private static final boolean allowLocalKeys =
            Boolean.parseBoolean(System.getProperty("javatest.regtest.allowLocalKeys", "true"));

    private static final Pattern
        OTHERVM_OPTION = Pattern.compile(".*/othervm[/ \t].*",    Pattern.DOTALL),
        MANUAL_OPTION  = Pattern.compile(".*/manual[/= \t].*",    Pattern.DOTALL),
        SHELL_ACTION   = Pattern.compile(".*[ \t]shell[/ \t].*",  Pattern.DOTALL),
        JUNIT_ACTION   = Pattern.compile(".*[ \t]junit[/ \t].*",  Pattern.DOTALL),
        IGNORE_ACTION  = Pattern.compile(".*[ \t]ignore[/ \t].*", Pattern.DOTALL);

    //----------member variables------------------------------------------------

    private Set<String> rootValidKeys;
    private ValidTagNames validTagNames;
    private ValidKeysTable validKeysTable;
    private boolean checkBugID;
}
