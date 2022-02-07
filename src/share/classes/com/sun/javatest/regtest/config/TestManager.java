/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import com.sun.javatest.TestFilter;
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
    public class NoTests extends Fault {
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

    private class Entry {
        final Path rootDir;
        boolean all = false;
        Map<String, Boolean> files = new LinkedHashMap<>();
        Set<String> groups = new LinkedHashSet<>();

        RegressionTestSuite testSuite;
        String subdir;
        WorkDirectory workDir;
        Path reportDir;

        Entry(Path rootDir) {
            this.rootDir = rootDir;
        }
    }

    public static class FileId {
        final Path file;
        final String id;

        public FileId(Path file, String id) {
            this.file = file;
            this.id = id;
        }
    }

    public TestManager(PrintWriter out, Path baseDir, TestFinder.ErrorHandler errHandler) {
        this.out = out;
        this.baseDir = baseDir.toAbsolutePath();
        this.errHandler = errHandler;
    }

    public void addTestFiles(Collection<Path> testFiles, boolean ignoreEmptyFiles) throws Fault {
        Map<Path, Path> rootDirCache = new HashMap<>();
        for (Path tf: testFiles) {
            addTest(rootDirCache, tf, null, ignoreEmptyFiles);
        }
    }

    public void addTestFileIds(Collection<FileId> testFiles, boolean ignoreEmptyFiles) throws Fault {
        Map<Path, Path> rootDirCache = new HashMap<>();
        for (FileId tf: testFiles) {
            addTest(rootDirCache, tf.file, tf.id, ignoreEmptyFiles);
        }
    }

    private void addTest(Map<Path, Path> rootDirCache, Path tf, String id, boolean ignoreEmptyFiles) throws Fault {
        Path f = canon(tf.toFile()).toPath();
        if (!Files.exists(f))
            throw new Fault(i18n, "tm.cantFindFile", tf);
        Path rootDir = getRootDir(rootDirCache, f);
        if (rootDir == null)
            throw new Fault(i18n, "tm.cantDetermineTestSuite", tf);

        Entry e = getEntry(rootDir);
        if (tf.equals(rootDir)) {
            e.all = true;
            e.files.clear();
        } else if (!e.all) {
            e.files.put(getRelativePath(rootDir, f, id), ignoreEmptyFiles);
        }
    }

    public void addGroups(Collection<String> groups) throws Fault {
        for (String g: groups) {
            int sep = g.lastIndexOf(":");
            if (sep == -1)
                throw new Fault(i18n, "tm.badGroupSpec", g);
            Path rootDir = canon((sep == 0) ? baseDir : Path.of(g.substring(0, sep)));
            if (!Files.exists(rootDir.resolve("TEST.ROOT")))
                throw new Fault(i18n, "tm.badGroupTestSuite", g);
            Entry e = getEntry(rootDir);
            e.groups.add(g.substring(sep + 1));
        }
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean isMultiRun() {
        return (map.size() > 1);
    }

    public Set<RegressionTestSuite> getTestSuites() throws Fault {
        LinkedHashSet<RegressionTestSuite> set = new LinkedHashSet<>();
        for (Entry e: map.values()) {
            if (e.testSuite == null) {
                try {
                e.testSuite = RegressionTestSuite.open(e.rootDir.toFile(), errHandler);
                if (!e.testSuite.getRootDir().toPath().equals(e.rootDir)) {
                    System.err.println("e.testSuite.getRootDir(): " + e.testSuite.getRootDir());
                    System.err.println("e.rootDir: " + e.rootDir);
                    System.err.println(e.testSuite.getRootDir().equals(e.rootDir));
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

    public void setWorkDirectory(Path wd) {
        if (wd == null)
            throw new NullPointerException();
        if (workDir != null)
            throw new IllegalStateException();
        workDir = wd;
    }

    public Path getWorkDirectory() {
        if (workDir == null)
            throw new IllegalStateException();
        return workDir;
    }

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
            } catch (WorkDirectory.Fault ex) {
                throw new Fault(i18n, "tm.cantRead", wd.getFileName().toString(), ex);
            } catch (FileNotFoundException ex) {
                throw new Fault(i18n, "tm.cantRead", wd.getFileName().toString(), ex);
            }
        }
        return e.workDir;

    }

    public void setReportDirectory(Path rd) {
        if (rd == null)
            throw new NullPointerException();
        if (reportDir != null)
            throw new IllegalStateException();
        reportDir = rd;
    }

    public Path getReportDirectory() {
        if (reportDir == null)
            throw new IllegalStateException();
        return reportDir;
    }

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

    public Set<String> getTests(RegressionTestSuite ts) throws Fault {
        Entry e = map.get(ts.getRootDir().toPath());
        if (e == null)
            throw new IllegalArgumentException();
        if (e.all)
            return null;
        WorkDirectory wd = getWorkDirectory(ts);
        Set<String> tests = new LinkedHashSet<>();
        for (Map.Entry<String,Boolean> me: e.files.entrySet()) {
            String test = me.getKey();
            boolean ignoreEmptyFiles = me.getValue();
            if (validatePath(wd, test))
                tests.add(test);
            else if (!ignoreEmptyFiles)
                throw new Fault(i18n, "tm.notATest", test);
        }
        for (Path f: expandGroups(e)) {
            String test = getRelativePath(e.rootDir, f, null);
            if (validatePath(wd, test))
                tests.add(test);
        }
        if (tests.isEmpty() && (!allowEmptyGroups || e.groups.isEmpty()))
            throw new NoTests();
        return tests;
    }

    // This method is to work around a bug in TestResultTable.validatePath
    // such that the extension of the file name is not validated.
    // In other words, an invalid path dir/file.ex1 will be reported as
    // valid if dir/file.ex2 exists and is valid.
    // The problem only exists for paths to files (not directories.)
    // The solution is to check the root-relative path in the test description
    // to make sure it is the same as the original path.
    // See JBS CODETOOLS-7900138, CODETOOLS-7900139
    @SuppressWarnings("cast") // temporary: to cover transition for generifying TreeIterator
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
                TreeIterator iter = trt.getIterator(new String[] { path }, new TestFilter[0]);
                while (iter.hasNext()) {
                    TestResult tr = (TestResult) iter.next();
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

    File canon(File file) {
        File f = file.isAbsolute() ? file : new File(baseDir.toFile(), file.getPath());
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return getNormalizedFile(f);
        }
    }

    Path canon(Path file) {
        Path f = file.isAbsolute() ? file : baseDir.resolve(file);
        try {
            return f.toRealPath();
        } catch (IOException e) {
            return getNormalizedFile(f);
        }
    }

    static File getNormalizedFile(File f) {
        return new File(f.getAbsoluteFile().toURI().normalize());
    }

    static Path getNormalizedFile(Path f) {
        return f.toAbsolutePath().normalize();
    }

    static String getRelativePath(Path base, Path f, String id) {
        // maybe use base.relativize(f) ?
        StringBuilder sb = new StringBuilder();
        for ( ; f != null; f = f.getParent()) {
            if (f.equals(base)) {
                if (id != null)
                    sb.append("#").append(id);
                return sb.toString();
            }
            if (sb.length() > 0)
                sb.insert(0, '/');
            sb.insert(0, f.getFileName().toString());
        }
        return null;
    }

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(TestManager.class);
}
