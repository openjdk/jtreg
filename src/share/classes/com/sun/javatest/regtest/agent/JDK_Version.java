/*
 * Copyright (c) 2007, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.agent;

import java.util.HashMap;
import java.util.Map;


public class JDK_Version implements Comparable<JDK_Version> {
    private static final Map<String, JDK_Version> values
            = new HashMap<>();

    public static JDK_Version V1_1 = JDK_Version.forName("1.1");
    public static JDK_Version V1_5 = JDK_Version.forName("1.5");
    public static JDK_Version V1_6 = JDK_Version.forName("1.6");
    public static JDK_Version V9 = JDK_Version.forName("9");
    public static JDK_Version V10 = JDK_Version.forName("10");
    public static JDK_Version V25 = JDK_Version.forName("25");

    public static JDK_Version forName(String name) {
        if (name == null)
            return null;

        synchronized (values) {
            JDK_Version v = values.get(name);
            if (v == null) {
                try {
                    int major;
                    if (name.startsWith("1.")) {
                        major = Integer.parseInt(name.substring(2));
                        if (major > 10) {  // align with javac: allow 1.9, 1.10
                            return null;
                        }
                    } else {
                        major = Integer.parseInt(name);
                        if (major < 5) {
                            return null;
                        }
                    }
                    values.put(name, v = new JDK_Version(major));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return v;
        }
    }

    public static JDK_Version forThisJVM() {
        return forName(System.getProperty("java.specification.version"));
    }

    private JDK_Version(int major) {
        this.major = major;
    }

    public final int major;

    public String name() {
        return (major < 9 ? "1." : "") + major;
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof JDK_Version
                && (major == ((JDK_Version) other).major));
    }

    @Override
    public int hashCode() {
        return major;
    }

    @Override
    public int compareTo(JDK_Version other) {
        return major < other.major ? -1 : major == other.major ? 0 : 1;
    }

    @Override
    public String toString() {
        return "JDK " + (major < 9 ? "1." : "") + major;
    }
}
