/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.util;

import java.util.Collection;
import java.util.List;

import com.sun.javatest.regtest.agent.StringArray;

/**
 * String utilities.
 * (Not restricted to -target 1.1)
 */
public class StringUtils extends StringArray {

    /**
     * Returns the part of a string before the first instance of a separator character,
     * or null if there is no such character.
     * @param s the string
     * @param sep the separator character
     * @return  the part of a string before the first instance of the separator character
     */
    public static String beforePart(String s, char sep) {
        int i = s.indexOf(sep);
        return (i == -1) ? null : s.substring(0, i);
    }

    /**
     * Returns the part of a string after the first instance of a separator character,
     * or the string itself if there is no such character.
     * @param s the string
     * @param sep the separator character
     * @return  the part of a string after the first instance of the separator character
     */
    public static String afterPart(String s, char sep) {
        int i = s.indexOf(sep);
        return (i == -1) ? s : s.substring(i + 1);
    }

    /**
     * Splits a string around instances of the given character separator.
     * @param s the string
     * @param sep the separator
     * @return the list of strings computed by splitting this string around
     *  matches of the given separator character
     */
    public static List<String> split(String s, char sep) {
        return List.of(s.split("\\Q" + sep + "\\E"));
    }

    public static String join(Collection<?> list) {
        return join(list, " ");
    }

    public static String join(Collection<?> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (Object s: list) {
            if (sb.length() > 0)
                sb.append(sep);
            sb.append(s);
        }
        return sb.toString();
    }
}
