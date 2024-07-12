/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Provide access to properties defined in LIBRARY.properties files.
 */
public final class LibraryProperties {
    private final boolean enablePreview;

    public LibraryProperties(boolean enablePreview) {
        this.enablePreview = enablePreview;
    }

    public static LibraryProperties of(Locations.LibLocn libLocn) throws UncheckedIOException {
        // default values being used when no LIBRARY.properties is present
        boolean enablePreview = false;

        // read LIBRARY.properties file and initialize fields accordingly
        Path root = libLocn.absSrcDir;
        if (root != null && Files.isDirectory(root)) {
            Path file = root.resolve("LIBRARY.properties");
            if (Files.exists(file)) {
                var properties = new Properties();
                try (var stream = Files.newInputStream(file)) {
                    properties.load(stream);
                } catch (IOException exception) {
                    throw new UncheckedIOException("Reading from file failed: " + file.toUri(), exception);
                }
                enablePreview = initEnablePreview(properties);
            }
        }
        return new LibraryProperties(enablePreview);
    }

    private static boolean initEnablePreview(Properties properties) {
        return Boolean.parseBoolean(properties.getProperty("enablePreview", "false"));
    }

    public boolean isEnablePreview() {
        return enablePreview;
    }
}
