/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.sun.javatest.TestFinder;
import com.sun.javatest.TestResult;
import com.sun.javatest.TestResultTable;
import com.sun.javatest.TestResultTable.TreeIterator;
import com.sun.javatest.TestSuite;
import com.sun.javatest.WorkDirectory;
import com.sun.javatest.regtest.Main.Fault;
import com.sun.javatest.regtest.tool.Version;
import com.sun.javatest.regtest.util.FileUtils;
import com.sun.javatest.util.I18NResourceBundle;

/**
 * Manage tests to be run by jtreg.
 */
public class TestManager {
    public static class NoTests extends Fault {
        private static final long serialVersionUID = 1L;

        public NoTests() {
            super(i18n, "tm.noTests");
        }
    }

    private final TestFinder.ErrorHandler errHandler;
    private final PrintWriter out;
    private final Path baseDir;
    private Path reportDir;
    private Path workDir;
    boolean allowEmptyGroups = true;

    Map<Path, Entry> map = new TreeMap<>();

    /**
     * A GroupSpec embodies an argument of the form  [path]:name  where path is
     * a file path to the root of a test suite and name is the name of a group
     * of tests defined in the files given in the "groups" entry in TEST.ROOT.
     */
    public static class GroupSpec {
        /** The test suite containing the group, or null for the default test suite. */
        final Path dir;
        /** The name for the group. */
        final String groupName;

        /* A "group" argument is of the form  [path]:id  where the path is a file path
         * to the root of the test suite. On Windows, we have to be careful about
         * the ambiguity between an absolute path beginning with a drive letter
         * and a relative path that is a single letter. Therefore, on Windows,
         * we accept the following for a path:
         * - (empty)
         * - a single non-alphabetic character followed by :id
         * - two or more characters followed by :id
         * Thus, letter:id is not accepted as a group spec, and so will be treated
         * elsewhere as a plain absolute file path instead.
         *
         * Additionally, since query strings are indicated by '?', and can contain
         * any string, the '?' character is not allowed to be in the file path, to
         * avoid picking up tests with a query string that contains ':' as being
         * group specs.
         */
        static final Pattern groupPtn = System.getProperty("os.name").matches("(?i)windows.*")
                ? Pattern.compile("(?<dir>|[^A-Za-z?]|[^?]{2,}):(?<group>[A-Za-z0-9_,]+)")
                : Pattern.compile("(?<dir>[^?]*):(?<group>[A-Za-z0-9_,]+)");

        /**
         * Returns true if a string may represent a named group of tests.
         *
         * @param s the string
         * @return true if the string may represent a named group of tests, and false otherwise
         */
        public static boolean isGroupSpec(String s) {
            return groupPtn.matcher(s).matches();
        }

        /**
         * Returns an object indicating a named group of tests in a specific test suite.
         *
         * @param s a string identifying the named group of tests
         * @return an object indicating a named group of tests in a specific test suite
         */
        public static GroupSpec of(String s) {
            Matcher m = groupPtn.matcher(s);
            if (!m.matches()) {
                throw new IllegalArgumentException(s);
            }

            String d = m.group("dir");
            Path dir = d.isEmpty() ? null : Path.of(d);
            String groupName = m.group("group");
            return new GroupSpec(dir, groupName);
        }

        private GroupSpec(Path dir, String groupName) {
            this.dir = dir;
            this.groupName = Objects.requireNonNull(groupName);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GroupSpec groupSpec = (GroupSpec) o;
            return Objects.equals(dir, groupSpec.dir) && groupName.equals(groupSpec.groupName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dir, groupName);
        }

        @Override
        public String toString() {
            return (dir == null ? "" : dir) + ":" + groupName;
        }
    }

    /**
     * A TestSpec embodies an argument of the form path[#id][?query] where
     * path is the path for a file in a test suite, id may indicate the name
     * of a test within that file, and query may indicate the part of that
     * test to be executed.
     *
     * Note the pattern is similar to but intentionally different from that
     * of a URL, where the query component precedes the fragment component.
     * As given, path, id and query are hierarchically related.
     *
     * The path may be a directory to indicate all the tests in the files
     * in and under that directory.
     */
    public static class TestSpec {
        public final Path file;
        public final String id;
        public final String query;

        /**
         * Returns true if a string may represent one or more tests.
         *
         * @param s the string
         * @return true if the string may represent one or more tests, and false otherwise
         */
        public static boolean isTestSpec(String s) {
            return fileIdQueryPtn.matcher(s).matches();
        }

