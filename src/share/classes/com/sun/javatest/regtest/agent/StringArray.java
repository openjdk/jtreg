/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

/**
 * Various static methods used to operate on arrays of strings.  This includes
 * an append method and several methods to split strings based on separators,
 * terminators, whitespace, etc.
 * Limited to -target 1.1, so no generics.
 */
public class StringArray {

    /**
     * Splits a string based on location of "=". Always returns an array of size
     * 2.  The first element is the stuff to the left of the "=" sign.  The
     * second element is the stuff to the right.  Assumes no spaces around the
     * "=". They will be part of the appropriate element in the array.  Useful
     * separating variable assignments of the form "foo=bar".
     *
     * @param s    The string to split.
     * @return     An array of length 2, the first element contains characters
     *             to the left of "=", the second element contains characters to
     *             the right of the "=".  Whitespace not stripped.
     */
    public static String[] splitEqual(String s) {
        String[] retVal = new String[2];

        int pos = s.indexOf("=");
        if (pos == -1) {
            retVal[0] = s;
            retVal[1] = null;

        } else {
            retVal[0] = s.substring(0, pos);
            retVal[1] = s.substring(pos+1);
        }

        return retVal;
    } // splitEqual()

    /**
     * Splits a string based on presence of a specified separator. Returns an
     * array of arbitrary length.  The end of each element in the array is
     * indicated by the separator or the end of the string.  If there is a
     * separator immediately before the end of the string, the final element
     * will be empty.  None of the strings will contain the separator. Useful
     * when separating strings such as "foo/bar/bas" using separator '/'.
     *
     * @param sep  The separator.
     * @param s    The string to split.
     * @return     An array of strings. Each string in the array is determined
     *             by the location of the provided sep in the original string,
     *             s.  Whitespace not stripped.
     * @see #splitTerminator
     */
    public static String[] splitSeparator(String sep, String s) {
        List<String> v = new ArrayList<>();
        int tokenStart = 0;
        int tokenEnd;

        while ((tokenEnd = s.indexOf(sep, tokenStart)) != -1) {
            v.add(s.substring(tokenStart, tokenEnd));
            tokenStart = tokenEnd+1;
        }
        v.add(s.substring(tokenStart));

        return v.toArray(new String[0]);
    } // splitSeparator()

    /**
     * Splits a string based on the presence of a specified terminator.  Returns
     * an array of arbitrary length.  The end of each element in the array is
     * indicated by the terminator.  None of the strings will contain the
     * terminator.  Useful when separating string such as "int foo; int bar;"
     * using terminator ';'.
     *
     * @param sep  The separator.
     * @param s    The string to split.
     * @return     An array of strings.  Each string in the array is determined
     *             by the location of the provided sep in the original string,
     *             s.  Whitespace not stripped.
     * @see #splitSeparator
     */
    public static String[] splitTerminator(String sep, String s) {
        List<String> v = new ArrayList<>();
        int tokenStart = 0;
        int tokenEnd;

        while ((tokenEnd = s.indexOf(sep, tokenStart)) != -1) {
            v.add(s.substring(tokenStart, tokenEnd));
            tokenStart = tokenEnd+1;
        }

        return v.toArray(new String[0]);
    } // splitTerminator()

    /**
     * Splits a string according to whitespace in the string.  Returns an array
     * of arbitrary length. Elements are delimited (start or end) by an
     * arbitrary number of Character.isWhitespace().  The whitespace characters
     * are removed.
     *
     * @param s    The string to split.
     * @return     An array of strings.  Each string in the array is determined
     *             by the presence of Character.isWhitespace().
     */
    public static String[] splitWS(String s) {
        if (s == null)
            return new String[0];

        List<String> v = new ArrayList<>();
        int tokenStart = 0;
        int tokenEnd;

        while (true) {
            if (tokenStart == s.length())
                break;
            while ((tokenStart < s.length())
                   && Character.isWhitespace(s.charAt(tokenStart)))
                tokenStart++;
            if (tokenStart == s.length())
                break;
            tokenEnd = tokenStart;
            while ((tokenEnd < s.length())
                   && !Character.isWhitespace(s.charAt(tokenEnd)))
                tokenEnd++;
            v.add(s.substring(tokenStart, tokenEnd));
            tokenStart = tokenEnd;
        }

        return v.toArray(new String[0]);
    } // splitWS()
}
