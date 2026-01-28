/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
import com.sun.javatest.regtest.agent.Flags;
import com.sun.javatest.regtest.exec.Action;
import com.sun.javatest.regtest.util.StringUtils;
import com.sun.javatest.util.I18NResourceBundle;

/**
  * This is a specific implementation of the TagTestFinder which is to be used
  * for JDK regression testing.  It follows the test-tag specifications as given
  * in http://openjdk.org/jtreg/tag-spec.txt.
  *
  * A test description consists of a single block comment in either Java files
  * or shell-script files.  A file may contain multiple test descriptions.
  *
  * @see com.sun.javatest.TestFinder
  * @see com.sun.javatest.finder.TagTestFinder
  */
public final class RegressionTestFinder extends TagTestFinder
{
    /**
     * Constructs the list of file names to exclude for pruning in the search
     * for files to examine for test descriptions.  This constructor also sets
     * the allowable comment formats.
     *
     * @param properties the test suite properties manager
     * @param errHandler a handler for error messages
     */
    public RegressionTestFinder(TestProperties properties, ErrorHandler errHandler) {
        setErrorHandler(errHandler);
        this.properties = properties;
        this.checkBugID = properties.checkBugID;

        Set<String> rootValidKeys = properties.validKeys;
        validTagNames = getValidTagNames(rootValidKeys != null);

        exclude(excludeNames);
        addExtension(".sh", ShScriptCommentStream.class);
        addExtension(".html", HTMLCommentStream.class);
        addExtension(".jasm", JavaCommentStream.class);
        addExtension(".jcod", JavaCommentStream.class);

        baseContext = RegressionContext.getDefault();
    }

    @SuppressWarnings("unchecked")
    Set<String> getAllowedExtensions() {
        return ((Map<String, ?>) getField("extensionTable")).keySet();
    }

    @SuppressWarnings("unchecked")
    Set<String> getIgnoredDirectories() {
        return ((Map<String, ?>) getField("excludeList")).keySet();
    }

