/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

import java.util.*;
import java.util.stream.Stream;

/**
 * This class contains several helper routines that are used by the jtreg plugin.
 */
public class JTRegUtils {

    /**
     * Are we inside a jtreg test root?
     */
    public static boolean isInJTRegRoot(PsiDirectory dir) {
        return dir != null ?
                isInJTRegRoot(dir.getVirtualFile()) :
                false;
    }

    /**
     * Are we inside a jtreg test root?
     */
    public static boolean isInJTRegRoot(VirtualFile file) {
        return findJTRegRoot(file) != null;
    }

    public static VirtualFile findJTRegRoot(VirtualFile file) {
        while (file != null) {
            if (file.findChild("TEST.ROOT") != null) {
                return file;
            }
            file = file.getParent();
        }
        return null;
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
        if (file instanceof PsiJavaFile) {
            Optional<PsiComment> optHeader = PsiTreeUtil.findChildrenOfType(file, PsiComment.class).stream()
                    .filter(JTRegUtils::hasTestTag).findFirst();
            if (optHeader.isPresent()) {
                PsiComment header = getTestHeader(optHeader.get());
                List<VirtualFile> roots = new ArrayList<>();
                VirtualFile pkgRoot = getPackageRoot(file);
                if (pkgRoot != null) {
                    roots.add(pkgRoot);
                } else {
                    roots.add(file.getVirtualFile().getParent());
                }
                JTRegTagParser.Result result = JTRegTagParser.parseTags(header);
                //watch out for library tags
                List<Tag> libTags = result.getName2Tag().get("library");
                if (libTags != null) {
                    for (Tag libTag : libTags) {
                        String libVal = libTag.getValue();
                        for (String lib : libVal.split(" ")) {
                            VirtualFile libFile;
                            if (lib.startsWith("/")) {
                                //absolute
                                libFile = findJTRegRoot(file.getVirtualFile()).findFileByRelativePath(lib.substring(1));
                            } else {
                                //relative
                                libFile = file.getParent().getVirtualFile().findFileByRelativePath(lib);
                            }
                            if (libFile != null && libFile.exists()) {
                                roots.add(libFile);
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
        return importStatement.getQualifiedName().startsWith("org.testng");
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
}
