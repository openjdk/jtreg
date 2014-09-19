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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.sun.javatest.TestResult;
import com.sun.javatest.TestResultTable;
import com.sun.javatest.TestResultTable.TreeIterator;
import com.sun.javatest.TestSuite;
import com.sun.javatest.WorkDirectory;
import com.sun.javatest.regtest.Main.Fault;
import com.sun.javatest.util.I18NResourceBundle;

/**
 * Manage tests to be run by jtreg.
 */
public class TestManager {
    class NoTests extends Main.Fault {
        NoTests() {
            super(i18n, "tm.noTests");
        }
    }

    private final PrintWriter out;
    private final File baseDir;
    private File reportDir;
    private File workDir;

    Map<File, Entry> map = new HashMap<File, Entry>();

    private class Entry {
        final File rootDir;
        boolean all = false;
        Map<String, Boolean> files = new LinkedHashMap<String, Boolean>();
        Set<String> groups = new LinkedHashSet<String>();

        RegressionTestSuite testSuite;
        String subdir;
        WorkDirectory workDir;
        File reportDir;

        Entry(File rootDir) {
            this.rootDir = rootDir;
        }
    }

    TestManager(PrintWriter out, File baseDir) {
        this.out = out;
        this.baseDir = baseDir.getAbsoluteFile();
    }

    void addTests(Collection<File> testFiles, boolean ignoreEmptyFiles) throws Fault {
        Map<File, File> rootDirCache = new HashMap<File, File>();
        for (File tf: testFiles) {
            File f = canon(tf);
            if (!f.exists())
                throw new Fault(i18n, "tm.cantFindFile", tf);
            File rootDir = getRootDir(rootDirCache, f);
            if (rootDir == null)
                throw new Fault(i18n, "tm.cantDetermineTestSuite", tf);

            Entry e = getEntry(rootDir);
            if (tf.equals(rootDir)) {
                e.all = true;
                e.files.clear();
            } else if (!e.all) {
                e.files.put(getRelativePath(rootDir, f), ignoreEmptyFiles);
            }
        }
    }

    void addGroups(Collection<String> groups) throws Fault {
        for (String g: groups) {
            int sep = g.lastIndexOf(":");
            if (sep == -1)
                throw new Fault(i18n, "tm.badGroupSpec", g);
            File rootDir = canon((sep == 0) ? baseDir : new File(g.substring(0, sep)));
            if (!new File(rootDir, "TEST.ROOT").exists())
                throw new Fault(i18n, "tm.badGroupTestSuite", g);
            Entry e = getEntry(rootDir);
            e.groups.add(g.substring(sep + 1));
        }
    }

    boolean isEmpty() {
        return map.isEmpty();
    }

    boolean isMultiRun() {
        return (map.size() > 1);
    }

