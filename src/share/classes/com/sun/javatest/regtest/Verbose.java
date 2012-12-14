/*
 * Copyright 2006-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.javatest.regtest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Values for the -verbose option
 */
public class Verbose {
    public static enum Mode { NONE, DEFAULT, SUMMARY, BRIEF, FULL };

    public static final Verbose DEFAULT  = new Verbose(Mode.DEFAULT);
    public static final Verbose SUMMARY  = new Verbose(Mode.SUMMARY);
    public static final Verbose ALL      = new Verbose(Mode.FULL);
    public static final Verbose PASS     = new Verbose(Mode.FULL, Mode.BRIEF, Mode.BRIEF);
    public static final Verbose FAIL     = new Verbose(Mode.BRIEF, Mode.FULL, Mode.BRIEF);
    public static final Verbose ERROR    = new Verbose(Mode.BRIEF, Mode.BRIEF, Mode.FULL);
    public static final Verbose TIME     = new Verbose(Mode.SUMMARY, true);

    static String[] values() {
        return new String[] {
            "default",
            "summary",
            "all",
            "pass",
            "fail",
            "error",
            "nopass",
            "time"
        };
    }

    static Verbose decode(String s) {
        // FIXME, use regexp to splt the string?
        // FIXME, check all words are valid?
        Set<String> opts = new HashSet<String>(Arrays.asList(StringArray.splitSeparator(",", s)));
        boolean defaultOpt = opts.contains("default");
        boolean summaryOpt = opts.contains("summary");
        boolean allOpt = opts.contains("all");
        boolean passOpt = opts.contains("pass");
        boolean failOpt = opts.contains("fail");
        boolean errorOpt = opts.contains("error");
        boolean nopassOpt = opts.contains("nopass");
        boolean timeOpt = opts.contains("time");

        if (defaultOpt) {
            if (summaryOpt || allOpt
                    || passOpt || failOpt || errorOpt
                    || nopassOpt)
                throw new IllegalArgumentException(s);
            return new Verbose(Mode.DEFAULT, timeOpt);
        }

        if (summaryOpt || allOpt || passOpt || failOpt || errorOpt || nopassOpt) {
            if (passOpt && nopassOpt)
                throw new IllegalArgumentException(s);
            Mode shortMode = summaryOpt ? Mode.SUMMARY : Mode.BRIEF;
            return new Verbose(
                    nopassOpt ? Mode.NONE : (allOpt || passOpt) ? Mode.FULL : shortMode,
                    (allOpt || failOpt) ? Mode.FULL : shortMode,
                    (allOpt || errorOpt) ? Mode.FULL : shortMode,
                    timeOpt);
        }

        return timeOpt ? Verbose.TIME : Verbose.DEFAULT;
    }

    private static Mode check(Mode currentMode, Mode newMode) {
        if (newMode == null)
            throw new NullPointerException();
        if (currentMode == null || currentMode == newMode)
            return newMode;

        return newMode;
    }

    Verbose(Mode m) {
        this(m, false);
    }

    Verbose(Mode m, boolean time) {
        this(m, m, m, time);
    }

    Verbose(Mode p, Mode f, Mode e) {
        this(p, f, e, false);
    }

    Verbose(Mode p, Mode f, Mode e, boolean t) {
        passMode = p;
        failMode = f;
        errorMode = e;
        time = t;
    }

    public String toString() {
        return "Verbose[p=" + passMode + ",f=" + failMode + ",e=" + errorMode + ",t=" + time + "]";
    }

    final Mode passMode;
    final Mode failMode;
    final Mode errorMode;
    final boolean time;
}