        /**
         * Returns an object indicating one or more tests.
         *
         * @param s a string identifying one or more tests
         * @return an object indicating one or more tests in a specific test suite
         */
        public static TestSpec of(String s) {
            Matcher m = fileIdQueryPtn.matcher(s);
            if (!m.matches()) {
                throw new IllegalArgumentException(s);
            }

            Path file = Path.of(m.group("file"));
            String id = m.group("id"); // may be null
            String query = m.group("query"); // may be null
            return new TestSpec(file, id, query);
        }

        static Pattern fileIdQueryPtn = Pattern.compile("(?<file>.+?)(#(?<id>[A-Za-z0-9-_]+))?(\\?(?<query>.*))?");

        private TestSpec(Path file, String id, String query) {
            this.file = Objects.requireNonNull(file);
            this.id = id;
            this.query = query;
        }

        /**
         * Returns the path for a test, as required by JavaTest.
         * The form contains the file and id as a relative URL.
         * It does not include the query.
         *
         * @return the path for a test
         */
        String getTestPath() {
            return id == null ? pathToString(file) : pathToString(file) + "#" + id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestSpec testSpec = (TestSpec) o;
            return file.equals(testSpec.file) && Objects.equals(id, testSpec.id) && Objects.equals(query, testSpec.query);
        }

        @Override
        public int hashCode() {
            return Objects.hash(file, id, query);
        }

        @Override
        public String toString() {
            return file + (id == null ? "" : "#" + id) + (query == null ? "" : "?" + query);
        }
    }

    /**
     * An object to encapsulate the details for the tests to be run in a single test suite.
     */
    private static class Entry {
        /**
         * The root directory for the test specs and group specs in this entry.
         */
        final Path rootDir;

        /**
         * Whether all tests in the test suite are to be included.
         * When true, the test specs and group specs are ignored.
         */
        boolean all = false;

        /**
         * The test specs for the tests to be run.
         * In these specs, the file is relative to the rootDir.
         */
        final Set<TestSpec> tests = new LinkedHashSet<>();

        /**
         * The names of the groups to be run.
         */
        final Set<String> groups = new LinkedHashSet<>();

        /**
         * The test suite containing the test specs and group specs for this entry.
         */
        RegressionTestSuite testSuite;

        /**
         * The subdirectory to use for the work directory and report directory,
         * when tests are to be run from multiple test suites.
         */
        String subdir;

        /**
         * The work directory to use when running the tests in this entry.
         */
        WorkDirectory workDir;

        /**
         * The report directory to use when running the tests in this entry.
         */
        Path reportDir;

        Entry(Path rootDir) {
            this.rootDir = rootDir;
        }
    }

    public TestManager(PrintWriter out, Path baseDir, TestFinder.ErrorHandler errHandler) {
        this.out = out;
        this.baseDir = baseDir.toAbsolutePath();
        this.errHandler = errHandler;
    }

    public void addTestSpecs(Collection<TestSpec> tests) throws Fault {
        Map<Path, Path> rootDirCache = new HashMap<>();
        for (TestSpec t : tests) {
            Path f = canon(t.file);
            if (!Files.exists(f))
                throw new Fault(i18n, "tm.cantFindFile", t.file);
            Path rootDir = getRootDir(rootDirCache, f);
            if (rootDir == null)
                throw new Fault(i18n, "tm.cantDetermineTestSuite", t.file);

            Entry e = getEntry(rootDir);
            if (f.equals(rootDir)) {
                e.all = true;
                e.tests.clear();
            } else if (!e.all) {
                e.tests.add(new TestSpec(rootDir.relativize(f), t.id, t.query));
            }
        }
    }

    public void addGroupSpecs(Collection<GroupSpec> groups) throws Fault {
        for (GroupSpec g: groups) {
            Path rootDir = canon((g.dir == null) ? baseDir : g.dir);
            if (!Files.exists(rootDir.resolve("TEST.ROOT")))
                throw new Fault(i18n, "tm.badGroupTestSuite", g);
            Entry e = getEntry(rootDir);
            e.groups.add(g.groupName);
        }
    }

    /**
     * Returns whether the test manager is empty or not.
     *
     * @return true if the test manager does not contain any tests specs or group specs.
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * Returns whether the test manager contains test specs or group specs
     * from different test suites.
     *
     * @return true if the test manager contains test specs or group specs
     * from different test suites
     */
    public boolean isMultiRun() {
        return (map.size() > 1);
    }