    Set<RegressionTestSuite> getTestSuites() throws Fault {
        LinkedHashSet<RegressionTestSuite> set = new LinkedHashSet<RegressionTestSuite>();
        for (Entry e: map.values()) {
            if (e.testSuite == null) {
                try {
                e.testSuite = RegressionTestSuite.open(e.rootDir);
                if (!e.testSuite.getRootDir().equals(e.rootDir)) {
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

    void setWorkDirectory(File wd) {
        if (wd == null)
            throw new NullPointerException();
        if (workDir != null)
            throw new IllegalStateException();
        workDir = wd;
    }

    WorkDirectory getWorkDirectory(RegressionTestSuite ts) throws Fault {
        Entry e = map.get(ts.getRootDir());
        if (e == null)
            throw new IllegalStateException();
        if (e.workDir == null) {
            if (e.subdir == null && isMultiRun())
                initSubdirs();
            File wd = (e.subdir == null) ? workDir : new File(workDir, e.subdir);
            try {
                if (WorkDirectory.isWorkDirectory(wd))
                    e.workDir = WorkDirectory.open(wd, ts);
                else if (wd.exists())
                    e.workDir = WorkDirectory.convert(wd, ts);
                else
                    e.workDir = WorkDirectory.create(wd, ts);
            } catch (WorkDirectory.Fault ex) {
                throw new Fault(i18n, "tm.cantRead", wd.getName(), ex);
            } catch (FileNotFoundException ex) {
                throw new Fault(i18n, "tm.cantRead", wd.getName(), ex);
            }
        }
        return e.workDir;

    }

    void setReportDirectory(File rd) {
        if (rd == null)
            throw new NullPointerException();
        if (reportDir != null)
            throw new IllegalStateException();
        reportDir = rd;
    }

    File getReportDirectory(RegressionTestSuite ts) throws Fault {
        Entry e = map.get(ts.getRootDir());
        if (e == null)
            throw new IllegalArgumentException();
        if (reportDir != null && e.reportDir == null) {
            if (e.subdir == null && isMultiRun())
                initSubdirs();
            e.reportDir = (e.subdir == null) ? reportDir : new File(reportDir, e.subdir);
        }
        return e.reportDir;
    }

    String getSubdirectory(RegressionTestSuite ts) throws Fault {
        if (map.size() <= 1)
            return null;

        Entry e = map.get(ts.getRootDir());
        if (e == null)
            throw new IllegalArgumentException();
        if (e.subdir == null)
            initSubdirs();
        return e.subdir;
    }

    Set<String> getTests(RegressionTestSuite ts) throws Fault {
        Entry e = map.get(ts.getRootDir());
        if (e == null)
            throw new IllegalArgumentException();
        if (e.all)
            return Collections.<String>emptySet();
        WorkDirectory wd = getWorkDirectory(ts);
        Set<String> tests = new LinkedHashSet<String>();
        for (Map.Entry<String,Boolean> me: e.files.entrySet()) {
            String test = me.getKey();
            boolean ignoreEmptyFiles = me.getValue();
            if (validatePath(wd, test))
                tests.add(test);
            else if (!ignoreEmptyFiles)
                throw new Fault(i18n, "tm.notATest", test);
        }
        for (File f: expandGroups(e)) {
            String test = getRelativePath(e.rootDir, f);
            if (validatePath(wd, test))
                tests.add(test);
        }
        if (tests.isEmpty())
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
    private boolean validatePath(WorkDirectory wd, String path) {
        try {
            TestResultTable trt = wd.getTestResultTable();
            if (trt.validatePath(path)) {
                File rootDir = wd.getTestSuite().getRootDir();
                File f = new File(rootDir, path);
                if (f.isDirectory())
                    return true;
                TreeIterator iter = trt.getIterator(new String[] { path }, null);
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

    Set<String> getGroups(RegressionTestSuite ts) throws Fault {
        Entry e = map.get(ts.getRootDir());
        if (e == null)
            throw new IllegalArgumentException();
        return e.groups;
    }

    private Entry getEntry(File rootDir) {
        Entry e = map.get(rootDir);
        if (e == null)
            map.put(rootDir, e = new Entry(rootDir));
        return e;
    }

    /**
     * Get the test suite root for a file in a test suite
     * @param cache a cache of earlier results to improve performance
     * @param file the file to test
     * @return the path for the enclosing directory containing TEST.ROOT,
     *      or null if these is no such directory
     */
    private File getRootDir(Map<File, File> rootDirCache, File file) {
        if (file == null)
            return null;
        if (file.isFile())
            return getRootDir(rootDirCache, file.getParentFile());
        File ts = rootDirCache.get(file);
        if (ts == null) {
            ts = new File(file, "TEST.ROOT").exists()
                    ? file : getRootDir(rootDirCache, file.getParentFile());
            rootDirCache.put(file, ts);
        }
        return ts;
    }

    /**
     * Determine subdirectories to use within a top-level work directory.
     * Existing subdirectories are honored if applicable.
     */
    private void initSubdirs() throws Fault {
        if (WorkDirectory.isWorkDirectory(workDir))
            throw new Fault(i18n, "tm.workDirNotSuitableInMultiTestSuiteMode");

        Set<String> subdirs = new HashSet<String>();

        // first, scan directory looking for existing test suites
        if (workDir.exists()) {
            if (!workDir.isDirectory())
                throw new Fault(i18n, "tm.notADirectory", workDir);
            for (File f: workDir.listFiles()) {
                String subdir = f.getName();
                subdirs.add(subdir); // record all names to avoid downstream clashes
                if (WorkDirectory.isUsableWorkDirectory(f)) {
                    File tsr = getTestSuiteForWorkDirectory(f);
                    Entry e = map.get(tsr);
                    if (e != null)
                        e.subdir = subdir;
                }
            }
        }

        // create new entries for test suites that do not have them
        for (Entry e: map.values()) {
            if (e.subdir ==  null) {
                String subdir = e.rootDir.getName();
                if (e.rootDir.getParentFile() != null)
                    subdir = e.rootDir.getParentFile().getName() + "_" + subdir;
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
            InputStream in = new FileInputStream(tsInfo);
            try {
                Properties p = new Properties();
                p.load(in);
                String tsr = p.getProperty("root");
                if (tsr != null)
                    return new File(tsr);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            // ignore
        }
        return new File("__UNKNOWN__");
    }

    private Set<File> expandGroups(Entry e) throws Main.Fault {
        try {
            Set<File> results = new LinkedHashSet<File>();
            GroupManager gm = e.testSuite.getGroupManager(out);
            for (String group: e.groups) {
                results.addAll(gm.getFiles(group));
            }
            return results;
        } catch (IOException ex) {
            throw new Main.Fault(i18n, "tm.cantReadGroups", e.testSuite.getRootDir(), ex);
        }
    }

    File canon(File file) {
        File f = file.isAbsolute() ? file : new File(baseDir, file.getPath());
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return getNormalizedFile(f);
        }
    }

    static File getNormalizedFile(File f) {
        return new File(f.getAbsoluteFile().toURI().normalize());
    }

    static String getRelativePath(File base, File f) {
        StringBuilder sb = new StringBuilder();
        for ( ; f != null; f = f.getParentFile()) {
            if (f.equals(base))
                return sb.toString();
            if (sb.length() > 0)
                sb.insert(0, '/');
            sb.insert(0, f.getName());
        }
        return null;
    }

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(TestManager.class);
}