    private Object getField(String name) {
        try {
            Field f = TagTestFinder.class.getDeclaredField(name);
            try {
                f.setAccessible(true);
                return f.get(this);
            } finally {
                f.setAccessible(false);
            }
        } catch (NoSuchFieldException
                 | SecurityException
                 | IllegalArgumentException
                 | IllegalAccessException e) {
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
        if (traceFinder) {
            System.err.println("RegressionTestFinder: reading " + file);
        }

        try {
            File tngRoot = properties.getTestNGRoot(file);
            if (tngRoot != null) {
                scanTestNGFile(tngRoot, file);
            } else {
                File junitRoot = properties.getJUnitRoot(file);
                if (junitRoot != null) {
                    scanJUnitFile(junitRoot, file);
                } else {
                    //super.scanFile(file);
                    modifiedScanFile(file);
                }
            }
        } catch (TestSuite.Fault e) {
            error(i18n, "finder.cant.read.test.properties", e.getMessage());
        }
    }

    /**
     * Scan a file, looking for comments and in the comments, for test
     * description data.
     * @param file The file to scan
     */
    // This is a proposed new version for TagTestFinder.scanFile.
    // The significant change is to look ahead for any additional
    // comments when deciding whether or not to set an id for the
    // test description. With this change, if a test file contains
    // more than one test description, all test descriptions are
    // given a unique id; previously, the first test description did not.
    // The externally visible effect is that putting the name of the
    // file on the command line explicitly will cause *all* the
    // tests in that file to be run, and not just the first.
    protected void modifiedScanFile(File file) {
        I18NResourceBundle super_i18n;
        boolean super_fastScan;
        try {
            Field i18nField = TagTestFinder.class.getDeclaredField("i18n");
            i18nField.setAccessible(true);
            super_i18n = (I18NResourceBundle) i18nField.get(this);
            Field fastScanField = TagTestFinder.class.getDeclaredField("fastScan");
            fastScanField.setAccessible(true);
            super_fastScan = (boolean) fastScanField.get(this);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new Error(ex);
        }
        int testDescNumber = 0;
        String name = file.getName();
        int dot = name.indexOf('.');
        if (dot == -1)
            return;
        String extn = name.substring(dot);
        Class<? extends CommentStream> csc = getClassForExtension(extn);
        if (csc == null) {
            error(super_i18n, "tag.noParser", file, extn);
            return;
        }
        CommentStream cs;
        try {
            cs = csc.getDeclaredConstructor().newInstance();
        }
        catch (ReflectiveOperationException e) {
            error(super_i18n, "tag.cantCreateClass", csc.getName(), extn);
            return;
        }

        try {
            LineCounterBufferedReader r = new LineCounterBufferedReader(new FileReader(file));
            cs.init(r);
            if (super_fastScan)
                cs.setFastScan(true);

            String comment = cs.readComment();
            int commentLine = r.lineNumber;
            while (comment != null) {
                Map<String,String> tagValues = parseComment(comment, file);

                // Look ahead to see if there are more comments
                String nextComment = cs.readComment();
                int nextCommentLine = r.lineNumber;

                if (!tagValues.isEmpty()) {
                    if (tagValues.get("id") == null) {
                        // if there are more comments to come, or if there have already
                        // been additional comments, set an explicit id for each set of tags
                        if ((nextComment != null && nextComment.trim().startsWith("@test")) || testDescNumber != 0) {
                            String test = tagValues.get("test");
                            Matcher m;
                            String id = (test != null
                                    && (m = Pattern.compile("id=(?<id>[A-Za-z0-9-_]+)\\b.*").matcher(test)).matches())
                                    ? m.group("id")
                                    : "id" + testDescNumber;
                            tagValues.put("id", id);
                        }
                        testDescNumber++;
                    }

                    // The "test" marker can now be removed so that we don't waste
                    // space unnecessarily.  We need to do the remove *after* the
                    // isEmpty() check because of the potential to interfere with
                    // defaults based on file extension. (i.e. The TD /* @test */
                    // still needs to evaluate to a valid test description.)
                    tagValues.remove("test");

                    foundTestDescription(tagValues, file, commentLine);
                }

                comment = nextComment;
                commentLine = nextCommentLine;
            }
        }
        catch (FileNotFoundException e) {
            error(super_i18n, "tag.cantFindFile", file);
        }
        catch (IOException e) {
            error(super_i18n, "tag.ioError", file);
        }
        finally {
            try {
                cs.close();
            }
            catch (IOException e) {
            }
        }
    }

    private static class LineCounterBufferedReader extends BufferedReader {
        int lineNumber;

        LineCounterBufferedReader(FileReader r) {
            super(r);
            lineNumber = 1;
        }
        @Override
        public int read() throws IOException {
            int ch = super.read();
            checkNewline(ch);
            return ch;
        }

        @Override
        public String readLine() throws IOException {
            String line = super.readLine();
            lineNumber++;
            return line;
        }

        @Override
        public int read(char[] buf, int offset, int length) throws IOException {
            int n = super.read(buf, offset, length);
            for (int i = offset; i < offset + n; i++) {
                checkNewline(buf[i]);
            }
            return n;
        }

        private void checkNewline(int ch) {
            if (ch == '\n') {
                lineNumber++;
            }
        }
    }

    protected void scanTestNGFile(File tngRoot, File file) throws TestSuite.Fault {
        if (isTestNGTest(file)) {
            scanFile(tngRoot, file, "testngClass", true);
        }
    }

    protected void scanJUnitFile(File junitRoot, File file) throws TestSuite.Fault {
        if (isJUnitTest(file)) {
            scanFile(junitRoot, file, "junitClass", false);
        }
    }

    protected void scanFile(File junitRoot, File file, String classPropertyName, boolean setImportsJUnit) throws TestSuite.Fault {
        Map<String,String> tagValues;
        PackageImportParser p = new PackageImportParser(junitRoot, file);
        p.parse();
        String className = p.inferClassName();
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            tagValues = readComments(file, in);
            if (tagValues == null) {
                tagValues = new HashMap<>();
                // could read more of file looking for annotations like @Test, @Factory
                // to guess whether this is really a test file or not
            }

            tagValues.put("packageRoot", getRootDir().toURI().relativize(junitRoot.toURI()).getPath());
            if (className == null) {
                tagValues.put("error", "cannot determine class name");
            } else {
                tagValues.put(classPropertyName, className);
            }
            if (setImportsJUnit &&  p.importsJUnit) {
                tagValues.put("importsJUnit", "true");
            }

            Set<String> libDirs = properties.getLibDirs(file);
            if (libDirs != null && !libDirs.isEmpty()) {
                tagValues.put("library", StringUtils.join(libDirs, " "));
            }

            foundTestDescription(tagValues, file, /*line*/0);
        } catch (IOException e) {
            error(i18n, "finder.ioError", file);
        }
    }