    /**
     * Returns the set of test suites that contain the test specs or group specs
     * that have been added.
     *
     * @return the test suites
     * @throws Fault if there is an error accessing any of the test suites
     */
    public Set<RegressionTestSuite> getTestSuites() throws Fault {
        LinkedHashSet<RegressionTestSuite> set = new LinkedHashSet<>();
        for (Entry e: map.values()) {
            if (e.testSuite == null) {
                try {
                e.testSuite = RegressionTestSuite.open(e.rootDir.toFile(), errHandler);
                if (!e.testSuite.getRootDir().toPath().equals(e.rootDir)) {
                    System.err.println("e.testSuite.getRootDir(): " + e.testSuite.getRootDir());
                    System.err.println("e.rootDir: " + e.rootDir);
                    System.err.println(e.testSuite.getRootDir().toPath().equals(e.rootDir));
                    throw new AssertionError();
                }
                } catch (TestSuite.Fault f) {
                    throw new Fault(i18n, "tm.cantOpenTestSuite", e.testSuite, f);
                }
            }
            set.add(e.testSuite);
        }
        return set;
    }

    /**
     * Sets the path for the work directory to be used for this run.
     * When running tests in multiple test suites, separate work
     * directories for each test suite will be created as subdirectories
     * of this directory.
     *
     * @param wd the path for the work directory
     */
    public void setWorkDirectory(Path wd) {
        if (wd == null)
            throw new NullPointerException();
        if (workDir != null)
            throw new IllegalStateException();
        workDir = wd;
    }

    /**
     * Returns the path for the work directory to be used for this run.
     *
     * @return the path for the work directory
     */
    public Path getWorkDirectory() {
        if (workDir == null)
            throw new IllegalStateException();
        return workDir;
    }

    /**
     * Returns the work directory to be used when running tests in a given test suite.
     *
     * @param ts the test suite
     * @return the work directory
     * @throws Fault if there is a problem accessing the work directory
     */
    public WorkDirectory getWorkDirectory(RegressionTestSuite ts) throws Fault {
        Entry e = map.get(ts.getRootDir().toPath());
        if (e == null)
            throw new IllegalStateException();
        if (e.workDir == null) {
            if (e.subdir == null && isMultiRun())
                initSubdirs();
            Path wd = (e.subdir == null) ? workDir : workDir.resolve(e.subdir);
            File wdf = wd.toFile();
            try {
                if (WorkDirectory.isWorkDirectory(wdf))
                    e.workDir = WorkDirectory.open(wdf, ts);
                else if (Files.exists(wd))
                    e.workDir = WorkDirectory.convert(wdf, ts);
                else
                    e.workDir = WorkDirectory.create(wdf, ts);
            } catch (WorkDirectory.Fault | FileNotFoundException ex) {
                throw new Fault(i18n, "tm.cantRead", wd.getFileName().toString(), ex);
            }
        }
        return e.workDir;

    }

    /**
     * Sets the path for the report directory to be used for this run.
     * When running tests in multiple test suites, separate report
     * directories for each test suite will be created as subdirectories
     * of this directory.
     *
     * @param rd the path
     */
    public void setReportDirectory(Path rd) {
        if (rd == null)
            throw new NullPointerException();
        if (reportDir != null)
            throw new IllegalStateException();
        reportDir = rd;
    }

    /**
     * Returns the path for the report directory to be used for this run.
     *
     * @return the path
     */
    public Path getReportDirectory() {
        if (reportDir == null)
            throw new IllegalStateException();
        return reportDir;
    }

    /**
     * Returns the report directory to be used when running tests in a given test suite.
     *
     * @param ts the test suite
     * @return the report directory
     * @throws Fault if there is a problem accessing the report directory
     */
    public Path getReportDirectory(RegressionTestSuite ts) throws Fault {
        Entry e = map.get(ts.getRootDir().toPath());
        if (e == null)
            throw new IllegalArgumentException();
        if (reportDir != null && e.reportDir == null) {
            if (e.subdir == null && isMultiRun())
                initSubdirs();
            e.reportDir = (e.subdir == null) ? reportDir : reportDir.resolve(e.subdir);
        }
        return e.reportDir;
    }

    /**
     * Returns the name of the subdirectory to use when running tests from
     * multiple test suites.
     *
     * @param ts the test suite
     * @return the name of the subdirectory
     * @throws Fault if there is a problem accessing the test suite
     */
    String getSubdirectory(RegressionTestSuite ts) throws Fault {
        if (map.size() <= 1)
            return null;

        Entry e = map.get(ts.getRootDir().toPath());
        if (e == null)
            throw new IllegalArgumentException();
        if (e.subdir == null)
            initSubdirs();
        return e.subdir;
    }

