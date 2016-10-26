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

package com.oracle.plugin.jtreg.ant;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.ExecutionEvent;
import com.intellij.lang.ant.config.impl.AntConfigurationImpl;
import com.intellij.lang.ant.config.impl.AntWorkspaceConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.util.Alarm;
import org.jdom.Element;

public class JTRegAntConfiguration extends AntConfigurationImpl {
    public JTRegAntConfiguration(Project project, AntWorkspaceConfiguration antWorkspaceConfiguration, DaemonCodeAnalyzer daemon) {
        super(project, antWorkspaceConfiguration, daemon);
    }

    @Override
    public void loadState(Element state) {
        Element prev = state.clone();
        super.loadState(state);
        Alarm a = new Alarm();
        a.addRequest(() -> postInit(prev, a), 100);
    }

    private void postInit(Element prev, Alarm a) {
        if (isInitialized()) {
            for (Element buildElement : prev.getChildren("buildFile")) {
                String url = buildElement.getAttributeValue("url");
                for (Element executeOnElement : buildElement.getChildren("executeOn")) {
                    String eventId = executeOnElement.getAttributeValue("event");
                    String targetName = executeOnElement.getAttributeValue("target");
                    if (eventId.equals("jtreg")) {
                        ExecutionEvent event = JTRegExecutionEvent.getInstance();
                        try {
                            event.readExternal(executeOnElement, getProject());
                            setTargetForEvent(getBuildFileByURL(url), targetName, event);
                        } catch (InvalidDataException readFailed) {
                            readFailed.printStackTrace();
                        }
                    }
                }
            }
        } else {
            a.addRequest(() -> postInit(prev, a), 100);
        }
    }

    AntBuildFile getBuildFileByURL(String url) {
        for (AntBuildFile antBuildFile : getBuildFiles()) {
            if (antBuildFile.getVirtualFile().getUrl().equals(url)) {
                return antBuildFile;
            }
        }
        return null;
    }

    public static final class JTRegExecutionEvent extends ExecutionEvent {

        private static final JTRegExecutionEvent ourInstance = new JTRegExecutionEvent();

        private JTRegExecutionEvent() {
        }

        public static JTRegExecutionEvent getInstance() {
            return ourInstance;
        }

        public String getTypeId() {
            return "jtreg";
        }

        public String getPresentableName() {
            return "Before jtreg test";
        }
    }
}
