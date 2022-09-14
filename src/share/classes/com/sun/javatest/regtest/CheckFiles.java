/*
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;

import com.sun.javatest.finder.CommentStream;
import com.sun.javatest.finder.HTMLCommentStream;
import com.sun.javatest.finder.JavaCommentStream;
import com.sun.javatest.finder.ShScriptCommentStream;

/**
 * Simple utility to check for files containing comments which may contain action tags,
 * (such as {@code @run}, {@code @compile}, etc.) but which do not contain {@code @test}.
 *
 * <pre>
 * Usage:
 *     java -cp jtreg.jar:javatest.jar com.sun.javatest.regtest.CheckFiles options... files-or-directories...
 * </pre>
 *
 * One option is supported:
 * <dl>
 * <dt>{@code -l}line-length
 * <dd>the amount of each suspect comment to display
 * </dl>
 *
 * <p>
 * After the option, a series of directories and/or files to check can be specified.
 * Directories will be recursively expanded looking for files to check.
 * Source-code management directories are ignored.
 * Files with the following extensions will be checked: {@code .java}, {@code .html}, {@code .sh}.
 * Other files will be ignored.
 */
public class CheckFiles {
    /**
     * Main entry point.
     * @param args options, followed by a series of directories or files to check for
     *      possibly-malformed test descriptions.
     */
    public static void main(String[] args) {
        new CheckFiles().run(args);
    }

    /**
     * Run the utility.
     * @param args options, followed by a series of directories or files to check for
     *      possibly-malformed test descriptions
     */
    public void run(String[] args) {
        int i;
        for (i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-l") && i+1 < args.length)
                lineLength = Integer.parseInt(args[++i]);
            else if (arg.startsWith("-")) {
                System.err.println("bad arg: " + arg);
                return;
            }
            else
                break;
        }
        count = 0;
        for (i = 0; i < args.length; i++) {
            File dir = new File(args[i]);
            scan(dir);
        }
        System.err.println(count + " suspect comments found");
    }

    /**
     * Scan a series of directories and files to check for possibly-malformed test descriptions.
     * @param files the files to check
     */
    public void scan(File... files) {
        for (File file : files) {
            scan(file);
        }
    }

    private void scan(File file) {
        if (file.isDirectory()) {
            if (!excludes.contains(file.getName()))
                scan(file.listFiles());
        }
        else {
            switch (getExtension(file)) {
            case JAVA:
                check(file, new JavaCommentStream());
                break;
            case HTML:
                check(file, new HTMLCommentStream());
                break;
            case SH:
                check(file, new ShScriptCommentStream());
                break;
            }
        }
    }

    private void check(File f, CommentStream cs) {
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            cs.init(r);
            String comment;
            while ((comment = cs.readComment()) != null)
                check(f, comment);
        }
        catch (IOException e) {
            System.err.println("error for " + f + ": " + e);
        }
    }

    private void check(File f, String comment) {
        comment = comment.replace('\r', ' ');
        comment = comment.replace('\n', ' ');
        if (comment.contains("@test"))
            return;
        if (comment.matches(".*@(run|main|compile|summary|bug).*")) {
            System.out.println(f + ": " + comment.substring(0, Math.min(lineLength, comment.length())));
            count++;
        }
    }

    private int count;
    private int lineLength = 80;

    private static final int HTML = 0;
    private static final int JAVA = 1;
    private static final int SH = 2;
    private static final int OTHER = 3;

    static int getExtension(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1)
            return OTHER;
        String e = name.toLowerCase().substring(dot + 1);
        switch (e) {
            case "java":
                return JAVA;
            case "html":
                return HTML;
            case "sh":
                return SH;
            default:
                return OTHER;
        }
    }

    private static final Set<String> excludes = Set.of(".hg", ".git");
}