    /**
     * Returns the set of tests to be run in a given test suite,
     * or {@code null} meaning "all tests".
     * The tests are identified in "URL form", containing the
     * path of the test relative to the test suite root, and
     * with an id if given. The query part of a test spec is
     * not included.
     *
     * @param ts the test suite
     * @return the list of tests, or {@code null} for all tests
     * @throws Fault if there is a problem with the tests to be run
     */
    public Set<String> getTests(RegressionTestSuite ts) throws Fault {
        Entry e = map.get(ts.getRootDir().toPath());
        if (e == null) {
            throw new IllegalArgumentException();
        }
        if (e.all) {
            return null; // all tests
        }
        WorkDirectory wd = getWorkDirectory(ts);
        Set<String> tests = new LinkedHashSet<>();
        for (TestSpec test: e.tests) {
            String t = test.getTestPath();
            if (validatePath(wd, t)) {
                tests.add(t);
            } else {
                throw new Fault(i18n, "tm.notATest", test);
            }
        }
        for (Path f: expandGroups(e)) {
            String test = pathToString(e.rootDir.relativize(f));
            if (test.isEmpty()) {
                return null; // all tests
            } else if (validatePath(wd, test)) {
                tests.add(test);
            }
        }
        if (tests.isEmpty() && (!allowEmptyGroups || e.groups.isEmpty()))
            throw new NoTests();
        return tests;
    }

    /**
     * Returns the list of tests to be run in a given test suite that
     * contain a non-null query in the test spec.
     * If there are multiple test specs for the same test path, then
     * the last one wins. This applies regardless of whether the test spec
     * contains a query or not.
     * The tests are identified in "modified URL form",  containing the
     * path of the test relative to the test suite root, an id if given,
     * and the query part of the test spec.
     *
     * @param ts the test suite
     * @return the list of tests
     * @throws Fault if there is a problem with the tests to be run
     */
    public List<String> getTestQueries(RegressionTestSuite ts) throws Fault {
        Entry e = map.get(ts.getRootDir().toPath());
        if (e == null) {
            throw new IllegalArgumentException();
        }
        if (e.all) {
            return List.of();
        }

        // Eliminate any duplicates for each test path with "last one wins"
        Map<String, TestSpec> map = new LinkedHashMap<>();
        for (TestSpec t : e.tests) {
            if (t.query != null && Files.isDirectory(e.rootDir.resolve(t.file))) {
                throw new Fault(i18n, "tm.invalidQuery", t);
            }
            map.put(t.getTestPath(), t);
        }
        // Return the remaining test specs that contain a query component
        return map.values().stream()
                .filter(t -> t.query != null)
                .map(t -> t.getTestPath() + "?" + t.query)
                .collect(Collectors.toList());
    }

    // This method is to work around a bug in TestResultTable.validatePath
    // such that the extension of the file name is not validated.
    // In other words, an invalid path dir/file.ex1 will be reported as
    // valid if dir/file.ex2 exists and is valid.
    // The problem only exists for paths to files (not directories.)
    // The solution is to check the root-relative path in the test description
    // to make sure it is the same as the original path.
    // See JBS CODETOOLS-7900138, CODETOOLS-7900139
    private boolean validatePath(WorkDirectory wd, String path) {
        try {
            TestResultTable trt = wd.getTestResultTable();
            if (trt.validatePath(path)) {
                // bypass check when fragment syntax used
                if (path.matches(".*#[A-Za-z0-9-_]+"))
                    return true;
                File rootDir = wd.getTestSuite().getRootDir();
                File f = new File(rootDir, path);
                if (f.isDirectory())
                    return true;
                TreeIterator iter = trt.getIterator(new String[] { path });
                while (iter.hasNext()) {
                    TestResult tr = iter.next();
                    String trp = tr.getDescription().getRootRelativePath();
                    if (path.equals(trp))
                        return true;
                }
            }
            return false;
        } catch (TestResult.Fault f) {
            return false;
        }
    }

    /**
     * Returns the names of the groups containing tests to be run in
     * the given test suite.
     *
     * @param ts the test suite
     * @return the names of the groups to be run
     * @throws Fault if there is a problem accessing the groups
     */
    public Set<String> getGroups(RegressionTestSuite ts) throws Fault {
        Entry e = map.get(ts.getRootDir().toPath());
        if (e == null)
            throw new IllegalArgumentException();
        return e.groups;
    }

    private Entry getEntry(Path rootDir) {
        Entry e = map.get(rootDir);
        if (e == null)
            map.put(rootDir, e = new Entry(rootDir));
        return e;
    }

