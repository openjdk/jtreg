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

package com.sun.javatest.regtest.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.javatest.regtest.agent.SearchPath;
import com.sun.javatest.regtest.util.StringUtils;

/**
 * A class to manage lists of options for java and javac, merging them as necessary.
 * The options that can be merged are those that might reasonably be specified by
 * the user and by jtreg itself, and for which a repeated value is invalid in some way.
 */
public class JDKOpts {
    /**
     * The JDK options supported by this class.
     * All options in this set take a value.
     * Repeated use of any of these options will be merged.
     * Options names will be converted their canonical form.
     */
    public enum Option {
        ADD_EXPORTS("--add-exports", "-XaddExports:"),
        ADD_EXPORTS_PRIVATE("--add-exports-private"),
        ADD_MODULES("--add-modules", "-addmods"),
        ADD_READS("--add-reads", "-XaddReads:"),
        CLASS_PATH("--class-path", "-classpath", "-cp"),
        LIMIT_MODULES("--limit-modules", "-limitmods"),
        MODULE_PATH("--module-path", "-modulepath", "-mp"),
        MODULE_SOURCE_PATH("--module-source-path", "-modulesourcepath"),
        PATCH_MODULE("--patch-module", "-Xpatch:"),
        SOURCE_PATH("--source-path", "-sourcepath");

        Option(String... names) {
            this.names = names;
        }

        final String[] names;
    }

    private final MergeHandler mergeHandler;
    private String pending;

    private static final char COMMA = ',';
    private static final char EQUALS = '=';
    private static final char NUL = '\0';
    private static final char PATHSEP = File.pathSeparatorChar;

    /**
     * Returns true if the option should be followed by an argument in the following position.
     * @param opt
     * @return true if a following argument is to be expected
     */
    public static boolean hasFollowingArg(String opt) {
        for (Option option: Option.values()) {
            for (String name: option.names) {
                if (opt.equals(name) && !name.endsWith(":") && !name.endsWith("="))
                    return true;
            }
        }
        return false;
    }

    /**
     * Creates an object to normalize a series of JDK options.
     */
    public JDKOpts() {
        mergeHandler = new MergeHandler();
    }

    /**
     * Returns the series of normalized options as a list.
     * @return a list
     */
    public List<String> toList() {
        return Collections.unmodifiableList(mergeHandler.opts);
    }

    /**
     * Adds a series of options.
     * @param opts the options.
     */
    public void addAll(List<String> opts) {
        for (String opt: opts) {
            add(opt);
        }
    }

    /**
     * Adds a series of options.
     * @param opts the options.
     */
    public void addAll(String... opts) {
        for (String opt: opts) {
            add(opt);
        }
    }

    /**
     * Adds a single option.
     * If the option needs an argument, it will be deferred until the following call,
     * which will be used as the value.
     * @param opt the option
     */
    public void add(String opt) {
        if (pending != null) {
            mergeHandler.handleOption(pending, opt);
            pending = null;
        } else if (hasFollowingArg(opt)) {
            pending = opt;
        } else {
            mergeHandler.handleOption(opt);
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
            if (opt.endsWith(":")) {
                add(opt + path);
            } else {
                add(opt);
                add(path.toString());
            }
        }
    }

    /**
     * Adds a series of {@code --patch-module} options for the directories
     * found on a search path.
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
                            mergeHandler.handleOption(Option.PATCH_MODULE, "--patch-module", subdir.getName() + "=" + subdir);
                        }
                    }
                }
            }
        }
    }


    /**
     * An option handler to merge multiple instances of the same option.
     */
    private static class MergeHandler extends OptionHandler {
        private final List<String> opts;
        private final Map<String, Integer> index;

        MergeHandler() {
            opts = new ArrayList<>();
            index = new HashMap<>();
        }

        @Override
        protected void handleOption(Option option, String opt, String arg) {
            switch (option) {
                case ADD_EXPORTS:
                    updateOptWhitespaceArg("--add-exports", arg, EQUALS, COMMA);
                    break;

                case ADD_EXPORTS_PRIVATE:
                    updateOptWhitespaceArg("--add-exports-private", arg, EQUALS, COMMA);
                    break;

                case ADD_MODULES:
                    updateOptWhitespaceArg("--add-modules", arg, NUL, COMMA);
                    break;

                case ADD_READS:
                    updateOptWhitespaceArg("--add-reads", arg, EQUALS, COMMA);
                    break;

                case CLASS_PATH:
                    updateOptWhitespaceArg("-classpath", arg, NUL, PATHSEP);
                    break;

                case LIMIT_MODULES:
                    updateOptWhitespaceArg("--limit-modules", arg, NUL, COMMA);
                    break;

                case MODULE_PATH:
                    updateOptWhitespaceArg("--module-path", arg, NUL, PATHSEP);
                    break;

                case MODULE_SOURCE_PATH:
                    updateOptWhitespaceArg("--module-source-path", arg, NUL, PATHSEP);
                    break;

                case PATCH_MODULE:
                    updateOptWhitespaceArg("--patch-module", arg, EQUALS, PATHSEP);
                    break;

                case SOURCE_PATH:
                    updateOptWhitespaceArg("-sourcepath", arg, NUL, PATHSEP);
                    break;

            }
        }

