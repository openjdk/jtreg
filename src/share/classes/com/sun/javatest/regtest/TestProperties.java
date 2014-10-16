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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.sun.javatest.TestSuite;
import com.sun.javatest.util.I18NResourceBundle;

/**
 * Provide access to properties defined in TEST.ROOT, with selective overrides
 * in TEST.properties in subdirectories.
 */
public class TestProperties {
    TestProperties(File rootDir) throws TestSuite.Fault {
        cache = new Cache(canon(rootDir));
        Cache.Entry e = cache.getEntry(cache.rootDir);

        validKeys = e.validKeys;

        // determine whether we want to enforce bugid syntax
        // the default is that we always do
        String bug = e.properties.getProperty("checkBugID");
        checkBugID = (bug == null) || !bug.trim().equals("false");

        String mode = e.properties.getProperty("defaultExecMode");
        defaultExecMode = ExecMode.fromString(mode);

        String gf = e.properties.getProperty("groups");
        groupFiles = (gf == null)
                ? Collections.<String>emptyList()
                : Arrays.asList(gf.split("\\s+"));

        String version = e.properties.getProperty("requiredVersion");
        requiredVersion = new Version(version);
    }

    Set<String> getValidKeys(File file) throws TestSuite.Fault {
        if (!allowLocalKeys)
            return validKeys;

        File dir = file.isDirectory() ? file : file.getParentFile();
        Cache.Entry e = cache.getEntry(dir);
        return e.validKeys;
    }

    Set<String> getValidRequiresProperties(File file) throws TestSuite.Fault {
        File dir = file.isDirectory() ? file : file.getParentFile();
        Cache.Entry e = cache.getEntry(dir);
        return e.validRequiresProperties;
    }

    ExecMode getDefaultExecMode() {
        return defaultExecMode;
    }

    List<String> getGroupFiles() {
        return groupFiles;
    }

    boolean useBootClassPath(File file) throws TestSuite.Fault {
        File dir = file.isDirectory() ? file : file.getParentFile();
        Cache.Entry e = cache.getEntry(dir);
        return e.useBootClassPath;
    }

    boolean useOtherVM(File file) throws TestSuite.Fault {
        File dir = file.isDirectory() ? file : file.getParentFile();
        Cache.Entry e = cache.getEntry(dir);
        return e.useOtherVM;
    }

    boolean isTestNG(File file) throws TestSuite.Fault {
        File dir = file.isDirectory() ? file : file.getParentFile();
        Cache.Entry e = cache.getEntry(dir);
        return e.testNGRoot != null;
    }

    File getTestNGRoot(File file) throws TestSuite.Fault {
        File dir = file.isDirectory() ? file : file.getParentFile();
        Cache.Entry e = cache.getEntry(dir);
        return e.testNGRoot;
    }

    boolean needsExclusiveAccess(File file) throws TestSuite.Fault {
        File dir = file.isDirectory() ? file : file.getParentFile();
        Cache.Entry e = cache.getEntry(dir);
        return e.needsExclusiveAccess;
    }

    Set<String> getLibDirs(File file) throws TestSuite.Fault {
        File dir = file.isDirectory() ? file : file.getParentFile();
        Cache.Entry e = cache.getEntry(dir);
        return e.libDirs;
    }

    Version getRequiredVersion() {
        return requiredVersion;
    }

