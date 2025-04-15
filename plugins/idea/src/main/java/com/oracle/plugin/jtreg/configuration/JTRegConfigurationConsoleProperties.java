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

package com.oracle.plugin.jtreg.configuration;

import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.JavaRerunFailedTestsAction;
import com.intellij.execution.testframework.JavaAwareTestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * This class defines policies to filter out failed tests etc. Currently unused.
 */
public class JTRegConfigurationConsoleProperties extends JavaAwareTestConsoleProperties<JTRegConfiguration> {
    public JTRegConfigurationConsoleProperties(Executor executor, JTRegConfiguration runConfiguration) {
        super("jtreg", runConfiguration, executor);
    }

    @Nullable
    @Override
    public AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
        return new JavaRerunFailedTestsAction(consoleView, this);
    }

    @Override
    public @Nullable SMTestLocator getTestLocator() {
        return JTRegTestLocator.INSTANCE;
    }

    private static class JTRegTestLocator implements SMTestLocator {

        private static final JTRegTestLocator INSTANCE = new JTRegTestLocator();

        // parse our custom 'jtreg://...' location hint emitted by JTRegTestListener
        @Override
        public @NotNull List<Location> getLocation(@NonNls @NotNull String protocol,
                                                   @NonNls @NotNull String path,
                                                   @NonNls @NotNull Project project,
                                                   @NotNull GlobalSearchScope scope) {
            if (!protocol.equals("jtreg")) {
                return List.of();
            }

            String[] pathParts = path.split("::", 2);
            String className = pathParts[0];
            String methodName = null;
            if (pathParts.length > 1) {
                methodName = pathParts[1];
            }
            JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            PsiClass cls = facade.findClass(className, scope);
            if (cls == null) {
                return List.of();
            }

            if (methodName == null) {
                return List.of(PsiLocation.fromPsiElement(cls));
            }

            PsiMethod[] methods = cls.findMethodsByName(methodName, false);
            if (methods.length == 1) {
                return List.of(PsiLocation.fromPsiElement(methods[0]));
            }

            return List.of();
        }
    }
}
