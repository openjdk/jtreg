/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.plugin.jtreg.util;

import com.intellij.execution.junit.JUnitUtil;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;


/**
 * This class contains several helper routines that are used by the jtreg plugin.
 */
public class JTRegUtils {

    public static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final Logger LOG = Logger.getInstance(JTRegUtils.class);

    /**
     * @param element PSI element
     * @return Whether this element is a class or method and corresponds to any supported third-party test framework.
     */
    public static boolean isThirdPartyTestElement(PsiElement element) {
        return ((element instanceof PsiMethod psiMethod)
                && (TestNGUtil.hasTest(psiMethod) || JUnitUtil.isTestAnnotated(psiMethod)))
                || ((element instanceof PsiClass psiClass)
                && (TestNGUtil.isTestNGClass(psiClass) || JUnitUtil.isTestClass(psiClass)));
    }

    /**
     * Are we inside a jtreg test root?
     */
    public static boolean isInJTRegRoot(PsiDirectory dir) {
        return null != dir && isInJTRegRoot(dir.getVirtualFile());
    }

    /**
     * Are we inside a jtreg test root?
     */
    public static boolean isInJTRegRoot(VirtualFile file) {
        return findJTRegRoot(file) != null;
    }

    /**
     * Given a file, searches up the vfs hierarchy for the closest parent directory containing the
     * associated test suite config (TEST.ROOT).
     * @param file the file
     * @return file referring to the test root directory or null if not found
     */
    public static VirtualFile findJTRegRoot(VirtualFile file) {
        VirtualFile test_root_file = findRootFile(file);
        if (test_root_file != null) {
            return file.getParent();
        }
        return null;
    }

    /**
     * Given a file, searches up the vfs hierarchy for the associated test suite
     * configuration (a TEST.ROOT file in a parent directory).
     * @param file the virtual file
     * @return virtual file referring to TEST.ROOT or null if not found.
     */
    private static VirtualFile findRootFile(VirtualFile file) {
        while (file != null) {
            VirtualFile rootFile = file.findChild("TEST.ROOT");
            if (rootFile != null) {
                return rootFile;
            }
            file = file.getParent();
        }
        return null;
    }

