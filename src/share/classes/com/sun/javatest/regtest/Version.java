/*
 * Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Access version info in jtreg.jar META-INF/MANIFEST.MF
 */
public class Version {
    public static Version instance() {
        if (instance == null)
            instance = new Version();
        return instance;
    }

    private static Version instance;

    private Properties manifest;
    final String product;
    final String version;
    final String milestone;
    final String build;
    final String buildJavaVersion;
    final String buildDate;

    private Version() {
        manifest = getManifestForClass(getClass());
        if (manifest == null)
            manifest = new Properties();

        product = manifest.getProperty("jtreg-Name");
        version = manifest.getProperty("jtreg-Version");
        milestone = manifest.getProperty("jtreg-Milestone");
        build = manifest.getProperty("jtreg-Build");
        buildJavaVersion = manifest.getProperty("jtreg-BuildJavaVersion");
        buildDate = manifest.getProperty("jtreg-BuildDate");
    }

    String getProperty(String name, String _default) {
        return manifest.getProperty(name, _default);
    }

    private Properties getManifestForClass(Class<?> c) {
        URL classPathEntry = getClassPathEntryForClass(c);
        if (classPathEntry == null)
            return null;

        try {
            Enumeration<URL> e = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (e.hasMoreElements()) {
                URL url = e.nextElement();
                if (url.getProtocol().equals("jar")) {
                    String path = url.getPath();
                    int sep = path.lastIndexOf("!");
                    URL u = new URL(path.substring(0, sep));
                    if (u.equals(classPathEntry )) {
                        Properties p = new Properties();
                        InputStream in = url.openStream();
                        p.load(in);
                        in.close();
                        return p;
                    }
                }
            }
        } catch (IOException ignore) {
        }
        return null;
    }

    private URL getClassPathEntryForClass(Class<?> c) {
        try {
            URL url = c.getResource("/" + c.getName().replace('.', '/') + ".class");
            if (url.getProtocol().equals("jar")) {
                String path = url.getPath();
                int sep = path.lastIndexOf("!");
                return new URL(path.substring(0, sep));
            }
        } catch (MalformedURLException ignore) {
        }
        return null;
    }
}
