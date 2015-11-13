/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.javatest.TestDescription;
import com.sun.javatest.TestEnvironment;
import com.sun.javatest.regtest.RegressionScript.TestClassException;

/**
 * Utilities to locate source and class files used by a test.
 */
public class Locations {
    static class LibLocn {
        final String name;
        final File absSrcDir;
        final File absClsDir;
        final boolean modular;

        LibLocn(String name, File absSrcDir, File absClsDir, boolean modular) {
            this.name = name;
            this.absSrcDir = absSrcDir;
            this.absClsDir = absClsDir;
            this.modular = modular;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof LibLocn) {
                LibLocn l = (LibLocn) other;
                return (name == null ? l.name == null : name.equals(l.name)
                        && absSrcDir.equals(l.absSrcDir)
                        && absClsDir.equals(l.absClsDir)
                        && modular == l.modular);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return ((name == null ? 0 : name.hashCode()) << 7)
                    + (absSrcDir.hashCode() << 5)
                    + (absClsDir.hashCode() << 3)
                    + (modular ? 1 : 2);
        }

        @Override
        public String toString() {
            return "LibLocn(" + name + ",src:" + absSrcDir + ",cls:" + absClsDir
                    + (modular ? ",mod" : "");
        }
    }

    static class ClassLocn {
        final String name;
        final LibLocn lib;
        final File absSrcFile;
        final File absClsFile;

        ClassLocn(String name, LibLocn lib, File absSrcFile, File absClsFile) {
            this.name = name;
            this.lib = lib;
            this.absSrcFile = absSrcFile;
            this.absClsFile = absClsFile;
        }

        boolean isUpToDate() {
            return absClsFile.exists()
                    && absClsFile.canRead()
                    && (absClsFile.lastModified() > absSrcFile.lastModified());
        }
     }

    private final RegressionParameters params;
    private final Map<String,String> availModules;
    private final String relTestDir;
    private final File absBaseSrcDir;
    private final File absTestSrcDir;
    private final File absBaseClsDir;
    private final File absTestClsDir;
    private final File absTestWorkDir;
    private final List<LibLocn> libList;

    Locations(RegressionEnvironment regEnv, TestDescription td)
            throws TestClassException {
        this.params = regEnv.params;
        availModules = params.getTestJDK().getModules(params.getTestVMJavaOptions());

        String packageRoot = td.getParameter("packageRoot");
        if (packageRoot != null) {
            relTestDir = packageRoot;
        } else {
            String d = td.getRootRelativeFile().getParent();
            if (d == null)
                relTestDir = "";
            else
                relTestDir = d;
        }

        absBaseSrcDir = params.getTestSuite().getRootDir();
        absTestSrcDir = new File(absBaseSrcDir, relTestDir);

        String testWorkDir = td.getRootRelativeFile().getPath().replaceAll("(?i)\\.[a-z]+", "");
        String id = td.getId();
        if (id != null)
            testWorkDir += "_" + id;
        testWorkDir += ".d";
        absTestWorkDir = params.getWorkDirectory().getFile(testWorkDir);

        try {
            String[] testClsDir = regEnv.lookup("testClassDir");
            if (testClsDir == null || testClsDir.length != 1)
                throw new TestClassException(PATH_TESTCLASS);
            absBaseClsDir = getThreadSafeDir(testClsDir[0]);
            absTestClsDir = new File(absBaseClsDir, relTestDir);
        } catch (TestEnvironment.Fault e) {
            throw new TestClassException(PATH_TESTCLASS);
        }

        libList = new ArrayList<LibLocn>();
        String libs = td.getParameter("library");
        for (String lib: StringUtils.splitWS(libs)) {
            libList.add(getLibLocn(td, lib));
        }
    }

