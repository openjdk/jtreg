/*
 * Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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


public enum JDK_Version {
    V1_1("1.1"),
    V1_2("1.2"),
    V1_3("1.3"),
    V1_4("1.4"),
    V1_5("1.5"),
    V1_6("1.6"),
    V1_7("1.7"),
    V1_8("1.8"),
    V1_9("1.9"),
    // proactive ...
    V1_10("1.10");

    JDK_Version(String name) {
        this.name = name;
    }

    public final String name;

    public static JDK_Version forName(String name) {
        for (JDK_Version v: values()) {
            if (v.name.equals(name))
                return v;
        }
        return null;
    }
    public static JDK_Version forThisJVM() {
        return forName(System.getProperty("java.specification.version"));
    }
}
