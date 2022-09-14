/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * TODO: XMLReporter
 * TODO: filter options
 * TODO: comparator option
 * TODO: css option
 **/

package com.sun.javatest.diff;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.sun.javatest.regtest.BadArgs;
import com.sun.javatest.regtest.tool.Option;
import com.sun.javatest.regtest.tool.OptionDecoder;
import com.sun.javatest.util.I18NResourceBundle;

import static com.sun.javatest.regtest.tool.Option.ArgType.*;


/**
 * Main entry point for jtdiff.
 */
public class Main {
    //---------- command line option decoding ----------------------------------

    private static final String COMPARE = "compare";
    private static final String OUTPUT = "output";
    private static final String DOC = "doc";
    private static final String FILES = "files";

    List<Option> options = List.of(
        new Option(NONE, COMPARE, "r", "-r", "-reason") {
            @Override
            public void process(String opt, String arg) {
                includeReason = true;
            }
        },
        new Option(NONE, COMPARE, "s", "-s", "-super") {
            @Override
            public void process(String opt, String arg) {
                superMode = true;
            }
        },
        new Option(OLD, OUTPUT, "o", "-o", "-outFile") {
            @Override
            public void process(String opt, String arg) {
                outFile = new File(arg);
            }
        },
        new Option(STD, OUTPUT, "format", "-format") {
            @Override
            public void process(String opt, String arg) {
                format = arg;
            }
        },
        new Option(OLD, OUTPUT, "title", "-title") {
            @Override
            public void process(String opt, String arg) {
                title = arg;
            }
        },
        new Option(REST, DOC, "help", "-h", "-help", "--help", "-usage") {
            @Override
            public void process(String opt, String arg) {
                if (help == null)
                    help = new Help(options);
                help.setCommandLineHelpQuery(arg);
            }
        },
        new Option(NONE, DOC, "help", "-version") {
            @Override
            public void process(String opt, String arg) {
                if (help == null)
                    help = new Help(options);
                help.setVersionFlag(true);
            }
        },
        new Option(FILE, FILES, null) {
            @Override
            public void process(String opt, String arg) {
                File f = new File(arg);
                fileArgs.add(f);
            }
        }
    );

    //---------- Command line invocation support -------------------------------

    /**
     * Standard entry point. Only returns if GUI mode is initiated; otherwise, it calls System.exit
     * with an appropriate exit code.
     * @param args An array of args, such as might be supplied on the command line.
     */
    public static void main(String[] args) {
        PrintWriter out = new PrintWriter(System.out, true);
        PrintWriter err = new PrintWriter(System.err, true);
        Main m = new Main(out, err);
        try {
            boolean ok;
            try {
                ok = m.run(args);
                if (!ok && (m.outFile != null)) {
                    // no need for an additional message if outFile == null
                    err.println(i18n.getString("main.diffsFound"));
                }
            } finally {
                out.flush();
            }

            if (!ok) {
                // take care not to exit if GUI might be around,
                // and take care to ensure JavaTestSecurityManager will
                // permit the exit
                exit(1);
            }
        } catch (Fault e) {
            err.println(i18n.getString("main.error", e.getMessage()));
            exit(2);
        } catch (BadArgs e) {
            err.println(i18n.getString("main.badArgs", e.getMessage()));
            new Help(m.options).showCommandLineHelp(out);
            exit(2);
        } catch (InterruptedException e) {
            err.println(i18n.getString("main.interrupted"));
            exit(2);
        } catch (Exception e) {
            err.println(i18n.getString("main.unexpectedException"));
            e.printStackTrace(System.err);
            exit(3);
        }
    } // main()

    public Main() {
        this(new PrintWriter(System.out, true), new PrintWriter(System.err, true));
    }

    public Main(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
    }

    /**
     * Decode command line args and perform the requested operations.
     * @param args An array of args, such as might be supplied on the command line.
     * @throws BadArgs if problems are found with any of the supplied args
     * @throws Fault if exception problems are found while trying to compare the results
     * @throws InterruptedException if the tool is interrupted while comparing the results
     */
    public final boolean run(String[] args) throws BadArgs, Fault, InterruptedException {
        new OptionDecoder(options).decodeArgs(args);

        if (superMode) {
            if (fileArgs.size() != 1 || !fileArgs.get(0).isDirectory())
                throw new Fault(i18n, "main.bad.super.dir");
            if (format != null)
                throw new Fault(i18n, "main.bad.super.format");
            if (outFile == null)
                throw new Fault(i18n, "main.no.output.dir");
        }

        return run();
    }

    private boolean run() throws Fault, InterruptedException {
        if (fileArgs.isEmpty() && !superMode && help == null) {
            help = new Help(options);
            help.setCommandLineHelpQuery(null);
        }

        if (help != null) {
            help.show(out);
            return true;
        }

        Diff d;
        if (superMode)
            d = new SuperDiff(fileArgs.get(0));
        else
            d = new StandardDiff(fileArgs);

        d.out = out;
        d.includeReason = includeReason;
        d.format = format;
        d.title = title;

        return d.report(outFile);
    }

    private static void exit(int exitCode) {
        System.exit(exitCode);
    }

    private final PrintWriter out;
    private final PrintWriter err;

    private boolean includeReason;
    private String format;
    private String title;
    private File outFile;
    private final List<File> fileArgs = new ArrayList<>();
    private boolean superMode;
    private Help help;

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(Main.class);
}
