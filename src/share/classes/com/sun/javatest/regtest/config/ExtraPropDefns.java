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

package com.sun.javatest.regtest.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javatest.regtest.agent.SearchPath;
import com.sun.javatest.regtest.util.FileUtils;


/**
 * Handle "extra property definitions" for @requires clauses.
 * By default, jtreg primarily collects the system properties, for use with @requires.
 * This class provides an extension mechanism to detect and provide additional characteristics
 * of the test JDK.
 */
public class ExtraPropDefns {
    /**
     * Used to report problems that are found.
     */
    static class Fault extends Exception {
        private static final long serialVersionUID = 1L;
        Fault(String msg) {
            super(msg);
        }

        Fault(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /**
     * Source files for classes to call to get property definitions.
     * All files must be java source files, containing a public class that
     * implements {@code Callable<Properties>}.
     * A file may be marked as "optional" by enclosing the name in '[' ... ']'.
     * No error will be reported if such a file does not exist.
     */
    private final List<String> files;

    /**
     * Source files for additional classes to be put on the application class path.
     * A directory may be given, which will be expanded to all the java source files
     * it contains (including i in subdirecories.
     * A file or directory may be marked as "optional" by enclosing the name in '[' ... ']'.
     * No error will be reported if such an item does not exist.
     */
    private final List<String> libs;

    /**
     * Source files for additional classes to be put on the application boot class path.
     * A directory may be given, which will be expanded to all the java source files
     * it contains (including i in subdirecories.
     * A file or directory may be marked as "optional" by enclosing the name in '[' ... ']'.
     * No error will be reported if such an item does not exist.
     */
    private final List<String> bootLibs;

    /**
     * Additional javac options to be specified when compiling the classes to get the
     * values of the extra properties.
     */
    private final List<String> javacOpts;

    /**
     * Additional VM options to be specified when running the classes to get the
     * values of the extra properties.
     */
    private final List<String> vmOpts;

    /**
     * A stream for logging messages.
     */
    private final PrintStream log;

    /**
     * The directory used to store classes to be put on the application class path.
     */
    private Path classDir;

    /**
     * The directory used to store classes to be put on the application boot class path.
     */
    private Path bootClassDir;

    /**
     * The list of names of classes to be called, to get extra properties.
     */
    private List<String> classes;

    ExtraPropDefns() {
        this(null, null, null, null, null);
    }

    ExtraPropDefns(String classes, String libs, String bootLibs, String javacOpts, String vmOpts) {
        this.files = asList(classes);
        this.libs = asList(libs);
        this.bootLibs = asList(bootLibs);
        this.javacOpts = asList(javacOpts);
        this.vmOpts = asList(vmOpts);
        log = System.err;
    }

    void compile(RegressionParameters params, JDK jdk, File outDir) throws Fault {
        compile(params, jdk, outDir.toPath());
    }

    void compile(RegressionParameters params, JDK jdk, Path outDir) throws Fault {
        Path baseDir = params.getTestSuite().getRootDir().toPath();
        classDir = outDir.resolve("classes");
        bootClassDir = outDir.resolve("bootClasses");
        compile(jdk, bootClassDir, new SearchPath(), baseDir, bootLibs, true);
        compile(jdk, classDir, new SearchPath(bootClassDir), baseDir, libs, true);
        classes = compile(jdk, classDir, new SearchPath(bootClassDir), baseDir, files, false);
    }

    Path getClassDir() {
        return classDir;
    }

    Path getBootClassDir() {
        return bootClassDir;
    }

    List<String> getClasses() {
        return classes;
    }

    List<String> getVMOpts() {
        return vmOpts;
    }

    private List<String> compile(JDK jdk, Path classDir, SearchPath classpath,
            Path srcDir, List<String> files, boolean allowDirs) throws Fault {
        if (files.isEmpty())
            return Collections.emptyList();

        List<String> classNames = new ArrayList<>();
        List<String> javacArgs = new ArrayList<>();
        javacArgs.add("-d");
        javacArgs.add(classDir.toString());

        try {
            // ensure classDir exists before creating the search path for -classpath
            Files.createDirectories(classDir);
        } catch (IOException e) {
            throw new Fault("cannot create classes directory", e);
        }

        // no need to differentiate -classpath and -Xbootclasspath/a: at compile time
        javacArgs.add("-classpath");
        javacArgs.add(new SearchPath(classDir).append(classpath).toString());

        javacArgs.addAll(javacOpts);

        boolean needCompilation = false;

        for (String e: files) {
            boolean optional;
            if (e.startsWith("[") && e.endsWith("]")) {
                optional = true;
                e = e.substring(1, e.length() - 1);
            } else {
                optional = false;
            }

            Path f = srcDir.resolve(e);
            if (!Files.exists(f)) {
                if (!optional) {
                    System.err.println("Cannot find file " + e + " for extra property definitions");
                }
                continue;
            }

            for (Path sf: expandJavaFiles(f, allowDirs)) {
                javacArgs.add(sf.toString());
                String cn = getClassNameFromFile(sf);
                classNames.add(cn);

                if (!needCompilation) {
                    Path cf = classDir.resolve(cn.replace(".", File.separator) + ".class");
                    if (!Files.exists(cf) || FileUtils.compareLastModifiedTimes(sf, cf) > 0) {
                        needCompilation = true;
                    }
                }
            }
        }

        if (needCompilation) {
            List<String> pArgs = new ArrayList<>();
            pArgs.add(jdk.getJavacProg().toString());
            pArgs.addAll(javacArgs);
            try {
                Process p = new ProcessBuilder(pArgs)
                        .redirectErrorStream(true)
                        .start();
                // pass thru any output from the compiler
                BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                try {
                    while ((line = in.readLine()) != null) {
                        log.println(line);
                    }
                } finally {
                    in.close();
                }
                int rc = p.waitFor();
                if (rc != 0) {
                    throw new Fault("Compilation of extra property definition files failed. rc=" + rc);
                }
            } catch (IOException e) {
                throw new Fault("Compilation of extra property definition files failed.", e);
            } catch (InterruptedException e) {
                throw new Fault("Compilation of extra property definition files failed.", e);
            }
        }

        return classNames;
    }

    /**
     * Expand a file or directory into a list of java source files.
     * Non-java source files are only permitted (and ignored) within directories.
     * @param file the file or directory
     * @param allowDirs whether directories are permitted or not
     * @return a list of java source files
     * @throws Fault if a bad file is found
     */
    private List<Path> expandJavaFiles(Path file, boolean allowDirs) throws Fault {
        List<Path> results = new ArrayList<>();
        expandJavaFiles(file, allowDirs, true, results);
        return results;
    }

    /**
     * Expand a file or directory into a list of java source files.
     * Non-java source files are only permitted (and ignored) within directories.
     * @param file the file or directory
     * @param allowDirs whether directories are permitted or not
     * @param rejectBadFiles whether to throw an exception if a bad file is found
     * @param results a list of java source files
     * @throws Fault if a bad file is found
     */
    private void expandJavaFiles(Path file, boolean allowDirs, boolean rejectBadFiles, List<Path> results) throws Fault {
        if (Files.isRegularFile(file)) {
            if (file.getFileName().toString().endsWith(".java")) {
                results.add(file);
            } else {
                if (rejectBadFiles) {
                    throw new Fault("unexpected file found in extra property definition files: " + file);
                }
            }
        } else if (Files.isDirectory(file)) {
            if (allowDirs) {
                for (Path child : FileUtils.listFiles(file)) {
                    expandJavaFiles(child, true, false, results);
                }
            } else {
                if (rejectBadFiles) {
                    throw new Fault("unexpected directory found in extra property definition files" + file);
                }
            }
        }
    }

    private String getClassNameFromFile(Path file) throws Fault {
        try {
            return getClassNameFromSource(Files.readString(file));
        } catch (IOException e) {
            throw new Fault("Problem reading " + file, e);
        }
    }

    private static Pattern packagePattern =
            Pattern.compile("package\\s+(((?:\\w+\\.)*)(?:\\w+))\\s*;");
    private static Pattern classPattern =
            Pattern.compile("(?:public\\s+)?(?:class|enum|interface|record)\\s+(\\w+)");

    private String getClassNameFromSource(String source) throws Fault {
        String packageName = null;

        Matcher matcher = packagePattern.matcher(source);
        if (matcher.find())
            packageName = matcher.group(1);

        matcher = classPattern.matcher(source);
        if (matcher.find()) {
            String className = matcher.group(1);
            return (packageName == null) ? className : packageName + "." + className;
        } else {
            throw new Fault("Could not extract the java class " +
                    "name from the provided source");
        }
    }

    private List<String> asList(String s) {
        return (s == null) ? Collections.<String>emptyList() : Arrays.asList(s.split("\\s+"));
    }
}