    /**
     * Get the test suite root for a file in a test suite
     * @param rootDirCache a cache of earlier results to improve performance
     * @param file the file to test
     * @return the path for the enclosing directory containing TEST.ROOT,
     *      or null if there is no such directory
     */
    private Path getRootDir(Map<Path, Path> rootDirCache, Path file) {
        if (file == null)
            return null;
        if (Files.isRegularFile(file))
            return getRootDir(rootDirCache, file.getParent());
        Path ts = rootDirCache.get(file);
        if (ts == null) {
            ts = Files.exists(file.resolve("TEST.ROOT"))
                    ? file : getRootDir(rootDirCache, file.getParent());
            rootDirCache.put(file, ts);
        }
        return ts;
    }

    /**
     * Determine subdirectories to use within a top-level work directory.
     * Existing subdirectories are honored if applicable.
     */
    private void initSubdirs() throws Fault {
        if (WorkDirectory.isWorkDirectory(workDir.toFile()))
            throw new Fault(i18n, "tm.workDirNotSuitableInMultiTestSuiteMode");

        Set<String> subdirs = new HashSet<>();

        // first, scan directory looking for existing test suites
        if (Files.exists(workDir)) {
            if (!Files.isDirectory(workDir))
                throw new Fault(i18n, "tm.notADirectory", workDir);
            for (Path f: FileUtils.listFiles(workDir)) {
                String subdir = f.getFileName().toString();
                subdirs.add(subdir); // record all names to avoid downstream clashes
                if (WorkDirectory.isUsableWorkDirectory(f.toFile())) {
                    File tsr = getTestSuiteForWorkDirectory(f.toFile());
                    Entry e = map.get(tsr.toPath());
                    if (e != null)
                        e.subdir = subdir;
                }
            }
        }

        // create new entries for test suites that do not have them
        for (Entry e: map.values()) {
            if (e.subdir ==  null) {
                String subdir = e.rootDir.getFileName().toString();
                if (e.rootDir.getParent() != null)
                    subdir = e.rootDir.getParent().getFileName() + "_" + subdir;
                if (subdirs.contains(subdir)) {
                    int n = 0;
                    String sdn;
                    while (subdirs.contains(sdn = (subdir + "_" + n)))
                        n++;
                    subdir = sdn;
                }
                e.subdir = subdir;
                subdirs.add(subdir);
            }
        }
    }

    private File getTestSuiteForWorkDirectory(File wd) {
        // Cannot use standard WorkDirectory.open(ws).getTestSuite().getRoot()
        // because jtreg does not follow standard protocol for tsInfo.
        // (There is no easy way to disambiguate jtreg test suites.)
        // So, have to read the testsuite file directly.
        File tsInfo = new File(new File(wd, "jtData"), "testsuite");
        try {
            try (InputStream in = new FileInputStream(tsInfo)) {
                Properties p = new Properties();
                p.load(in);
                String tsr = p.getProperty("root");
                if (tsr != null)
                    return new File(tsr);
            }
        } catch (IOException e) {
            // ignore
        }
        return new File("__UNKNOWN__");
    }

    private Set<Path> expandGroups(Entry e) throws Fault {
        try {
            Set<Path> results = new LinkedHashSet<>();
            GroupManager gm = e.testSuite.getGroupManager(out);

            if (gm.invalid()) {
                Version v = e.testSuite.getRequiredVersion();
                boolean reportErrorIfInvalidGroups = (v.version != null)
                        && (v.compareTo(new Version("5.1 b01")) >= 0);
                if (reportErrorIfInvalidGroups) {
                    throw new Fault(i18n, "tm.invalidGroups");
                }
            }

            for (String group: e.groups) {
                try {
                    results.addAll(gm.getFiles(group));
                } catch (GroupManager.InvalidGroup ex) {
                    throw new Fault(i18n, "tm.invalidGroup", group);

                }
            }
            return results;
        } catch (IOException ex) {
            throw new Fault(i18n, "tm.cantReadGroups", e.testSuite.getRootDir(), ex);
        }
    }

    private Path canon(Path file) {
        Path f = file.isAbsolute() ? file : baseDir.resolve(file);
        try {
            return f.toRealPath();
        } catch (IOException e) {
            return getNormalizedFile(f);
        }
    }

    private static Path getNormalizedFile(Path f) {
        return f.toAbsolutePath().normalize();
    }

    private static String pathToString(Path p) {
        return p.toString().replace(File.separatorChar, '/');
    }

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(TestManager.class);
}