    private class PackageImportParser {
        private final File rootDir;
        private final File file;
        String packageName;
        boolean importsJUnit;

        PackageImportParser(File rootDir, File file) {
            this.rootDir = rootDir;
            this.file = file;
        }

        void parse() {
            try (BufferedReader in = new BufferedReader(new FileReader(file))) {
                StreamTokenizer st = new StreamTokenizer(in);
                st.resetSyntax();
                st.slashSlashComments(true);
                st.slashStarComments(true);
                st.wordChars('a', 'z');
                st.wordChars('A', 'Z');
                st.wordChars('$', '$');
                st.wordChars('_', '_');
                // the following are treated as word characters to simplify parsing
                // package names and imports as a single token
                st.wordChars('0', '9');
                st.wordChars('.', '.');
                st.wordChars('*', '*');
                st.whitespaceChars(0, ' ');
                st.eolIsSignificant(false);

                // parse package and import statements
                int t;
                while ((t = st.nextToken()) != StreamTokenizer.TT_EOF) {
                    if (t != StreamTokenizer.TT_WORD)
                        return;

                    switch (st.sval) {
                        case "package":
                            if (st.nextToken() != StreamTokenizer.TT_WORD)
                                return;
                            packageName = st.sval;
                            if (st.nextToken() != ';')
                                return;
                            break;

                        case "import":
                            t = st.nextToken();
                            if (t == StreamTokenizer.TT_WORD && st.sval.equals("static")) {
                                t = st.nextToken();
                            }
                            if (t == StreamTokenizer.TT_WORD && st.sval.startsWith("org.junit")) {
                                importsJUnit = true;
                                return; // no need to read further
                            }
                            if (st.nextToken() != ';')
                                return;
                    }
                }
            } catch (IOException e) {
                error(i18n, "finder.ioError", file);
            }
        }

        String inferClassName() {
            String path = rootDir.toURI().relativize(file.toURI()).getPath();
            String fn = file.getName();
            String cn = fn.replace(".java", "");
            String pkg_fn = (packageName == null) ? file.getName() : packageName.replace('.', '/') + "/" + fn;
            if (path.equalsIgnoreCase(pkg_fn)) {
                return (packageName == null) ? cn : packageName + "." + cn;
            } else if (path.toLowerCase().endsWith("/" + pkg_fn.toLowerCase())) {
                String mn = path.substring(0, path.length() - pkg_fn.length());
                return  mn + ((packageName == null) ? cn : packageName + "." + cn);
            } else {
                return null;
            }
        }
    }

    private Map<String,String> readComments(File file, BufferedReader in) throws IOException {
        CommentStream cs = new JavaCommentStream();
        cs.init(in);
        cs.setFastScan(true);

        Map<String, String> tagValues = null;
        String comment;
        int index = 1;
        while ((comment = cs.readComment()) != null) {
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
        return isClassOrInterfaceFile(file);
    }

    protected boolean isJUnitTest(File file) {
        // for now, ignore comments and annotations, and
        // assume *.java is a test
        return isClassOrInterfaceFile(file);
    }

    private boolean isClassOrInterfaceFile(File file) {
        String name = file.getName();
        return name.endsWith(".java")
                && !name.equals("module-info.java")
                && !name.equals("package-info.java");
    }

    private File canon(File f) {
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return new File(f.getAbsoluteFile().toURI().normalize());
        }
    }

