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

package com.oracle.plugin.jtreg.configuration.ui;

import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.oracle.plugin.jtreg.configuration.JTRegConfiguration;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * This class models the dialog associated with the (project-wide) jtreg tool settings.
 */
public class JTRegConfigurable<T extends JTRegConfiguration> extends SettingsEditor<T> {
    private JTextField jtregQuery;
    private JTextField jtregOptions;
    private TextFieldWithBrowseButton jtregDir;
    private TextFieldWithBrowseButton workDirectory;
    private JrePathEditor jrePathEditor;
    private JPanel mainPane;
    private JRadioButton fileRadioButton;
    private JRadioButton directoryRadioButton;
    private TextFieldWithBrowseButton file;
    private TextFieldWithBrowseButton directory;

    Project project;

    public JTRegConfigurable(final Project project) {
        this.project = project;
        ActionListener listener = this::updateComponents;
        fileRadioButton.addActionListener(listener);
        directoryRadioButton.addActionListener(listener);
    }

    private void createUIComponents() {
        jrePathEditor = new JrePathEditor(DefaultJreSelector.projectSdk(project));
        jtregDir = new TextFieldWithBrowseButton();
        jtregDir.addBrowseFolderListener("Directory with Strategies", null, project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
        workDirectory = new TextFieldWithBrowseButton();
        workDirectory.addBrowseFolderListener("Directory with Strategies", null, project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
        file = new TextFieldWithBrowseButton();
        file.addBrowseFolderListener("File with Strategies", null, project,
                FileChooserDescriptorFactory.createSingleFileDescriptor());
        directory = new TextFieldWithBrowseButton();
        directory.addBrowseFolderListener("Directory with Strategies", null, project,
                FileChooserDescriptorFactory.createSingleFolderDescriptor());
    }

    private void updateComponents(ActionEvent _unused) {
        file.setEnabled(fileRadioButton.isSelected());
        jtregQuery.setEnabled(fileRadioButton.isSelected());
        directory.setEnabled(directoryRadioButton.isSelected());
    }

    @Override
    public void applyEditorTo(final JTRegConfiguration configuration) {
        configuration.setAlternativeJrePath(jrePathEditor.getJrePathOrName());
        configuration.setAlternativeJrePathEnabled(jrePathEditor.isAlternativeJreSelected());
        configuration.setRunClass(fileRadioButton.isSelected() ?
                FileUtil.toSystemIndependentName(file.getText().trim()) : null);
        configuration.setPackage(directoryRadioButton.isSelected() ?
                FileUtil.toSystemIndependentName(directory.getText().trim()) : null);
        configuration.setProgramParameters(jtregOptions.getText().trim());
        configuration.setQuery(jtregQuery.getText().trim());
        configuration.setWorkingDirectory(workDirectory.getText().isEmpty() ?
                null : FileUtil.toSystemIndependentName(workDirectory.getText().trim()));
    }

    @Override
    public void resetEditorFrom(final JTRegConfiguration configuration) {
        jrePathEditor.setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled());
        final String runClass = configuration.getRunClass();
        if (runClass != null) {
            fileRadioButton.setSelected(true);
            file.setText(FileUtil.toSystemDependentName(runClass));
        } else {
            directoryRadioButton.setSelected(true);
            final String aPackage = configuration.getPackage();
            directory.setText(aPackage != null ? FileUtil.toSystemDependentName(aPackage) : null);
        }
        jtregOptions.setText(configuration.getProgramParameters());
        jtregQuery.setText(configuration.getQuery());
        String workDir = configuration.getWorkingDirectory();
        workDirectory.setText(workDir == null ? "" : FileUtil.toSystemDependentName(workDir));
        updateComponents(null);
    }

    @NotNull
    @Override
    public JComponent createEditor() {
        return mainPane;
    }
}
