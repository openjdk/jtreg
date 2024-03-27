/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Scanner;
import java.util.regex.MatchResult;

public final class AgentVerbose {
    public enum Mode { NONE, DEFAULT, SUMMARY, BRIEF, FULL }

    /**
     * Returns a verbose instance.
     *
     * @param s string-representation to parse
     * @return DEFAULT or a verbose instance parsed from the input string
     */
    public static AgentVerbose ofStringRepresentation(String s) {
        if (s == null || s.trim().isEmpty()) return new AgentVerbose(Mode.DEFAULT);
        try (Scanner scanner = new Scanner(s)) {
            scanner.findInLine("Verbose\\[p=(.+),f=(.+),e=(.+),t=(.+),m=(.+)]");
            MatchResult result = scanner.match();
            Mode p = Mode.valueOf(result.group(1));
            Mode f = Mode.valueOf(result.group(2));
            Mode e = Mode.valueOf(result.group(3));
            boolean t = Boolean.parseBoolean(result.group(4));
            boolean m = Boolean.parseBoolean(result.group(5));
            return new AgentVerbose(p, f, e, t, m);
        }
    }

    private AgentVerbose(Mode mode) {
        this(mode, mode, mode, false, false);
    }

    private AgentVerbose(Mode passMode, Mode failMode, Mode errorMode, boolean time, boolean multiRun) {
        this.passMode = passMode;
        this.failMode = failMode;
        this.errorMode = errorMode;
        this.time = time;
        this.multiRun = multiRun;
    }

    @Override
    public String toString() {
        return "Verbose[p=" + passMode + ",f=" + failMode + ",e=" + errorMode + ",t=" + time + ",m=" + multiRun + "]";
    }

    public final Mode passMode;
    public final Mode failMode;
    public final Mode errorMode;
    public final boolean time;
    public final boolean multiRun;
}