    protected Map<String, String> normalize(Map<String, String> tagValues) {
        Map<String, String> newTagValues = new HashMap<>();
        String fileName = getCurrentFile().getName();
        String baseName = fileName.substring(0, fileName.lastIndexOf("."));
        boolean isTestNG = tagValues.containsKey("testngClass");
        boolean isJUnit = tagValues.containsKey("junitClass");

        // default values
        newTagValues.put("title", " ");
        newTagValues.put("source", fileName);

        if (isTestNG) {
            if (tagValues.get("run") != null) {
                tagValues.put("error", PARSE_BAD_RUN);
            }
            String className = tagValues.get("testngClass");
            newTagValues.put("run", Action.REASON_ASSUMED_ACTION + " testng "
                             + className + LINESEP);
        } else if (isJUnit) {
            if (tagValues.get("run") != null) {
                tagValues.put("error", PARSE_BAD_RUN);
            }
            String className = tagValues.get("junitClass");
            newTagValues.put("run", Action.REASON_ASSUMED_ACTION + " junit "
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
            switch (name) {
                case "summary":
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
                    break;
                case "bug":
                case "key":
                    {
                        // force some keywords
                        name = "keywords";
                        String oldValue = newTagValues.get("keywords");
                        if (oldValue != null)
                            value = oldValue + " " + value;
                        break;
                    }
                case "test":
                    {
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
                        break;
                    }
                default:
                    break;
            }
//          System.out.println("--- NAME: " + name + " VALUE: " + value);
            newTagValues.put(name, value);
        }

        String value = newTagValues.get("run");

        // force more key words based on actions
        Set<String> keywords = split(newTagValues.get("keywords"), "\\s+");

        if (match(value, OTHERVM_OPTION) || match(value, BOOTCLASSPATH_OPTION))
            keywords.add("othervm");

        if (match(value, MANUAL_OPTION))
            keywords.add("manual");

        if (match(value, NATIVE_OPTION))
            keywords.add("native");

        if (match(value, SHELL_ACTION))
            keywords.add("shell");

        if (match(value, JUNIT_ACTION) || isJUnit)
            keywords.add("junit");

        if (match(value, TESTNG_ACTION) || isTestNG)
            keywords.add("testng");

        if (match(value, DRIVER_ACTION))
            keywords.add("driver");

        if (match(value, IGNORE_ACTION))
            keywords.add("ignore");

        newTagValues.put("keywords", StringUtils.join(keywords, " "));

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
            if (t == 0) {
                // zero means no-limit
                maxTimeout = 0;
                break;
            }
            if (t > maxTimeout)
                maxTimeout = t;
        }
        if (maxTimeout > 0)
            newTagValues.put("maxTimeout", String.valueOf(maxTimeout));

        try {
            String modules = newTagValues.get(MODULES);
            if (modules == null || modules.isEmpty()) {
                Set<String> defaultModules = properties.getModules(getCurrentFile());
                if (defaultModules != null && !defaultModules.isEmpty()) {
                    processModules(newTagValues, defaultModules);
                }
            }

            String enablePreview = newTagValues.get(ENABLE_PREVIEW);
            if (enablePreview == null) {
                boolean ep = properties.getEnablePreview(getCurrentFile());
                if (ep) {
                    newTagValues.put(ENABLE_PREVIEW, "true");
                }
            }
        } catch (TestSuite.Fault e) {
            error(i18n, "finder.cant.read.test.properties", e.getMessage());
        }

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

    private Set<String> split(String s, String regex) {
        Set<String> result = new LinkedHashSet<>();
        if (s != null) {
            result.addAll(List.of(s.split(regex)));
        }
        return result;
    }

    /**
     * Make sure that the provided name-value pair is of the proper format as
     * described in the tag-spec.
     *
     * @param entries   The map of the entries being read
     * @param name      The name of the entry that has been read
     * @param value     The value of the entry that has been read
     */
    protected void processEntry(Map<String, String> entries, String name, String value)
    {
        // translate the shorthands into run actions
        if (name.startsWith(COMPILE)
            || name.startsWith(CLEAN)
            || name.startsWith(BUILD)
            || name.startsWith(IGNORE)) {
            value = name + " " + value;
            name = RUN;
        }

        try {
            switch (name) {
                case RUN:
                    processRun(entries, value);
                    break;

                case BUG:
                    processBug(entries, value);
                    break;

                case REQUIRES:
                    processRequires(entries, value);
                    break;

                case KEY:
                    processKey(entries, value);
                    break;

                case MODULES:
                    processModules(entries, value);
                    break;

                case LIBRARY:
                    processLibrary(entries, value);
                    break;

                case COMMENT:
                    // no-op
                    break;

                case ENABLE_PREVIEW:
                    processEnablePreview(entries, value);
                    break;

                default:
                    if (!validTagNames.contains(name)) {
                        parseError(entries, PARSE_TAG_BAD + name);
                    } else {
                        entries.put(name, value);
                    }
            }
        } catch (TestSuite.Fault e) {
            reportError(entries, e.getMessage());
        }
    }

    @Override
    protected void foundTestDescription(TestDescription td) {
        String wrp = TestResult.getWorkRelativePath(td);
        TestDescription other = paths.get(wrp);
        if (other != null && !td.getRootRelativeURL().equals(other.getRootRelativeURL())) {
            error(i18n, "finder.jtrClash", td.getFile(), other.getFile());
            return;
        }

        super.foundTestDescription(td);
        paths.put(wrp, td);

    }
    Map<String, TestDescription> paths = new HashMap<>();

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
        tagValues.putIfAbsent(ERROR, value);
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
        for (String bugid : StringUtils.splitWS(value)) {
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

        tagValues.merge(REQUIRES, value, (a, b) -> "(" + a + ") & (" + b + ")");
    }

    /**
     * Verify that the provided set of keys are allowed for the current
     * test-suite.  The set of keys is stored in the system property
     * {@code env.regtest.key}.
     *
     * @param tagValues The map of all of the current tag values.
     * @param value     The value of the entry currently being processed.
     */
    private void processKey(Map<String, String> tagValues, String value)
            throws TestSuite.Fault {
        // @key is allowed to appear only once in a test definition
        if (tagValues.containsKey(KEY)) {
            reportError(tagValues, PARSE_MULTIPLE_KEY_NOT_ALLOWED);
            return;
        }
        if (value.trim().length() == 0) {
            parseError(tagValues, PARSE_KEY_EMPTY);
            return;
        }

        // make sure that the provided keys are all valid
        Set<String> validKeys = properties.getValidKeys(getCurrentFile());
        StringBuilder newValue = new StringBuilder();
        for (String key: StringUtils.splitWS(value)) {
            String k = key.replace("-", "_");
            if (!validKeys.contains(k)) {
                parseError(tagValues, PARSE_KEY_BAD + key);
                continue;
            }
            if (newValue.length() > 0)
                newValue.append(" ");
            newValue.append(k);
        }

        if (newValue.length() > 0)
            tagValues.put(KEY, newValue.toString());
    }

    /**
     * Analyse the contents of @modules.
     * @param tagValues The map of all of the current tag values.
     * @param value     The value of the entry currently being processed.
     */
    private void processModules(Map<String, String> tagValues, String value)
            throws TestSuite.Fault {
        if (value.trim().length() == 0) {
            parseError(tagValues, PARSE_MODULES_EMPTY);
            return;
        }

        processModules(tagValues, List.of(value.trim().split("\\s+")));
    }

    private void processModules(Map<String, String> tagValues, Collection<String> modules) {
        for (String word : modules) {
            try {
                Modules.Entry m = Modules.parse(word);
            } catch (Modules.Fault f) {
                parseError(tagValues, PARSE_BAD_MODULE + f.getMessage());
                return;
            }
        }

        String oldValue = tagValues.get(MODULES);
        String value = StringUtils.join(modules, " ");
        if (oldValue == null)
            tagValues.put(MODULES, value);
        else
            tagValues.put(MODULES, oldValue + " " + value);
    }

    /**
     * Create the library-directory list.  Path names are prepended left to
     * right.
     *
     * @param tagValues The map of all of the current tag values.
     * @param value     The value of the entry currently being processed.
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

    private void processEnablePreview(Map<String, String> tagValues, String value) {
        if (value.isEmpty()) {
            tagValues.put(ENABLE_PREVIEW, "true");
        } else {
            String v = value.trim();
            switch (v) {
                case "false":
                case "true":
                    tagValues.put(ENABLE_PREVIEW, v);
                    break;
                default:
                    parseError(tagValues, PARSE_INVALID_ENABLE_PREVIEW + v);
            }
        }

    }

    private Set<String> getValidTagNames(boolean allowKey) {
        Set<String> tags = new HashSet<>();
        // JDK specific tags
        tags.add(TEST);
        tags.add(BUG);
        tags.add(SUMMARY);
        tags.add(AUTHOR);
        tags.add(LIBRARY);
        tags.add(MODULES);
        tags.add(CLEAN);
        tags.add(COMPILE);
        tags.add(IGNORE);
        tags.add(RUN);
        tags.add(BUILD);
        tags.add(REQUIRES);
        tags.add(COMMENT);
        tags.add(ENABLE_PREVIEW);

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
    public static final String ENABLE_PREVIEW = "enablePreview";
    public static final String ERROR   = "error";
    public static final String IGNORE  = "ignore";
    public static final String KEY     = "key";
    public static final String LIBRARY = "library";
    public static final String MODULES = "modules";
    public static final String REQUIRES = "requires";
    public static final String RUN     = "run";
    public static final String SUMMARY = "summary";
    public static final String COMMENT = "comment";

    private static final String LINESEP = System.getProperty("line.separator");

    static final String[] excludeNames = { ".hg", ".git" };

    // These are all the error messages used in the finder.
    static final String
        PARSE_TAG_BAD         = "Invalid tag: ",
        PARSE_BUG_EMPTY       = "No value provided for `@bug'",
        PARSE_BUG_INVALID     = "Invalid or unrecognized bugid: ",
        PARSE_KEY_EMPTY       = "No value provided for `@key'",
        PARSE_KEY_BAD         = "Invalid key: ",
        PARSE_MULTIPLE_KEY_NOT_ALLOWED = "@key may be used at most once in a given test",
        PARSE_LIB_EMPTY       = "No value provided for `@library'",
        PARSE_LIB_AFTER_RUN   = "`@library' must appear before first action tag",
        PARSE_MODULES_EMPTY   = "No values provided for @modules",
        PARSE_BAD_MODULE      = "Invalid item in @modules: ",
        PARSE_BAD_RUN         = "Explicit action tag not allowed",
        PARSE_REQUIRES_EMPTY  = "No expression for @requires",
        PARSE_REQUIRES_SYNTAX = "Syntax error in @requires expression: ",
        PARSE_RUN_ENDS_WITH_BUILD = "No action after @build",
        PARSE_MULTIPLE_COMMENTS_NOT_ALLOWED
                              = "Multiple test descriptions not allowed",
        PARSE_INVALID_ENABLE_PREVIEW
                              = "invalid value for @enablePreview: ";


    private static final Pattern
        BOOTCLASSPATH_OPTION = getOptionPattern("bootclasspath"),
        OTHERVM_OPTION =       getOptionPattern("othervm"),
        MANUAL_OPTION  =       getOptionPattern("manual"),
        NATIVE_OPTION  =       getOptionPattern("native"),
        SHELL_ACTION   =       getActionPattern("shell"),
        JUNIT_ACTION   =       getActionPattern("junit"),
        TESTNG_ACTION  =       getActionPattern("testng"),
        DRIVER_ACTION  =       getActionPattern("driver"),
        IGNORE_ACTION  =       getActionPattern("ignore");

    private static Pattern getActionPattern(String name) {
        return Pattern.compile("(?s).*(" + Action.REASON_USER_SPECIFIED + "|" + Action.REASON_ASSUMED_ACTION + ") \\Q" + name + "\\E\\b.*");
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

    private static final boolean traceFinder = Flags.get("traceFinder");
}
