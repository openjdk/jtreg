/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.javatest.TestDescription;
import com.sun.javatest.regtest.agent.SearchPath;
import com.sun.javatest.regtest.tool.Version;
import com.sun.javatest.regtest.util.StringUtils;

/**
 * Utilities to locate source and class files used by a test.
 */
public class Locations {
    /**
     * Used to report problems that are found.
     */
    public static class Fault extends Exception {
        private static final long serialVersionUID = 1L;
        public Fault(String msg) {
            super(msg);
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
        public static enum Kind { PACKAGE, PRECOMPILED_JAR, SYS_MODULE, USER_MODULE };
        public final String name;
        public final File absSrcDir;
        public final File absClsDir;
        public final Kind kind;

        LibLocn(String name, File absSrcDir, File absClsDir, Kind kind) {
            this.name = name;
            this.absSrcDir = absSrcDir;
            this.absClsDir = absClsDir;
            this.kind = kind;
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
        public final File absSrcFile;
        public final File absClsFile;

        ClassLocn(LibLocn lib, String optModule, String className, File absSrcFile, File absClsFile) {
            this.lib = lib;
            this.optModule = optModule;
            this.className = className;
            this.absSrcFile = absSrcFile;
            this.absClsFile = absClsFile;
        }

        public boolean isUpToDate() {
            return absClsFile.exists()
                    && absClsFile.canRead()
                    && (absClsFile.lastModified() > absSrcFile.lastModified());
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

    private final File absBaseSrcDir;
    private final File absTestSrcDir;
    private final File absBaseClsDir;
    private final File absTestClsDir;
    private final File absTestPatchDir;
    private final File absTestModulesDir;
    private final File absTestWorkDir;
    private final String relLibDir;
    private final List<LibLocn> libList;

    public Locations(RegressionParameters params, TestDescription td)
            throws Fault {
        testSuite = params.getTestSuite();
        systemModules = params.getTestJDK().getSystemModules(params);
        jtpath = params.getJavaTestClassPath();
        testJDK = params.getTestJDK();

        Version v = testSuite.getRequiredVersion();
        boolean useUniqueClassDir = (v.version != null)
                && (v.compareTo(new Version("4.2 b08")) >= 0);

        File relTestFile = td.getRootRelativeFile();
        String relTestDir = relTestFile.getParent();
        if (relTestDir == null)
            relTestDir = "";

        String testName = relTestFile.getName();
        String testId = td.getId();
        String uniqueTestSubDir = testName.replaceAll("(?i)\\.[a-z]+$",
                ((testId == null ? "" : "_" + testId) + ".d"));

        String packageRoot = td.getParameter("packageRoot");
        String relTestSrcDir = relLibDir = (packageRoot != null) ? packageRoot : relTestDir;

        absBaseSrcDir = params.getTestSuite().getRootDir();
        absTestSrcDir = new File(absBaseSrcDir, relTestSrcDir);

        File relTestWorkDir = new File(relTestDir, uniqueTestSubDir);
        absTestWorkDir = params.getWorkDirectory().getFile(relTestWorkDir.getPath());

        absBaseClsDir = getThreadSafeDir(params.getWorkDirectory().getFile("classes"),
                params.getConcurrency());
        String relTestClsDir = (packageRoot != null) ? packageRoot
                : useUniqueClassDir ? new File(relTestDir, uniqueTestSubDir).getPath()
                : relTestDir;
        absTestClsDir = new File(absBaseClsDir, relTestClsDir);

        // The following assumes we will never have test code in a package
        // or subpackage beginning patches or modules when we also have
        // test patches or test modules. If that becomes not true, then
        // we should use another subdir (classes?) for the classes on the
        // classpath, so that classes, modules and patches are sibling
        // directories.
        absTestPatchDir = new File(absTestClsDir, "patches");
        absTestModulesDir = new File(absTestClsDir, "modules");

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
            checkLibPath(libTail);
            if (new File(absBaseSrcDir, libTail).exists()) {
                return createLibLocn(lib, absBaseSrcDir, absBaseClsDir);
            } else {
                try {
                    for (File extRoot: testSuite.getExternalLibRoots(td)) {
                        if (new File(extRoot, libTail).exists()) {
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
                File dir = null;
                if (name.equals("java.home")) {
                    dir = testJDK.getFile();
                } else if (name.equals("jtreg.home")) {
                    dir = jtpath.asList().get(0).getParentFile().getParentFile();
                }
                if (dir != null) {
                    String libTail = lib.substring(end + 2);
                    File absLib = new File(dir, libTail);
                    if (absLib.exists())
                        return new LibLocn(lib, null, absLib, LibLocn.Kind.PRECOMPILED_JAR);
                }
            }
        } else {
            checkLibPath(relLibDir.replace(File.separatorChar, '/') + "/" + lib);
            if (new File(absTestSrcDir, lib).exists())
                return createLibLocn(lib, absTestSrcDir, new File(absBaseClsDir, relLibDir));
        }
        throw new Fault(CANT_FIND_LIB + lib);
    }

    private void checkLibPath(String lib) throws Fault {
        try {
            String l = new URI(lib).normalize().toString();
            if (l.equals("..") || l.startsWith("../"))
                throw new Fault("effective library path is outside the test suite: " + l);
        } catch (URISyntaxException e) {
            throw new Fault("invalid library path: " + lib);
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
    private LibLocn createLibLocn(String lib, File absBaseSrcDir, File absBaseClsDir) throws Fault {
        String relLib = (lib.startsWith("/") ? lib.substring(1) : lib);
        File absLib = normalize(new File(absBaseSrcDir, relLib));
        if (absLib.isFile() && absLib.getName().endsWith(".jar")) {
            return new LibLocn(lib, null, absLib, LibLocn.Kind.PRECOMPILED_JAR);
        } else {
            if (!absLib.isDirectory())
                throw new Fault(BAD_LIB + lib);
            File absLibSrcDir = absLib;
            File absLibClsDir = normalize(new File(absBaseClsDir, lib));
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
    public Set<LibLocn.Kind> getDirKinds(File absSrcDir) {
        Set<LibLocn.Kind> kinds = EnumSet.noneOf(LibLocn.Kind.class);
        for (File f: absSrcDir.listFiles()) {
            if (f.isDirectory()) {
                if (isSystemModule(f.getName())) {
                    kinds.add(LibLocn.Kind.SYS_MODULE);
                } else if (new File(f, "module-info.java").exists()) {
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
    public LibLocn.Kind getDirKind(File absSrcDir) throws Fault {
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
     * Gets the path of the test source directory.
     * @return the path
     */
    public File absTestSrcDir() {
        return absTestSrcDir;
    }

    /**
     * Gets the path of the test source directory, or to a module within it.
     * @param optModule the name of the module, or null for "no module"
     * @return the path
     */
    public File absTestSrcDir(String optModule) {
        return getFile(absTestSrcDir, optModule);
    }

    /**
     * Gets the path of a source file in the test source directory or a module within it.
     * @param optModule the name of the module, or null for "no module"
     * @param srcFile the file
     * @return the path
     * @throws IllegalArgumentException if the path is not a relative path
     */
    public File absTestSrcFile(String optModule, File srcFile) {
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
    public List<File> absTestSrcPath() {
        List<File> list = new ArrayList<>();
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
    public List<File> absLibSrcList(LibLocn.Kind kind) {
        List<File> list = new ArrayList<>();
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
    public List<File> absLibSrcJarList() {
        List<File> list = new ArrayList<>();
        for (LibLocn l: libList) {
            if (l.kind == LibLocn.Kind.PRECOMPILED_JAR) {
                File f = l.absClsDir;
                if (f.isFile() && f.getName().endsWith(".jar") && f.exists())
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
    public File absBaseClsDir() {
        return absBaseClsDir;
    }

    /**
     * Gets the directory for the compiled classes of a test in the unnamed module.
     * @return the directory
     */
    public File absTestClsDir() {
        return absTestClsDir;
    }

    /**
     * Gets the directory for the compiled classes in either the unnamed module or
     * a named module.
     * @param optModule the name of the module, or null for the unnamed module
     * @return the directory
     */
    public File absTestClsDir(String optModule) {
        if (optModule == null) {
            return absTestClsDir;
        } else if (isSystemModule(optModule)) {
            return new File(absTestPatchDir(), optModule);
        } else {
            return new File(absTestModulesDir(), optModule);
        }
    }

    /**
     * Gets the search path for the classes for a test, consisting of the test class directory,
     * and the class directories of all libraries of PACKAGE kind.
     * @return the search path
     */
    // (just) used to set test.class.path or TESTCLASSPATH
    public List<File> absTestClsPath() {
        List<File> list = new ArrayList<>();
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
    public List<File> absLibClsList(LibLocn.Kind kind) {
        List<File> list = new ArrayList<>();
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
    public File absTestWorkFile(String name) {
        return new File(absTestWorkDir, name);
    }

    /**
     * Gets the directory in which to store the compiled classes of any user-defined
     * modules for a test.
     * @return the directory
     */
    public File absTestModulesDir() {
        return absTestModulesDir;
    }

    /**
     * Gets the directory in which to store the compiled classes to patch system
     * modules for a test.
     * @return the patch directory
     */
    public File absTestPatchDir() {
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
            if (getFile(absTestSrcDir, moduleName).exists()) {
                list.add(new LibLocn(null, absTestSrcDir, absTestPatchDir(), LibLocn.Kind.SYS_MODULE));
            }
            for (LibLocn l : libList) {
                if (l.kind == LibLocn.Kind.SYS_MODULE && getFile(l.absSrcDir, moduleName).exists()) {
                    list.add(l);
                }
            }
            return list;
        } else {
            if (getFile(absTestSrcDir, moduleName).exists()) {
                return Collections.singletonList(
                        new LibLocn(null, absTestSrcDir, absTestModulesDir(), LibLocn.Kind.USER_MODULE));
            }
            for (LibLocn l : libList) {
                if (l.kind == LibLocn.Kind.USER_MODULE && getFile(l.absSrcDir, moduleName).exists()) {
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
            File sf, cf;

            if ((sf = getFile(locn.absSrcDir, optModule, relSrc)).exists()) {
                cf = getFile(locn.absClsDir, optModule, relCls);
                return new ClassLocn(locn, optModule, className, sf, cf);
            }

            // Special case for file to be directly in the test dir
            if (locn.name == null && optModule == null) {
                int sep = relSrc.lastIndexOf(File.separatorChar);
                if (sep >= 0) {
                    String baseName = relSrc.substring(sep + 1);
                    if ((sf = new File(absTestSrcDir, baseName)).exists()) {
                        cf = new File(absTestClsDir, relCls);
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

        File pkgSrcDir, pkgClsDir;
        if (optPackage == null) {
            pkgSrcDir = getFile(l.absSrcDir, optModule);
            pkgClsDir = getFile(l.absClsDir, optModule);
        } else {
            String p = optPackage.replace('.', File.separatorChar);
            pkgSrcDir = getFile(l.absSrcDir, optModule, p);
            pkgClsDir = getFile(l.absClsDir, optModule, p);
        }

        if (!pkgSrcDir.isDirectory())
            return;

        for (File sf: pkgSrcDir.listFiles()) {
            String fn = sf.getName();
            if (sf.isDirectory()) {
                if (recursive) {
                    String subpkg = (optPackage == null) ? fn : optPackage + "." + fn;
                    locateClassesInPackage(l, optModule, subpkg, true, results);
                }
            } else if (sf.isFile() && hasExtn(fn, extns)) {
                String cn = fn.substring(0, fn.lastIndexOf("."));
                String className = (optPackage == null) ? cn : optPackage + "." + cn;
                File cf = new File(pkgClsDir, cn + ".class");
                results.add(new ClassLocn(l, optModule, className, sf, cf));
            }
        }
    }

    private static final String[] extns = { ".java", ".jasm", ".jcod" };

    private File getFile(File absBaseDir, String optModule) {
        return (optModule == null) ? absBaseDir : new File(absBaseDir, optModule);
    }

    private File getFile(File absBaseDir, String optModule, String relFile) {
        return new File(getFile(absBaseDir, optModule), relFile);
    }

    private static File normalize(File f) {
        return new File(f.toURI().normalize());
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

    private static final ThreadLocal<Integer> uniqueNum = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return uniqueId.getAndIncrement();
        }
    };

    private File getThreadSafeDir(File file, int concurrency) {
        return (concurrency == 1)
                ? file
                : new File(file, String.valueOf(getCurrentThreadId()));
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
