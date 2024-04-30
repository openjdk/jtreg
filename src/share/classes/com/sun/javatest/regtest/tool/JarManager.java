/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javatest.regtest.agent.SearchPath;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * A manager for collections of jar files defined in jar.properties.
 */
public class JarManager {

    private final Properties props = new Properties();
    private final Path libDir;

    /**
     * Returns the location of the code source for a class.
     * The location may be a {@link Files#isRegularFile(Path,LinkOption[]) regular file}
     * (for a jar file)
     * or a {@link Files#isDirectory(Path,LinkOption[]) directory}
     * (for a "classes" directory).
     *
     * @param clazz the class
     * @return a path for the location
     */
    static Path forClass(Class<?> clazz) {
        URL u = clazz.getProtectionDomain().getCodeSource().getLocation();
        try {
            return Path.of(u.toURI());
        } catch (URISyntaxException e) {
            throw new Error(e);
        }
    }

    /**
     * Creates a manager for the jar files in a given directory.
     *
     * @param libDir the directory
     */
    JarManager(Path libDir) {

        InputStream in = getClass().getResourceAsStream("jars.properties");
        if (in != null) {
            try (InputStreamReader r = new InputStreamReader(in)) {
                props.load(r);
            } catch (IOException e) {
                throw new Error("problem reading jars.properties");
            }
        }

        this.libDir = libDir;
    }

    /**
     * Returns a search path for the jar files for a named component.
     *
     * By default, the jar files are found using entries in a
     * resource properties file, {@code jars.properties}, but can
     * be overridden if needed by setting a system property identifying
     * the jar files.  The name of the system property is
     * the component name followed by {@code ".path"}.
     *
     * @param name the name of the component
     * @return the search path for the component
     */
    SearchPath getPath(String name) {
        SearchPath result = new SearchPath();
        String p = System.getProperty(name + ".path");
        if (p != null) {
            result.append(p);
            if (!result.isEmpty()) {
                return result;
            }
        }

        String jars = props.getProperty(name);
        if (jars != null) {
            for (String jar : jars.split("\\s+")) {
                result.append(libDir.resolve(jar));
            }
        }

        return result;
    }

    /**
     * Returns the path for the named jar, or {@code null} if the jar cannot be found.
     * @param name the name
     * @return the path
     */
    Path getFile(String name) {
        List<Path> files = getPath(name).asList();
        return files.size() == 1 ? files.get(0) : null;
    }
}
