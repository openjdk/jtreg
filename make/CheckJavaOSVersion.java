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

/**
 * Checks the value of System.getProperty("os.version") against an expected value.
 * For more info, see
 * JDK-8253702: BigSur version number reported as 10.16, should be 11.nn
 * https://bugs.openjdk.java.net/browse/JDK-8253702
 */
public class CheckJavaOSVersion {
    public static void main(String... args) {
        checkJavaOSVersion(args[0]);
    }

    private static void checkJavaOSVersion(String expectVersion) {
        String osVersion = System.getProperty("os.version");
        if (!osVersion.startsWith(expectVersion)) {
            System.err.println("The version of JDK you are using does not report the OS version correctly.");
            System.err.println("    java.home:    " + System.getProperty("java.home"));
            System.err.println("    java.version: " + System.getProperty("java.version"));
            System.err.println("    os.version:   " + osVersion + "  (expected: " + expectVersion + ")");
            System.err.println("Use a more recent update of this version of JDK, or a newer version of JDK.");
            System.exit(1);
        }
    }
}