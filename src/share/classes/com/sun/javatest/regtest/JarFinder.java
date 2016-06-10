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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import com.sun.javatest.regtest.agent.SearchPath;

/**
 *
 */
public class JarFinder {

    private List<String> jars;
    private List<String> classes;
    private File libDir;

    JarFinder(String first, String... rest) {
        jars = new ArrayList<String>();
        jars.add(first);
        jars.addAll(Arrays.asList(rest));
    }

    JarFinder classes(String... classes) {
        this.classes = Arrays.asList(classes);
        return this;
    }

    JarFinder classes(Class<?>... classes) {
        this.classes = new ArrayList<String>();
        for (Class<?> c : classes)
            this.classes.add(c.getName());
        return this;
    }

    JarFinder libDir(File libDir) {
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
            File lib = new File(home, "lib");
            for (String jar : jars) {
                result.append(new File(lib, jar));
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
                        }
                        if (uri.getScheme().equals("file"))
                            result.append(new File(uri.getPath()));
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
                result.append(new File(libDir, jar));
            }
        }

        return result;
    }

    File getFile() {
        SearchPath p = getPath();
        if (p != null) {
            List<File> files = p.split();
            if (files.size() == 1)
                return files.get(0);
        }
        return null;
    }

    private static final String PATHSEP = File.pathSeparator;

}
