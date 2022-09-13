/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import com.sun.javatest.regtest.util.FileUtils;
import com.sun.javatest.regtest.util.GraphUtils;
import com.sun.javatest.regtest.util.GraphUtils.Node;
import com.sun.javatest.regtest.util.GraphUtils.TarjanNode;
import com.sun.javatest.util.I18NResourceBundle;

/**
 * Manage test groups, for use on the jtreg command line.
 */
public class GroupManager {
    public static void main(String... args) throws Exception {
        Path root = Path.of(args[0]);
        List<String> files = new ArrayList<>();
        files.addAll(List.of(args).subList(1, args.length));
        PrintWriter out = new PrintWriter(System.err);
        try {
            GroupManager gm = new GroupManager(out, root, files);
            gm.setIgnoredDirectories(List.of("SCCS"));
            gm.setAllowedExtensions(List.of(".java", ".sh", ".html"));
            for (Group g: gm.groups.values())
                System.err.println(g.name + ": " + g.getFiles());
        } finally {
            out.flush();
        }
    }

    public static final String GROUP_PREFIX = ":";
    public static final String EXCLUDE_PREFIX = "-";

    final PrintWriter out;
    final Path root;
    final Map<String, Group> groups = new HashMap<>();
    private Collection<String> ignoreDirs = Collections.emptySet();
    private Collection<String> allowExtns = Collections.emptySet();

    public static class InvalidGroup extends Exception {
        private static final long serialVersionUID = 1L;
    }

    GroupManager(PrintWriter out, Path root, List<String> files) throws IOException {
        this.out = out;
        this.root = root;

        for (String f: files) {
            boolean optional;
            if (f.startsWith("[") && f.endsWith("]")) {
                f = f.substring(1, f.length() - 1);
                optional = true;
            } else
                optional = false;

            Path file = root.resolve(f);
            if (optional && !Files.exists(file)) {
                continue;
            }


            try (BufferedReader in = Files.newBufferedReader(file)){
                Properties p = new Properties();
                p.load(in);
                for (Map.Entry<Object,Object> e: p.entrySet()) {
                    String groupName = (String) e.getKey();
                    String entryDef = (String) e.getValue();
                    Group g = getGroup(groupName);
                    g.addEntry(new Entry(file, root, entryDef));
                }
            }
        }

        validate();
    }

    void setAllowedExtensions(Collection<String> extns) {
        allowExtns = new HashSet<>(extns);
    }

    void setIgnoredDirectories(Collection<String> names) {
        ignoreDirs = new HashSet<>(names);
    }

    public Set<Path> getFiles(String group) throws InvalidGroup {
        Group g = getGroup(group);
        if (g.invalid)
            throw new InvalidGroup();
        return g.getFiles();
    }

    private Group getGroup(String name) {
        Group g = groups.get(name);
        if (g == null)
           groups.put(name, g = new Group(name));
        return g;
    }

    public Set<String> getGroups() {
        return groups.keySet();
    }

    public boolean invalid() {
        return groups.values().stream().anyMatch(g -> g.invalid);
    }

    private void validate() {
        for (Group g: groups.values()) {
            if (!g.name.matches("(?i)[a-z][a-z0-9_]*")) {
                error(g, i18n.getString("gm.invalid.name.for.group"));
            }
            for (Entry e: g.entries) {
                List<Set<Path>> allFiles = List.of(e.includeFiles, e.excludeFiles);
                for (Set<Path> files: allFiles) {
                    for (Path f: files) {
                        if (!Files.exists(f)) {
                            URI u = root.toUri().relativize(f.toUri());
                            error(e.origin, g, i18n.getString("gm.file.not.found", u.getPath()));
                        }
                    }
                }
                for (Group eg: e.includeGroups) {
                    if (eg.isEmpty())
                        error(e.origin, g, i18n.getString("gm.group.not.found", eg.name));
                    if (eg == g)
                        error(e.origin, g, i18n.getString("gm.group.includes.itself"));
                }
            }
        }

        final Map<Group, TarjanNode<Group>> nodes = new HashMap<>();
        for (Group g: groups.values()) {
            nodes.put(g, new TarjanNode<>(g) {
                @Override
                public Iterable<? extends TarjanNode<Group>> getDependencies() {
                    List<TarjanNode<Group>> deps = new ArrayList<>();
                    for (Entry e : data.entries) {
                        for (Group g : e.includeGroups) {
                            deps.add(nodes.get(g));
                        }
                    }
                    return deps;
                }

                @Override
                public String printDependency(Node<Group> to) {
                    return to.data.name;
                }
            });
        }

        Set<? extends Set<? extends TarjanNode<Group>>> cycles = GraphUtils.tarjan(nodes.values());
        for (Set<? extends TarjanNode<Group>> cycle : cycles) {
            if (cycle.size() > 1) {
                String s = cycle.stream()
                        .map(tn -> tn.data.name)
                        .collect(Collectors.joining(", "));
                cycle.stream()
                        .map(tn -> tn.data)
                        .forEach(g -> error(g, i18n.getString("gm.cycle.detected", s)));
            }
        }
    }

