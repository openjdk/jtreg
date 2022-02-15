/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.spi.ToolProvider;

public class ToolProviderTest{
    public static void main(String... args) {
        var jtreg = ToolProvider.findFirst("jtreg")
            .orElseThrow(() -> new AssertionError("`jtreg` not found by name"));

        var out = new StringWriter();
        var err = new StringWriter();
        int code = jtreg.run(new PrintWriter(out), new PrintWriter(err), "-help");

        if (code != 0)
            throw new AssertionError("non-zero exit code: " + code);

        if (!out.toString().contains("Usage:"))
            throw new AssertionError("No help text printed?\nout=" + out + "\nerr=" + err);
    }
}
