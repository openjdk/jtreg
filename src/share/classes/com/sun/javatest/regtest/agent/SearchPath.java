/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A path, as in an ordered set of file system locations, such as directories,
 * zip files and jar files.
 */
public class SearchPath {
    /**
     * Create an empty path.
     */
    public SearchPath() {
    }

    /**
     * Create a path containing the concatenation of a series of files.
     * Equivalent to {@code new Path().append(files)}.
     * @param files
     */
    public SearchPath(File... files) {
        append(files);
    }

    /**
     * Create a path containing the concatenation of a series of paths.
     * Equivalent to {@code new Path().append(paths)}.
     * @param paths
     */
    public SearchPath(SearchPath... paths) {
        append(paths);
    }

    /**
     * Create a path containing the concatenation of a series of paths.
     * Equivalent to {@code new Path().append(paths)}.
     * @param paths
     */
    public SearchPath(String... paths) {
        append(paths);
    }

    /**
     * Append a series of files to the path.  Files that do not exist
     * are ignored.
     * @param files files to be added to the path
     * @return the path itself
     */
    public SearchPath append(Collection<File> files) {
        for (File f: files) {
            if (f.exists()) {
                entries.add(f);
            }
        }
        return this;
    }

    /**
     * Append a series of files to the path.  Files that do not exist
     * are ignored.
     * @param files files to be added to the path
     * @return the path itself
     */
    public SearchPath append(File... files) {
        for (File f: files) {
            if (f.exists()) {
                entries.add(f);
            }
        }
        return this;
    }

    /**
     * Append a series of paths to the path.
     * @param paths paths to be added to the path
     * @return the path itself
     */
    public SearchPath append(SearchPath... paths) {
        for (SearchPath p: paths) {
            entries.addAll(p.entries);
        }
        return this;
    }

    /**
     * Append a series of paths to the path.
     * @param paths paths to be added to the path
     * @return the path itself
     */
    public SearchPath append(String... paths) {
        for (String p: paths) {
            for (String q: p.split(Pattern.quote(PATHSEP))) {
                if (q.length() > 0) {
                    File f = new File(q);
                    if (f.exists()) {
                        entries.add(f);
                    }
                }
            }
        }
        return this;
    }

    /**
     * Remove files from a path.
     * @param files files to be removed from the path
     * @return the path itself
     */
    public SearchPath removeAll(Collection<File> files) {
        entries.removeAll(files);
        return this;
    }


    /**
     * Retain just specified files on a path.
     * @param files files to be retained the path
     * @return the path itself
     */
    public SearchPath retainAll(Collection<File> files) {
        entries.retainAll(files);
        return this;
    }

    /**
     * Return the series of files that are currently on the path.
     * @return the files on the path
     */
    public List<File> asList() {
        return new ArrayList<File>(entries);
    }

    /**
     * Check if this path is empty.
     * @return true if this path does not have any files on it
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Return the string value of this path.
     * @return the string value of this path
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (File e: entries) {
            if (sb.length() > 0)
                sb.append(PATHSEP);
            sb.append(e);
        }
        return sb.toString();
    }

    // For now, with only append operations, a LinkedHashSet is good enough.
    // If we wanted more flexible operations, it may be desirable to keep
    // both a list (to record the order) and a set (to help detect duplicates).
    private final Set<File> entries = new LinkedHashSet<File>();
    private static final String PATHSEP = File.pathSeparator;
}
