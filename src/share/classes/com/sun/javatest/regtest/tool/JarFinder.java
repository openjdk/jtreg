/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.tool;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sun.javatest.regtest.agent.SearchPath;

/**
 *
 */
public class JarFinder {

    private List<String> jars;
    private List<String> classes;
    private Path libDir;

    JarFinder(String first, String... rest) {
        jars = new ArrayList<>();
        jars.add(first);
        jars.addAll(Arrays.asList(rest));
    }

    JarFinder classes(String... classes) {
        this.classes = Arrays.asList(classes);
        return this;
    }

    JarFinder classes(Class<?>... classes) {
        this.classes = new ArrayList<>();
        for (Class<?> c : classes)
            this.classes.add(c.getName());
        return this;
    }

    JarFinder libDir(Path libDir) {
        this.libDir = libDir;
        return this;
    }

    SearchPath getPath() {
        SearchPath result = new SearchPath();
        for (String jar: jars) {
            String v = System.getProperty(jar);
            if (v == null)
                break;
            result.append(v);
        }
        if (!result.isEmpty())
            return result;

        String home = System.getProperty("jtreg.home");
        if (home != null) {
            Path lib = Path.of(home).resolve("lib");
            for (String jar : jars) {
                result.append(lib.resolve(jar));
            }
            if (!result.isEmpty())
                return result;
        }

        if (classes != null)  {
            for (String className: classes) {
                String resName = className.replace(".", "/") + ".class";
                try {
                    URL url = SearchPath.class.getClassLoader().getResource(resName);
                    if (url != null) {
                        // use URI to avoid encoding issues, e.g. Program%20Files
                        URI uri = url.toURI();
                        if (uri.getScheme().equals("jar")) {
                            String ssp = uri.getRawSchemeSpecificPart();
                            int sep = ssp.lastIndexOf("!");
                            uri = new URI(ssp.substring(0, sep));
                        } else if (uri.getScheme().equals("file")) {
                            // not a jar, try the root path of class files
                            String ssp = uri.getRawSchemeSpecificPart();
                            int sep = ssp.indexOf(resName);
                            uri = new URI("file://" + ssp.substring(0, sep));
                        }
                        if (uri.getScheme().equals("file"))
                            result.append(Path.of(uri));
                    }
                } catch (URISyntaxException ignore) {
                    ignore.printStackTrace(System.err);
                }
            }
            if (!result.isEmpty())
                return result;
        }

        if (libDir != null) {
            for (String jar : jars) {
                result.append(libDir.resolve(jar));
            }
        }

        return result;
    }

    Path getFile() {
        SearchPath p = getPath();
        if (p != null) {
            List<Path> files = p.asList();
            if (files.size() == 1)
                return files.get(0);
        }
        return null;
    }
}
