/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Reads the file given on the command line as a problem-list template,
 * substitutes values, and writes the result to System.out.
 */
class ProblemList {
    public static void main(String... args) throws IOException {
        new ProblemList().run(args);
    }

    void run(String... args) throws IOException {
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        String osVersion = System.getProperty("os.version");

        List<String> lines = Files.readAllLines(Path.of(args[0]));
        lines.stream()
                .map(l -> l.startsWith("#") ? l
                        : l.replace("OSNAME", standardize(osName))
                            .replace("ARCH", standardize(osArch))
                            .replace("REV", standardize(osVersion)))
                .forEach(System.out::println);
    }

    static String standardize(String s) {
        return s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}