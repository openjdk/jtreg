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
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.components.JBTextField;
import com.oracle.plugin.jtreg.configuration.JTRegConfiguration;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * This class models the "Run/Debug Configurations" dialog for the JTReg tool settings.
 */
public class JTRegConfigurable<T extends JTRegConfiguration> extends SettingsEditor<T> {
    private JTextField jtregQuery;
    private JTextField jtregOptions;
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

    /**
     * Gets called for each JTReg configuration entry
     * when it appears in the "Run/Debug Configurations" window.
     */
    private void createUIComponents() {
        jrePathEditor = new JrePathEditor(DefaultJreSelector.projectSdk(project));
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

    /**
     * Gets frequently called to retrieve data from the form.
     *
     * @param configuration JTReg configuration to be populated.
     */
    @Override
    public void applyEditorTo(@NotNull final JTRegConfiguration configuration) {
        applyEditorJrePathTo(configuration);

        configuration.setRunClass(fileRadioButton.isSelected() ?
                FileUtil.toSystemIndependentName(file.getText().trim()) : null);
        configuration.setPackage(directoryRadioButton.isSelected() ?
                FileUtil.toSystemIndependentName(directory.getText().trim()) : null);
        configuration.setProgramParameters(jtregOptions.getText().trim());
        configuration.setQuery(jtregQuery.getText().trim());
        configuration.setWorkingDirectory(workDirectory.getText().isEmpty() ?
                null : FileUtil.toSystemIndependentName(workDirectory.getText().trim()));
    }

    /**
     * Retrieves the raw string value from the JRE Path Editor,
     * checks whether it is present in the list of the JRE Path Editor.
     * If it is in the list, the value is retrieved as usual.
     * Otherwise, the method checks if the value is a valid JDK path,
     * and if so, adds it to the JRE Path Editor list.
     * <p>
     * If the user manually selects the JRE path, it is always considered "alternative".
     * <p>
     * Resolves the window freeze issue when the user specifies a JRE path
     * that is missing from the project's SDK list.
     * <p>
     * <b>Note:</b> Using the methods
     * {@link JrePathEditor#getJrePathOrName} and {@link JrePathEditor#isAlternativeJreSelected}
     * is resource-intensive when the selected path corresponds to the valid Java home,
     * but this path is not present in the JrePathEditor list.
     *
     * @param configuration the JTReg configuration
     *                      whose fields {@code alternativeJrePath} and {@code isAlternativeJre}
     *                      need to be populated.
     * @see com.intellij.execution.ui.JreComboboxEditor#getItem()
     * @see org.jetbrains.jps.model.java.impl.JdkVersionDetectorImpl#detectJdkVersionInfo
     */
    private void applyEditorJrePathTo(@NotNull JTRegConfiguration configuration) {
        final ComboBox<JrePathEditor.JreComboBoxItem> jreComboBox = jrePathEditor.getComponent();
        final ComboBoxEditor jreComboBoxEditor = jreComboBox.getEditor();
        final JBTextField jreTextField = (JBTextField) jreComboBoxEditor.getEditorComponent();

        final String jrePathOrNameText = jreTextField.getText().trim();
        final boolean inList = IntStream.range(0, jreComboBox.getItemCount())
                .mapToObj(jreComboBox::getItemAt)
                .map(JrePathEditor.JreComboBoxItem::getPresentableText)
                .filter(Objects::nonNull)
                .anyMatch(pathOrName -> pathOrName.equals(jrePathOrNameText));

        final String alternativeJrePath;
        final boolean alternativeJREPathEnabled;
        if (inList) { // safe to get item from JRE path editor
            alternativeJrePath = jrePathEditor.getJrePathOrName();
            alternativeJREPathEnabled = jrePathEditor.isAlternativeJreSelected();
        } else { // JRE path editor would be time-consuming
            alternativeJrePath = FileUtil.toSystemIndependentName(jrePathOrNameText); // The value here should always be the path, not the name.
            alternativeJREPathEnabled = true;
            if (JdkUtil.checkForJdk(alternativeJrePath)) {
                // If the path is a valid JDK, add it to the ComboBox list.
                jrePathEditor.setPathOrName(alternativeJrePath, true);
            }
        }
        configuration.setAlternativeJrePath(alternativeJrePath);
        configuration.setAlternativeJrePathEnabled(alternativeJREPathEnabled);
    }

    /**
     * Gets called to populate the fields in the form from the stored JTReg configuration.
     *
     * @param configuration JTReg stored configuration.
     */
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
