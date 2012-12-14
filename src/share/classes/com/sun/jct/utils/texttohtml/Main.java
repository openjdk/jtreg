/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.jct.utils.texttohtml;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 *
 * @author jjg
 */
public class Main {
    /**
     * An exception to report bad command line arguments.
     */
    public static class BadArgs extends Exception {
        static final long serialVersionUID = 0;
        BadArgs(String msg) {
            super(msg);
        }
    }

    public static void main(String[] args) {
        try {
            Main m = new Main(args);
            m.run();
        } catch (BadArgs e) {
            System.err.println("Error: " + e);
            exit(2);
        } catch (IOException e) {
            System.err.println("IO error: " + e);
            exit(2);
        }
    }

    /** Creates a new instance of Main */
    public Main() {
    }

    public Main(String[] args) throws BadArgs {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-template"))
                templateFile = new File(args[++i]);
            else if (arg.equals("-text"))
                textFile = new File(args[++i]);
            else if (arg.equals("-html"))
                htmlFile = new File(args[++i]);
            else if (arg.equals("-title"))
                title = args[++i];
            else if (arg.equals("-style"))
                style = args[++i];
            else
                throw new BadArgs("unknown option: " + arg);
        }
    }

    public static class Ant extends Task {
        private Main m = new Main();

        public void setTemplateFile(File file) {
            m.templateFile = file;
        }

        public void setTextFile(File file) {
            m.textFile = file;
        }

        public void setHtmlFile(File file) {
            m.htmlFile = file;
        }

        public void setTitle(String title) {
            m.title = title;
        }

        public void setStyle(String style) {
            m.style = style;
        }

        @Override
        public void execute() {
            try {
                m.run();
            } catch (BadArgs e) {
                throw new BuildException(e.getMessage());
            } catch (IOException e) {
                throw new BuildException(e);
            }
        }
    }

    private void run() throws BadArgs, IOException {
        if (textFile == null)
            throw new BadArgs("no text file specified");
        if (htmlFile == null)
            throw new BadArgs("no html file specified");

        Pattern p = Pattern.compile("</(head|body)>", Pattern.CASE_INSENSITIVE);

        BufferedWriter out = new BufferedWriter(new FileWriter(htmlFile));
        try {
            if (templateFile == null) {
                println(out, "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">");
                println(out, "<html><head>");
                insertHeaders(out);
                println(out, "</head><body>");
                insertEscapedText(out);
                println(out, "</body></html>");
            } else {
                BufferedReader in = new BufferedReader(new FileReader(templateFile));
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        Matcher m = p.matcher(line);
                        int start = 0;
                        while (m.find(start)) {
                            out.write(line.substring(start, m.start()));
                            String word = m.group(1);
                            if (word.equals("head"))
                                insertHeaders(out);
                            else if (word.equals("body"))
                                insertEscapedText(out);
                            out.write(m.group(0));
                            start = m.end();
                        }
                        println(out, line.substring(start));
                    }
                } finally {
                    in.close();
                }
            }
        } finally {
            out.close();
        }
    }

    private void println(BufferedWriter out, String text) throws IOException {
        out.write(text);
        out.newLine();
    }

    private void insertHeaders(BufferedWriter out) throws IOException {
        if (title != null)
            println(out, "<title>" + title + "</title>");
        if (style != null)
            println(out, "<style type=\"text/css\">" + style + "</style>");
    }

    private void insertEscapedText(BufferedWriter out) throws IOException {
        println(out, "<pre>");
        Pattern p = Pattern.compile("([&<>])");
        BufferedReader in = new BufferedReader(new FileReader(textFile));
        try {
            String line;
            while ((line = in.readLine()) != null) {
                Matcher m = p.matcher(line);
                int start = 0;
                while (m.find(start)) {
                    out.write(line.substring(start, m.start()));
                    char ch = m.group(1).charAt(0);
                    switch (ch) {
                        case '&': out.write("&amp;"); break;
                        case '<': out.write("&lt;"); break;
                        case '>': out.write("&gt;"); break;
                        default:  out.write(ch);
                    }
                    start = m.end();
                }
                println(out, line.substring(start));
            }
        } finally {
            in.close();
        }
        println(out, "</pre>");
    }

    private static void exit(int rc) {
        try {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null)
                sm.checkExit(rc);
            System.exit(rc);
        } catch (SecurityException ignore) {
        }
    }

    private File templateFile;
    private File textFile;
    private File htmlFile;
    private String title;
    private String style;
}
