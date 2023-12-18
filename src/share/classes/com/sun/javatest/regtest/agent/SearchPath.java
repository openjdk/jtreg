/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.agent;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A search path, as in an ordered set of file system locations,
 * such as directories, zip files and jar files.
 */
public final class SearchPath {
    /**
     * Creates an empty search path.
     */
    public SearchPath() { }

    /**
     * Creates a search path containing a series of entries.
     * Equivalent to {@code new Path().append(files)}.
     *
     * @param entries the entries to be included in the search path
     */
    public SearchPath(Path... entries) {
        append(entries);
    }

    /**
     * Creates a search path containing the concatenation of a series of search paths.
     * Equivalent to {@code new Path().append(paths)}.
     *
     * @param paths the paths to be included in the search path
     */
    public SearchPath(SearchPath... paths) {
        append(paths);
    }

    /**
     * Creates a search path containing the concatenation of a series of search paths.
     * Equivalent to {@code new Path().append(paths)}.
     *
     * @param paths the search paths to be included in the new search path
     *
     * @throws InvalidPathException if any of the paths contain invalid file paths
     */
    public SearchPath(String... paths) throws InvalidPathException {
        append(paths);
    }

    /**
     * Appends a series of entries to this search path.
     * Entries that do not exist are ignored.
     *
     * @param entries the entries to be added to the path
     * @return the path itself
     */
    public SearchPath append(Collection<Path> entries) {
        for (Path e: entries) {
            if (Files.exists(e)) {
                this.entries.add(e);
            }
        }
        return this;
    }

    /**
     * Appends a series of entries to this search path.
     * Entries that do not exist are ignored.
     *
     * @param entries entries to be added to the path
     * @return the path itself
     *
     * @throws InvalidPathException if any of the files are invalid
     */
    public SearchPath append(Path... entries) throws InvalidPathException {
        for (Path e: entries) {
            if (Files.exists(e)) {
                this.entries.add(e);
            }
        }
        return this;
    }

    /**
     * Appends a series of paths to this search path.
     *
     * @param paths paths to be added to the search path
     *
     * @return the path itself
     */
    public SearchPath append(SearchPath... paths) {
        for (SearchPath p: paths) {
            entries.addAll(p.entries);
        }
        return this;
    }

    /**
     * Appends a series of paths to this search path.
     *
     * @param paths paths to be added to the search path
     * @return the path itself
     *
     * @throws InvalidPathException if any of the paths contain invalid file paths
     */
    public SearchPath append(String... paths) throws InvalidPathException {
        for (String p: paths) {
            for (String q: p.split(Pattern.quote(PATHSEP))) {
                if (q.length() > 0) {
                    Path f = Paths.get(q);
                    if (Files.exists(f)) {
                        entries.add(f);
                    }
                }
            }
        }
        return this;
    }

    /**
     * Removes entries from this search path.
     *
     * @param entries entries to be removed from the search path
     * @return the path itself
     */
    public SearchPath removeAll(Collection<Path> entries) {
        this.entries.removeAll(entries);
        return this;
    }

    /**
     * Retains just specified entries on this search path.
     *
     * @param entries entries to be retained in the search path
     * @return the path itself
     *
     * @throws InvalidPathException if any of the entries are invalid
     */
    public SearchPath retainAll(Collection<Path> entries) {
        this.entries.retainAll(entries);
        return this;
    }

    /**
     * Returns the list of entries that are currently on this search path.
     *
     * @return the entries on the search path
     */
    public List<Path> asList() {
        return new ArrayList<>(entries);
    }

    /**
     * Checks if this search path is empty.
     *
     * @return {@code true} if this path does not have any entries on it,
     *      and {@code false} otherwise
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Returns the string value of this search path.
     *
     * @return the string value of this search path
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Path e: entries) {
            if (sb.length() > 0)
                sb.append(PATHSEP);
            sb.append(e);
        }
        return sb.toString();
    }

    // For now, with only append operations, a LinkedHashSet is good enough.
    // If we wanted more flexible operations, it may be desirable to keep
    // both a list (to record the order) and a set (to help detect duplicates).
    private final Set<Path> entries = new LinkedHashSet<>();
    private static final String PATHSEP = File.pathSeparator;
}