        @Override
        protected void handleUnknown(String opt) {
            opts.add(opt);
        }

        /**
         * Update the list of options with a single-word multi-valued option.
         * This is for options of the form
         * {@code -option:key <keysep> value <valsep> value <valsep> value }
         * implying we assume that {@code keysep} is the first character
         * of its kind in {@code opt}.
         * @param opt the option name and values to add or merge into the list
         * @param keySep the separator between the key and the values
         * @param valSep the separator between values
         */
        private void updateOptAdjacentArg(String opt, char keySep, char valSep) {
            int i = opt.indexOf(keySep);
            String key = opt.substring(0, i + 1);
            String optValues = opt.substring(i + 1);
            Integer pos = index.get(key);
            if (pos == null) {
                pos = opts.size();
                opts.add(opt);
                index.put(key, pos);
            } else {
                Set<String> allValues = new LinkedHashSet<>();
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

        private void updateOptWhitespaceArg(String opt, String arg, char keySep, char valueSep) {
            String argKey;
            List<String> argValues;
            if (keySep != 0 && arg.indexOf(keySep) != -1) {
                argKey = StringUtils.beforePart(arg, keySep);
                argValues = StringUtils.split(StringUtils.afterPart(arg, keySep), valueSep);
            } else {
                argKey = null;
                argValues = StringUtils.split(arg, valueSep);
            }

            String indexKey = (argKey == null) ? opt : opt + "=" + argKey;
            Integer pos = index.get(indexKey);
            if (pos == null) {
                pos = opts.size();
                opts.add(opt);
                opts.add(join(argKey, keySep, argValues, valueSep));
                index.put(indexKey, pos);
            } else {
                Set<String> allValues = new LinkedHashSet<>();
                String old = opts.get(pos + 1);
                List<String> oldValues = StringUtils.split((argKey == null) ? old : StringUtils.afterPart(old, keySep), valueSep);
                allValues.addAll(oldValues);
                allValues.addAll(argValues);
                opts.set(pos + 1, join(argKey, keySep, allValues, valueSep));
            }
        }

        /**
         * Returns a string composed of given constituent parts.
         * The parts may be an optional initial key, and a series of values with a
         * specified separator. If a key is provided, it will be followed by the
         * key separator character in the result.
         * @param key the key, or null if none
         * @param keySet the separator to follow the key if one is specified
         * @param values the values
         * @param valSep the separator to use if more than one key
         * @return the composite string
         */
        static String join(String key, char keySep, Collection<String> values, char valSep) {
            StringBuilder sb = new StringBuilder();
            if (key != null) {
                sb.append(key).append(keySep);
            }
            boolean needSep = false;
            for (String v : values) {
                if (needSep) {
                    sb.append(valSep);
                }
                sb.append(v);
                needSep = true;
            }
            return sb.toString();
        }
    };

    public static abstract class OptionHandler {
        public void handleOptions(String... opts) {
            handleOptions(Arrays.asList(opts));
        }

        public void handleOptions(List<String> opts) {
            Iterator<String> iter = opts.iterator();
            while (iter.hasNext()) {
                String opt = iter.next();
                handleOption(opt, iter);
            }
        }

        void handleOption(String opt) {
            handleOption(opt, Collections.<String>emptyIterator());
        }

        void handleOption(String opt, String arg) {
            handleOption(opt, Collections.singleton(arg).iterator());
        }

        void handleOption(String opt, Iterator<String> rest) {
            for (Option o: Option.values()) {
                for (String name: o.names) {
                    if (name.startsWith("--")) {
                        if (opt.equals(name)) {
                            handleOption(o, opt, rest.next());
                            return;
                        } else if (opt.startsWith(name + "=")) {
                            handleOption(o, opt, opt.substring(name.length() + 1));
                            return;
                        }
                    } else {
                        if (name.endsWith(":")) {
                            if (opt.startsWith(name)) {
                                handleOption(o, opt, opt.substring(name.length()));
                                return;
                            }
                        } else if (opt.equals(name)) {
                            handleOption(o, opt, rest.next());
                            return;
                        }
                    }
                }
            }
            handleUnknown(opt);
        }

        protected abstract void handleOption(Option option, String opt, String arg);
        protected abstract void handleUnknown(String opt);
    }
}