    private void error(Group g, String message) {
        if (g.entries.isEmpty()) {
            out.println(i18n.getString("gm.group.prefix", g.name, message));
            g.invalid = true;
        } else {
            error(g.entries.get(0).origin, g, message);
        }
    }

    private void error(Path f, Group g, String message) {
        out.println(i18n.getString("gm.file.group.prefix", f, g.name, message));
        g.invalid = true;
    }

    private class Group {
        final String name;
        final List<Entry> entries;
        private Set<Path> files;
        boolean invalid;

        Group(String name) {
            this.name = name;
            entries = new ArrayList<>();
        }

        boolean isEmpty() {
            return entries.isEmpty();
        }

        void addEntry(Entry e) {
            entries.add(e);
        }

        Set<Path> getFiles() {
            if (files == null) {
                files = new LinkedHashSet<>();
                Set<Path> inclFiles = new HashSet<>();
                Set<Path> exclFiles = new HashSet<>();
                for (Entry e: entries) {
                    inclFiles.addAll(e.includeFiles);
                    for (Group g: e.includeGroups)
                        inclFiles.addAll(g.getFiles());
                    exclFiles.addAll(e.excludeFiles);
                    for (Group g: e.excludeGroups)
                        exclFiles.addAll(g.getFiles());
                }
                addFiles(files, inclFiles, exclFiles);
            }
            return files;
        }

        private void addFiles(Collection<Path> files, Collection<Path> includes, Collection<Path> excludes) {
            for (Path incl: includes) {
                if (contains(files, incl) || contains(excludes, incl))
                    continue;

                if (Files.isRegularFile(incl))
                    addFile(files, incl);
                else if (Files.isDirectory(incl)) {
                    Set<Path> excludesForIncl = filter(incl, excludes);
                    if (excludesForIncl.isEmpty())
                        addFile(files, incl);
                    else
                        addFiles(files, list(incl), excludesForIncl);
                }
            }
        }

        private void addFile(Collection<Path> files, Path file) {
            files.removeIf(f -> contains(file, f));
            files.add(file);
        }

        private boolean contains(Collection<Path> files, Path file) {
            for (Path f: files) {
                if (f.equals(file) || contains(f, file)) {
                    return true;
                }
            }
            return false;
        }

        private boolean contains(Path dir, Path file) {
            return file.startsWith(dir);
        }

        private Set<Path> filter(Path dir, Collection<Path> files) {
            Set<Path> results = null;
            for (Path f: files) {
                if (f.startsWith(dir)) {
                    if (results == null) results = new LinkedHashSet<>();
                    results.add(f);
                }
            }
            return results == null ? Set.of() : results;
        }

        private List<Path> list(Path file) {
            List<Path> children = new ArrayList<>();
            for (Path f: FileUtils.listFiles(file)) {
                String fn = f.getFileName().toString();
                if (Files.isDirectory(f) && !ignoreDirs.contains(fn)
                        || Files.isRegularFile(f) && allowExtns.contains(getExtension(fn)))
                    children.add(f);
            }
            return children;
        }

        private String getExtension(String name) {
            int sep = name.lastIndexOf(".");
            return (sep == -1) ? null : name.substring(sep);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    class Entry {
        final Path origin;

        final Set<Path> includeFiles = new LinkedHashSet<>();
        final Set<Path> excludeFiles = new LinkedHashSet<>();
        final Set<Group> includeGroups = new LinkedHashSet<>();
        final Set<Group> excludeGroups = new LinkedHashSet<>();

        Entry(Path origin, Path root, String def) {
            this.origin = origin;

            def = def.trim();
            if (def.length() == 0)
                return;

            for (String item: def.split("\\s+")) {
                boolean exclude = item.startsWith(EXCLUDE_PREFIX);
                if (exclude)
                    item = item.substring(1);

                if (item.startsWith(GROUP_PREFIX)) {
                    String name = item.substring(1);
                    (exclude ? excludeGroups : includeGroups).add(getGroup(name));
                } else {
                    String name = item;
                    if (name.startsWith("/"))
                        name = name.substring(1);
                    if (name.endsWith("/"))
                        name = name.substring(0, name.length() - 1);
                    Path f = name.equals("") ? root : root.resolve(name);
                    (exclude ? excludeFiles : includeFiles).add(f);
                }
            }
        }

        @Override
        public String toString() {
            return "Entry[origin:" + origin
                    + "inclFiles:" + includeFiles
                    + ",exclFiles:" + excludeFiles
                    + ",inclGroups:" + includeGroups
                    + ",exclGroups:" + excludeGroups
                    + "]";
        }
    }

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(GroupManager.class);
}
