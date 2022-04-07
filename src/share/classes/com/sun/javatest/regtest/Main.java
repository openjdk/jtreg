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

package com.sun.javatest.regtest;

import com.sun.javatest.Harness;
import com.sun.javatest.regtest.tool.Tool;
import com.sun.javatest.util.I18NResourceBundle;

import java.io.PrintWriter;

/**
 * Main entry point to be used to access the Regression Test Harness for JDK: jtreg.
 */
public class Main {

    /**
     * Standard entry point.
     * Only returns if GUI mode is initiated; otherwise, it calls System.exit with an
     * appropriate exit code.
     * @param args An array of options and arguments, such as might be supplied on the command line.
     */
    public static void main(String[] args) {
        Tool.main(args);
    } // main()

    /**
     * Exception to report a problem while running the test harness.
     */
    public static class Fault extends Exception {
        static final long serialVersionUID = -6780999176737139046L;
        public Fault(I18NResourceBundle i18n, String s, Object... args) {
            super(i18n.getString(s, args));
        }
    }

    /** Execution OK. */
    public static final int EXIT_OK = 0;
    /** No tests found. */
    public static final int EXIT_NO_TESTS = 1;
    /** One or more tests failed. */
    public static final int EXIT_TEST_FAILED = 2;
    /** One or more tests had an error. */
    public static final int EXIT_TEST_ERROR = 3;
    /** Bad user args. */
    public static final int EXIT_BAD_ARGS = 4;
    /** Other fault occurred. */
    public static final int EXIT_FAULT = 5;
    /** Unexpected exception occurred. */
    public static final int EXIT_EXCEPTION = 6;


    public Main() {
        this(new PrintWriter(System.out, true), new PrintWriter(System.err, true));
    }

    public Main(PrintWriter out, PrintWriter err) {
        tool = new Tool(out, err);
    }

    /**
     * Decode command line args and perform the requested operations.
     * @param args An array of args, such as might be supplied on the command line.
     * @throws BadArgs if problems are found with any of the supplied args
     * @throws Main.Fault if a serious error occurred during execution
     * @throws Harness.Fault if exception problems are found while trying to run the tests
     * @throws InterruptedException if the harness is interrupted while running the tests
     * @return an exit code: 0 for success, greater than 0 for an error
     */
    public int run(String[] args) throws
            BadArgs, Fault, Harness.Fault, InterruptedException {
        return tool.run(args);
    }

    private Tool tool;
}
