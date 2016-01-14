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

    public void addPath(String opt, SearchPath path) {
        if (path != null && !path.isEmpty()) {
            if (opt.endsWith(":")) {
                add(opt + path);
            } else {
                add(opt);
                add(path.toString());
            }
        }
    }

    public void add(String opt) {
        if (opt.equals("-classpath")
                || opt.startsWith("-sourcepath")) {
            pending = opt;
            return;
        } else if (opt.equals("-cp")) {
            pending = "-classpath";
            return;
        }

        if (opt.startsWith("-Xpatch:")) {
            updateOpt(opt, File.pathSeparator);
        } else if (opt.startsWith("-XaddExports:")) {
            updateOpt(opt, ",");
        } else if (opt.startsWith("-")) {
            opts.add(opt);
        } else if (pending != null) {
            Integer pos = index.get(pending);
            if (pos == null) {
                opts.add(pending);
                opts.add(opt);
                index.put(pending, opts.size() - 1);
            } else {
                opts.set(pos, opts.get(pos) + File.pathSeparator + opt);
            }
        } else {
            opts.add(opt);
        }

        pending = null;
    }

    private void updateOpt(String opt, String sep) {
        int i = opt.indexOf(":");
        String key = opt.substring(0, i + 1);
        String optValues = opt.substring(i + 1);
        Integer pos = index.get(key);
        if (pos == null) {
            opts.add(opt);
            index.put(key, opts.size() - 1);
        } else {
            Set<String> allValues = new LinkedHashSet<String>();
            String[] oldValues = opts.get(pos).substring(i + 1).split(sep);
            allValues.addAll(Arrays.asList(oldValues));
            allValues.addAll(Arrays.asList(optValues.split(sep)));
            StringBuilder sb = new StringBuilder(key);
            for (String v: allValues) {
                if (sb.length() > key.length()) {
                    sb.append(sep);
                }
                sb.append(v);
            }
            opts.set(pos, sb.toString());
        }
    }
}
