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
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.oracle.plugin.jtreg.configuration.JTRegConfiguration;
import com.oracle.plugin.jtreg.service.JTRegService;
import com.oracle.plugin.jtreg.util.JTRegUtils;
import org.jetbrains.annotations.NotNull;

/**
 * This class generates a jtreg configuration from a given file selected in the IDE.
 */
public class JTRegClassConfigurationProducer extends JTRegConfigurationProducer {

    /**
     * @see JTRegConfigurationProducer#isConfigurationFromContext
     * @see com.intellij.execution.application.AbstractApplicationConfigurationProducer#setupConfigurationFromContext
     * @see com.intellij.execution.testframework.AbstractInClassConfigurationProducer#setupConfigurationFromContext
     * @see com.intellij.psi.util.PsiTreeUtil#isAncestor
     */
    @Override
    protected boolean setupConfigurationFromContext(@NotNull JTRegConfiguration configuration,
                                                    @NotNull ConfigurationContext context,
                                                    @NotNull Ref<PsiElement> sourceElement) {
        Location<PsiElement> contextLocation = context.getLocation();
        assert contextLocation != null;

        PsiElement element = contextLocation.getPsiElement();

        if (!JTRegUtils.isRunnableByJTReg(element)) {
            return false;
        }

        setupConfigurationModule(context, configuration);
        final Module originalModule = configuration.getConfigurationModule().getModule();

        configuration.setAlternativeJrePathEnabled(JTRegService.getInstance(configuration.getProject()).isAlternativeJrePathEnabled());
        configuration.setAlternativeJrePath(JTRegService.getInstance(configuration.getProject()).getAlternativeJrePath());
        configuration.setProgramParameters(JTRegService.getInstance(configuration.getProject()).getJTregOptions());
        configuration.setWorkingDirectory(JTRegService.getInstance(configuration.getProject()).getWorkDir());

        if (element instanceof PsiDirectory runDir) {
            configuration.setPackage(runDir.getVirtualFile().getPath());
        } else {
            PsiFile runFile = (element instanceof PsiFile psiFile) ? psiFile : element.getContainingFile();
            if (null != runFile && null != runFile.getVirtualFile()) {
                configuration.setRunClass(runFile.getVirtualFile().getPath());
            }
        }

        configuration.restoreOriginalModule(originalModule);

        preventRunPriorityLoss(element, sourceElement);

        element = findExactRunElement(element);

        configuration.setQuery(getQuery(element));
        configuration.setName(nameForElement(element));

        initBeforeTaskActions(configuration);
        return true;
    }

    /**
     * Ensures that the Application, TestNG, or JUnit run configuration does not receive a higher priority.
     * This applies when the user attempts to run a file containing a single class for TestNG/JUnit,
     * or a "main" method for the Application run configuration.
     * <p>
     * The class {@link com.intellij.execution.actions.PreferredProducerFind} sorts the applicable runners using
     * {@link com.intellij.execution.actions.ConfigurationFromContext#COMPARATOR},
     * removing more general ones and retaining more specific or equal configurations.
     * <p>
     * When the user tries to run a test on a file, and another type of Run Configuration can intercept the execution,
     * this method sets the {@code PsiClass} element in the {@code sourceElement} reference to ensure priority is retained.
     *
     * @param element       current PSI element
     * @param sourceElement a reference to the source element for the run configuration
     *                      (by default contains the element at caret,
     *                      can be updated by the producer to point to a higher-level element in the tree).
     * @see com.intellij.execution.application.AbstractApplicationConfigurationProducer#setupConfigurationFromContext
     * @see com.intellij.execution.testframework.AbstractInClassConfigurationProducer#setupConfigurationFromContext
     * @see com.intellij.execution.actions.PreferredProducerFind#doGetConfigurationsFromContext
     * @see com.intellij.execution.actions.ConfigurationFromContext#COMPARATOR
     * @see com.intellij.psi.util.PsiTreeUtil#isAncestor
     */
    private void preventRunPriorityLoss(PsiElement element, @NotNull Ref<PsiElement> sourceElement) {
        if (element instanceof PsiClassOwner psiClassOwner) {
            PsiClass[] psiClasses = psiClassOwner.getClasses();
            if (1 == psiClasses.length) {
                sourceElement.set(psiClasses[0]); // for Application/TestNG/JUnit
            } else {
                PsiClass mainClass = ApplicationConfigurationType.getMainClass(element);
                if (null != mainClass) {
                    sourceElement.set(mainClass); // for Application
                }
            }
        }
    }

    /**
     * Generates a name for the run test configuration.
     *
     * @param element the current PSI element.
     * @return One of the following: {"ClassName", "ClassName::TestMethodName"}, or the default: "FileName.java".
     * @see #findExactRunElement(PsiElement)
     */
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
