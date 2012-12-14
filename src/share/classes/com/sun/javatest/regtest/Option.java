/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

public abstract class Option {

    public enum ArgType {
        NONE,       // -opt
        STD,        // -opt:arg
        OLD,        // -opt:arg or -opt arg
        OPT,        // -opt or -opt:arg
        WILDCARD,   // -optarg
        REST,       // -opt rest of args
        FILE        // file
    }

    public Option(ArgType a, String g, String ln, String... names) {
        argType = a;
        group = g;
        lockName = (ln != null && ln.length() == 0 && names.length > 0 ? names[0] : ln);
        this.names = names;
    }

    public String[] getChoices() {
        return null;
    }

    public boolean matches(String name) {
        switch (argType) {
            case FILE:
                return false;
            case WILDCARD:
                for (String n: names) {
                    if (name.startsWith(n))
                        return true;
                }
                break;
            default:
                for (String n: names) {
                    if (name.equals(n))
                        return true;
                }
                break;
        }
        return false;
    }

    public String getValue(String arg) {
        switch (argType) {
            case WILDCARD:
                for (String n: names) {
                    if (arg.startsWith(n))
                        return arg.substring(n.length());
                }
                break;
        }
        return null;
    }

    @Override
    public String toString() {
        return ("Option[" + argType + "," + group +"," + lockName + "," + Arrays.asList(names) + "]");
    }

    public abstract void process(String opt, String arg) throws BadArgs;

    public final ArgType argType;
    public final String group;
    public final String lockName;
    public final String[] names;
}

