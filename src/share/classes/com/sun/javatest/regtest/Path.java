/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.javatest.regtest;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A path, as in a sequence of file system locations, such as directories,
 * zip files and jar files.
 */
public class Path {
    /**
     * Create an empty path.
     */
    Path() {
    }

    /**
     * Create a path containing the concatenation of a series of files.
     * Equivalent to {@code new Path().append(files)}.
     * @param files
     */
    Path(File... files) {
        append(files);
    }

    /**
     * Create a path containing the concatenation of a series of paths.
     * Equivalent to {@code new Path().append(paths)}.
     * @param paths
     */
    Path(Path... paths) {
        append(paths);
    }

    /**
     * Create a path containing the concatenation of a series of paths.
     * Equivalent to {@code new Path().append(paths)}.
     * @param paths
     */
    Path(String... paths) {
        append(paths);
    }

    /**
     * Append a series of files to the path.  Files that do not exist
     * are ignored.
     * @param files files to be added to the path
     * @return the path itself
     */
    Path append(File... files) {
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
    Path append(Path... paths) {
        for (Path p: paths) {
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
    Path append(String... paths) {
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
    File[] split() {
        List<File> v = new ArrayList<File>();
        for (String s: StringArray.splitSeparator(PATHSEP, value)) {
            if (s.length() > 0) {
                v.add(new File(s));
            }
        }
        return v.toArray(new File[v.size()]);
    }

    /**
     * Check if this path contains a subpath.
     * @param path the subpath to be checked
     * @return true if this path contains the subpath
     */
    boolean contains(Path path) {
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

    String value = "";
    private static String PATHSEP = File.pathSeparator;
}
