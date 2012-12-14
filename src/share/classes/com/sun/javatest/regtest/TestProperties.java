/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;

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
    }

    Set<String> getValidKeys(File file) throws TestSuite.Fault {
        if (!allowLocalKeys)
            return validKeys;

        File dir = file.isDirectory() ? file : file.getParentFile();
        Cache.Entry e = cache.getEntry(dir);
        return e.validKeys;
    }

    ExecMode getDefaultExecMode() {
        return defaultExecMode;
    }

    boolean useOtherVM(File file) throws TestSuite.Fault {
        File dir = file.isDirectory() ? file : file.getParentFile();
        Cache.Entry e = cache.getEntry(dir);
        return e.useOtherVM;
    }

    boolean needsExclusiveAccess(File file) throws TestSuite.Fault {
        File dir = file.isDirectory() ? file : file.getParentFile();
        Cache.Entry e = cache.getEntry(dir);
        return e.needsExclusiveAccess;
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

    static class Cache {
        class Entry {
            final Entry parent;
            final File dir;
            final Properties properties;
            final Set<String> validKeys;
            final boolean useOtherVM;
            private final Set<String> otherVMDirs;
            final boolean needsExclusiveAccess;
            private final Set<String> exclusiveAccessDirs;

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
                    validKeys = initSet(parent == null ? null : parent.validKeys, "keys");

                    // add the list of othervm dirs
                    otherVMDirs = initSet(parent == null ? null : parent.otherVMDirs, "othervm.dirs");

                    // add the list of exclusive access dirs
                    exclusiveAccessDirs = initSet(parent == null ? null : parent.exclusiveAccessDirs, "exclusiveAccess.dirs");
                } else {
                    if (parent == null)
                        throw new IllegalStateException("TEST.ROOT not found");
                    properties = parent.properties;
                    validKeys = parent.validKeys;
                    otherVMDirs = parent.otherVMDirs;
                    exclusiveAccessDirs = parent.exclusiveAccessDirs;
                }

                useOtherVM = initUseOtherVM(parent, dir);
                needsExclusiveAccess = initNeedsExclusiveAccess(parent, dir);
            }

            private Set<String> initSet(Set<String> parent, String propertyName) {
                String[] values = StringArray.splitWS(properties.getProperty(propertyName));
                if (parent == null || values.length > 0) {
                    Set<String> set = (parent == null) ? new HashSet<String>() : new HashSet<String>(parent);
                    set.addAll(Arrays.asList(values));
                    return set;
                } else {
                    return parent;
                }
            }

            private boolean initUseOtherVM(Entry parent, File dir) {
                if (parent == null)
                    return false;

                if (parent.useOtherVM)
                    return true;

                for (String otherVMDir: otherVMDirs) {
                    if (includes(new File(rootDir, otherVMDir), dir))
                        return true;
                }

                return false;
            }

            private boolean initNeedsExclusiveAccess(Entry parent, File dir) {
                if (parent == null)
                    return false;

                if (parent.needsExclusiveAccess)
                    return true;

                for (String exclusiveAccessDir: exclusiveAccessDirs) {
                    if (includes(new File(rootDir, exclusiveAccessDir), dir))
                        return true;
                }

                return false;
            }

            private boolean includes(File dir, File file) {
                for ( ; file != null; file = file.getParentFile()) {
                    if (dir.equals(file))
                        return true;
                }
                return false;
            }
        }


        /** Cache map, using weak and soft references. */
        WeakHashMap<File, SoftReference<Entry>> map;
        /** Strong reference to most recent entry, and all its ancestors */
        Entry lastUsedEntry;
        File rootDir;

        Cache(File rootDir) {
            this.rootDir = rootDir;
            map = new WeakHashMap<File, SoftReference<Entry>>();
        }

        Entry getEntry(File dir) throws TestSuite.Fault {
            if (lastUsedEntry == null || !lastUsedEntry.dir.equals(dir))
                lastUsedEntry = getEntryInternal(dir);
            return lastUsedEntry;
        }

        private Entry getEntryInternal(File dir) throws TestSuite.Fault {
            SoftReference<Entry> ref = map.get(dir);
            Entry e = (ref == null) ? null : ref.get();
            if (e == null) {
                Entry parent = dir.equals(rootDir) ? null : getEntryInternal(dir.getParentFile());
                e = new Entry(parent, dir);
            }
            return e;
        }
    }

    private static final boolean allowLocalKeys =
            Boolean.parseBoolean(System.getProperty("javatest.regtest.allowLocalKeys", "true"));

    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(RegressionTestSuite.class);
}
