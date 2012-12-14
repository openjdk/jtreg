/*
 * Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
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
    public static final Verbose TIME     = new Verbose(Mode.SUMMARY, true, false);

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
        boolean multiRunOpt = opts.contains("multirun");

        if (defaultOpt) {
            if (summaryOpt || allOpt
                    || passOpt || failOpt || errorOpt
                    || nopassOpt)
                throw new IllegalArgumentException(s);
            return new Verbose(Mode.DEFAULT, timeOpt, multiRunOpt);
        }

        if (summaryOpt || allOpt || passOpt || failOpt || errorOpt || nopassOpt) {
            if (passOpt && nopassOpt)
                throw new IllegalArgumentException(s);
            Mode shortMode = summaryOpt ? Mode.SUMMARY : Mode.BRIEF;
            return new Verbose(
                    nopassOpt ? Mode.NONE : (allOpt || passOpt) ? Mode.FULL : shortMode,
                    (allOpt || failOpt) ? Mode.FULL : shortMode,
                    (allOpt || errorOpt) ? Mode.FULL : shortMode,
                    timeOpt,
                    multiRunOpt);
        }

        if (timeOpt)
            return new Verbose(Mode.SUMMARY, true, multiRunOpt);
        else
            return new Verbose(Mode.DEFAULT, false, multiRunOpt);
    }

    private static Mode check(Mode currentMode, Mode newMode) {
        if (newMode == null)
            throw new NullPointerException();
        if (currentMode == null || currentMode == newMode)
            return newMode;

        return newMode;
    }

    Verbose(Mode m) {
        this(m, false, false);
    }

    Verbose(Mode m, boolean time, boolean multiRun) {
        this(m, m, m, time, multiRun);
    }

    Verbose(Mode p, Mode f, Mode e) {
        this(p, f, e, false, false);
    }

    Verbose(Mode p, Mode f, Mode e, boolean t, boolean m) {
        passMode = p;
        failMode = f;
        errorMode = e;
        time = t;
        multiRun = m;
    }

    boolean isDefault() {
        return (passMode == Mode.DEFAULT)
                && (failMode == Mode.DEFAULT)
                && (errorMode == Mode.DEFAULT);
    }

    @Override
    public String toString() {
        return "Verbose[p=" + passMode + ",f=" + failMode + ",e=" + errorMode + ",t=" + time + ",m=" + multiRun + "]";
    }

    final Mode passMode;
    final Mode failMode;
    final Mode errorMode;
    final boolean time;
    final boolean multiRun;
}
