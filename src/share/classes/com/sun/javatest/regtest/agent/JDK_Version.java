/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public enum JDK_Version {
    V1_1("1.1"),
    V1_2("1.2"),
    V1_3("1.3"),
    V1_4("1.4"),
    V1_5("1.5"),
    V1_6("1.6"),
    V1_7("1.7"),
    V1_8("1.8"),
    V9("9"),
    // proactive ...
    V10("10"),
    V11("11"),
    V12("12");

    JDK_Version(String name) {
        this.name = name;
        this.major = name.startsWith("1.") ? name.substring(2) : name;
    }

    public final String name;
    public final String major;

    public static JDK_Version forName(String name) {
        if (name == null)
            return null;

        // for now, always allow/ignore optional leading 1.
        Pattern p = Pattern.compile("(1\\.)?([1-9][0-9]*).*");
        Matcher m = p.matcher(name);
        if (m.matches()) {
            String major = m.group(2);
            for (JDK_Version v : values()) {
                if (v.major.equals(major)) {
                    return v;
                }
            }
        }
        return null;
    }
    public static JDK_Version forThisJVM() {
        return forName(System.getProperty("java.specification.version"));
    }
}
