/*
 * Copyright 2005-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.sun.javatest.finder.CommentStream;
import com.sun.javatest.finder.HTMLCommentStream;
import com.sun.javatest.finder.JavaCommentStream;
import com.sun.javatest.finder.ShScriptCommentStream;

public class CheckFiles
{
    public static void main(String[] args) {
        new CheckFiles().run(args);
    }

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

    public void scan(File[] files) {
        for (int i = 0; i < files.length; i++)
            scan(files[i]);
    }

    public void scan(File file) {
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
        try {
            BufferedReader r = new BufferedReader(new FileReader(f));
            cs.init(r);
            String comment;
            while ((comment = cs.readComment()) != null)
                check(f, comment);
            r.close();
        }
        catch (IOException e) {
            System.err.println("error for " + f + ": " + e);
        }
    }

    private void check(File f, String comment) {
        comment = comment.replace('\r', ' ');
        comment = comment.replace('\n', ' ');
        if (comment.indexOf("@test") != -1)
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
        if (e.equals("java"))
            return JAVA;
        else if (e.equals("html"))
            return HTML;
        else if (e.equals("sh"))
            return SH;
        else
            return OTHER;
    }

    private static Set<String> excludes;
    static {
        excludes = new HashSet<String>();
        excludes.add("SCCS");
        excludes.add("Codemgr_wsdata");
        excludes.add(".hg");
        excludes.add(".svn");
        excludes.add("RCS");
    }
}
