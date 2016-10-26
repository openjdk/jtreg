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

import com.intellij.execution.*;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.oracle.plugin.jtreg.executors.JTRegDebuggerRunner;
import com.oracle.plugin.jtreg.runtime.JTRegTestListener;
import com.oracle.plugin.jtreg.service.JTRegService;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class tells the IDE how a given configuration should be translated into an actual command line invocation.
 */
class JTRegConfigurationRunnableState extends JavaTestFrameworkRunnableState<JTRegConfiguration> {
    private final JTRegConfiguration myConfiguration;

    public JTRegConfigurationRunnableState(JTRegConfiguration configuration, ExecutionEnvironment executionEnvironment) {
        super(executionEnvironment);
        myConfiguration = configuration;
    }

    @NotNull
    @Override
    protected String getFrameworkName() {
        return "jtreg";
    }

    @NotNull
    @Override
    protected String getFrameworkId() {
        return "jtreg";
    }

    @Override
    protected void passTempFile(ParametersList parametersList, String s) {
        //do nothing
    }

    @NotNull
    @Override
    protected JTRegConfiguration getConfiguration() {
        return myConfiguration;
    }

    @Nullable
    @Override
    protected TestSearchScope getScope() {
        return TestSearchScope.SINGLE_MODULE;
    }

    @Override
    protected JavaParameters createJavaParameters() throws ExecutionException {

        try {
            getConfiguration().checkConfiguration();
        } catch (RuntimeConfigurationException err) {
            throw new CantRunException(err.getMessage());
        }

        JavaParameters javaParameters = super.createJavaParameters();
        javaParameters.getProgramParametersList().clearAll();
        javaParameters.setMainClass("com.sun.javatest.regtest.Main");

        String jdkString = getConfiguration().getJDKString();

        javaParameters.getProgramParametersList().add("-jdk:" + jdkString);

        String customJTRegOptions = getConfiguration().getProgramParameters();
        if (customJTRegOptions != null) {
            javaParameters.getProgramParametersList().addParametersString(customJTRegOptions);
        }

        if (getEnvironment().getRunner() instanceof JTRegDebuggerRunner) {
            JTRegDebuggerRunner runner = (JTRegDebuggerRunner) getEnvironment().getRunner();
            javaParameters.getProgramParametersList().add("-debug:-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=127.0.0.1:" + runner.address);
            boolean hasMode = false;
            for (String s : new String[] { "-ovm", "-othervm", "-avm", "-agentvm" }) {
                if (javaParameters.getProgramParametersList().hasParameter(s)) {
                    javaParameters.getProgramParametersList().replaceOrAppend(s, "-avm");
                    hasMode = true;
                    break;
                }
            }
            if (!hasMode) {
                javaParameters.getProgramParametersList().add("-avm");
            }
            javaParameters.getProgramParametersList().replaceOrAppend("-conc:", "-conc:1");
        }

        //convert any vm passed by intellij options into jtreg -vmoptions
        for (String vmOption : javaParameters.getVMParametersList().getParameters()) {
            javaParameters.getProgramParametersList().add("-vmoption:" + vmOption);
        }
        javaParameters.getVMParametersList().clearAll();

        javaParameters.getProgramParametersList().add("-o:com.oracle.plugin.jtreg.runtime.JTRegTestListener");
        javaParameters.getProgramParametersList().add("-od:" + PathUtil.getJarPathForClass(JTRegConfiguration.class));
        if (getConfiguration().getRunClass() != null) {
            javaParameters.getProgramParametersList().add(getConfiguration().getRunClass());
        } else {
            javaParameters.getProgramParametersList().add(getConfiguration().getPackage());
        }
        return javaParameters;
    }

    @Override
    protected void configureRTClasspath(JavaParameters javaParameters) {
        JTRegService jtregSettings = JTRegService.getInstance(getConfiguration().getProject());
        javaParameters.getClassPath().add(jtregSettings.getJTRegDir() + "/lib/jtreg.jar");
        javaParameters.getClassPath().add(jtregSettings.getJTRegDir() + "/lib/javatest.jar");
        javaParameters.getClassPath().add(jtregSettings.getJTRegDir() + "/lib/testng.jar");
        javaParameters.getClassPath().add(jtregSettings.getJTRegDir() + "/lib/junit.jar");
        javaParameters.getClassPath().add(jtregSettings.getJTRegDir() + "/lib/asmtools.jar");
    }

    @Override
    protected List<String> getNamedParams(String parameters) {
        return Stream.of(parameters.split(" ")).collect(Collectors.toList());
    }


    @NotNull
    @Override
    protected OSProcessHandler createHandler(Executor executor) throws ExecutionException {
        final OSProcessHandler processHandler = new KillableColoredProcessHandler(createCommandLine());
        ProcessTerminatedListener.attach(processHandler);
        final SearchForTestsTask searchingForTestsTask = createSearchingForTestsTask();
        if (searchingForTestsTask != null) {
            searchingForTestsTask.attachTaskToProcess(processHandler);
        }
        return processHandler;
    }

    @NotNull
    @Override
    public ExecutionResult execute(@NotNull final Executor executor, @NotNull final ProgramRunner runner) throws ExecutionException {
        return startSMRunner(executor);
    }

    @Override
    protected boolean isSmRunnerUsed() {
        return true;
    }

    @Override
    public SearchForTestsTask createSearchingForTestsTask() {
        //todo add here test detection based on myConfiguration.getPackage(), for class configuration - do nothing
        return null;
    }

    @NotNull
    @Override
    protected String getForkMode() {
        return "none";
    }

    @Override
    protected void passForkMode(String s, File file, JavaParameters javaParameters) throws ExecutionException {
    }
}
