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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.sun.javatest.TestFinder;
import com.sun.javatest.TestSuite;
import com.sun.javatest.regtest.tool.Version;
import com.sun.javatest.regtest.util.StringUtils;
import com.sun.javatest.util.I18NResourceBundle;

/**
 * Provide access to properties defined in TEST.ROOT, with selective overrides
 * in TEST.properties in subdirectories.
 */
public class TestProperties {
    TestProperties(File rootDir, TestFinder.ErrorHandler errHandler) {
        this.errHandler = errHandler;

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
                ? Collections.emptyList()
                : List.of(gf.split("\\s+"));

        String version = e.properties.getProperty("requiredVersion");
        requiredVersion = new Version(version);

        String epd = e.properties.getProperty("requires.extraPropDefns");
        if (epd == null) {
            extraPropDefns = new ExtraPropDefns();
        } else {
            extraPropDefns = new ExtraPropDefns(
                    epd,
                    e.properties.getProperty("requires.extraPropDefns.libs"),
                    e.properties.getProperty("requires.extraPropDefns.bootlibs"),
                    e.properties.getProperty("requires.extraPropDefns.javacOpts"),
                    e.properties.getProperty("requires.extraPropDefns.vmOpts")
            );
        }
    }

    Set<String> getValidKeys(File file) throws TestSuite.Fault {
        if (!allowLocalKeys)
            return validKeys;

        return getEntry(file).validKeys;
    }

    Set<String> getValidRequiresProperties(File file) throws TestSuite.Fault {
        return getEntry(file).validRequiresProperties;
    }

    ExecMode getDefaultExecMode() {
        return defaultExecMode;
    }

    List<String> getGroupFiles() {
        return groupFiles;
    }

    boolean useBootClassPath(File file) throws TestSuite.Fault {
        return getEntry(file).useBootClassPath;
    }

    boolean useOtherVM(File file) throws TestSuite.Fault {
        return getEntry(file).useOtherVM;
    }

    boolean isTestNG(File file) throws TestSuite.Fault {
        return getEntry(file).testNGRoot != null;
    }

    File getTestNGRoot(File file) throws TestSuite.Fault {
        return getEntry(file).testNGRoot;
    }

    boolean isJUnit(File file) throws TestSuite.Fault {
        return getEntry(file).junitRoot != null;
    }

    File getJUnitRoot(File file) throws TestSuite.Fault {
        return getEntry(file).junitRoot;
    }

    boolean needsExclusiveAccess(File file) {
        return getEntry(file).needsExclusiveAccess;
    }

    Set<String> getLibDirs(File file) throws TestSuite.Fault {
        return getEntry(file).libDirs;
    }

    Set<String> getLibBuildArgs(File file) throws TestSuite.Fault {
        return getEntry(file).libBuildArgs;
    }

    Set<String> getModules(File file) throws TestSuite.Fault {
        return getEntry(file).modules;
    }

    Version getRequiredVersion() {
        return requiredVersion;
    }

    Set<File> getExternalLibs(File file) throws TestSuite.Fault {
        return getEntry(file).extLibRoots;
    }

    ExtraPropDefns getExtraPropDefns() {
        return extraPropDefns;
    }

    int getMaxOutputSize(File file) {
        return getEntry(file).maxOutputSize;
    }

    boolean getAllowSmartActionArgs(File file) {
        return getEntry(file).allowSmartActionArgs;
    }

    boolean getEnablePreview(File file) {
        return getEntry(file).enablePreview;
    }

    private Cache.Entry getEntry(File file) {
        File dir = file.isDirectory() ? file : file.getParentFile();
        return cache.getEntry(dir);
    }

    private void error(I18NResourceBundle i18n, String key, Object... args) {
        errHandler.error(i18n.getString(key, args));
    }