    /**
     * Parse a test suite configuration.
     * @param rootFile a file representing a test suite configuration (TEST.ROOT)
     * @return a Properties object containing the parsed TEST.ROOT
     */
    private static Properties parseTestSuiteConfig(VirtualFile rootFile) {
        Properties prop = null;
        try {
            prop = new Properties();
            InputStream input = rootFile.getInputStream();
            prop.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return prop;
    }

    // cache parsed test configurations.
    private static HashMap<VirtualFile, Properties> _cachedTestConfigs = new HashMap<>();

    /**
     * Parse a test suite configuration.
     * @param rootFile a file representing a test suite configuration (TEST.ROOT)
     * @return a Properties object containing the parsed TEST.ROOT
     */
    private static Properties testSuiteConfigForRootFile(VirtualFile rootFile) {
        Properties p = _cachedTestConfigs.get(rootFile);
        if (p == null) {
            p = parseTestSuiteConfig(rootFile);
            if (p != null) {
                LOG.debug("Parsing test suite config " + rootFile + "...");
                _cachedTestConfigs.put(rootFile, p);
                LOG.debug("Content: " + p);
            }
        } else {
            LOG.debug("Returning cached test suite config for " + rootFile);
        }
        return p;
    }

    /**
     * Is the given file a jtreg test?
     */
    public static boolean isJTRegTestData(Project project, VirtualFile file) {
        return isJTRegTestData(PsiUtil.getPsiFile(project, file));
    }

    /**
     * Is the given file a jtreg test?
     */
    public static boolean isJTRegTestData(PsiFile file) {
        if (file instanceof PsiJavaFile) {
            return PsiTreeUtil.findChildrenOfType(file, PsiComment.class).stream()
                    .anyMatch(JTRegUtils::hasTestTag);
        }
        return false;
    }

    /**
     * Retrieve the source roots associated with a jtreg test.
     */
    public static List<VirtualFile> getTestRoots(Project project, VirtualFile file) {
        return isJTRegTestData(project, file) ?
                getJTRegRoots(PsiUtil.getPsiFile(project, file)) :
                getTestNgRoots(PsiUtil.getPsiFile(project, file));
    }

    /**
     * Retrieve the source roots associated with a testng test.
     */
    public static List<VirtualFile> getTestNgRoots(PsiFile file) {
        VirtualFile pkgRoot = getPackageRoot(file);
        return pkgRoot == null ?
                Collections.singletonList(file.getVirtualFile().getParent()) :
                Collections.singletonList(pkgRoot);
    }

    /**
     * Infer the source root given a package name.
     */
    public static VirtualFile getPackageRoot(PsiFile file) {
        if (file instanceof PsiJavaFile) {
            Optional<PsiPackageStatement> optPackageDecl = PsiTreeUtil.findChildrenOfType(file, PsiPackageStatement.class).stream().findFirst();
            if (optPackageDecl.isPresent()) {
                String[] pkgs = optPackageDecl.get().getPackageName().split("\\.");
                VirtualFile root = file.getVirtualFile();
                for (int i = pkgs.length - 1 ; i >= 0 ; i--) {
                    root = root.getParent();
                    if (!root.getName().equals(pkgs[i])) {
                        return null;
                    }
                }
                return root.getParent();
            }
        }
        return null;
    }

    /**
     * Retrieve the source roots associated with jtreg test with header.
     */
    public static List<VirtualFile> getJTRegRoots(PsiFile file) {
        LOG.debug("JTregRoots for " + file + "...");
        if (file instanceof PsiJavaFile) {
            Optional<PsiComment> optHeader = PsiTreeUtil.findChildrenOfType(file, PsiComment.class).stream()
                    .filter(JTRegUtils::hasTestTag).findFirst();
            if (optHeader.isPresent()) {
                PsiComment header = getTestHeader(optHeader.get());
                List<VirtualFile> roots = new ArrayList<>();
                VirtualFile pkgRoot = getPackageRoot(file);
                if (pkgRoot != null) {
                    LOG.debug("Package root: " + pkgRoot.getParent());
                    roots.add(pkgRoot);
                } else {
                    LOG.debug("Package root not found, adding immediate parent.");
                    roots.add(file.getVirtualFile().getParent());
                }
                JTRegTagParser.Result result = JTRegTagParser.parseTags(header);
                //watch out for library tags
                List<Tag> libTags = result.getName2Tag().get("library");
                if (libTags != null) {
                    for (Tag libTag : libTags) {
                        String libVal = libTag.getValue();
                        for (String lib : libVal.split(" ")) {
                            VirtualFile libFile = null;
                            LOG.debug("Processing @library \"" + lib + "\"...");
                            if (lib.startsWith("/")) {
                                //absolute
                                // Excerpt from jtreg tags specification:
                                // "If an argument begins with '/', it will first be evaluated relative to the root
                                //  directory of the test suite. It is an error if the resulting path is outside the
                                //  test suite."
                                VirtualFile testRootFile = findRootFile(file.getVirtualFile());
                                if (testRootFile != null) {
                                    VirtualFile jtRegRoot = testRootFile.getParent();
                                    libFile = jtRegRoot.findFileByRelativePath(lib.substring(1));
                                    if (libFile != null) {
                                        LOG.debug("Found : " + libFile + " relative to test suite root.");
                                    } else {
                                        // "If the result does not identify an existing directory, it will be further
                                        // evaluated against each entry of a search path in turn, until an existing
                                        // directory is found. The search path is specified by the external.lib.roots
                                        // entry in the test suite configuration files."
                                        LOG.debug("Nothing found relative to test suite root.");
                                        Properties testSuiteConfig = testSuiteConfigForRootFile(testRootFile);
                                        if (testSuiteConfig != null) {
                                            String s = testSuiteConfig.getProperty("external.lib.roots");
                                            if (s != null) {
                                                s = s.trim();
                                                LOG.debug("external.lib.roots = \"" + s + "\"");
                                                // Note: jtreg tag specification for "external.lib.roots" talks about a
                                                // search path with separate segments; however, all usages I see in our
                                                // configurations are single paths, so to keep matters simple I treat it
                                                // as a single path.
                                                // The "external.lib.roots" is relative to the jtreg root
                                                VirtualFile searchPath = jtRegRoot.findFileByRelativePath(s);
                                                if (searchPath != null) {
                                                    libFile = searchPath.findFileByRelativePath(lib);
                                                }
                                            }
                                        }
                                    }
                                }

                            } else {
                                //relative
                                libFile = file.getParent().getVirtualFile().findFileByRelativePath(lib);
                            }
                            if (libFile != null) {
                                LOG.debug("@library \"" + lib + "\" resolves to " + libFile + ".");
                                if (libFile.exists()) {
                                    LOG.debug("which exists.");
                                    roots.add(libFile);
                                } else {
                                    LOG.debug("which does not exists.");
                                }
                            }
                        }
                    }
                }
                return roots;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Is the given file a testng test?
     */
    public static boolean isTestNGTestData(Project project, VirtualFile file) {
        return isTestNGTestData(PsiUtil.getPsiFile(project, file));
    }

    /**
     * Determines whether the given element is a valid test-related element.
     * The element must be one of the following:
     * <ul>
     *     <li>An element inside a test file</li>
     *     <li>A test file</li>
     *     <li>A test directory</li>
     * </ul>
     *
     * <p>Additionally, the following conditions must be met:</p>
     * <ul>
     *     <li>A test file must contain the {@code @test} comment tag.</li>
     *     <li>A test file must be located inside a test directory.</li>
     *     <li>A test directory must have a parent directory that contains a {@code TEST.ROOT} file.</li>
     * </ul>
     *
     * @param element The element for the run configuration (by default, contains the element at the caret).
     * @return {@code true} if the element is a valid test-related element, {@code false} otherwise.
     */
    public static boolean isRunnableByJTReg(@NotNull PsiElement element) {
        PsiFile runFile;
        PsiDirectory runDir;
        if (element instanceof PsiDirectory psiDirectory) {
            runFile = null;
            runDir = psiDirectory;
        } else {
            runFile = (element instanceof PsiFile psiFile) ? psiFile : element.getContainingFile();
            runDir = null != runFile ? runFile.getContainingDirectory() : null;
        }
        return isInJTRegRoot(runDir) && ((element instanceof PsiDirectory) || isJTRegTestData(runFile));
    }

    /**
     * Is the given file a testng test?
     */
    public static boolean isTestNGTestData(PsiFile file) {
//        if (file instanceof PsiJavaFile) {
//            for (PsiClass psiClass : ((PsiJavaFile) file).getClasses()) {
//                if (TestNGUtil.isTestNGClass(psiClass)) return true;
//            }
//        }
        //would be nice to rely on TestNG to do this (see above) but doesn't work as the file is not
        //under test root (yet!) so we use an heuristics instead (look for 'import org.testng')
        if (file instanceof PsiJavaFile) {
            return Stream.of(((PsiJavaFile) file).getImportList().getImportStatements())
                    .anyMatch(JTRegUtils::isTestNGImport);
        }
        return false;
    }

    /**
     * Is the given file a testng test?
     */
    public static boolean isTestNGImport(PsiImportStatement importStatement) {
        String qualifiedName = importStatement.getQualifiedName();
        //qualifiedName can be null if the import statement hasn't been fully written yet
        return qualifiedName != null && qualifiedName.startsWith("org.testng");
    }

    /**
     * Judge whether the given file is a junit test.
     */
    public static boolean isJUnitTestData(Project project, VirtualFile file) {
        return isJUnitTestData(PsiUtil.getPsiFile(project, file));
    }

    /**
     * Judge whether the given file has the junit import statement.
     */
    public static boolean isJUnitTestData(PsiFile file) {
        if (file instanceof PsiJavaFile) {
            return Stream.of(((PsiJavaFile) file).getImportList().getImportStatements())
                    .anyMatch(JTRegUtils::isJunitImport);
        }
        return false;
    }

    /**
     * Judge whether the statement is a junit import statement.
     */
    public static boolean isJunitImport(PsiImportStatement importStatement) {
        String qualifiedName = importStatement.getQualifiedName();
        return qualifiedName != null && qualifiedName.startsWith("org.junit");
    }

    /**
     * Create (if not existing) and get the jtreg project library.
     */
    public static Library createJTRegLibrary(Project project, String jtregDir) {
        return updateJTRegLibrary(project, null, jtregDir);
    }

    /**
     * Update the jtreg project library. The library would be created if it doesn't exist.
     */
    public static Library updateJTRegLibrary(Project project, String oldJtregDir, String newJtregDir) {
        LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
        final LibraryTable.ModifiableModel tableModel = libraryTable.getModifiableModel();
        Library library = tableModel.getLibraryByName("jtreg-libs");
        if (library == null) {
            library = tableModel.createLibrary("jtreg-libs");
        }
        final Library.ModifiableModel libraryModel = library.getModifiableModel();
        String oldDir = "file://" + oldJtregDir + File.separator + "lib";
        if (oldJtregDir != null && !oldJtregDir.isBlank() && libraryModel.isJarDirectory(oldDir, OrderRootType.CLASSES)) {
            libraryModel.removeRoot(oldDir, OrderRootType.CLASSES);
        }
        String newDir = "file://" + newJtregDir + File.separator + "lib";
        if (newJtregDir != null && !newJtregDir.isBlank() && !libraryModel.isJarDirectory(newDir, OrderRootType.CLASSES)) {
            libraryModel.addJarDirectory(newDir, true);
        }
        ApplicationManager.getApplication().runWriteAction(() -> {
            libraryModel.commit();
            tableModel.commit();
        });
        return library;
    }

    /**
     * Does the given file contain a jtreg header?
     */
    private static boolean hasTestTag(PsiElement e) {
        return getTestHeader(e) != null;
    }

    /**
     * Does the given file contain a jtreg header?
     */
    private static PsiComment getTestHeader(PsiElement e) {
        while (e instanceof PsiComment) {
            PsiComment comment = (PsiComment) e;
            if (comment.getText().contains("@test")) {
                return comment;
            }
            e = PsiTreeUtil.skipSiblingsForward(e, PsiWhiteSpace.class);
        }
        return null;
    }

    /**
     * Workaround incompatible signature change from 2016.2 to 2016.3
     */
    public static AntBuildFile[] getAntBuildFiles(AntConfiguration antConfiguration) {
        try {
            Method m = antConfiguration.getClass().getDeclaredMethod("getBuildFiles");
            return (AntBuildFile[])m.invoke(antConfiguration);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
