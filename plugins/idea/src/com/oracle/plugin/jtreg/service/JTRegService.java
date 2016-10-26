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

package com.oracle.plugin.jtreg.service;

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.element.ElementUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class acts as a model for the jtreg tool settings ui. Its state can be persisted to the project configuration
 * file, so that jtreg settings can effectively be shared on a per-project basis.
 */
@State(name = "JTRegService")
public class JTRegService implements PersistentStateComponent<Element> {

    Project project;

    //state
    private String jtregOptions = "";
    private boolean alternativeJrePathEnabled = false;
    private String alternativeJrePath = "";
    private String jtregDir = "";
    private String workDir = "";
    private List<Pair<String, String>> optTargets = new ArrayList<>();

    public JTRegService(Project project) {
        this.project = project;
    }

    public static JTRegService getInstance(Project project) {
        return ServiceManager.getService(project, JTRegService.class);
    }

    @Nullable
    @Override
    public Element getState() {
        Element jtreg = new Element("jtreg");
        Element jtregPath = new Element("path");
        jtregPath.addContent(jtregDir);
        jtreg.addContent(jtregPath);
        Element jtregWork = new Element("workDir");
        jtregWork.addContent(workDir);
        jtreg.addContent(jtregWork);
        Element jrePath = new Element("jre");
        jrePath.setAttribute("alt", String.valueOf(alternativeJrePathEnabled));
        if (alternativeJrePathEnabled) {
            jrePath.setAttribute("value", alternativeJrePath);
        }
        jtreg.addContent(jrePath);
        Element opts = new Element("options");
        opts.addContent(jtregOptions);
        jtreg.addContent(opts);
        if (!optTargets.isEmpty()) {
            Element ant = new Element("ant");
            for (Pair<String, String> antBuildTarget : optTargets) {
                Element target = new Element("target");
                target.setAttribute("file", antBuildTarget.first);
                target.setAttribute("name", antBuildTarget.second);
                ant.addContent(target);
            }
            jtreg.addContent(ant);
        }
        return jtreg;
    }

    @Override
    public void loadState(Element jtreg) {
        try {
            jtregDir = Optional.of(jtreg.getChildText("path")).orElse("");
            workDir = Optional.of(jtreg.getChildText("workDir")).orElse("");
            jtregOptions = Optional.of(jtreg.getChildText("options")).orElse("");
            Element jre = jtreg.getChild("jre");
            if (jre != null) {
                alternativeJrePathEnabled = jre.getAttribute("alt").getBooleanValue();
                if (alternativeJrePathEnabled) {
                    alternativeJrePath = jre.getAttribute("value").getValue();
                }
            } else {
                alternativeJrePathEnabled = false;
            }
            Element ant = jtreg.getChild("ant");
            if (ant != null) {
                List<Pair<String, String>> targets = new ArrayList<>();
                for (Element target : ant.getChildren("target")) {
                    String url = target.getAttribute("file").getValue();
                    String targetName = target.getAttribute("name").getValue();
                    targets.add(Pair.create(url, targetName));
                }
                optTargets = targets;
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }

    public String getJTregOptions() {
        return jtregOptions;
    }

    public void setJTRegOptions(String jtregOptions) {
        this.jtregOptions = jtregOptions;
    }

    public boolean isAlternativeJrePathEnabled() {
        return alternativeJrePathEnabled;
    }

    public void setAlternativePathEnabled(boolean enabled) {
        alternativeJrePathEnabled = enabled;
    }

    public String getAlternativeJrePath() {
        return alternativeJrePath;
    }

    public void setAlternativeJrePath(String alternativeJrePath) {
        this.alternativeJrePath = alternativeJrePath;
    }

    public String getJTRegDir() {
        return jtregDir;
    }

    public void setJTRegDir(String jtregDir) {
        this.jtregDir = jtregDir;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public void setOptTargets(List<AntBuildTarget> targets) {
        optTargets = targets.stream()
                .map(t -> Pair.create(t.getModel().getBuildFile().getVirtualFile().getUrl(), t.getName()))
                .collect(Collectors.toList());
    }

    public List<AntBuildTarget> getOptTargets(AntConfiguration antConfiguration) {
        return optTargets.stream()
                .map(p -> findTargetByFileAndName(antConfiguration, p.first, p.second))
                .collect(Collectors.toList());
    }
    //where
        private AntBuildTarget findTargetByFileAndName(AntConfiguration antConfiguration, String url, String name) {
            for (AntBuildFile file : antConfiguration.getBuildFiles()) {
                if (file.getVirtualFile().getUrl().equals(url)) {
                    AntBuildTarget foundTarget = file.getModel().findTarget(name);
                    if (foundTarget != null) {
                        return foundTarget;
                    }
                }
            }
            return null;
        }
}
