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
import com.intellij.execution.Location;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.JavaRunConfigurationProducerBase;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.impl.AntBeforeRunTask;
import com.intellij.lang.ant.config.impl.AntBeforeRunTaskProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.oracle.plugin.jtreg.configuration.JTRegConfiguration;
import com.oracle.plugin.jtreg.configuration.JTRegIterationLocation;
import com.oracle.plugin.jtreg.service.JTRegService;
import com.oracle.plugin.jtreg.configuration.JTRegConfigurationType;
import com.oracle.plugin.jtreg.util.JTRegUtils;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static com.oracle.plugin.jtreg.util.JTRegUtils.*;

/**
 * This class is used to generate a run configuration for JTReg for the file or directory selected in the IDE.
 */
public class JTRegConfigurationProducer extends JavaRunConfigurationProducerBase<JTRegConfiguration> {

    public JTRegConfigurationProducer() {
        super(JTRegConfigurationType.getInstance());
    }

    /**
     * @see #setupConfigurationFromContext
     * @see com.intellij.execution.application.AbstractApplicationConfigurationProducer#isConfigurationFromContext
     * @see com.intellij.execution.testframework.AbstractInClassConfigurationProducer#isConfigurationFromContext
     */
    @Override
    protected boolean setupConfigurationFromContext(@NotNull JTRegConfiguration configuration,
                                                    @NotNull ConfigurationContext context,
                                                    @NotNull Ref<PsiElement> sourceElement) {
        Location<PsiElement> contextLocation = context.getLocation();
        if (null == contextLocation) {
            return false;
        }

        PsiElement element = contextLocation.getPsiElement();

        if (!JTRegUtils.isRunnableByJTReg(element)) {
            return false;
        }

        setupConfigurationModule(context, configuration);

        configuration.setAlternativeJrePathEnabled(JTRegService.getInstance(configuration.getProject()).isAlternativeJrePathEnabled());
        configuration.setAlternativeJrePath(JTRegService.getInstance(configuration.getProject()).getAlternativeJrePath());
        configuration.setProgramParameters(JTRegService.getInstance(configuration.getProject()).getJTregOptions());
        configuration.setWorkingDirectory(JTRegService.getInstance(configuration.getProject()).getWorkDir());

        if (element instanceof PsiDirectory runDir) {
            configuration.setPackage(runDir.getVirtualFile().getPath());
            configuration.setName(nameForElement(element));
        } else {
            PsiFile runFile = (element instanceof PsiFile psiFile) ? psiFile : element.getContainingFile();
            if (null != runFile && null != runFile.getVirtualFile()) {
                configuration.setRunClass(runFile.getVirtualFile().getPath());
            } else {
                return false;
            }

            preventRunPriorityLoss(element, sourceElement);
            element = findExactRunElement(element);
            if (contextLocation instanceof JTRegIterationLocation iterationLocation) {
                configuration.setQuery(getQuery(element, iterationLocation.getIteration()));
                configuration.setName(nameForElement(element) + " [" + iterationLocation.getIterationName() + "]");
            } else {
                configuration.setQuery(getQuery(element, -1));
                configuration.setName(nameForElement(element));
            }
        }

        initBeforeTaskActions(configuration);
        return true;
    }

    /**
     * Ensures that the Application, TestNG, or JUnit run configuration does not receive a higher priority.
     * This applies when the user attempts to run a file containing a single class for TestNG/JUnit,
     * or a "main" method for the Application run configuration.
     * <p>
     * The class {@link com.intellij.execution.actions.PreferredProducerFind} sorts the applicable runners using
     * {@link com.intellij.execution.actions.ConfigurationFromContext#COMPARATOR}.
     * This comparator prefers configuration A over configuration B
     * when the source element of A is nested within B in the PSI tree.
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
     * @return One of the following: {"ClassName", "ClassName::TestMethodName"}, or the default "FileName.java".
     * @see #findExactRunElement(PsiElement)
     */
    @Nullable("when null, IDEA automatically assigns the default name: 'Unnamed'")
    private String nameForElement(@NotNull PsiElement element) {
        switch (element) {
            case PsiFileSystemItem psiFileOrDir -> {
                return psiFileOrDir.getName();
            }
            case PsiClass psiClass -> {
                return psiClass.getQualifiedName();
            }
            case PsiMethod psiMethod -> {
                String className = ((PsiClass) psiMethod.getParent()).getQualifiedName();
                return className + "::" + psiMethod.getName();
            }
            default -> {
                PsiFile psiFile = element.getContainingFile();
                return null != psiFile ? psiFile.getName() : null;
            }
        }
    }

