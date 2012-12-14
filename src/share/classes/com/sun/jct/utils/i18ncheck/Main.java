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

package com.sun.jct.utils.i18ncheck;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.FileScanner;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

/**
 *
 * @author jjg
 */
public class Main {
    public static void main(String[] args) {
        try {
            boolean ok = new Main().run(args);
            exit(ok ? 0 : 1);
        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace(System.err);
            exit(2);
        }
    }


    public boolean run(String[] args) throws Exception {
        // scan a set of files to build up a list of i18n tags,
        // the files that define them and the files that reference them,
        // in order to determine any issues.
        int i;
        for (i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-p") && i + 1 < args.length)
                addPatternFile(new File(args[++i]));
            else if (arg.startsWith("-"))
                throw new Exception("unknown option: " + arg);
            else
                break;
        }

        for ( ; i < args.length; i++)
            addArgFile(new File(args[i]));

        return execute();
    }

    void addPatternFile(File file) {
//        System.err.println("add pattern file " + file);
        patternFiles.add(file);
    }

    void addArgFile(File file) {
//        System.err.println("add file " + file);
        argFiles.add(file);
    }

    void addArgFiles(File baseDir, String[] files) {
        if (files == null)
            return;
        for (String f: files)
            addArgFile(new File(baseDir, f));
    }

    boolean execute() throws IOException {
        for (File file: patternFiles)
            readPatterns(file);

        for (File file: argFiles) {
            if (file.isDirectory())
                readDirectory(file);
            else {
                String extn = getExtension(file);
                if (equal(extn, ".properties"))
                    readProperties(file);
                else
                    readFile(file);
            }
        }

        return report(nameTable);
    }

    void readPatterns(File file) throws IOException {
//        System.err.println("read pattern file " + file);
        BufferedReader in = new BufferedReader(new FileReader(file));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0 || line.startsWith("#"))
                continue;
            String[] words = line.split(" +");
            if (words.length < 2)
                continue;
            Pattern pat = Pattern.compile(words[0]);
            String[] keys = new String[words.length - 1];
            System.arraycopy(words, 1, keys, 0, keys.length);
            patterns.add(new PatternInfo(pat, keys));
        }
        in.close();
    }

    void readDirectory(File dir) throws IOException {
        File[] children = dir.listFiles();
        for (File file: children) {
            if (file.isDirectory())
                continue;
            String extn = getExtension(file);
            if (equal(extn, ".properties"))
                readProperties(file);
            else if (equal(extn, ".java"))
                readFile(file);

        }
    }

    void readProperties(File file) throws IOException {
//        System.err.println("read properties file " + file);
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        Properties p = new Properties();
        p.load(in);
        in.close();
        for (Enumeration<?> e = p.propertyNames(); e.hasMoreElements(); ) {
            String n = (String) e.nextElement();
            define(n, file);
        }
    }

    void readFile(File file) throws IOException {
//        System.err.println("read source file " + file);
        BufferedReader in = new BufferedReader(new FileReader(file));
        String line;
        while ((line = in.readLine()) != null) {
            for (PatternInfo p: patterns) {
                Matcher m = p.pattern.matcher(line);
                if (m.find()) {
                    for (String k: p.keys) {
                        Matcher g = groupNumber.matcher(k);
                        if (g.find()) {
                            int n = Integer.valueOf(g.group(1));
                            String name = k.substring(0, g.start()) + m.group(n) + k.substring(g.end());
                            refer(name, file);
                        } else
                            refer(k, file);
                    }
                    break;
                }
            }
        }
        in.close();

    }

    boolean report(Map<String, NameInfo> table) {
        // invert the data structure to get File -> Set<name>
        Map<File, Set<String>> undefinedNames = new TreeMap<File, Set<String>>();
        Map<File, Set<String>> unusedNames = new TreeMap<File, Set<String>>();
        for (NameInfo e: table.values()) {
            if (e.definitions == null || e.definitions.size() == 0)
                insert(undefinedNames, e.name, e.references);
            if (e.references == null || e.references.size() == 0)
                insert(unusedNames, e.name, e.definitions);
        }

        write("undefined", undefinedNames);
        write("unused", unusedNames);

        return (undefinedNames.size() == 0 && unusedNames.size() == 0);
    }

    void insert(Map<File, Set<String>> table, String name, Set<File> files) {
        for (File f: files) {
            Set<String> s = table.get(f);
            if (s == null)
                table.put(f, s = new TreeSet<String>());
            s.add(name);
        }
    }

    void write(String title, Map<File, Set<String>> table) {
        if (table.size() > 0) {
            System.err.println("The following files have " + title + " names");
            for (Map.Entry<File, Set<String>> e: table.entrySet()) {
                System.err.println("  " + e.getKey());
                for (String n: e.getValue())
                    System.err.println("     " + n);
            }
        }
    }

    void define(String name, File file) {
        NameInfo e = nameTable.get(name);
        if (e == null) {
            e = new NameInfo(name);
            nameTable.put(name, e);
        }
        if (e.definitions == null)
            e.definitions = new TreeSet<File>();
        e.definitions.add(file);
    }

    void refer(String name, File file) {
        NameInfo e = nameTable.get(name);
        if (e == null) {
            e = new NameInfo(name);
            nameTable.put(name, e);
        }
        if (e.references == null)
            e.references = new TreeSet<File>();
        e.references.add(file);
    }

    String getExtension(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf(".");
        return (dot == -1 ? null : n.substring(dot));
    }

    void error(String message) {
        System.err.println(message);
    }

    private static <T> boolean equal(T t1, T t2) {
        return (t1 == null ? t2 == null : t1.equals(t2));
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

    class NameInfo {
        NameInfo(String name) {
            this.name = name;
        }
        final String name;
        Set<File> definitions;
        Set<File> references;
    }

    class PatternInfo {
        PatternInfo(Pattern pattern, String[] keys) {
            this.pattern = pattern;
            this.keys = keys;
        }

        final Pattern pattern;
        final String[] keys;
    }

    public static class Ant extends Task {
        File patternFile;
        List<FileSet> fileSets;

        public void setPatternFile(File file) {
            patternFile = file;
        }

        public void addFileSet(FileSet fs) {
            if (fileSets == null)
                fileSets = new ArrayList<FileSet>();
            fileSets.add(fs);
        }

        @Override
        public void execute() {
            Main m = new Main();
            m.addPatternFile(patternFile);
            for (FileSet fs: fileSets) {
                FileScanner s = fs.getDirectoryScanner(getProject());
                m.addArgFiles(s.getBasedir(), s.getIncludedFiles());
            }

            try {
                boolean ok = m.execute();
                if (!ok)
                    throw new BuildException("Errors occurred");
            } catch (IOException e) {
                throw new BuildException(e);
            }
        }
    }

    List<File> patternFiles = new ArrayList<File>();
    List<File> argFiles = new ArrayList<File>();

    Map<String, NameInfo> nameTable = new TreeMap<String, NameInfo>();
    List<PatternInfo> patterns = new ArrayList<PatternInfo>();
    Pattern groupNumber = Pattern.compile("\\\\([0-9]*)");
}
