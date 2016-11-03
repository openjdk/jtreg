/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.sun.javatest.TestDescription;
import com.sun.javatest.regtest.util.StringUtils;

/**
 * Utility class to handle the entries in a {@code @modules} tag.
 */
public class Modules implements Iterable<Modules.Entry> {
    /**
     * Used to report problems that are found.
     */
    public static class Fault extends Exception {
        private static final long serialVersionUID = 1L;

        Fault(String message) {
            super(message);
        }
    }

    public enum Phase { STATIC, DYNAMIC };

    /**
     * Simple container for parsed entry in an @modules tag
     */
    public static class Entry {
        public final String moduleName;
        public final String packageName;
        public final boolean addExports;
        public final boolean addOpens;

        Entry(String moduleName, String packageName, boolean addExports, boolean addOpens) {
            this.moduleName = moduleName;
            this.packageName = packageName;
            this.addExports = addExports;
            this.addOpens = addOpens;
        }

        /**
         * Returns true if an export should be added in the specified phase
         * @param phase the phase
         * @return  true if an export should be added in the specified phase
         */
        public boolean needAddExports(Phase phase) {
            return (packageName != null);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(moduleName);
            if (packageName != null) {
                sb.append("/").append(packageName);
                String sep = ":";
                if (addOpens) {
                    if (addExports) {
                        sb.append(sep).append("+open");
                    } else {
                        sb.append(sep).append("open");
                    }
                }
            }
            return sb.toString();
        }
    }

    /**
     * Parse an entry in an @modules list.
     * @param s the entry
     * @return an Entry
     * @throws Fault if there is an error in the entry
     */
    public static Entry parse(String s) throws Fault {
        String moduleName;
        String packageName = null;
        boolean addExports = false;
        boolean addOpens = false;

        int slash = s.indexOf("/");
        if (slash == -1) {
            moduleName = s;
        } else {
            moduleName = s.substring(0, slash);
            int colon = s.indexOf(":", slash + 1);
            if (colon == -1) {
                packageName = s.substring(slash + 1);
                addExports = true;
            } else {
                packageName = s.substring(slash + 1, colon);
                String[] modifiers = s.substring(colon + 1).split(",");
                for (String m : modifiers) {
                    switch (m) {
                        case "open":
                        case "private":
                            addOpens = true;
                            break;
                        case "+open":
                            addExports = true;
                            addOpens = true;
                            break;
                        default:
                            throw new Fault("bad modifier: " + m);
                    }
                }
            }
        }
        if (!isDottedName(moduleName)) {
            throw new Fault("invalid module name: " + moduleName);
        }
        if (packageName != null && !isDottedName(packageName)) {
            throw new Fault("invalid package name: " + packageName);
        }

        return new Entry(moduleName, packageName, addExports, addOpens);
    }

    private static boolean isDottedName(String qualId) {
        for (String id : qualId.split("\\.")) {
            if (!isValidIdentifier(id)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidIdentifier(String id) {
        if (id.length() == 0) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(id.charAt(0))) {
            return false;
        }
        for (int i = 1; i < id.length(); i++) {
            if (!Character.isJavaIdentifierPart(id.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a collection of Entry objects for the entries in a test description's @modules tag.
     * If there is no @modules tag, the collection will be empty.
     * @param params the parameters for this test run
     * @param td the test description
     * @throws Fault if there is an error in the @modules tag.
     */
    public Modules(RegressionParameters params, TestDescription td) throws Fault {
        String tagEntries = td.getParameter("modules");
        if (tagEntries == null) {
            entries = Collections.emptySet();
        } else {
            entries = new LinkedHashSet<>();
            for (String s : tagEntries.trim().split("\\s+")) {
                entries.add(getEntry(params, s));
            }
        }
    }

    /**
     * Returns true if the test description does not have an @modules tag.
     * @return true if the test description does not have an @modules tag
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Returns an iterator for the entries in the @modules tag.
     * @return an iterator for the entries in the @modules tag
     */
    @Override // defined by java.lang.Iterable
    public Iterator<Entry> iterator() {
        return entries.iterator();
    }

    @Override
    public String toString() {
        return StringUtils.join(entries, " ");
    }

    private Entry getEntry(RegressionParameters params, String s) throws Fault {
        Map<String, Entry> cache = caches.get(params);
        if (cache == null) {
            caches.put(params, cache = new HashMap<>());
        }
        Entry e = cache.get(s);
        if (e == null) {
            cache.put(s, e = parse(s));
        }
        return e;
    }


    Set<Entry> entries;

    /**
     * A cache of Module.Entry objects, specific to the parameters for the test run.
     */
    private static final WeakHashMap<RegressionParameters, Map<String, Entry>> caches = new WeakHashMap<>();
}