    private File canon(File f) {
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return new File(f.getAbsoluteFile().toURI().normalize());
        }
    }

    private final TestFinder.ErrorHandler errHandler;
    private final Cache cache;
    /*private*/ final boolean checkBugID;
    /*private*/ final Set<String> validKeys;
    final ExecMode defaultExecMode;
    final List<String> groupFiles;
    final Version requiredVersion;
    final ExtraPropDefns extraPropDefns;

    class Cache {
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
            final File junitRoot;
            private final Set<File> junitDirs;
            final Set<String> libDirs;
            final Set<String> libBuildArgs;
            final Set<File> extLibRoots;
            final Set<String> modules;
            final int maxOutputSize;
            final boolean allowSmartActionArgs;
            final boolean enablePreview;

            Entry(Entry parent, File dir) {
                this.parent = parent;
                this.dir = dir;

                File file = new File(dir, (parent == null) ? "TEST.ROOT" : "TEST.properties");
                if (file.canRead()) {
                    properties = (parent == null) ? new Properties() : new Properties(parent.properties);
                    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
                        properties.load(in);
                    } catch (IOException e) {
                        error(i18n, "props.cantRead", file);
                    }

                    // add the list of valid keys
                    validKeys = initKeywordSet(parent == null ? null : parent.validKeys, "keys");

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

                    // add the list of JUnit dirs
                    junitDirs = initFileSet(parent == null ? null : parent.junitDirs, "JUnit.dirs", dir);

                    // add the list of library dirs for TestNG tests
                    libDirs = initLibDirSet(parent == null ? null : parent.libDirs, "lib.dirs", dir);

                    // add the list of library dirs for TestNG tests
                    libBuildArgs = initSimpleSet(parent == null ? null : parent.libBuildArgs, "lib.build");

                    // add the list of external library roots
                    extLibRoots = initFileSet(parent == null ? null : parent.extLibRoots, "external.lib.roots", dir);

                    // add the list of default modules used by tests
                    modules = initSimpleSet(parent == null ? null : parent.modules, "modules");

                    // add the maxOutputSize for result content
                    maxOutputSize = getInt("maxOutputSize", -1);

                    // determine whether tests can use "smart action args"
                    allowSmartActionArgs = initAllowSmartActionArgs(parent);

                    // determine whether tests use preview features, and so require --enable-preview option
                    enablePreview = initEnablePreview(parent);
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
                    junitDirs = parent.junitDirs;
                    libDirs = parent.libDirs;
                    libBuildArgs = parent.libBuildArgs;
                    extLibRoots = parent.extLibRoots;
                    modules = parent.modules;
                    maxOutputSize = parent.maxOutputSize;
                    allowSmartActionArgs = parent.allowSmartActionArgs;
                    enablePreview = parent.enablePreview;
                }

                useBootClassPath= initUseBootClassPath(parent, dir);
                useOtherVM = initUseOtherVM(parent, dir);
                needsExclusiveAccess = initNeedsExclusiveAccess(parent, dir);
                testNGRoot = initTestNGRoot(parent, dir);
                junitRoot = initJUnitRoot(parent, dir);
            }

            private int getInt(String propertyName, int defaultValue) {
                String v = properties.getProperty(propertyName);
                try {
                    if (v != null) {
                        return Integer.parseInt(v.trim());
                    }
                } catch (NumberFormatException e) {
                    error(i18n, "props.bad.value", propertyName, v);
                }
                return defaultValue;
            }

            private Set<File> initFileSet(Set<File> parent, String propertyName, File baseDir) {
                String[] values = StringUtils.splitWS(properties.getProperty(propertyName));
                if (parent == null || values.length > 0) {
                    Set<File> set = (parent == null) ? new LinkedHashSet<>() : new LinkedHashSet<>(parent);
                    //set.addAll(List.of(values));
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
                String[] values = StringUtils.splitWS(properties.getProperty(propertyName));
                if (parent == null || values.length > 0) {
                    Set<String> set = (parent == null) ? new LinkedHashSet<>() : new LinkedHashSet<>(parent);
                    for (String v: values) {
                        if (v.startsWith("/")) {
                            set.add(v);
                        } else {
                            File f = toFile(baseDir, v);
                            if (f != null) {
                                set.add("/" + rootDir.toPath()
                                                .relativize(f.toPath())
                                                .toString()
                                                .replace(File.separatorChar, '/'));
                            }
                        }
                    }
                    return Collections.unmodifiableSet(set);
                } else {
                    return parent;
                }
            }

            private Set<String> initKeywordSet(Set<String> parent, String propertyName) {
                String[] values = StringUtils.splitWS(properties.getProperty(propertyName));
                if (parent == null || values.length > 0) {
                    Set<String> set = (parent == null) ? new LinkedHashSet<>() : new LinkedHashSet<>(parent);
                    for (String v: values) {
                        try {
                            RegressionKeywords.validateKey(v);
                            set.add(v.replace("-", "_"));
                        } catch (RegressionKeywords.Fault e) {
                            File file = new File(dir, (parent == null) ? "TEST.ROOT" : "TEST.properties");
                            error(i18n, "props.bad.keyword", file, v, e.getMessage());
                        }
                    }
                    return Collections.unmodifiableSet(set);
                } else {
                    return parent;
                }
            }

            private Set<String> initSimpleSet(Set<String> parent, String propertyName) {
                String[] values = StringUtils.splitWS(properties.getProperty(propertyName));
                if (parent == null || values.length > 0) {
                    Set<String> set = (parent == null) ? new LinkedHashSet<>() : new LinkedHashSet<>(parent);
                    set.addAll(List.of(values));
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

            private File initJUnitRoot(Entry parent, File dir) {
                if (parent == null)
                    return null;

                if (parent.junitRoot != null)
                    return parent.junitRoot;

                for (File junitDir: junitDirs) {
                    if (includes(junitDir, dir))
                        return junitDir;
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

            private boolean initAllowSmartActionArgs(Entry parent) {
                if (properties.containsKey("allowSmartActionArgs")) {
                    return properties.getProperty("allowSmartActionArgs").equals("true");
                }

                if (parent != null) {
                    return parent.allowSmartActionArgs;
                }

                // If and when this code is run, the main requiredVersion member is not yet initialized.
                String rv = properties.getProperty("requiredVersion");
                if (rv != null) {
                    return new Version(rv).compareTo(new Version("4.2 b14")) >= 0;
                }

                return false;
            }

            private boolean initEnablePreview(Entry parent) {
                if (properties.containsKey("enablePreview")) {
                    return properties.getProperty("enablePreview").equals("true");
                }

                if (parent != null) {
                    return parent.enablePreview;
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
            map = new HashMap<>();
        }

        synchronized Entry getEntry(File dir) {
            if (lastUsedEntry == null || !lastUsedEntry.dir.equals(dir))
                lastUsedEntry = getEntryInternal(dir);
            return lastUsedEntry;
        }

        private Entry getEntryInternal(File dir) {
            SoftReference<Entry> ref = map.get(dir);
            Entry e = (ref == null) ? null : ref.get();
            if (e == null) {
                Entry parent = dir.equals(rootDir) ? null : getEntryInternal(dir.getParentFile());
                map.put(dir, new SoftReference<>(e = new Entry(parent, dir)));
            }
            return e;
        }
    }

    private static final boolean allowLocalKeys =
            Boolean.parseBoolean(System.getProperty("javatest.regtest.allowLocalKeys", "true"));

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(RegressionTestSuite.class);
}
