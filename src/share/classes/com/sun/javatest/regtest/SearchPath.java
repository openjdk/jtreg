/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

/**
 * A path, as in a sequence of file system locations, such as directories,
 * zip files and jar files.
 */
public class SearchPath {
    /**
     * Create an empty path.
     */
    SearchPath() {
    }

    /**
     * Create a path containing the concatenation of a series of files.
     * Equivalent to {@code new Path().append(files)}.
     * @param files
     */
    SearchPath(List<File> files) {
        append(files);
    }


    /**
     * Create a path containing the concatenation of a series of files.
     * Equivalent to {@code new Path().append(files)}.
     * @param files
     */
    SearchPath(File... files) {
        append(files);
    }

    /**
     * Create a path containing the concatenation of a series of paths.
     * Equivalent to {@code new Path().append(paths)}.
     * @param paths
     */
    SearchPath(SearchPath... paths) {
        append(paths);
    }

    /**
     * Create a path containing the concatenation of a series of paths.
     * Equivalent to {@code new Path().append(paths)}.
     * @param paths
     */
    SearchPath(String... paths) {
        append(paths);
    }

    /**
     * Append a series of files to the path.  Files that do not exist
     * are ignored.
     * @param files files to be added to the path
     * @return the path itself
     */
    SearchPath append(List<File> files) {
        for (File f: files) {
            if (f.exists()) {
                if (value.length() > 0)
                    value += PATHSEP;
                value += f.getPath();
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
    SearchPath append(File... files) {
        for (File f: files) {
            if (f.exists()) {
                if (value.length() > 0)
                    value += PATHSEP;
                value += f.getPath();
            }
        }
        return this;
    }

    /**
     * Append a series of paths to the path.
     * @param paths paths to be added to the path
     * @return the path itself
     */
    SearchPath append(SearchPath... paths) {
        for (SearchPath p: paths) {
            if (p.value.length() > 0) {
                if (value.length() > 0)
                    value += PATHSEP;
                value += p.value;
            }
        }
        return this;
    }

    /**
     * Append a series of paths to the path.
     * @param paths paths to be added to the path
     * @return the path itself
     */
    SearchPath append(String... paths) {
        for (String p: paths) {
            if (p.length() > 0) {
                if (value.length() > 0)
                    value += PATHSEP;
                value += p;
            }
        }
        return this;
    }

    /**
     * Return the series of files that are currently on the path.
     * @return the files on the path
     */
    List<File> split() {
        List<File> list = new ArrayList<File>();
        for (String s: StringArray.splitSeparator(PATHSEP, value)) {
            if (s.length() > 0) {
                list.add(new File(s));
            }
        }
        return list;
    }

    /**
     * Check if this path contains a subpath.
     * @param path the subpath to be checked
     * @return true if this path contains the subpath
     */
    boolean contains(SearchPath path) {
        return value.equals(path.value)
                || value.startsWith(path.value + PATHSEP)
                || value.endsWith(PATHSEP + path.value)
                || value.contains(PATHSEP + path.value + PATHSEP);
    }

    /**
     * Check if this path is empty.
     * @return true if this path does not have any files on it
     */
    boolean isEmpty() {
        return (value.length() == 0);
    }

    /**
     * Return the string value of this path.
     * @return the string value of this path
     */
    @Override
    public String toString() {
        return value;
    }

    private String value = "";
    private static final String PATHSEP = File.pathSeparator;
}
