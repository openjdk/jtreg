/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.exec;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.sun.javatest.regtest.agent.SearchPath;
import com.sun.javatest.regtest.config.JDKOpts;
import com.sun.javatest.regtest.util.StringUtils;

import static com.sun.javatest.regtest.util.StringUtils.afterPart;
import static com.sun.javatest.regtest.util.StringUtils.beforePart;
import static com.sun.javatest.regtest.util.StringUtils.split;
import static com.sun.javatest.regtest.util.StringUtils.join;


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
    private Map<String,List<String>> addOpens;
    private Map<String,List<String>> addReads;
    private SearchPath modulePath;
    private SearchPath classPath;
    private SearchPath sourcePath;
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
        JDKOpts.OptionHandler h = new JDKOpts.OptionHandler() {
            @Override
            protected void handleOption(JDKOpts.Option option, String opt, String arg) {
                switch (option) {
                    case ADD_EXPORTS:
                        setAddExports(beforePart(arg, '='), split(afterPart(arg, '='), ','));
                        break;

                    case ADD_MODULES:
                        setAddModules(split(arg, ','));
                        break;

                    case ADD_EXPORTS_PRIVATE:
                    case ADD_OPENS:
                        setAddOpens(beforePart(arg, '='), split(afterPart(arg, '='), ','));
                        break;

                    case ADD_READS:
                        setAddReads(beforePart(arg, '='), split(afterPart(arg, '='), ','));
                        break;

                    case CLASS_PATH:
                        setClassPath(new SearchPath(arg));
                        break;

                    case SOURCE_PATH:
                        setSourcePath(new SearchPath(arg));
                        break;

                    case LIMIT_MODULES:
                        setLimitModules(split(arg, ','));
                        break;

                    case MODULE_PATH:
                        setModulePath(new SearchPath(arg));
                        break;

                    case PATCH_MODULE:
                        setPatchPath(beforePart(arg, '='), new SearchPath(afterPart(arg, '=')));
                         break;

                }
            }

            @Override
            protected void handleUnknown(String opt) { }

        };
        h.handleOptions(opts);
        return this;
    }

    ModuleConfig setAddModules(List<String> mods) {
        addMods = mods;
        return this;
    }

    ModuleConfig setLimitModules(List<String> mods) {
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

    ModuleConfig setAddOpensToUnnamed(Set<String> modules) {
        for (String module: modules) {
            if (module.contains("/")) {
                setAddOpens(module, Collections.singletonList("ALL-UNNAMED"));
            }
        }
        return this;
    }

    ModuleConfig setAddExports(String modulePackage, List<String> targetModules) {
        if (addExports == null)
            addExports = new TreeMap<>();
        addExports.put(modulePackage, targetModules);
        return this;
    }

    ModuleConfig setAddOpens(String modulePackage, List<String> targetModules) {
        if (addOpens == null)
            addOpens = new TreeMap<>();
        addOpens.put(modulePackage, targetModules);
        return this;
    }

    ModuleConfig setAddReads(String module, List<String> targetModules) {
        if (addReads == null)
            addReads = new TreeMap<>();
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

    ModuleConfig setSourcePath(SearchPath sourcePath) {
        this.sourcePath = sourcePath;
        return this;
    }

    ModuleConfig setModulePath(SearchPath modulePath) {
        this.modulePath = modulePath;
        return this;
    }

    ModuleConfig setPatchPath(String module, SearchPath patchPath) {
        if (patch == null)
            patch = new TreeMap<>();
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
                table.addRow(label, e.getKey(), join(e.getValue(), " "));
                label = null;
            }
        }

        if (addOpens != null && !addOpens.isEmpty()) {
            String label = "add opens:";
            for (Map.Entry<String, List<String>> e: addOpens.entrySet()) {
                table.addRow(label, e.getKey(), join(e.getValue(), " "));
                label = null;
            }
        }

        if (addReads != null && !addReads.isEmpty()) {
            String label = "add reads:";
            for (Map.Entry<String, List<String>> e: addReads.entrySet()) {
                table.addRow(label, e.getKey(), join(e.getValue(), " "));
                label = null;
            }
        }

        if (modulePath != null) {
            String label = "module path:";
            for (File file: modulePath.asList()) {
                table.addRow(label, file.getPath());
                label = null;
            }
        }

        if (sourcePath != null) {
            String label = "source path:";
            for (File file: sourcePath.asList()) {
                table.addRow(label, file.getPath());
                label = null;
            }
        }

        if (classPath != null) {
            String label = "class path:";
            for (File file: classPath.asList()) {
                table.addRow(label, file.getPath());
                label = null;
            }
        }

        if (bootClassPathAppend != null) {
            String label = "boot class path (append):";
            for (File file: bootClassPathAppend.asList()) {
                table.addRow(label, file.getPath());
                label = null;
            }
        }

        if (patch != null) {
            String label = "patch:";
            for (Map.Entry<String, SearchPath> e: patch.entrySet()) {
                String module = e.getKey();
                for (File file: e.getValue().asList()) {
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
        List<List<String>> rows = new ArrayList<>();

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
                    if (widths[col] > 0) {
                        pw.write(" ");
                    }
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
