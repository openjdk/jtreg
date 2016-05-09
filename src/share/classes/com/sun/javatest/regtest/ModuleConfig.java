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

package com.sun.javatest.regtest;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.sun.javatest.regtest.agent.SearchPath;


/**
 * Reports details on module configurations.
 * The configuration can include any of the following information:
 *  add mods, limit mods, add exports, add reads, module path, class path,
 *  boot class path (append), patch (old style), patch.
 */
public class ModuleConfig {
    private final String title;
    private List<String> addMods;
    private List<String> limitMods;
    private Map<String,List<String>> addExports;
    private Map<String,List<String>> addReads;
    private SearchPath modulePath;
    private SearchPath classPath;
    private SearchPath bootClassPathAppend;
    private Map<String,SearchPath> patch;

    ModuleConfig(String title) {
        this.title = title;
    }

    ModuleConfig setFromOpts(JDKOpts opts) {
        setFromOpts(opts.toList());
        return this;
    }

    ModuleConfig setFromOpts(List<String> opts) {
        ListIterator<String> iter = opts.listIterator();
        while (iter.hasNext()) {
            String opt = iter.next();
            if (opt.equals("-addmods")) {
                setAddMods(Arrays.asList(iter.next().split(",")));
            } else if (opt.equals("-limitmods")) {
                setLimitMods(Arrays.asList(iter.next().split(",")));
            } else if (opt.startsWith("-XaddExports:")) {
                int sep = opt.indexOf(":");
                int eq = opt.indexOf("=");
                String modulePackage = opt.substring(sep + 1, eq);
                List<String> targetModules = Arrays.asList(opt.substring(eq + 1).split(","));
                setAddExports(modulePackage, targetModules);
            } else if (opt.startsWith("-XaddReads:")) {
                int sep = opt.indexOf(":");
                int eq = opt.indexOf("=");
                String module = opt.substring(sep + 1, eq);
                List<String> targetModules = Arrays.asList(opt.substring(eq + 1).split(","));
                setAddReads(module, targetModules);
            } else if (opt.startsWith("-Xbootclasspath/a:")) {
                int sep = opt.indexOf(":");
                setBootClassPathAppend(new SearchPath(opt.substring(sep + 1)));
            } else if (opt.equals("-classpath") || opt.equals("-cp")) {
                setClassPath(new SearchPath(iter.next()));
            } else if (opt.equals("-modulepath") || opt.equals("-mp")) {
                setModulePath(new SearchPath(iter.next()));
            } else if (opt.startsWith("-Xpatch:")) {
                int sep = opt.indexOf(":");
                int eq = opt.indexOf("=");
                String module = opt.substring(sep + 1, eq);
                SearchPath path = new SearchPath(opt.substring(eq + 1));
                setXPatch(module, path);
            }
        }
        return this;
    }

    ModuleConfig setAddMods(List<String> mods) {
        addMods = mods;
        return this;
    }

    ModuleConfig setLimitMods(List<String> mods) {
        limitMods = mods;
        return this;
    }

    ModuleConfig setAddExportsToUnnamed(Set<String> modules) {
        for (String module: modules) {
            if (module.contains("/")) {
                setAddExports(module, Collections.singletonList("ALL-UNNAMED"));
            }
        }
        return this;
    }

    ModuleConfig setAddExports(String modulePackage, List<String> targetModules) {
        if (addExports == null)
            addExports = new TreeMap<String, List<String>>();
        addExports.put(modulePackage, targetModules);
        return this;
    }

    ModuleConfig setAddReads(String module, List<String> targetModules) {
        if (addReads == null)
            addReads = new TreeMap<String, List<String>>();
        addReads.put(module, targetModules);
        return this;
    }

    ModuleConfig setBootClassPathAppend(SearchPath bootClassPathAppend) {
        this.bootClassPathAppend = bootClassPathAppend;
        return this;
    }

    ModuleConfig setClassPath(SearchPath classPath) {
        this.classPath = classPath;
        return this;
    }

    ModuleConfig setModulePath(SearchPath modulePath) {
        this.modulePath = modulePath;
        return this;
    }

    ModuleConfig setXPatch(String module, SearchPath patchPath) {
        if (patch == null)
            patch = new TreeMap<String, SearchPath>();
        patch.put(module, patchPath);
        return this;
    }

    void write(PrintWriter pw) {


        Table table = new Table();
        if (addMods != null && !addMods.isEmpty()) {
            table.addRow("add modules:", StringUtils.join(addMods, " "));
        }

        if (limitMods != null && !limitMods.isEmpty()) {
            table.addRow("limit modules:", StringUtils.join(limitMods, " "));
        }

        if (addExports != null && !addExports.isEmpty()) {
            String label = "add exports:";
            for (Map.Entry<String, List<String>> e: addExports.entrySet()) {
                table.addRow(label, e.getKey(), StringUtils.join(e.getValue(), " "));
                label = null;
            }
        }

        if (addReads != null && !addReads.isEmpty()) {
            String label = "add reads:";
            for (Map.Entry<String, List<String>> e: addReads.entrySet()) {
                table.addRow(label, e.getKey(), StringUtils.join(e.getValue(), " "));
                label = null;
            }
        }

        if (modulePath != null) {
            String label = "module path:";
            for (File file: modulePath.split()) {
                table.addRow(label, file.getPath());
                label = null;
            }
        }

        if (classPath != null) {
            String label = "class path:";
            for (File file: classPath.split()) {
                table.addRow(label, file.getPath());
                label = null;
            }
        }

        if (bootClassPathAppend != null) {
            String label = "boot class path (append):";
            for (File file: bootClassPathAppend.split()) {
                table.addRow(label, file.getPath());
                label = null;
            }
        }

        if (patch != null) {
            String label = "patch:";
            for (Map.Entry<String, SearchPath> e: patch.entrySet()) {
                String module = e.getKey();
                for (File file: e.getValue().split()) {
                    table.addRow(label, module, file.getPath());
                    label = null;
                    module = null;
                }
            }
        }

        if (table.rows.isEmpty())
            return;

        pw.println(title);
        table.write(pw, 2);
        pw.println();
    }

    private static class Table {
        List<List<String>> rows = new ArrayList<List<String>>();

        void addRow(String... items) {
            rows.add(Arrays.asList(items));
        }

        void write(PrintWriter pw, int indent) {
            int maxCols = 0;
            for (List<String> row : rows) {
                maxCols = Math.max(maxCols, row.size());
            }

            int[] widths = new int[maxCols];
            for (List<String> row : rows) {
                int col = 0;
                for (String item: row) {
                    // Exclude the last non-empty column of any row from the width calculation
                    // so that it can flow into other columns present in other rows.
                    // This helps prevent filenames (always the last entry in a row)
                    // from bloating column widths.
                    if (item != null && col < row.size() - 1) {
                        widths[col] = Math.max(widths[col], item.length());
                    }
                    col++;
                }
            }

            for (List<String> row : rows) {
                space(pw, indent);
                int col = 0;
                for (String item: row) {
                    if (item == null) {
                        space(pw, widths[col]);
                    } else {
                        pw.write(item);
                        space(pw, widths[col] - item.length());
                    }
                    pw.write(" ");
                    col++;
                }
                pw.println();
            }

        }

        private void space(PrintWriter pw, int spaces) {
            for (int i = 0; i < spaces; i++)
                pw.print(" ");
        }
    }
}