    private LibLocn getLibLocn(TestDescription td, String lib) throws TestClassException {
        if (lib.startsWith("/")) {
            if (new File(absBaseSrcDir, lib).exists())
                return createLibLocn(lib, absBaseSrcDir, absBaseClsDir);
            else {
                try {
                    for (File extRoot: params.getTestSuite().getExternalLibRoots(td)) {
                        if (new File(extRoot, lib).exists()) {
                            // since absBaseSrcDir/lib does not exist, we can safely
                            // use absBaseClsDir/lib for the compiled classes
                            return createLibLocn(lib, extRoot, absBaseClsDir);
                        }
                    }
                } catch (RegressionTestSuite.Fault e) {
                    throw new TestClassException(CANT_FIND_LIB + e);
                }
            }
        } else {
            if (new File(absTestSrcDir, lib).exists())
                return createLibLocn(lib, absTestSrcDir, absTestClsDir);
        }
        throw new TestClassException(CANT_FIND_LIB + lib);
    }

    private LibLocn createLibLocn(String lib, File absSrcDir, File absClsDir) {
        boolean modular = false;
        File absLibSrcDir = normalize(new File(absSrcDir, lib));
        if (absLibSrcDir.isDirectory()) {
            for (File f: absLibSrcDir.listFiles()) {
                if (f.isDirectory()) {
                    if (isSystemModule(f.getName()) || new File(f, "module-info.java").exists()) {
                        modular = true;
                        break;
                    }
                }
            }
        }
        File absLibClsDir = normalize(new File(absClsDir, lib));
        return new LibLocn(lib, absLibSrcDir, absLibClsDir, modular);
    }

    boolean isSystemModule(String name) {
        return (availModules != null) && availModules.containsKey(name);
    }

    File absTestSrcDir() {
        return absTestSrcDir;
    }

    File absTestSrcDir(String module) {
        return (module == null) ? absTestSrcDir : new File(absTestSrcDir, module);
    }

    List<File> absTestSrcPath() {
        List<File> list = new ArrayList<File>();
        list.add(absTestSrcDir);
        for (LibLocn l: libList)
            list.add(l.absSrcDir);
        return list;
    }

    List<File> absSrcLibList() {
        List<File> list = new ArrayList<File>();
        for (LibLocn l: libList)
            list.add(l.absSrcDir);
        return list;
    }

    List<File> absSrcJarLibList() {
        List<File> list = new ArrayList<File>();
        for (LibLocn l: libList) {
            File f = l.absSrcDir;
            if (f.isFile() && f.getName().endsWith(".jar") && f.exists())
                list.add(f);
        }
        return list;
    }

    File absBaseClsDir() {
        return absBaseClsDir;
    }

    File absTestClsDir() {
        return absTestClsDir;
    }

    File absTestClsDir(String module) {
        // TODO: for now, assume use of patch dir
        return (module == null) ? absTestClsDir : new File(absTestPatchDir(), module);
    }

    List<File> absTestClsPath() {
        List<File> list = new ArrayList<File>();
        list.add(absTestClsDir);
        for (LibLocn l: libList)
            list.add(l.absClsDir);
        return list;
    }

    List<File> absClsLibList() {
        List<File> list = new ArrayList<File>();
        for (LibLocn l: libList)
            list.add(l.absClsDir);
        return list;
    }

    File absTestWorkFile(String name) {
        return new File(absTestWorkDir, name);
    }

    File absTestModulesDir() {
        return absTestWorkFile("modules");
    }

    File absTestPatchDir() {
        return absTestWorkFile("patch");
    }

    List<ClassLocn> locateClasses(String name) throws TestRunException {
        if (name.equals("*"))
            return locateClassesInPackage(null);
        else if (name.endsWith(".*"))
            return locateClassesInPackage(name.substring(0, name.length() - 2));
        else
            return Collections.singletonList(locateClass(name));
    }

    private static final String[] extns = { ".java", ".jasm", ".jcod" };

