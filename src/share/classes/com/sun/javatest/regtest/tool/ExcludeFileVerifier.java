/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.List;
import java.util.regex.Pattern;

public class ExcludeFileVerifier {
    private PrintWriter out;

    public ExcludeFileVerifier(PrintWriter out) {
        this.out = out;
    }

    private boolean hadErrors = false;
    public boolean getHadErrors() {
        return hadErrors;
    }
    public boolean verify(File file, List<String> validTestNames) {
        var usedTestNames = new ArrayList<String>();
        var checks = new ArrayList<Check>();
        checks.add(new LineFormatCheck());
        checks.add(new TestExistsCheck(validTestNames));
        checks.add(new DuplicateCheck(usedTestNames));

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line = null;
            int n = 0;
            while ((line = br.readLine()) != null) {
                n++;
                if (lineIsComment(line.trim())) continue;
                for (Check c : checks) {
                    if(!c.check(line.trim())) {
                        out.println(file.getAbsolutePath() + " line " + n + " is invalid. Reason:");
                        out.println(c.description());
                        out.println("Line contents:");
                        out.println("--------------");
                        out.println(line);
                        out.println("--------------");
                        hadErrors = true;
                        break;
                    }
                }
                usedTestNames.add(testName(line));
            }
        }
        catch (FileNotFoundException e) {
            System.out.println("File does not exist: "  + file.getAbsolutePath());
        }
        catch (IOException e) {
            System.out.println("File cannot be read: "  + file.getAbsolutePath());
        }
        return true;
    }

    static boolean lineIsComment(String line) {
        return line.isBlank() || line.trim().startsWith("#");
    }

    private static String testName(String line) {
        line = line.trim();
        String[] words = line.split("\\s+");
        return words.length >= 1 ? words[0] : null;
    }

    abstract static class Check {
        public abstract String description();
        public abstract boolean check(String line);
    }

    static class LineFormatCheck extends Check {
        private static final String commalist = "([\\w-]+)(,[\\w-]+)*";
        private static Pattern pattern = Pattern.compile("\\S+\\s+" + commalist + "\\s+" + commalist + ".*");
        public String description() {
            return "Must follow: <test-name> <bugid>(,<bugid>)* <platform>(,<platform>)* <description>";
        }

        public boolean check(String line) {
            return pattern.matcher(line).matches();
        }
    }

    static class TestExistsCheck extends Check {
        private List<String> validTestNames;

        public TestExistsCheck(List<String> validTestNames) {
            this.validTestNames = validTestNames;
        }

        public String description() {
            return "The fully qualified test must exist.";
        }

        public boolean check(String line) {
            return validTestNames.contains(testName(line));
        }
    }

    static class DuplicateCheck extends Check {
        private List<String> usedTestNames;

        public DuplicateCheck(List<String> usedTestNames) {
            this.usedTestNames = usedTestNames;
        }

        public String description() {
            return "Exclude file cannot contain duplicate entries.";
        }

        public boolean check(String line) {
            return !usedTestNames.contains(testName(line));
        }
    }
}
