/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.plugin.jtreg.util;

/**
 * Misc utility methods for formatting test results messages back onto the IDE console.
 */
public class MapSerializerUtil {

    static final char ESC_CHAR = '|';

    static char escape(final char c) {
        switch (c) {
            case '\n':
                return 'n';
            case '\r':
                return 'r';
            case '\u0085':
                return 'x'; // next-line character
            case '\u2028':
                return 'l'; // line-separator character
            case '\u2029':
                return 'p'; // paragraph-separator character
            case '|':
                return '|';
            case '\'':
                return '\'';
            case '[':
                return '[';
            case ']':
                return ']';
            default:
                return 0;
        }
    }

    /**
     * Escapes characters specified by provider with '\' and specified character.
     *
     * @param str initial string
     * @return escaped string.
     */
    public static String escapeStr(final String str) {
        if (str == null) return null;
        int finalCount = calcFinalEscapedStringCount(str);

        if (str.length() == finalCount) return str;

        char[] resultChars = new char[finalCount];
        int resultPos = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            final char escaped = escape(c);
            if (escaped != 0) {
                resultChars[resultPos++] = ESC_CHAR;
                resultChars[resultPos++] = escaped;
            } else {
                resultChars[resultPos++] = c;
            }
        }

        if (resultPos != finalCount) {
            throw new RuntimeException("Incorrect escaping for '" + str + "'");
        }
        return new String(resultChars);
    }

    private static int calcFinalEscapedStringCount(final String name) {
        int result = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (escape(c) != 0) {
                result += 2;
            } else {
                result += 1;
            }
        }

        return result;
    }
}
