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

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JavaRunConfigurationProducerBase;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.impl.AntBeforeRunTask;
import com.intellij.lang.ant.config.impl.AntBeforeRunTaskProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.oracle.plugin.jtreg.configuration.JTRegConfiguration;
import com.oracle.plugin.jtreg.service.JTRegService;
import com.oracle.plugin.jtreg.configuration.JTRegConfigurationType;
import com.oracle.plugin.jtreg.util.JTRegUtils;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static com.oracle.plugin.jtreg.util.JTRegUtils.*;

/**
 * This class serves as a common superclass for both file and folder-based configuration producers.
 */
public abstract class JTRegConfigurationProducer extends JavaRunConfigurationProducerBase<JTRegConfiguration> implements Cloneable {

    public JTRegConfigurationProducer() {
        super(JTRegConfigurationType.getInstance());
    }

    /**
     * @see JTRegClassConfigurationProducer#setupConfigurationFromContext
     */
    @Override
    public boolean isConfigurationFromContext(@NotNull JTRegConfiguration unitConfiguration,
                                              @NotNull ConfigurationContext context) {
        Location<PsiElement> contextLocation = context.getLocation();
        if (contextLocation == null) {
            return false;
        }

        Location<?> location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
        if (location == null) {
            return false;
        }

        PsiElement element = location.getPsiElement();
        element = findExactRunElement(element);

        String contextQuery = getQuery(element);

        PsiFile contextFile = (element instanceof PsiFile psiFile) ? psiFile : element.getContainingFile();
        VirtualFile contextVirtualFile = null != contextFile ? contextFile.getVirtualFile() : null;
        String contextFilePath = null != contextVirtualFile ? contextVirtualFile.getPath() : null;

        String contextDirPath = (element instanceof PsiDirectory d) ? d.getVirtualFile().getPath() : null;

        return NOT_NULLIZED_STRING_EQUALS.test(contextFilePath, unitConfiguration.getRunClass())
                && NOT_NULLIZED_STRING_EQUALS.test(contextDirPath, unitConfiguration.getPackage())
                && NOT_NULLIZED_STRING_EQUALS.test(contextQuery, unitConfiguration.getQuery());
    }

    /**
     * Finds the nearest test element among the parents of the given element:
     * <ol>
     *     <li>Test method</li>
     *     <li>Test class</li>
     *     <li>File</li>
     * </ol>
     * If no test element is found among the parents, returns the given element.
     * <p>
     * An element is considered test-runnable only if all its parent elements,
     * up to the containing file, are also test-runnable.
     * <p>
     * A directory is also considered a test-runnable element.
     *
     * @param element The element for the run configuration (by default, contains the element at the caret).
     * @return The nearest test element found, or {@code element} if no test element is found among the parents.
     * @see JTRegClassConfigurationProducer#nameForElement(PsiElement)
     * @see #getQuery(PsiElement)
     */
    @NotNull
    protected PsiElement findExactRunElement(@NotNull final PsiElement element) {
        PsiElement retval = null;
        for (PsiElement e = element; null != e; e = e.getParent()) {
            if (IS_FILE_OR_DIR_ELEMENT.test(e)) {
                if (null == retval) {
                    retval = e;
                }
                break;
            }

            if (IS_THIRD_PARTY_TEST_ELEMENT.test(e)) {
                final PsiElement identifyingElement = ((PsiNameIdentifierOwner) e).getIdentifyingElement();
                if (null != identifyingElement) { // null for name of the non-test inner class
                    if (null == retval) {
                        // When found, check the left hierarchy up to the class for runnability
                        retval = identifyingElement;
                    }
                } else {
                    retval = null;
                }
            } else {
                retval = null;
            }
        }

        return null != retval ? retval : element;
    }

