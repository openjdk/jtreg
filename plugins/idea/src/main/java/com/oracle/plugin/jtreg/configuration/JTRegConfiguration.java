/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.oracle.plugin.jtreg.configuration.ui.JTRegConfigurable;
import com.oracle.plugin.jtreg.service.JTRegService;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The jtreg configuration.
 */
public class JTRegConfiguration extends JavaTestConfigurationBase {
    private String jtregOptions;
    private boolean alternativeJrePathEnabled;
    private String alternativeJrePath;
    private String file;
    private String query;
    private String directory;
    private String workDirectory;

    public JTRegConfiguration(Project project, ConfigurationFactory configurationFactory) {
        super("jtreg", new JavaRunConfigurationModule(project, false), configurationFactory);
    }

    @NotNull
    public String getFrameworkPrefix() {
        return "jtreg";
    }

    @Override
    public void setEnvs(Map<String, String> map) {
    }

    @NotNull
    @Override
    public Map<String, String> getEnvs() {
        return Collections.emptyMap();
    }

    @Override
    public void setPassParentEnvs(boolean b) {
    }

    @Override
    public boolean isPassParentEnvs() {
        return false;
    }

    @Override
    public Collection<Module> getValidModules() {
        if (file != null) {
            VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(file);
            if (vf != null) {
                return Collections.singletonList(ModuleUtilCore.findModuleForFile(vf, getProject()));
            }
        }
        return Arrays.asList(ModuleManager.getInstance(getProject()).getModules());
    }

    @Nullable
    @Override
    public RefactoringElementListener getRefactoringElementListener(PsiElement psiElement) {
        return null;
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
        super.readExternal(element);
        XmlSerializer.deserializeInto(this, element);
    }


    @Override
    public void writeExternal(Element element) throws WriteExternalException {
        super.writeExternal(element);

        XmlSerializer.serializeInto(this, element);
    }

    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        SettingsEditorGroup<JTRegConfiguration> group = new SettingsEditorGroup<>();
        group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new JTRegConfigurable<>(getProject()));
        //provides e.g. coverage tab
        JavaRunConfigurationExtensionManager.getInstance().appendEditors(this, group);
        //if some logging would be interesting to have aside with console
        group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());
        return group;
    }

    @Nullable
    @Override
    public JTRegConfigurationRunnableState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment executionEnvironment) throws ExecutionException {
        return new JTRegConfigurationRunnableState(this, executionEnvironment);
    }

    @Override
    public SMTRunnerConsoleProperties createTestConsoleProperties(Executor executor) {
        //produces some additional benefits for rerun failed tests, etc
        return new JTRegConfigurationConsoleProperties(executor, JTRegConfiguration.this);
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        super.checkConfiguration();
        String fileOrDirectory = file != null ? file : directory;
        if (fileOrDirectory != null) {
            final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(fileOrDirectory);
            if (file == null || !file.isValid()) {
                throw new RuntimeConfigurationWarning("Test " + (this.file != null ? "file " : "directory ") + fileOrDirectory + " doesn't exist");
            }
        } else {
            throw new RuntimeConfigurationWarning("Nothing found to run");
        }
        String jtregDir = JTRegService.getInstance(getProject()).getJTRegDir();
        if (jtregDir != null && !jtregDir.isEmpty()) {
            final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(jtregDir);
            if (file == null || !file.isValid()) {
                throw new RuntimeConfigurationWarning("Configured jtreg path " + jtregDir + " doesn't exist");
            }
            VirtualFile lib = file.findChild("lib");
            boolean error = lib == null || !lib.isValid();
            if (!error) {
                VirtualFile jar = lib.findChild("jtreg.jar");
                error = jar == null || !jar.isValid();
            }
            if (error) {
                throw new RuntimeConfigurationWarning("Configured jtreg path " + jtregDir + " doesn't point to a valid jtreg installation");
            }
        } else {
            throw new RuntimeConfigurationWarning("No jtreg path configured");
        }

        if (getJDKString() == null) {
            throw new RuntimeConfigurationWarning("No valid JDK configured for running jtreg tests");
        }
    }

    String getJDKString() {
        String jdkString = null;
        if (isAlternativeJrePathEnabled()) {
            String jdkPathString = getAlternativeJrePath();
            Sdk sdk = ProjectJdkTable.getInstance().findJdk(jdkPathString);
            if (sdk != null) {
                jdkString = sdk.getHomePath();
            } else if (JdkUtil.checkForJdk(jdkPathString)) {
                jdkString = jdkPathString;
            }
        } else {
            String defaultJdk = DefaultJreSelector.projectSdk(getProject()).getNameAndDescription().first;
            jdkString = ProjectJdkTable.getInstance().findJdk(defaultJdk).getHomePath();
        }
        return jdkString;
    }

    @Override
    public String getRunClass() {
        return file;
    }

    public void setRunClass(String file) {
        this.file = file;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    @Nullable
    @Override
    public String getPackage() {
        return directory;
    }

    public void setPackage(String directory) {
        this.directory = directory;
    }

    @Override
    public boolean isAlternativeJrePathEnabled() {
        return alternativeJrePathEnabled;
    }

    @Override
    public void setAlternativeJrePathEnabled(boolean alternativeJREPathEnabled) {
        this.alternativeJrePathEnabled = alternativeJREPathEnabled;
    }

    @Override
    public String getAlternativeJrePath() {
        return alternativeJrePath;
    }

    @Override
    public void setAlternativeJrePath(String alternativeJrePath) {
        this.alternativeJrePath = alternativeJrePath;
    }

    @Override
    public String getProgramParameters() {
        return jtregOptions;
    }

    @Override
    public void setProgramParameters(String jtregOptions) {
        this.jtregOptions = jtregOptions;
    }

    @Nullable
    @Override
    public String getWorkingDirectory() {
        return workDirectory;
    }

    @Override
    public void setWorkingDirectory(@Nullable String s) {
        workDirectory = s;
    }

    @Override
    public String getVMParameters() {
        return "";
    }

    @Override
    public void setVMParameters(String s) { }

    public void bePatternConfiguration(List<PsiClass> list, PsiMethod psiMethod) {
        //do nothing
    }

    public void beMethodConfiguration(Location<PsiMethod> location) {
        //do nothing
    }

    public void beClassConfiguration(PsiClass psiClass) {
        //do nothing
    }

    public boolean isConfiguredByElement(PsiElement psiElement) {
        return false;
    }

    public TestSearchScope getTestSearchScope() {
        return null;
    }

    public void setSearchScope(TestSearchScope testSearchScope) {
        //do nothing
    }

    public String getTestType() {
        return "jtreg";
    }

    public byte getTestFrameworkId() {
        return 2; //for now
    }
}
