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

package com.oracle.plugin.jtreg.service.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.impl.MetaTarget;
import com.intellij.lang.ant.config.impl.TargetChooserDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.AnActionButtonUpdater;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.oracle.plugin.jtreg.service.JTRegService;
import icons.AntIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * This class models the dialog associated with the (project-wide) jtreg tool settings.
 */
public class JTRegServiceConfigurable implements SearchableConfigurable {
    private JTextField jtregOptions;
    private TextFieldWithBrowseButton jtregDir;
    private TextFieldWithBrowseButton workDir;
    private JrePathEditor jrePathEditor;
    private JPanel mainPane;
    private JPanel myListPane;
    private CollectionListModel<AntBuildTarget> myModel;


    Project project;
    JTRegService service;
    AntConfigurationBase antConfiguration;

    public JTRegServiceConfigurable(Project project, JTRegService service, AntConfiguration antConfiguration) {
        this.project = project;
        this.service = service;
        this.antConfiguration = (AntConfigurationBase)antConfiguration;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        return mainPane;
    }

    @NotNull
    @Override
    public String getId() {
        return "jtreg";
    }

    @Nullable
    @Override
    public Runnable enableSearch(String s) {
        return null;
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "jtreg Settings";
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return getId();
    }

    @Override
    public boolean isModified() {
        return !jtregOptions.getText().trim().equals(service.getJTregOptions()) ||
                jrePathEditor.isAlternativeJreSelected() != service.isAlternativeJrePathEnabled() ||
                !jtregDir.getText().trim().equals(service.getJTRegDir()) ||
                !workDir.getText().trim().equals(FileUtil.toSystemDependentName(service.getWorkDir())) ||
                (jrePathEditor.isAlternativeJreSelected() && !Comparing.equal(jrePathEditor.getJrePathOrName(), service.getAlternativeJrePath())) ||
                !myModel.getItems().equals(service.getOptTargets(antConfiguration));

    }

    @Override
    public void apply() throws ConfigurationException {
        service.setJTRegOptions(jtregOptions.getText().trim());
        service.setAlternativePathEnabled(jrePathEditor.isAlternativeJreSelected());
        service.setAlternativeJrePath(jrePathEditor.getJrePathOrName());
        service.setJTRegDir(FileUtil.toSystemIndependentName(jtregDir.getText().trim()));
        service.setWorkDir(workDir.getText().trim());
        service.setOptTargets(myModel.getItems());
    }

    @Override
    public void reset() {
        jtregOptions.setText(service.getJTregOptions());
        jrePathEditor.setPathOrName(service.getAlternativeJrePath(), service.isAlternativeJrePathEnabled());
        jtregDir.setText(FileUtil.toSystemDependentName(service.getJTRegDir()));
        workDir.setText(FileUtil.toSystemDependentName(service.getWorkDir()));
        myModel.removeAll();
        for (AntBuildTarget target : service.getOptTargets(antConfiguration)) {
            myModel.add(target);
        }
    }

    @Override
    public void disposeUIResources() {
        //do nothing
    }

    private void createUIComponents() {
        jrePathEditor = new JrePathEditor(DefaultJreSelector.projectSdk(project));
        jtregDir = new TextFieldWithBrowseButton();
        jtregDir.addBrowseFolderListener("Directory with Strategies", null, project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
        workDir = new TextFieldWithBrowseButton();
        workDir.addBrowseFolderListener("Directory with Strategies", null, project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
        myModel = new CollectionListModel<>();
        JBList myList = new JBList(myModel);
        myList.getEmptyText().setText(ExecutionBundle.message("before.launch.panel.empty"));
        myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        myList.setCellRenderer(new MyListCellRenderer());

        ToolbarDecorator myDecorator = ToolbarDecorator.createDecorator(myList);
        if (!SystemInfo.isMac) {
            myDecorator.setAsUsualTopToolbar();
        }


        antConfiguration.ensureInitialized();
        boolean antConfigEnabled = antConfiguration.getBuildFiles().length != 0;

        myDecorator.setEditAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
                int index = myList.getSelectedIndex();
                if (index == -1)
                    return;
                AntBuildTarget prevTarget = myModel.getElementAt(index);
                AntBuildTarget newTarget = pickTarget(prevTarget);
                if (newTarget != null) {
                    myModel.setElementAt(newTarget, index);
                }
            }
        });
        myDecorator.setEditActionUpdater(new AnActionButtonUpdater() {
            @Override
            public boolean isEnabled(AnActionEvent e) {
                int index = myList.getSelectedIndex();
                return index != -1;
            }
        });
        myDecorator.setAddAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
                AntBuildTarget target = pickTarget(null);
                myModel.add(target);
            }
        });
        myDecorator.setAddActionUpdater(new AnActionButtonUpdater() {
            @Override
            public boolean isEnabled(AnActionEvent e) {
                return antConfigEnabled;
            }
        });
        myListPane = myDecorator.createPanel();
    }

    AntBuildTarget pickTarget(AntBuildTarget prev) {
        TargetChooserDialog dlg = new TargetChooserDialog(project, prev);
        if (dlg.showAndGet()) {
            return dlg.getSelectedTarget();
        } else {
            return null;
        }
    }

    private class MyListCellRenderer extends JBList.StripedListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof AntBuildTarget) {
                AntBuildTarget target = (AntBuildTarget) value;
                setIcon(getTaskIcon(target));
                setText(getDescription(target));
            }
            return this;
        }

        public Icon getTaskIcon(AntBuildTarget antTarget) {
            return antTarget instanceof MetaTarget ? AntIcons.MetaTarget : AntIcons.Target;
        }

        public String getDescription(AntBuildTarget antTarget) {
            String targetName = antTarget.getName();
            return targetName == null ?
                    AntBundle.message("ant.target.before.run.description.empty", new Object[0]) :
                    AntBundle.message("ant.target.before.run.description", new Object[]{targetName != null ? targetName : "<not selected>"});
        }
    }
}
