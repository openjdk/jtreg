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

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import icons.JTRegPluginIcons;

import javax.swing.*;

/**
 * The jtreg configuration type.
 */
public class JTRegConfigurationType implements ConfigurationType {

    @Override
    public String getDisplayName() {
        return "jtreg";
    }

    @Override
    public String getConfigurationTypeDescription() {
        return "jtreg";
    }

    @Override
    public Icon getIcon() {
        return JTRegPluginIcons.JTREG_ICON_16;
    }

    @NotNull
    @Override
    public String getId() {
        return "jtreg";
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
        return new ConfigurationFactory[]{new ConfigurationFactory(this) {
            @NotNull
            @Override
            public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
                return new JTRegConfiguration(project, this);
            }

            @NotNull
            public String getId() {
                return "jtreg";
            }
        }};
    }

    @NotNull
    public static JTRegConfigurationType getInstance() {
        return ConfigurationTypeUtil.findConfigurationType(JTRegConfigurationType.class);
    }
}