    /**
     * @see #setupConfigurationFromContext
     * @see com.intellij.execution.application.AbstractApplicationConfigurationProducer#isConfigurationFromContext
     * @see com.intellij.execution.testframework.AbstractInClassConfigurationProducer#isConfigurationFromContext
     */
    @Override
    public boolean isConfigurationFromContext(@NotNull JTRegConfiguration unitConfiguration,
                                              @NotNull ConfigurationContext context) {
        Location<PsiElement> contextLocation = context.getLocation();
        if (contextLocation == null) {
            return false;
        }

        PsiElement element = findExactRunElement(contextLocation.getPsiElement());

        int iteration = contextLocation instanceof JTRegIterationLocation il ? il.getIteration() : -1;
        String contextQuery = getQuery(element, iteration);

        PsiFile contextFile = (element instanceof PsiFile psiFile) ? psiFile : element.getContainingFile();
        VirtualFile contextVirtualFile = null != contextFile ? contextFile.getVirtualFile() : null;
        String contextFilePath = null != contextVirtualFile ? contextVirtualFile.getPath() : null;

        String contextDirPath = (element instanceof PsiDirectory d) ? d.getVirtualFile().getPath() : null;

        return Comparing.strEqual(contextFilePath, unitConfiguration.getRunClass())
                && Comparing.strEqual(contextDirPath, unitConfiguration.getPackage())
                && Comparing.strEqual(contextQuery, unitConfiguration.getQuery());
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
     * @see #nameForElement(PsiElement)
     * @see #getQuery(PsiElement,int)
     */
    @NotNull
    private PsiElement findExactRunElement(@NotNull PsiElement element) {
        PsiElement retval = null;
        for (PsiElement e = element; null != e; e = e.getParent()) {
            if (e instanceof PsiFileSystemItem) {
                if (null == retval) {
                    retval = e;
                }
                break;
            }

            if (isThirdPartyTestElement(e)) {
                if (null == retval) {
                    // When found, check the rest of the hierarchy up to the class for runnability
                    retval = e;
                }
            } else {
                retval = null;
            }
        }

        return null != retval ? retval : element;
    }

    @NotNull
    private static String getQuery(PsiElement element, int iteration) {
        if (element instanceof PsiMethod psiMethod) {
            if (JUnitUtil.isTestAnnotated(psiMethod)) {
                String query = "junit-select:";
                if (iteration != -1) {
                    query += "iteration:";
                }
                query += "method:" + getJUnitMethodQuery(psiMethod);
                if (iteration != -1) {
                    query += "[" + iteration + "]";
                }
                return query;
            } else if (TestNGUtil.hasTest(psiMethod)) {
                // just the method name for TestNG
                return psiMethod.getName();
            }
        } else if (element instanceof PsiClass psiClass && JUnitUtil.isTestClass(psiClass)) {
            return "junit-select:class:" + binaryNameFor(psiClass);
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
        Project project = configuration.getProject();
        AntConfigurationBase antConfiguration = (AntConfigurationBase)AntConfiguration.getInstance(project);
        antConfiguration.ensureInitialized();
        List<AntBuildTarget> targets = JTRegService.getInstance(project).getOptTargets(antConfiguration);
        if (!targets.isEmpty()) {
            List<BeforeRunTask> beforeTasks = targets.stream().map(target -> {
                AntBeforeRunTask beforeTask =
                        AntBeforeRunTaskProvider.getProvider(project, AntBeforeRunTaskProvider.ID)
                                .createTask(configuration);
                beforeTask.setTargetName(target.getName());
                beforeTask.setAntFileUrl(target.getModel().getBuildFile().getVirtualFile().getUrl());
                beforeTask.setEnabled(true);
                return beforeTask;
            }).collect(Collectors.toList());

            RunManagerEx.getInstanceEx(project)
                    .setBeforeRunTasks(configuration, beforeTasks, false);
        }
    }
}
