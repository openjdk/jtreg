/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compare strings checking for embedded series of digits.
 */
public class NaturalComparator implements Comparator<String> {
    public static void main(String... args) {
        Set<String> set = new TreeSet<String>(new NaturalComparator(true));
        set.addAll(Arrays.asList(args));
        System.err.println(set);
    }

    final boolean ignoreCase;

    NaturalComparator(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    public int compare(String s1, String s2) {
        int i1 = 0;
        int i2 = 0;
        while (i1 < s1.length() && i2 < s2.length()) {
            char c1 = s1.charAt(i1);
            char c2 = s2.charAt(i2);
            if (Character.isDigit(c1) && Character.isDigit(c2)) {
                int n1 = Character.digit(c1, 10);
                while (++i1 < s1.length() && Character.isDigit(c1 = s1.charAt(i1)))
                    n1 = n1 * 10 + Character.digit(c1, 10);
                int n2 = Character.digit(c2, 10);
                while (++i2 < s2.length() && Character.isDigit(c2 = s2.charAt(i2)))
                    n2 = n2 * 10 + Character.digit(c2, 10);
                if (n1 < n2) return -1;
                if (n1 > n2) return +1;
            } else {
                if (ignoreCase) {
                    c1 = Character.toLowerCase(c1);
                    c2 = Character.toLowerCase(c2);
                }
                int cmp = ((Character) c1).compareTo((Character) c2);
                if (cmp != 0) return cmp;
                i1++; i2++;
            }
        }
        if (i1 < s1.length()) return +1;
        if (i2 < s2.length()) return -1;
        return 0;
    }

}
