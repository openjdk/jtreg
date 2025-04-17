/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.intellij.execution.Location;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

public class JTRegIterationLocation extends Location<PsiElement> {

    private final Location<PsiElement> delegate;
    private final int testIteration;
    private final String iterationName;

    public JTRegIterationLocation(Location<PsiElement> delegate, int testIteration, String iterationName) {
        this.delegate = delegate;
        this.testIteration = testIteration;
        this.iterationName = iterationName;
    }

    public int getIteration() {
        return testIteration;
    }

    public String getIterationName() {
        return iterationName;
    }

    @Override
    public @NotNull PsiElement getPsiElement() {
        return delegate.getPsiElement();
    }

    @Override
    public @NotNull Project getProject() {
        return delegate.getProject();
    }

    @Override
    public @NotNull <T extends PsiElement> Iterator<Location<T>> getAncestors(Class<T> aClass, boolean b) {
        return delegate.getAncestors(aClass, b);
    }

    @Override
    public @Nullable Module getModule() {
        return delegate.getModule();
    }
}
