/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

    public void exec(String[] cmd, Map<String, String> envArgs) {
        initPW();
        // Env variables
        for (Map.Entry<String, String> var : envArgs.entrySet()) {
            pw.println(var.getKey() + "=" + escape(var.getValue()) + CONT);
        }
        final int CMD = 1, ARG = 2;
        int state = CMD;
        for (int i = 0; i < cmd.length; i++) {
            String word = cmd[i];
            switch (state) {
                case CMD:
                    pw.print("    ");
                    pw.print(escape(word));
                    if (i + 1 < cmd.length) {
                        pw.println(" \\");
                        pw.print("       ");
                    }
                    state = ARG;
                    break;
                case ARG:
                    pw.print(' ');
                    pw.print(escape(word));
            }
        }
        pw.println();
    }

    public void exec(String cmd) {
        initPW();
        for (String line: cmd.split("[\r\n]+"))
            pw.println(line);
    }

    public void java(Map<String, String> envArgs, String javaCmd, Map<String, String> javaProps, List<String> javaOpts, String className, List<String> classArgs) {
        initPW();
        // Env variables
        for (Map.Entry<String, String> var : envArgs.entrySet()) {
            pw.println(var.getKey() + "=" + escape(var.getValue()) + CONT);
        }
        // Java executable
        String indent = "    ";
        pw.println(indent + escape(javaCmd) + CONT);
        // System properties
        indent += "    ";
        for (Map.Entry<String, String> e: javaProps.entrySet())
            pw.println(indent + "-D" + escape(e.getKey()) + "=" + escape(e.getValue()) + CONT);
        // additional JVM options
        if (javaOpts.size() > 0) {
            String sep = indent;
            for (String o: javaOpts) {
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

//    public void javac(String[] cmdArgs) {
//        initPW();
//        // what about env variables, including the TEST.* variables?
//        File javac = action.script.getCompileJDK().getJavacProg();
//        pw.print(escape(javac.getAbsolutePath()));
//        for (String arg: cmdArgs) {
//            pw.print(" ");
//            pw.print(escape(arg));
//        }
//        pw.println();
//    }

    void javac(Map<String, String> envArgs, String javacCmd, List<String> javacVMOpts, Map<String, String> javacProps, List<String> javacArgs) {
        initPW();
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
        for (Map.Entry<String, String> e: javacProps.entrySet())
            pw.println(indent + "-J-D" + escape(e.getKey()) + "=" + escape(e.getValue()) + CONT);
        String sep = indent;
        for (String a: javacArgs) {
            pw.print(sep);
            pw.print(escape(a));
            sep = " ";
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

    private static final String CONT = " \\";

    private Action action;
    private PrintWriter pw;
}
