/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javatest.TestDescription;
import com.sun.javatest.TestEnvironment;
import com.sun.javatest.regtest.RegressionScript.TestClassException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utilities to locate source and class files used by a test.
 */
public class Locations {
    static class LibLocn {
        final String name;
        final File absSrcDir;
        final File absClsDir;

        LibLocn(String name, File absSrcDir, File absClsDir) {
            this.name = name;
            this.absSrcDir = absSrcDir;
            this.absClsDir = absClsDir;
        }

        @Override
        public String toString() {
            return "LibLocn(" + name + ",src:" + absSrcDir + ",cls:" + absClsDir + ")";
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
    private final String relTestDir;
    private final File absBaseSrcDir;
    private final File absTestSrcDir;
    private final File absBaseClsDir;
    private final File absTestClsDir;
    private final List<LibLocn> libList;

    Locations(RegressionEnvironment regEnv, TestDescription td)
            throws TestClassException {
        this.params = regEnv.params;

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
        String libPath = td.getParameter("library");
        for (String lib: StringArray.splitWS(libPath)) {
            boolean absLib = lib.startsWith("/");
            File s = absLib ? absBaseSrcDir : absTestSrcDir;
            File c = absLib ? absBaseClsDir : absTestClsDir;
            libList.add(new LibLocn(lib, normalize(new File(s, lib)),
                    normalize(new File(c, lib))));
        }
    }

    File absTestSrcDir() {
        return absTestSrcDir;
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
            if (f.getName().endsWith(".jar") && f.exists())
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

    List<ClassLocn> locateClasses(String name) throws TestRunException {
        if (name.equals("*"))
            return locateClassesInPackage(null);
        else if (name.endsWith(".*"))
            return locateClassesInPackage(name.substring(0, name.length() - 2));
        else
            return Collections.singletonList(locateClass(name));
    }

    private ClassLocn locateClass(String className) throws TestRunException {
        String relSrc = className.replace('.', File.separatorChar) + ".java";
        String relCls = className.replace('.', File.separatorChar) + ".class";
        File sf, cf;

        // Check testSrcDir
        if ((sf = new File(absTestSrcDir, relSrc)).exists()) {
            cf = new File(absTestClsDir, relCls);
            LibLocn tl = new LibLocn(null, absTestSrcDir, absTestClsDir);
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
                LibLocn tl = new LibLocn(null, absTestSrcDir, absTestClsDir);
                return new ClassLocn(className, tl, sf, cf);
            }
        }

        // create the list of directory names that we looked in and fail
        StringBuilder dirListStr = new StringBuilder();
        dirListStr.append(absTestSrcDir).append(" ");
        for (LibLocn l: libList)
            dirListStr.append(l.absSrcDir).append(" ");
        throw new TestRunException(CANT_FIND_SRC + relSrc +
                                   LIB_LIST + dirListStr);
    }

    private List<ClassLocn> locateClassesInPackage(String packageName) throws TestRunException {
        List<ClassLocn> results = new ArrayList<ClassLocn>();

        // Check testSrcDir
        LibLocn tl = new LibLocn(null, absTestSrcDir, absTestClsDir);
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
            if (!fn.endsWith(".java"))
                continue;
            String cn = fn.substring(fn.length() - 5);
            String className = (packageName == null) ? cn : packageName + "." + cn;
            File cf = new File(pkgClsDir, cn + ".class");
            results.add(new ClassLocn(className, l, sf, cf));
        }
    }

    File absTestSrcFile(File srcFile) {
        if (srcFile.isAbsolute())
            throw new IllegalArgumentException();
        return new File(absTestSrcDir, srcFile.getPath());
    }

    private static File normalize(File f) {
        return new File(f.toURI().normalize());
    }

    //----------thread safety-----------------------------------------------

    private static final AtomicInteger uniqueId = new AtomicInteger(0);

    private static final ThreadLocal<Integer> uniqueNum =
        new ThreadLocal<Integer> () {
            @Override protected Integer initialValue() {
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
        CANT_FIND_SRC         = "Can't find source file: ",
        LIB_LIST              = " in directory-list: ",
        PATH_TESTCLASS        = "Unable to locate test class directory!?";
}