    private ClassLocn locateClass(String className) throws TestRunException {
        for (String e: extns) {
            String relSrc = className.replace('.', File.separatorChar) + e;
            String relCls = className.replace('.', File.separatorChar) + ".class";
            File sf, cf;

            // Check testSrcDir
            if ((sf = new File(absTestSrcDir, relSrc)).exists()) {
                cf = new File(absTestClsDir, relCls);
                LibLocn tl = new LibLocn(null, absTestSrcDir, absTestClsDir, false);
                return new ClassLocn(className, tl, sf, cf);
            }

            // Check lib list
            for (LibLocn l: libList) {
                if ((sf = new File(l.absSrcDir, relSrc)).exists()) {
                    cf = new File(l.absClsDir, relCls);
                    return new ClassLocn(className, l, sf, cf);
                }
            }

            // Check for file to be directly in the test dir
            int sep = relSrc.lastIndexOf(File.separatorChar);
            if (sep >= 0) {
                String baseName = relSrc.substring(sep + 1);
                if ((sf = new File(absTestSrcDir, baseName)).exists()) {
                    cf = new File(absTestClsDir, relCls);
                    LibLocn tl = new LibLocn(null, absTestSrcDir, absTestClsDir, false);
                    return new ClassLocn(className, tl, sf, cf);
                }
            }
        }

        // create the list of directory names that we looked in and fail
        StringBuilder dirListStr = new StringBuilder();
        dirListStr.append(absTestSrcDir).append(" ");
        for (LibLocn l: libList)
            dirListStr.append(l.absSrcDir).append(" ");
        throw new TestRunException(CANT_FIND_CLASS + className +
                                   LIB_LIST + dirListStr);
    }

    private List<ClassLocn> locateClassesInPackage(String packageName) throws TestRunException {
        List<ClassLocn> results = new ArrayList<ClassLocn>();

        // Check testSrcDir
        LibLocn tl = new LibLocn(null, absTestSrcDir, absTestClsDir, false);
        locateClassesInPackage(packageName, tl, results);

        // Check lib list
        for (LibLocn l: libList) {
            locateClassesInPackage(packageName, l, results);
        }

        return results;
    }

    private void locateClassesInPackage(
            String packageName, LibLocn l, List<ClassLocn> results)
            throws TestRunException {

        File pkgSrcDir, pkgClsDir;
        if (packageName == null) {
            pkgSrcDir = l.absSrcDir;
            pkgClsDir = l.absClsDir;
        } else {
            String p = packageName.replace('.', File.separatorChar);
            pkgSrcDir = new File(l.absSrcDir, p);
            pkgClsDir = new File(l.absClsDir, p);
        }

        if (!pkgSrcDir.isDirectory())
            return;

        for (File sf: pkgSrcDir.listFiles()) {
            if (!sf.isFile())
                continue;
            String fn = sf.getName();
            if (!(fn.endsWith(".java") || fn.endsWith(".jasm") || fn.endsWith(".jcod")))
                continue;
            String cn = fn.substring(0, fn.length() - 5);
            String className = (packageName == null) ? cn : packageName + "." + cn;
            File cf = new File(pkgClsDir, cn + ".class");
            results.add(new ClassLocn(className, l, sf, cf));
        }
    }

    File absTestSrcFile(String module, File srcFile) {
        if (srcFile.isAbsolute())
            throw new IllegalArgumentException();
        if (module != null) {
            return new File(new File(absTestSrcDir, module), srcFile.getPath());
        }
        return new File(absTestSrcDir, srcFile.getPath());
    }

    private static File normalize(File f) {
        return new File(f.toURI().normalize());
    }

    //----------thread safety-----------------------------------------------

    private static final AtomicInteger uniqueId = new AtomicInteger(0);

    private static final ThreadLocal<Integer> uniqueNum = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return uniqueId.getAndIncrement();
        }
    };

    private File getThreadSafeDir(String name) {
        return (params.getConcurrency() == 1)
                ? new File(name)
                : new File(name, String.valueOf(getCurrentThreadId()));
    }

    private static int getCurrentThreadId() {
        return uniqueNum.get();
    }

    //----------misc statics---------------------------------------------------

    public static final String
        CANT_FIND_CLASS       = "Can't find source for class: ",
        LIB_LIST              = " in directory-list: ",
        PATH_TESTCLASS        = "Unable to locate test class directory!?",
        CANT_FIND_LIB         = "Can't find library: ";
}
