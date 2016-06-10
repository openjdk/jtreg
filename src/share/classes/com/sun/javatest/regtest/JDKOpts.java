/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.javatest.regtest.agent.SearchPath;

/**
 * A class to manage lists of options for java and javac, merging them as necessary.
 * The options that can be merged are those that might reasonably be specified by
 * the user and by jtreg itself, and for which a repeated value is invalid in some way.
 *
 * @author jjg
 */
public class JDKOpts {
    private final List<String> opts;
    private final Map<String, Integer> index;
    private String pending;
    private char pendingSeparator;

    public JDKOpts() {
        opts = new ArrayList<String>();
        index = new HashMap<String, Integer>();
    }

    public boolean isEmpty() {
        return opts.isEmpty();
    }

    public int size() {
        return opts.size();
    }

    public String get(int i) {
        return opts.get(i);
    }

    public List<String> toList() {
        return Collections.unmodifiableList(opts);
    }

    public void addAll(List<String> opts) {
        for (String opt: opts) {
            add(opt);
        }
    }

    public void addAll(String... opts) {
        for (String opt: opts) {
            add(opt);
        }
    }

    public void add(String opt) {
        if (pending != null) {
            // this is the "value" to the preceding option
            updateOpt(pending, opt, pendingSeparator);
            pending = null;
        } else if (opt.equals("-classpath")
                || opt.equals("-sourcepath")) {
            pending = opt;
            pendingSeparator = File.pathSeparatorChar;
        } else if (opt.equals("-cp")) {
            pending = "-classpath";
            pendingSeparator = File.pathSeparatorChar;
        } else if (opt.equals("-addmods")
                || opt.equals("-limitmods")) {
            pending = opt;
            pendingSeparator = ',';
        } else if (opt.startsWith("-Xpatch:")) {
            updateXpatch(opt);
        } else if (opt.startsWith("-XaddReads:")) {
            updateAddReads(opt);
        } else if (opt.startsWith("-XaddExports:")) {
            updateAddExports(opt);
        } else if (opt.startsWith("-")) {
            opts.add(opt);
        } else {
            opts.add(opt);
        }
    }

    /**
     * Adds a path-valued option.
     * If opt ends with ":", a single option is added; otherwise opt and path are added
     * as two distinct items.
     * @param opt the option
     * @param path the path value
     */
    public void addPath(String opt, SearchPath path) {
        if (path != null && !path.isEmpty()) {
            if (opt.equals("-Xpatch:")) {
            } else if (opt.endsWith(":")) {
                add(opt + path);
            } else {
                add(opt);
                add(path.toString());
            }
        }
    }

    /**
     * Adds a series of -Xpatch options for the directories found on a search path.
     * The directories are assumed to be named for the modules they contain.
     * Note: jar files on the search path are not supported by this method.
     * @param patchPath the search path on which to look for modules to be patched
     */
    public void addAllXPatch(SearchPath patchPath) {
        if (patchPath != null) {
            for (File dir : patchPath.asList()) {
                File[] subdirs = dir.listFiles();
                if (subdirs != null) {
                    Arrays.sort(subdirs); // for repeatability; good enough for now
                    for (File subdir: subdirs) {
                        if (subdir.isDirectory()) {
                            updateXpatch("-Xpatch:" + subdir.getName() + "=" + subdir);
                        }
                    }
                }
            }
        }
    }

    /**
     * Update -XaddExports. -XaddExports:module/package=module,module
     * Only one instance per module/package is allowed.
     * @param opt
     */
    private void updateAddExports(String opt) {
        updateOpt(opt, '=', ',');
    }

    /**
     * Update -XaddReads. -XaddReads:module=module,module
     * Only one instance per module is allowed.
     * @param opt
     */
    private void updateAddReads(String opt) {
        updateOpt(opt, '=', ',');
    }

    /**
     * Update -Xpatch.  -Xpatch:module=classpath.
     * Only one instance of the option is allowed per module.
     * @param opt the option
     */
    private void updateXpatch(String opt) {
        // -Xpatch:module=path
        updateOpt(opt, '=', File.pathSeparatorChar);
    }

    /**
     * Update the list of options with a single-word multi-valued option.
     * @param opt the option name and values to add or merge into the list
     * @param keySep the separator between the key and the values
     * @param valSep the separator between values
     */
    private void updateOpt(String opt, char keySep, char valSep) {
        int i = opt.indexOf(keySep);
        String key = opt.substring(0, i + 1);
        String optValues = opt.substring(i + 1);
        Integer pos = index.get(key);
        if (pos == null) {
            pos = opts.size();
            opts.add(opt);
            index.put(key, pos);
        } else {
            Set<String> allValues = new LinkedHashSet<String>();
            String[] oldValues = opts.get(pos).substring(i + 1).split(String.valueOf(valSep));
            allValues.addAll(Arrays.asList(oldValues));
            allValues.addAll(Arrays.asList(optValues.split(String.valueOf(valSep))));
            StringBuilder sb = new StringBuilder(key); // includes keySep
            for (String v: allValues) {
                if (sb.length() > key.length()) {
                    sb.append(valSep);
                }
                sb.append(v);
            }
            opts.set(pos, sb.toString());
        }
    }

    /**
     * Update the list of options with a space-separated multi-valued option.
     * @param opt the name of the option to update
     * @param values the values for the option to update
     * @param valSep the separator between values
     */
    private void updateOpt(String opt, String values, char valSep) {
        Integer pos = index.get(opt);
        if (pos == null) {
            pos = opts.size();
            opts.add(opt);
            opts.add(values);
            index.put(opt, pos);
        } else {
            Set<String> allValues = new LinkedHashSet<String>();
            String[] oldValues = opts.get(pos + 1).split(String.valueOf(valSep));
            allValues.addAll(Arrays.asList(oldValues));
            allValues.addAll(Arrays.asList(values.split(String.valueOf(valSep))));
            StringBuilder sb = new StringBuilder();
            for (String v: allValues) {
                if (sb.length() > 0) {
                    sb.append(valSep);
                }
                sb.append(v);
            }
            opts.set(pos + 1, sb.toString());
        }

    }
}
