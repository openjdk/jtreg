/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.tool;

import com.sun.javatest.Harness;
import com.sun.javatest.regtest.BadArgs;
import com.sun.javatest.regtest.Main;
import com.sun.javatest.util.I18NResourceBundle;

import java.io.PrintWriter;
import java.util.spi.ToolProvider;

public class JtregToolProvider implements ToolProvider {
    @Override
    public String name() {
        return "jtreg";
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String... args) {
        var main = new Main(out, err);
        try {
            return main.run(args);
        } catch (BadArgs e) {
            err.println(i18n.getString("main.badArgs", e.getMessage()));
            return Main.EXIT_BAD_ARGS;
        } catch (Main.Fault | Harness.Fault e) {
            err.println(i18n.getString("main.error", e.getMessage()));
            return Main.EXIT_FAULT;
        } catch (InterruptedException e) {
            err.println(i18n.getString("main.interrupted"));
            return Main.EXIT_EXCEPTION;
        } catch (RuntimeException | Error e) {
            err.println(i18n.getString("main.unexpectedException", e.toString()));
            e.printStackTrace(System.err);
            return Main.EXIT_EXCEPTION;
        }
    }

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(Tool.class);
}