    private File canon(File f) {
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return new File(f.getAbsoluteFile().toURI().normalize());
        }
    }

    private final Cache cache;
    /*private*/ final boolean checkBugID;
    /*private*/ final Set<String> validKeys;
    final ExecMode defaultExecMode;
    final List<String> groupFiles;
    final Version requiredVersion;

    static class Cache {
        class Entry {
            final Entry parent;
            final File dir;
            final Properties properties;
            final Set<String> validKeys;
            final Set<String> validRequiresProperties;
            final boolean useBootClassPath;
            private final Set<File> bootClassPathDirs;
            final boolean useOtherVM;
            private final Set<File> otherVMDirs;
            final boolean needsExclusiveAccess;
            private final Set<File> exclusiveAccessDirs;
            final File testNGRoot;
            private final Set<File> testNGDirs;
            final Set<String> libDirs;

            Entry(Entry parent, File dir) throws TestSuite.Fault {
                this.parent = parent;
                this.dir = dir;

                File file = new File(dir, (parent == null) ? "TEST.ROOT" : "TEST.properties");
                if (file.canRead()) {
                    properties = (parent == null) ? new Properties() : new Properties(parent.properties);
                    try {
                        BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
                        properties.load(in);
                        in.close();
                    } catch (IOException e) {
                        throw new TestSuite.Fault(i18n, "suite.cantRead", file);
                    }

                    // add the list of valid keys
                    validKeys = initSimpleSet(parent == null ? null : parent.validKeys, "keys");

                    // add the list of valid properties for @requires
                    validRequiresProperties = initSimpleSet(parent == null ? null : parent.validRequiresProperties, "requires.properties");

                    // add the list of bootclasspath dirs
                    bootClassPathDirs = initFileSet(parent == null ? null : parent.bootClassPathDirs, "bootclasspath.dirs", dir);

                    // add the list of othervm dirs
                    otherVMDirs = initFileSet(parent == null ? null : parent.otherVMDirs, "othervm.dirs", dir);

                    // add the list of exclusive access dirs
                    exclusiveAccessDirs = initFileSet(parent == null ? null : parent.exclusiveAccessDirs, "exclusiveAccess.dirs", dir);

                    // add the list of TestNG dirs
                    testNGDirs = initFileSet(parent == null ? null : parent.testNGDirs, "TestNG.dirs", dir);

                    // add the list of library dirs for TestNG tests
                    libDirs = initLibDirSet(parent == null ? null : parent.libDirs, "lib.dirs", dir);
                } else {
                    if (parent == null)
                        throw new IllegalStateException("TEST.ROOT not found");
                    properties = parent.properties;
                    validKeys = parent.validKeys;
                    validRequiresProperties = parent.validRequiresProperties;
                    bootClassPathDirs = parent.bootClassPathDirs;
                    otherVMDirs = parent.otherVMDirs;
                    exclusiveAccessDirs = parent.exclusiveAccessDirs;
                    testNGDirs = parent.testNGDirs;
                    libDirs = parent.libDirs;
                }

                useBootClassPath= initUseBootClassPath(parent, dir);
                useOtherVM = initUseOtherVM(parent, dir);
                needsExclusiveAccess = initNeedsExclusiveAccess(parent, dir);
                testNGRoot = initTestNGRoot(parent, dir);
            }

            private Set<File> initFileSet(Set<File> parent, String propertyName, File baseDir) {
                String[] values = StringArray.splitWS(properties.getProperty(propertyName));
                if (parent == null || values.length > 0) {
                    Set<File> set = (parent == null) ? new LinkedHashSet<File>() : new LinkedHashSet<File>(parent);
                    //set.addAll(Arrays.asList(values));
                    for (String v: values) {
                        File f = toFile(baseDir, v);
                        if (f != null)
                            set.add(f);
                    }
                    return Collections.unmodifiableSet(set);
                } else {
                    return parent;
                }
            }

            private Set<String> initLibDirSet(Set<String> parent, String propertyName, File baseDir) {
                String[] values = StringArray.splitWS(properties.getProperty(propertyName));
                if (parent == null || values.length > 0) {
                    Set<String> set = (parent == null) ? new LinkedHashSet<String>() : new LinkedHashSet<String>(parent);
                    for (String v: values) {
                        File f = toFile(baseDir, v);
                        if (f != null) {
                            set.add("/" + rootDir.toURI().relativize(f.toURI()));
                        }
                    }
                    return Collections.unmodifiableSet(set);
                } else {
                    return parent;
                }
            }

            private Set<String> initSimpleSet(Set<String> parent, String propertyName) {
                String[] values = StringArray.splitWS(properties.getProperty(propertyName));
                if (parent == null || values.length > 0) {
                    Set<String> set = (parent == null) ? new LinkedHashSet<String>() : new LinkedHashSet<String>(parent);
                    set.addAll(Arrays.asList(values));
                    return Collections.unmodifiableSet(set);
                } else {
                    return parent;
                }
            }

            private boolean initUseBootClassPath(Entry parent, File dir) {
                if (parent == null)
                    return false;

                if (parent.useBootClassPath)
                    return true;

                for (File bootClassPathDir: bootClassPathDirs) {
                    if (includes(bootClassPathDir, dir))
                        return true;
                }

                return false;
            }

            private boolean initUseOtherVM(Entry parent, File dir) {
                if (parent == null)
                    return false;

                if (parent.useOtherVM)
                    return true;

                for (File otherVMDir: otherVMDirs) {
                    if (includes(otherVMDir, dir))
                        return true;
                }

                return false;
            }

            private boolean initNeedsExclusiveAccess(Entry parent, File dir) {
                if (parent == null)
                    return false;

                if (parent.needsExclusiveAccess)
                    return true;

                for (File exclusiveAccessDir: exclusiveAccessDirs) {
                    if (includes(exclusiveAccessDir, dir))
                        return true;
                }

                return false;
            }

            private File initTestNGRoot(Entry parent, File dir) {
                if (parent == null)
                    return null;

                if (parent.testNGRoot != null)
                    return parent.testNGRoot;

                for (File testNGDir: testNGDirs) {
                    if (includes(testNGDir, dir))
                        return testNGDir;
                }

                return null;
            }

            private boolean includes(File dir, File file) {
                for ( ; file != null; file = file.getParentFile()) {
                    if (dir.equals(file))
                        return true;
                }
                return false;
            }

            private File toFile(File baseDir, String v) {
                if (v.startsWith("/")) {
                    File f = new File(rootDir, v.substring(1));
                    if (f.exists())
                        return new File(f.toURI().normalize());
                } else {
                    File f;
                    if ((f = new File(baseDir, v)).exists())
                        return new File(f.toURI().normalize());
                    else if ((f = new File(rootDir, v)).exists()) // for backwards compatibility
                        return new File(f.toURI().normalize());
                }
                return null;
            }
        }


        /** Cache map, using soft references. */
        Map<File, SoftReference<Entry>> map;
        /** Strong reference to most recent entry, and all its ancestors */
        Entry lastUsedEntry;
        File rootDir;

        Cache(File rootDir) {
            this.rootDir = rootDir;
            map = new HashMap<File, SoftReference<Entry>>();
        }

        synchronized Entry getEntry(File dir) throws TestSuite.Fault {
            if (lastUsedEntry == null || !lastUsedEntry.dir.equals(dir))
                lastUsedEntry = getEntryInternal(dir);
            return lastUsedEntry;
        }

        private Entry getEntryInternal(File dir) throws TestSuite.Fault {
            SoftReference<Entry> ref = map.get(dir);
            Entry e = (ref == null) ? null : ref.get();
            if (e == null) {
                Entry parent = dir.equals(rootDir) ? null : getEntryInternal(dir.getParentFile());
                map.put(dir, new SoftReference<Entry>(e = new Entry(parent, dir)));
            }
            return e;
        }
    }

    private static final boolean allowLocalKeys =
            Boolean.parseBoolean(System.getProperty("javatest.regtest.allowLocalKeys", "true"));

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(RegressionTestSuite.class);
}
