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

import java.io.File;
import java.util.List;

public class AntOptionDecoder extends OptionDecoder {
    public AntOptionDecoder(List<Option> options) {
        super(options);
    }

    public void process(String name, boolean value) throws BadArgs {
        if (value)
            process(name, name);
    }

    public void process(String name, File value) throws BadArgs {
        if (value != null)
            process(name, value.getPath());
    }

    public void process(String name, String value) throws BadArgs {
        if (value == null)
            return;

        Option o = getOption(name);
        if (o == null)
            throw new AssertionError("can't find " + name);

        checkConflicts(o, name);

        // synthesize a command line option in case jtreg gets redispatched
        // in child
        String opt;
        switch(o.argType) {
            case FILE:
            case NONE:
            case REST:

                opt = "-" + name;

                break;
            case OLD:
            case STD:

                opt = "-" + name + ":" + value;

                break;
            case OPT:

                opt = "-" + name + (value == null ? "" : ":" + value);

                break;
            case WILDCARD:

                opt = "-" + name + value;

                break;
            default:

                throw new Error();
        }

        if (debugOptions)
            System.err.println("AntOptionDecoder.process: " + name + " " + value);

        o.process(opt, value);
    }

}
