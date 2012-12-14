/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This class defines any error in parsing the provided test description. A
 * parsing error may occur either during initial examination of the tags in
 * the finder or during more extensive verification of the run action.
 *
 * @author Iris A Garcia
 */
public class ParseException extends TestRunException
{
    static final long serialVersionUID = 5598548899306920122L;
    public ParseException(String msg) {
        super(PARSE_EXCEPTION + msg);
    } // ParseException()

    public ParseException(Throwable t) {
        super(PARSE_EXCEPTION + t.getMessage());
        initCause(t);
    } // ParseExeptionException()

    //----------misc statics----------------------------------------------------

    private static final String
        PARSE_EXCEPTION       = "Parse Exception: ";
}
