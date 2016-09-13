/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Utilities for handling keyword expressions.
 */
public class RegressionKeywords {
    static class Fault extends Exception {
        private static final long serialVersionUID = 1L;

        Fault(String msg) {
            super(msg);
        }
    }

    static void validateKey(String key) throws Fault {
        if (key.length() == 0)
            throw new Fault("empty");

        char c = key.charAt(0);
        if (!(Character.isUnicodeIdentifierStart(c)
                || (allowNumericKeywords && Character.isDigit(c)))) {
            throw new Fault("invalid character: " + c);
        }
        for (int i = 1; i < key.length(); i++) {
            c = key.charAt(i);
            if (!(Character.isUnicodeIdentifierPart(c) || c == '-')) {
                throw new Fault("invalid character: " + c);
            }
        }
    }

    public static final boolean allowNumericKeywords = true;
}
