/*
 * Copyright (c) 2006, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Access version info in jtreg.jar META-INF/MANIFEST.MF
 */
public class Version implements Comparable<Version> {
    public static Version getCurrent() {
        if (currentVersion == null)
            currentVersion = new Version();
        return currentVersion;
    }

    private static Version currentVersion;

    private Properties manifest;
    public final String versionString;
    public final String product;
    public final String version;
    public final String milestone;
    public final String build;
    public final String buildJavaVersion;
    public final String buildDate;

    private Version() {
        manifest = getManifestForClass(getClass());
        if (manifest == null)
            manifest = new Properties();


        product = getManifestProperty("jtreg-Name");
        // if new-style jtreg-VersionString is given,
        // derive the other old-style properties from it
        versionString = getManifestProperty("jtreg-VersionString");
        if (versionString == null) {
            version = getManifestProperty("jtreg-Version");
            milestone = getManifestProperty("jtreg-Milestone");
            build = getManifestProperty("jtreg-Build");
        } else {
            Runtime.Version v = Runtime.Version.parse(versionString);
            version = v.version().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("."));
            milestone = v.pre().orElse(null);
            build = v.build().isPresent() ? v.build().get().toString() : null;
        }

        buildJavaVersion = getManifestProperty("jtreg-BuildJavaVersion");
        buildDate = getManifestProperty("jtreg-BuildDate");
    }

    private String getManifestProperty(String key) {
        // Allow overriding values for testing new versions
        return System.getProperty("OVERRIDE-" + key, manifest.getProperty(key));
    }

    public Version(String versionAndBuild) {
        if (versionAndBuild != null) {
            // pattern for:
            // old-style     4.2 b12
            // new-style     5.2+1
            Pattern versionPattern = Pattern.compile("(?<version>[0-9.]+)(\\s+b|\\+)(?<build>[0-9]+)");
            Matcher matcher = versionPattern.matcher(versionAndBuild);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(versionAndBuild);
            }
            this.versionString = null;
            this.version = matcher.group("version");
            this.build = matcher.group("build");
        } else {
            this.versionString = null;
            this.version = null;
            this.build = null;
        }

        this.product = null;
        this.milestone = null;
        this.buildJavaVersion = null;
        this.buildDate = null;
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
                        try (InputStream in = url.openStream()) {
                            p.load(in);
                        }
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

    private static int[] parseDottedInts(String s) {
        String[] elems = s.split("\\.");
        int[] result = new int[elems.length];
        for (int i = 0; i < elems.length; i++) {
            result[i] = Integer.parseInt(elems[i]);
        }
        return result;
    }

    /**
     * Compares this object with the specified object for order. Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.
     *
     * @param other the version against which to compare
     */
    @Override
    public int compareTo(Version other) {
        if (this.version == null || other.version == null) {
            return 0;
        }

        int[] thisDots = parseDottedInts(this.version);
        int[] otherDots = parseDottedInts(other.version);

        for (int i = 0; i < Math.max(thisDots.length, otherDots.length); i++) {
            int thisDot = i < thisDots.length ? thisDots[i] : 0;
            int otherDot = i < otherDots.length ? otherDots[i] : 0;

            if (thisDot > otherDot) {
                return 1;
            }
            if (thisDot < otherDot) {
                return -1;
            }
            // if equal, check the next order
        }

        // all parts of the version are, equal - check the build
        int thisBuild = this.getBuild();
        int otherBuild = other.getBuild();
        if (thisBuild == 0 || otherBuild == 0) {
            return 0;
        }
        return Integer.compare(thisBuild, otherBuild);
    }

    private int getBuild() {
        if (build == null) {
            return 0;
        }

        if (build.matches("b?[0-9]+")) {
            String b = build.startsWith("b") ? build.substring(1) : build;
            return Integer.parseInt(b);
        } else {
            // ignore malformed or invalid build numbers
            return 0;
        }
    }

    String getVersionBuildString() {
        return versionString != null
                ? versionString
                : String.format("%s+%s", version, build.replaceAll("^0+", ""));
    }

    @Override
    public String toString() {
        List<String> l = new ArrayList<>();
        if (versionString != null) {
            l.add("versionString=" + versionString);
        }
        if (version != null) {
            l.add("version=" + version);
        }
        if (milestone != null) {
            l.add("milestone=" + milestone);
        }
        if (build != null) {
            l.add("build=" + build);
        }
        return "Version[" + String.join(",", l) + "]";
    }
}
