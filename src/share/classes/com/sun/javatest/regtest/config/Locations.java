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

package com.sun.javatest.regtest.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.sun.javatest.TestDescription;
import com.sun.javatest.regtest.agent.SearchPath;
import com.sun.javatest.regtest.tool.Version;
import com.sun.javatest.regtest.util.FileUtils;
import com.sun.javatest.regtest.util.StringUtils;

/**
 * Utilities to locate source and class files used by a test.
 */
public final class Locations {
    /**
     * Used to report problems that are found.
     */
    public static class Fault extends Exception {
        private static final long serialVersionUID = 1L;
        public Fault(String msg) {
            super(msg);
        }
        public Fault(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /**
     * A library location.
     * A library location has a name, as given in a test description,
     * a directory for its source files, a directory for its class files,
     * and a kind, as determined by looking at its contents.
     * As a special case, a library location with no name is used to represent
     * the location of the source and class files of the test itself.
     */
    public static class LibLocn {
        public enum Kind { PACKAGE, PRECOMPILED_JAR, SYS_MODULE, USER_MODULE }
        public final String name;
        public final Path absSrcDir;
        public final Path absClsDir;
        public final Kind kind;

        LibLocn(String name, Path absSrcDir, Path absClsDir, Kind kind) {
            this.name = name;
            this.absSrcDir = absSrcDir;
            this.absClsDir = absClsDir;
            this.kind = kind;
        }

        public boolean isLibrary() {
            return name != null;
        }

        public boolean isTest() {
            return name == null;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof LibLocn) {
                LibLocn l = (LibLocn) other;
                return (name == null ? l.name == null : name.equals(l.name)
                        && absSrcDir.equals(l.absSrcDir)
                        && absClsDir.equals(l.absClsDir)
                        && kind == l.kind);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return ((name == null ? 0 : name.hashCode()) << 7)
                    + (absSrcDir.hashCode() << 5)
                    + (absClsDir.hashCode() << 3)
                    + kind.hashCode();
        }

        @Override
        public String toString() {
            return "LibLocn(" + name + ",src:" + absSrcDir + ",cls:" + absClsDir + "," + kind + ")";
        }
    }

    /**
     * A class location.
     * A class location has a class name, a source file and a class file.
     * In addition, it exists in the context of a library location, and
     * depending on the kind of library, in a module within that library.
     */
    public static class ClassLocn {
        public final LibLocn lib;
        public final String optModule;
        public final String className;
        public final Path absSrcFile;
        public final Path absClsFile;

        ClassLocn(LibLocn lib, String optModule, String className, Path absSrcFile, Path absClsFile) {
            this.lib = lib;
            this.optModule = optModule;
            this.className = className;
            this.absSrcFile = absSrcFile;
            this.absClsFile = absClsFile;
        }

        public boolean isUpToDate() {
            return Files.exists(absClsFile)
                    && Files.isReadable(absClsFile)
                    && FileUtils.compareLastModifiedTimes(absClsFile, absSrcFile) > 0;
        }

        @Override
        public String toString() {
            return "ClassLocn(" + lib.name + "," + optModule + "," + className +
                    "," + absSrcFile + "," + absClsFile + ")";
        }
     }

    private final RegressionTestSuite testSuite;
    private final Set<String> systemModules;
    private final SearchPath jtpath;
    private final JDK testJDK;

    private final Path absTestFile;
    private final Path absBaseSrcDir;
    private final Path absTestSrcDir;
    private final Path absBaseClsDir;
    private final Path absTestClsDir;
    private final Path absTestPatchDir;
    private final Path absTestModulesDir;
    private final Path absTestWorkDir;
    private final Path relLibDir;
    private final List<LibLocn> libList;

    /**
     * Creates an object to handle the various locations for a test.
     *
     * @param params the parameters for the test run
     * @param td     the test
     * @param logger an object to which to write logging messages
     *
     * @throws Fault if an error occurs
     */
    public Locations(RegressionParameters params, TestDescription td, Consumer<String> logger)
            throws Fault {
        testSuite = params.getTestSuite();
        systemModules = params.getTestJDK().getSystemModules(params, logger);
        jtpath = params.getJavaTestClassPath();
        testJDK = params.getTestJDK();

        Version v = testSuite.getRequiredVersion();
        boolean useUniqueClassDir = (v.version != null)
                && (v.compareTo(new Version("4.2 b08")) >= 0);

        absTestFile = td.getFile().toPath().toAbsolutePath();
        Path relTestFile = td.getRootRelativeFile().toPath();
        Path relTestDir = relTestFile.getParent();
        if (relTestDir == null) {
            relTestDir = Path.of(".");  // use normalize later to eliminate "."
        }

        String testName = relTestFile.getFileName().toString();
        String testId = td.getId();
        String uniqueTestSubDir = testName.replaceAll("(?i)\\.[a-z]+$",
                ((testId == null ? "" : "_" + testId) + ".d"));

        String packageRoot = td.getParameter("packageRoot");
        Path relTestSrcDir = relLibDir = (packageRoot != null) ? Path.of(packageRoot) : relTestDir;

        absBaseSrcDir = params.getTestSuite().getRootDir().toPath();
        absTestSrcDir = absBaseSrcDir.resolve(relTestSrcDir).normalize();

        Path workDirRoot = params.getWorkDirectory().getRoot().toPath();
        Path relTestWorkDir = relTestDir.resolve(uniqueTestSubDir);
        absTestWorkDir = workDirRoot.resolve(relTestWorkDir);

        absBaseClsDir = getThreadSafeDir(workDirRoot.resolve("classes"), params.getConcurrency());
        Path relTestClsDir = (packageRoot != null) ? Path.of(packageRoot)
                : useUniqueClassDir ? relTestDir.resolve(uniqueTestSubDir)
                : relTestDir;
        absTestClsDir = absBaseClsDir.resolve(relTestClsDir).normalize();

        // The following assumes we will never have test code in a package
        // or subpackage beginning patches or modules when we also have
        // test patches or test modules. If that becomes not true, then
        // we should use another subdir (classes?) for the classes on the
        // classpath, so that classes, modules and patches are sibling
        // directories.
        absTestPatchDir = absTestClsDir.resolve("patches");
        absTestModulesDir = absTestClsDir.resolve("modules");

        libList = new ArrayList<>();
        String libs = td.getParameter("library");
        for (String lib: StringUtils.splitWS(libs)) {
            libList.add(getLibLocn(td, lib));
        }
    }

    public List<LibLocn> getLibs() {
        return libList;
    }

    /**
     * Gets the library location for a library specified in a test description.
     * @param td the test description
     * @param lib the name (path) of the library as specified in the test description
     * @return the resolved library location
     * @throws Fault if there is an error resolving the library location
     */
    private LibLocn getLibLocn(TestDescription td, String lib) throws Fault {
        if (lib.startsWith("/")) {
            String libTail = lib.substring(1);
            checkLibPath(Path.of(libTail));
            if (Files.exists(absBaseSrcDir.resolve(libTail))) {
                return createLibLocn(lib, absBaseSrcDir, absBaseClsDir);
            } else {
                try {
                    for (File extRootFile: testSuite.getExternalLibRoots(td)) {
                        Path extRoot = extRootFile.toPath();
                        if (Files.exists(extRoot.resolve(libTail))) {
                            // since absBaseSrcDir/lib does not exist, we can safely
                            // use absBaseClsDir/lib for the compiled classes
                            return createLibLocn(lib, extRoot, absBaseClsDir);
                        }
                    }
                } catch (RegressionTestSuite.Fault e) {
                    throw new Fault(CANT_FIND_LIB + e);
                }
            }
        } else if (lib.startsWith("${") && lib.endsWith(".jar")) {
            int end = lib.indexOf("}/");
            if (end != -1) {
                String name = lib.substring(2, end);
                Path dir = null;
                if (name.equals("java.home")) {
                    dir = testJDK.getAbsoluteHomeDirectory();
                } else if (name.equals("jtreg.home")) {
                    dir = jtpath.asList().get(0).getParent().getParent();
                }
                if (dir != null) {
                    String libTail = lib.substring(end + 2);
                    Path absLib = dir.resolve(libTail);
                    if (Files.exists(absLib))
                        return new LibLocn(lib, null, absLib, LibLocn.Kind.PRECOMPILED_JAR);
                }
            }
        } else {
            checkLibPath(relLibDir.resolve(lib));
            if (Files.exists(absTestSrcDir.resolve(lib)))
                return createLibLocn(lib, absTestSrcDir, absBaseClsDir.resolve(relLibDir));
        }
        throw new Fault(CANT_FIND_LIB + lib);
    }

    private void checkLibPath(Path lib) throws Fault {
        Path l = lib.normalize();
        if (l.startsWith(Path.of(".."))) {
            throw new Fault("effective library path is outside the test suite: " + l);
        }
    }

    /**
     * Creates a library location.
     * The library kind is inferred by looking at its contents.
     * @param lib  the name (path) of the library as specified in the test description
     * @param absBaseSrcDir the base directory for the library's source files
     * @param absBaseClsDir the base directory for the library's class files
     * @return a library location
     * @throws Fault if there is an error resolving the library location
     */
    private LibLocn createLibLocn(String lib, Path absBaseSrcDir, Path absBaseClsDir) throws Fault {
        String relLib = (lib.startsWith("/") ? lib.substring(1) : lib);
        Path absLib = absBaseSrcDir.resolve(relLib).normalize();
        if (Files.isRegularFile(absLib) && absLib.getFileName().toString().endsWith(".jar")) {
            return new LibLocn(lib, null, absLib, LibLocn.Kind.PRECOMPILED_JAR);
        } else {
            if (!Files.isDirectory(absLib))
                throw new Fault(BAD_LIB + lib);
            Path absLibSrcDir = absLib;
            Path absLibClsDir = absBaseClsDir.resolve(relLib).normalize();
            LibLocn.Kind kind = getDirKind(absLibSrcDir);
            return new LibLocn(lib, absLibSrcDir, absLibClsDir, kind);
        }
    }

    /**
     * Gets the set of kinds of contents of a source directory.
     * The set will include:
     * <ul>
     * <li>USER_MODULE, if the source directory contains one or more directories
     *      which in turn contain module-info.java
     * <li>SYS_MODULE, if the source directory contains one or more directories
     *      directory whose names match that of a system module
     * <li>PACKAGE, if the source directory contains a directory which is neither
     *      of the above
     * </ul>
     * @param absSrcDir the source directory to examine
     * @return the kinds of libraries found in the given source directory
     */
    public Set<LibLocn.Kind> getDirKinds(Path absSrcDir) {
        Set<LibLocn.Kind> kinds = EnumSet.noneOf(LibLocn.Kind.class);
        for (Path f : FileUtils.listFiles(absSrcDir)) {
            if (Files.isDirectory(f)) {
                if (isSystemModule(f.getFileName().toString())) {
                    kinds.add(LibLocn.Kind.SYS_MODULE);
                } else if (Files.exists((f.resolve("module-info.java")))) {
                    kinds.add(LibLocn.Kind.USER_MODULE);
                } else {
                    kinds.add(LibLocn.Kind.PACKAGE);
                }
            } else {
                // ignore for now; could categorize as UNNAMED_PACKAGE?
            }

        }
        return kinds;
    }

    /**
     * Gets the kind of a source directory.
     * The kind is one of:
     * <ul>
     * <li>USER_MODULE, if the source directory contains directories which in turn contain
     *     module-info.java
     * <li>SYS_MODULE, if the source directory contains directories whose name matches that
     *     of a system module
     * <li>PACKAGE, if none of the above
     * </ul>
     * It is an error if the source directory contains both user modules and system modules.
     * @param absSrcDir the source directory
     * @return the kind of the source directory
     * @throws Locations.Fault if the directory contains more than one kind of content
     */
    public LibLocn.Kind getDirKind(Path absSrcDir) throws Fault {
        Set<LibLocn.Kind> kinds = getDirKinds(absSrcDir);
        switch (kinds.size()) {
            case 0:
                return LibLocn.Kind.PACKAGE;
            case 1:
                return kinds.iterator().next();
            default:
                throw new Fault(MIXED_LIB + absSrcDir);
        }
    }

    boolean isSystemModule(String name) {
        return (systemModules != null) && systemModules.contains(name);
    }

    /**
     * Gets the path of the test defining file.
     * @return the path
     */
    public Path absTestFile() {
        return absTestFile;
    }

    /**
     * Gets the path of the test source directory.
     * @return the path
     */
    public Path absTestSrcDir() {
        return absTestSrcDir;
    }

    /**
     * Gets the path of the test source directory, or to a module within it.
     * @param optModule the name of the module, or null for "no module"
     * @return the path
     */
    public Path absTestSrcDir(String optModule) {
        return getFile(absTestSrcDir, optModule);
    }

    /**
     * Gets the path of a source file in the test source directory or a module within it.
     * @param optModule the name of the module, or null for "no module"
     * @param srcFile the file
     * @return the path
     * @throws IllegalArgumentException if the path is not a relative path
     */
    public Path absTestSrcFile(String optModule, File srcFile) {
        if (srcFile.isAbsolute())
            throw new IllegalArgumentException();
        return getFile(absTestSrcDir, optModule, srcFile.getPath());
    }

    /**
     * Gets a search path for the source of a test, consisting of the test source directory,
     * and the source directories of all libraries of PACKAGE kind.
     * @return the search path
     */
    // (just) used to set test.src.path or TESTSRCPATH
    public List<Path> absTestSrcPath() {
        List<Path> list = new ArrayList<>();
        list.add(absTestSrcDir);
        for (LibLocn l: libList) {
            if (l.kind == LibLocn.Kind.PACKAGE) {
                list.add(l.absSrcDir);
            }
        }
        return list;
    }

    /**
     * Gets a list of the source directories of all libraries of a given kind.
     * @param kind the kind
     * @return the directories of the specified kind
     */
    public List<Path> absLibSrcList(LibLocn.Kind kind) {
        List<Path> list = new ArrayList<>();
        for (LibLocn l: libList) {
            if (l.kind == kind) {
                list.add(l.absSrcDir);
            }
        }
        return list;
    }

    /**
     * Gets a list of all jar-file libraries for the test in the test suite.
     * @return the list of jar-file libraries
     */
    public List<Path> absLibSrcJarList() {
        List<Path> list = new ArrayList<>();
        for (LibLocn l: libList) {
            if (l.kind == LibLocn.Kind.PRECOMPILED_JAR) {
                Path f = l.absClsDir;
                if (Files.isRegularFile(f) && f.getFileName().toString().endsWith(".jar") && Files.exists(f))
                    list.add(f);
            }
        }
        return list;
    }

    /**
     * Gets the base directory for all compiled classes.
     * This is different from the directory for the classes for any specific library
     * or test. It is used when setting up permissions for tests that use the security
     * manager, to ensure that a test can read all necessary compiled classes.
     * @return the base directory
     */
    public Path absBaseClsDir() {
        return absBaseClsDir;
    }

    /**
     * Gets the directory for the compiled classes of a test in the unnamed module.
     * @return the directory
     */
    public Path absTestClsDir() {
        return absTestClsDir;
    }

    /**
     * Gets the directory for the compiled classes in either the unnamed module or
     * a named module.
     * @param optModule the name of the module, or null for the unnamed module
     * @return the directory
     */
    public Path absTestClsDir(String optModule) {
        if (optModule == null) {
            return absTestClsDir;
        } else if (isSystemModule(optModule)) {
            return absTestPatchDir().resolve(optModule);
        } else {
            return absTestModulesDir().resolve(optModule);
        }
    }

    /**
     * Gets the search path for the classes for a test, consisting of the test class directory,
     * and the class directories of all libraries of PACKAGE kind.
     * @return the search path
     */
    // (just) used to set test.class.path or TESTCLASSPATH
    public List<Path> absTestClsPath() {
        List<Path> list = new ArrayList<>();
        list.add(absTestClsDir);
        for (LibLocn l: libList) {
            switch (l.kind) {
                case PACKAGE:
                case PRECOMPILED_JAR:
                    list.add(l.absClsDir);
            }
        }
        return list;
    }

    /**
     * Gets a list of the class directories of all libraries of a given kind.
     * @param kind the kind
     * @return the list
     */
    public List<Path> absLibClsList(LibLocn.Kind kind) {
        List<Path> list = new ArrayList<>();
        for (LibLocn l: libList) {
            if (l.kind == kind) {
                list.add(l.absClsDir);
            }
        }
        return list;
    }

    /**
     * Gets a file within the test-specific subdirectory of the work directory.
     * @param name the name of the subdirectory
     * @return the file
     */
    public Path absTestWorkFile(String name) {
        return absTestWorkDir.resolve(name);
    }

    /**
     * Gets the directory in which to store the compiled classes of any user-defined
     * modules for a test.
     * @return the directory
     */
    public Path absTestModulesDir() {
        return absTestModulesDir;
    }

    /**
     * Gets the directory in which to store the compiled classes to patch system
     * modules for a test.
     * @return the patch directory
     */
    public Path absTestPatchDir() {
        return absTestPatchDir;
    }

    /**
     * Locates a set of classes.
     * The name is as defined for the @build tag.
     * The following forms are allowed:
     * <ul>
     * <li>C -- class C in the unnamed package in the unnamed module
     * <li>p.C -- class C in package p in the unnamed module
     * <li>* -- all classes in the unnamed package in the unnamed module
     * <li>p.* -- all classes in package p in the unnamed module
     * </ul>
     * All forms can be prefixed with "m/" to specify module m instead of the
     * unnamed package.
     * The test source directory is searched first, followed by any library directories.
     *
     * @param name the name of the classes to be built
     * @return the locations of the classes identified by {@code name}
     * @throws Locations.Fault if there is a problem locating any of the classes
     */
    public List<ClassLocn> locateClasses(String name) throws Fault {
        List<LibLocn> searchLocns;
        String optModule;
        String className;
        int sep = name.indexOf("/");
        if (sep > 0) {
            optModule = name.substring(0, sep);
            className = name.substring(sep + 1);

            List<LibLocn> moduleLocns = getModuleLocn(optModule);
            if (moduleLocns.isEmpty()) {
                throw new Fault("can't find module " + optModule + " in test directory or libraries");
            }
            searchLocns = moduleLocns;
        } else {
            optModule = null;
            className = name;

            searchLocns = new ArrayList<>();
            searchLocns.add(new LibLocn(null, absTestSrcDir, absTestClsDir, LibLocn.Kind.PACKAGE));
            for (LibLocn l: libList) {
                if (l.kind == LibLocn.Kind.PACKAGE) {
                    searchLocns.add(l);
                }
            }
        }

        List<ClassLocn> results;
        if (className.equals("*")) {
            results = locateClassesInPackage(searchLocns, optModule, null);
        } else if (className.endsWith(".*")) {
            String packageName = className.substring(0, className.length() - 2);
            results = locateClassesInPackage(searchLocns, optModule, packageName);
        } else {
            results = locateClass(searchLocns, optModule, className);
        }

        if (results.isEmpty()) {
            if (optModule == null) {
                throw new Fault("can't find " + className + " in test directory or libraries");
            } else {
                throw new Fault("can't find " + className + " in module " + optModule
                        + " in " + searchLocns.get(0).absSrcDir);
            }
        }

        return results;
    }

    /**
     * Locates a module in either the test source directory or in module libraries.
     */
    List<LibLocn> getModuleLocn(String moduleName) {
        if (moduleName == null) {
            throw new NullPointerException();
        } else if (isSystemModule(moduleName)) {
            List<LibLocn> list = new ArrayList<>();
            if (Files.exists(getFile(absTestSrcDir, moduleName))) {
                list.add(new LibLocn(null, absTestSrcDir, absTestPatchDir(), LibLocn.Kind.SYS_MODULE));
            }
            for (LibLocn l : libList) {
                if (l.kind == LibLocn.Kind.SYS_MODULE && Files.exists(getFile(l.absSrcDir, moduleName))) {
                    list.add(l);
                }
            }
            return list;
        } else {
            if (Files.exists(getFile(absTestSrcDir, moduleName))) {
                return Collections.singletonList(
                        new LibLocn(null, absTestSrcDir, absTestModulesDir(), LibLocn.Kind.USER_MODULE));
            }
            for (LibLocn l : libList) {
                if (l.kind == LibLocn.Kind.USER_MODULE && Files.exists(getFile(l.absSrcDir, moduleName))) {
                    return Collections.singletonList(l);
                }
            }
            return Collections.emptyList();
        }
    }

    /**
     * Locates the first instance of a class in a series of locations.
     * @return a singleton list if the file is found; or an empty list otherwise.
     */
    private List<ClassLocn> locateClass(List<LibLocn> locns, String optModule, String className) {
        for (LibLocn l: locns) {
            ClassLocn cl = locateClass(l, optModule, className);
            if (cl != null) {
                return Collections.singletonList(cl);
            }
        }
        return Collections.emptyList();
    }

    /**
     * Locates an instance of a class in a given location.
     * @return the instance, or null if not found.
     */
    private ClassLocn locateClass(LibLocn locn, String optModule, String className) {
        for (String e: extns) {
            String relSrc = className.replace('.', File.separatorChar) + e;
            String relCls = className.replace('.', File.separatorChar) + ".class";
            Path sf, cf;

            if (Files.exists(sf = getFile(locn.absSrcDir, optModule, relSrc))) {
                cf = getFile(locn.absClsDir, optModule, relCls);
                return new ClassLocn(locn, optModule, className, sf, cf);
            }

            // Special case for file to be directly in the test dir
            if (locn.name == null && optModule == null) {
                int sep = relSrc.lastIndexOf(File.separatorChar);
                if (sep >= 0) {
                    String baseName = relSrc.substring(sep + 1);
                    if (Files.exists(sf = absTestSrcDir.resolve(baseName))) {
                        cf = absTestClsDir.resolve(relCls);
                        return new ClassLocn(locn, null, className, sf, cf);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Locates the classes for a package in a series of locations.
     */
    private List<ClassLocn> locateClassesInPackage(List<LibLocn> locns, String optModule, String optPackage) throws Fault {
        List<ClassLocn> results = new ArrayList<>();
        boolean recursive = (optModule != null) && (optPackage == null);
        for (LibLocn l: locns) {
            locateClassesInPackage(l, optModule, optPackage, recursive, results);
        }
        return results;
    }

    /**
     * Locates the classes for a package in a given location.
     */
    private void locateClassesInPackage(LibLocn l, String optModule, String optPackage,
            boolean recursive, List<ClassLocn> results) throws Fault {

        Path pkgSrcDir, pkgClsDir;
        if (optPackage == null) {
            pkgSrcDir = getFile(l.absSrcDir, optModule);
            pkgClsDir = getFile(l.absClsDir, optModule);
        } else {
            String p = optPackage.replace('.', File.separatorChar);
            pkgSrcDir = getFile(l.absSrcDir, optModule, p);
            pkgClsDir = getFile(l.absClsDir, optModule, p);
        }

        if (!Files.isDirectory(pkgSrcDir))
            return;

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(pkgSrcDir)) {
            for (Path sf : ds) {
                String fn = sf.getFileName().toString();
                if (Files.isDirectory(sf)) {
                    if (recursive) {
                        String subpkg = (optPackage == null) ? fn : optPackage + "." + fn;
                        locateClassesInPackage(l, optModule, subpkg, true, results);
                    }
                } else if (Files.isReadable(sf) && hasExtn(fn, extns)) {
                    String cn = fn.substring(0, fn.lastIndexOf("."));
                    String className = (optPackage == null) ? cn : optPackage + "." + cn;
                    Path cf = pkgClsDir.resolve(cn + ".class");
                    results.add(new ClassLocn(l, optModule, className, sf, cf));
                }
            }
        } catch (IOException e) {
            throw new Fault("error reading directory " + pkgSrcDir, e);
        }
    }

    private static final String[] extns = { ".java", ".jasm", ".jcod" };

    private Path getFile(Path absBaseDir, String optModule) {
        return (optModule == null) ? absBaseDir : absBaseDir.resolve(optModule);
    }

    private Path getFile(Path absBaseDir, String optModule, String relFile) {
        return getFile(absBaseDir, optModule).resolve(relFile);
    }

    private boolean hasExtn(String name, String... extns) {
        for (String e: extns) {
            if (name.endsWith(e))
                return true;
        }
        return false;
    }

    //----------thread safety-----------------------------------------------

    private static final AtomicInteger uniqueId = new AtomicInteger(0);

    private static final ThreadLocal<Integer> uniqueNum = new ThreadLocal<>() {
        @Override
        protected Integer initialValue() {
            return uniqueId.getAndIncrement();
        }
    };

    private Path getThreadSafeDir(Path file, int concurrency) {
        return (concurrency == 1)
                ? file
                : file.resolve(String.valueOf(getCurrentThreadId()));
    }

    private static int getCurrentThreadId() {
        return uniqueNum.get();
    }

    //----------misc statics---------------------------------------------------

    public static final String
        CANT_FIND_CLASS       = "Can't find source for class: ",
        LIB_LIST              = " in directory-list: ",
        PATH_TESTCLASS        = "Unable to locate test class directory!?",
        CANT_FIND_LIB         = "Can't find library: ",
        BAD_LIB               = "Bad file for library: ",
        BAD_FILE_IN_LIB       = "Bad file in library: ",
        MIXED_LIB             = "Can't mix packages, user modules, and patches for system module in library: ";
}
