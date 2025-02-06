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

package com.oracle.plugin.jtreg.configuration.producers;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.oracle.plugin.jtreg.configuration.JTRegConfiguration;
import com.oracle.plugin.jtreg.service.JTRegService;
import com.oracle.plugin.jtreg.util.JTRegUtils;

/**
 * This class generates a jtreg configuration from a given file selected in the IDE.
 */
public class JTRegClassConfigurationProducer extends JTRegConfigurationProducer {

    @Override
    protected boolean setupConfigurationFromContext(JTRegConfiguration configuration, ConfigurationContext context, Ref<PsiElement> sourceElement) {
        final Location<PsiElement> contextLocation = context.getLocation();
        assert contextLocation != null;
        final PsiElement element = contextLocation.getPsiElement();

        PsiFile psiFile = contextLocation.getPsiElement().getContainingFile();
        if (psiFile == null ||
                !JTRegUtils.isInJTRegRoot(psiFile.getContainingDirectory()) ||
                (!JTRegUtils.isJTRegTestData(psiFile) &&
                 !JTRegUtils.isTestNGTestData(psiFile))) return false;
        setupConfigurationModule(context, configuration);
        final Module originalModule = configuration.getConfigurationModule().getModule();
        configuration.setAlternativeJrePathEnabled(JTRegService.getInstance(configuration.getProject()).isAlternativeJrePathEnabled());
        configuration.setAlternativeJrePath(JTRegService.getInstance(configuration.getProject()).getAlternativeJrePath());
        configuration.setProgramParameters(JTRegService.getInstance(configuration.getProject()).getJTregOptions());
        configuration.setWorkingDirectory(JTRegService.getInstance(configuration.getProject()).getWorkDir());
        configuration.setRunClass(psiFile.getVirtualFile().getPath());
        configuration.restoreOriginalModule(originalModule);

        configuration.setQuery(getQuery(element));
        configuration.setName(nameForElement(element));

        initBeforeTaskActions(configuration);
        return true;
    }

    private static String nameForElement(PsiElement element) {
        if (element instanceof PsiIdentifier
                && element.getParent() instanceof PsiMethod method) {
            String className = ((PsiClass) method.getParent()).getQualifiedName();
            return className + "::" + method.getName();
        } else if (element instanceof PsiIdentifier
                && element.getParent() instanceof PsiClass cls) {
            return cls.getQualifiedName();
        } else {
            return element.getContainingFile().getName();
        }
    }
}