    @NotNull
    protected static String getQuery(PsiElement element) {
        boolean isJUnit = JTRegUtils.isJUnitTestData(element.getContainingFile());
        boolean isTestNG = JTRegUtils.isTestNGTestData(element.getContainingFile());
        if (element instanceof PsiIdentifier
                && element.getParent() instanceof PsiMethod method) {
            if (isJUnit) {
                return "junit-select:method:" + getJUnitMethodQuery(method);
            } else if (isTestNG) {
                // just the method name for TestNG
                return method.getName();
            }
        } else if (isJUnit && element instanceof PsiIdentifier
                && element.getParent() instanceof PsiClass cls) {
            return "junit-select:class:" + binaryNameFor(cls);
        }
        return "";
    }

    private static String getJUnitMethodQuery(PsiMethod method) {
        StringJoiner paramTypeNames = new StringJoiner(",");
        PsiParameterList params = method.getParameterList();
        for (int i = 0; i < params.getParametersCount(); i++) {
            PsiParameter param = params.getParameter(i);
            PsiType type = param.getType();
            paramTypeNames.add(binaryNameFor(type));
        }
        String className = binaryNameFor(((PsiClass) method.getParent()));
        return className + "#" + method.getName() + '(' + paramTypeNames + ')';
    }

    private static String binaryNameFor(PsiType type) {
        if (type instanceof PsiPrimitiveType) {
            return type.getCanonicalText();
        } else if (type instanceof PsiClassType clsType) {
            return binaryNameFor(clsType.resolve());
        } else if (type instanceof PsiArrayType arrayType) {
            return binaryNameFor(arrayType);
        } else {
            return type.getCanonicalText();
        }
    }

    private static String binaryNameFor(PsiClass cls) {
        // handle nested classes. Convert '.' to '$'
        String nestedName = cls.getName();
        PsiClass current = cls;
        while ((current = current.getContainingClass()) != null) {
            nestedName = current.getName() + "." + nestedName;
        }
        String qualName = cls.getQualifiedName();
        String packageName = qualName.substring(0, qualName.length() - nestedName.length());
        return packageName + nestedName.replace('.', '$');
    }

    private static String binaryNameFor(PsiArrayType arrayType) {
        PsiType component = arrayType.getDeepComponentType();
        String componentName;
        if (component instanceof PsiPrimitiveType primitiveType) {
            componentName = descriptorFor(primitiveType);
        } else if (component instanceof PsiClassType clsType) {
            componentName = "L" + binaryNameFor(clsType.resolve()) + ";";
        } else {
            componentName = component.getCanonicalText();
        }
        return "[".repeat(arrayType.getArrayDimensions()) + componentName;
    }

    private static String descriptorFor(PsiPrimitiveType primitiveType) {
        return switch (primitiveType.getCanonicalText()) {
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> primitiveType.getCanonicalText();
        };
    }

    /**
     * @see ConfigurationFromContext#COMPARATOR
     * @see com.intellij.execution.actions.PreferredProducerFind#doGetConfigurationsFromContext
     */
    @Override
    public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
        RunConfiguration otherCnf = other.getConfiguration();
        return otherCnf instanceof ApplicationConfiguration
                || otherCnf instanceof TestNGConfiguration
                || otherCnf instanceof JUnitConfiguration
                || super.shouldReplace(self, other);
    }

    public void initBeforeTaskActions(JTRegConfiguration configuration) {
        AntConfigurationBase antConfiguration = (AntConfigurationBase)AntConfiguration.getInstance(configuration.getProject());
        antConfiguration.ensureInitialized();
        List<AntBuildTarget> targets = JTRegService.getInstance(configuration.getProject()).getOptTargets(antConfiguration);
        if (!targets.isEmpty()) {
            List<BeforeRunTask> beforeTasks = targets.stream().map(target -> {
                AntBeforeRunTask beforeTask =
                        AntBeforeRunTaskProvider.getProvider(antConfiguration.getProject(), AntBeforeRunTaskProvider.ID)
                                .createTask(configuration);
                beforeTask.setTargetName(target.getName());
                beforeTask.setAntFileUrl(target.getModel().getBuildFile().getVirtualFile().getUrl());
                beforeTask.setEnabled(true);
                return beforeTask;
            }).collect(Collectors.toList());

            RunManagerEx.getInstanceEx(configuration.getProject())
                    .setBeforeRunTasks(configuration, beforeTasks, false);
        }
    }
}
