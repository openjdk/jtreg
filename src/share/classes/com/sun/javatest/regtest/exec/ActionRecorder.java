/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.exec;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Class to record the commands executed in the course of an Action.
 */
public class ActionRecorder {

    ActionRecorder(Action action) {
        this.action = action;
    }

    public void exec(List<String> cmd, Map<String, String> envArgs) {
        initPW();
        printWorkDir();
        // Env variables
        for (Map.Entry<String, String> var : envArgs.entrySet()) {
            pw.println(var.getKey() + "=" + escape(var.getValue()) + CONT);
        }
        final int CMD = 1, ARG = 2;
        int state = CMD;
        int size = cmd.size();
        String indent = "    ";
        String sep = indent;
        for (int i = 0; i < size; i++) {
            String word = cmd.get(i);
            switch (state) {
                case CMD:
                    pw.print(indent);
                    pw.print(escape(word));
                    if (i + 1 < size)
                        pw.println(CONT);
                    state = ARG;
                    indent += "    ";
                    sep = indent;
                    break;
                case ARG:
                    if (word.startsWith("-") && sep.equals(" ")) {
                        pw.println(CONT);
                        sep = indent;
                    }
                    pw.print(sep);
                    pw.print(escape(word));
                    sep = " ";
            }
        }
        pw.println();
    }

    public void exec(String cmd) {
        initPW();
        printWorkDir();
        for (String line: cmd.split("[\r\n]+"))
            pw.println(line);
    }

    public void java(Map<String, String> envArgs, String javaCmd, Map<String, String> javaProps, List<String> javaOpts, String className, List<String> classArgs) {
        initPW();
        printWorkDir();
        // Env variables
        for (Map.Entry<String, String> var : envArgs.entrySet()) {
            pw.println(var.getKey() + "=" + escape(var.getValue()) + CONT);
        }
        // Java executable
        String indent = "    ";
        pw.println(indent + escape(javaCmd) + CONT);
        // System properties
        indent += "    ";
        for (Map.Entry<String, String> e: javaProps.entrySet()) {
            pw.println(indent + "-D" + escape(e.getKey()) + "=" + escape(e.getValue()) + CONT);
        }
        // additional JVM options
        if (javaOpts.size() > 0) {
            String sep = indent;
            for (String o : javaOpts) {
                if (o.startsWith("-") && sep.equals(" ")) {
                    pw.println(CONT);
                    sep = indent;
                }
                pw.print(sep);
                pw.print(escape(o));
                sep = " ";
            }
            pw.println(CONT);
        }
        // class name
        pw.print(indent + escape(className));
        // class args
        for (String a: classArgs) {
            pw.print(" ");
            pw.print(escape(a));
        }
        pw.println();
    }

    void javac(Map<String, String> envArgs, String javacCmd, List<String> javacVMOpts, Map<String, String> javacProps, List<String> javacArgs) {
        initPW();
        printWorkDir();
        // Env variables
        for (Map.Entry<String, String> var : envArgs.entrySet()) {
            pw.println(var.getKey() + "=" + escape(var.getValue()) + CONT);
        }
        // javac executable
        String indent = "    ";
        pw.println(indent + escape(javacCmd) + CONT);
        indent += "    ";
        // javac VM Options
        for (String o: javacVMOpts) {
            pw.println(indent + "-J" + escape(o) + CONT);
        }
        // System properties
        for (Map.Entry<String, String> e: javacProps.entrySet()) {
            pw.println(indent + "-J-D" + escape(e.getKey()) + "=" + escape(e.getValue()) + CONT);
        }
        String sep = indent;
        for (String a: javacArgs) {
            if (a.startsWith("-") && sep.equals(" ")) {
                pw.println(CONT);
                sep = indent;
            }
            pw.print(sep);
            pw.print(escape(a));
            sep = " ";
        }
        pw.println();
    }

    public void asmtools(String toolClassName, List<String> toolArgs) {
        initPW();
        printWorkDir();
        String javaHome = System.getProperty("java.home");
        String javaCmd = new File(javaHome, "bin/java").toString();
        // Java executable
        String indent = "    ";
        pw.println(indent + escape(javaCmd) + CONT);
        // additional JVM options
        pw.println(indent + "-classpath " + action.script.getAsmToolsPath() + CONT);
        // class name
        pw.print(indent + escape(toolClassName));
        // class args
        for (String a: toolArgs) {
            pw.print(" ");
            pw.print(escape(a));
        }
        pw.println();
    }

    public void close() {
        if (pw != null)
            pw.close();
    }

    private void initPW() {
        if (pw == null) {
            pw = new PrintWriter(action.section.createOutput("rerun"));
        }
    }

    private String escape(String word) {
        // simplistic but good enough for now
        for (int i = 0; i < word.length(); i++) {
            switch (word.charAt(i)) {
                case ' ': case '\\': case '$':
                    return "'" + word + "'";
            }
        }
        return word;
    }

    private void printWorkDir() {
        pw.println("cd " + escape(action.script.absTestScratchDir().toString())
                + " &&" + CONT);
    }

    private static final String CONT = " \\";

    private final Action action;
    private PrintWriter pw;
}
