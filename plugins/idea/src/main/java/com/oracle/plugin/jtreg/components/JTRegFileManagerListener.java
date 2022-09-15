/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.plugin.jtreg.components;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.oracle.plugin.jtreg.service.JTRegService;
import com.oracle.plugin.jtreg.util.JTRegUtils;
import java.io.File;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

/**
 * This listener intercepts editor events for file opened/closed. Each time a new jtreg test is opened, the project root
 * model is updated, so that the test source is displayed without errors. Changes in the headers will be picked up
 * and reflected in the root model.
 */
public class JTRegFileManagerListener implements FileEditorManagerListener {

    public static final Logger LOG = Logger.getInstance(JTRegFileManagerListener.class);

    Project project;

    class TestInfo {
        VirtualFile file;
        Module module;
        Alarm alarm = new Alarm();
        VirtualFile contentRoot;
        List<VirtualFile> roots = new ArrayList<>();
        Library jtregLib;
        Document document;
        DocumentListener listener = new DocumentListener() {
            @Override
            public void documentChanged(DocumentEvent e) {
                alarm.cancelAllRequests();
                alarm.addRequest(() -> processFileOpened(TestInfo.this), 1000);
            }
        };

        TestInfo(VirtualFile file) {
            this.file = file;
            this.module = ModuleUtilCore.findModuleForFile(file, project);
            this.contentRoot = ProjectRootManager.getInstance(project).getFileIndex().getContentRootForFile(file);
            this.document = FileDocumentManager.getInstance().getDocument(file);
            document.addDocumentListener(listener);
        }

        void dispose() {
            alarm.cancelAllRequests();
            document.removeDocumentListener(listener);
            //clear out test info
            file = null;
            roots = null;
            jtregLib = null;
            contentRoot = null;
            module = null;
            alarm = null;
            document = null;
        }
    }

    Map<VirtualFile, TestInfo> testInfos = new HashMap<>();
    TestRootManager rootManager = new TestRootManager();


    public JTRegFileManagerListener(Project project) {
        this.project = project;
    }

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (JTRegUtils.isInJTRegRoot(file)) {
            LOG.info("test file opened: " + file + " @ " + project.getName());
            DumbService.getInstance(project).smartInvokeLater(() -> {
                LOG.info("test file opened [smart]: " + file + " @ " + project.getName());
                TestInfo testInfo = new TestInfo(file);
                testInfos.put(file, testInfo);
                processFileOpened(testInfo);
            }, ModalityState.NON_MODAL);
        }
    }

    void processFileOpened(TestInfo testInfo) {
        VirtualFile file = testInfo.file;
        LOG.info("processing file opened: " + testInfo.file + " @ " + project.getName());
        boolean isJtreg = JTRegUtils.isJTRegTestData(project, file);
        boolean isTestNg = JTRegUtils.isTestNGTestData(project, file);
        boolean isJUnit = JTRegUtils.isJUnitTestData(project, file);
        if (isJtreg || isTestNg || isJUnit) {
            //add jtreg roots
            try (TestRootManager.TestRootModel rootModel = rootManager.rootModel(testInfo)) {
                List<VirtualFile> oldRoots = testInfo.roots;
                List<VirtualFile> testRoots = JTRegUtils.getTestRoots(project, file);
                if (oldRoots == null || !oldRoots.equals(testRoots)) {
                    if (oldRoots != null) {
                        rootModel.removeSourceFolders(oldRoots);
                    }
                    testInfo.roots = testRoots;
                    if (!testRoots.isEmpty()) {
                        rootModel.addSourceFolders(testRoots);
                    }
                }
                if (isTestNg || isJUnit) {
                    String libDir = JTRegService.getInstance(project).getJTRegDir();
                    Library library = JTRegUtils.createJTRegLibrary(project, libDir);
                    testInfo.jtregLib = library;
                    rootModel.addLibrary(library);
                } else if (testInfo.jtregLib != null) {
                    rootModel.removeLibrary(testInfo.jtregLib);
                    testInfo.jtregLib = null;
                }
            }
        } else {
            try (TestRootManager.TestRootModel rootModel = rootManager.rootModel(testInfo)) {
                List<VirtualFile> oldRoots = testInfo.roots;
                if (oldRoots != null) {
                    rootModel.removeSourceFolders(oldRoots);
                }
                testInfo.roots = Collections.emptyList();
                if (testInfo.jtregLib != null) {
                    rootModel.removeLibrary(testInfo.jtregLib);
                    testInfo.jtregLib = null;
                }
            }
        }
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        TestInfo testInfo = testInfos.get(file); //did we open the file?
        if (testInfo != null) {
            LOG.info("test file closed: " + file + " @ " + project.getName());
            DumbService.getInstance(project).smartInvokeLater(() -> {
                LOG.info("test file closed [smart]: " + file + " @ " + project.getName());
                processFileClosed(testInfo);
            }, ModalityState.NON_MODAL);
        }
    }

    void processFileClosed(TestInfo testInfo) {
        VirtualFile file = testInfo.file;
        LOG.info("processing file closed: " + testInfo.file + " @ " + project.getName());
        if (!file.exists() || JTRegUtils.isJTRegTestData(project, file)
                || JTRegUtils.isTestNGTestData(project, file) || JTRegUtils.isJUnitTestData(project, file)) {
            if (project.isOpen()) {
                try (TestRootManager.TestRootModel rootModel = rootManager.rootModel(testInfo)) {
                    List<VirtualFile> rootsToRemove = file.exists() ?
                            JTRegUtils.getTestRoots(project, file) : testInfo.roots;
                    rootModel.removeSourceFolders(rootsToRemove);
                    if (testInfo.jtregLib != null) {
                        rootModel.removeLibrary(testInfo.jtregLib);
                        testInfo.jtregLib = null;
                    }
                }
            }
        }
        testInfo.dispose();
        testInfos.remove(file);
    }

    class TestRootManager {

        Map<VirtualFile, Integer> refCount = new HashMap<>();
        // Record the library reference count used by the module.
        Map<Module, Integer> moduleLibRefCount = new HashMap<>();

        TestRootModel rootModel(TestInfo testInfo) {
            ModifiableRootModel modifiableRootModel = ModuleRootManager.getInstance(testInfo.module).getModifiableModel();
            ContentEntry contentEntry = Stream.of(modifiableRootModel.getContentEntries())
                    .filter(e -> e.getFile().equals(testInfo.contentRoot))
                    .findFirst().orElse(null);
            return new TestRootModel(contentEntry, modifiableRootModel);
        }

        void dispose() {
            refCount.clear();
            moduleLibRefCount.clear();
        }

        class TestRootModel implements AutoCloseable {
            ContentEntry contentEntry;
            ModifiableRootModel modifiableRootModel;

            TestRootModel(ContentEntry contentEntry, ModifiableRootModel modifiableRootModel) {
                this.contentEntry = contentEntry;
                this.modifiableRootModel = modifiableRootModel;
            }

            void addSourceFolders(List<VirtualFile> sourceRoots) {
                for (VirtualFile f : sourceRoots) {
                    Integer i = refCount.get(f);
                    if (i == null) {
                        LOG.debug("Adding source folder: " + f);
                        contentEntry.addSourceFolder(f, true);
                    }
                    refCount.put(f, i == null ? 1 : i + 1);
                }
            }

            void removeSourceFolders(List<VirtualFile> sourceRoots) {
                for (SourceFolder s : contentEntry.getSourceFolders()) {
                    if (!s.isTestSource()) continue;
                    for (VirtualFile f : sourceRoots) {
                        if (f.getUrl().equals(s.getUrl())) {
                            removeSourceFolder(f, s);
                        }
                    }
                }
            }

            void removeSourceFolder(VirtualFile f, SourceFolder s) {
                Integer i = refCount.get(f);
                if (i == null) {
                    //not found - skip
                } else if (i == 1) {
                    LOG.debug("Removing source folder: " + s);
                    contentEntry.removeSourceFolder(s);
                    refCount.remove(f);
                } else {
                    refCount.put(f, i - 1);
                }
            }

            /**
             * Add the project library to the module.
             */
            void addLibrary(Library library) {
                Integer i = moduleLibRefCount.get(modifiableRootModel.getModule());
                if (i == null) {
                    LOG.info("Adding library in module: " + modifiableRootModel.getModule().getName());
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        modifiableRootModel.addLibraryEntry(library);
                    });
                }
                moduleLibRefCount.put(modifiableRootModel.getModule(), i == null ? 1 : i + 1);
            }

            /**
             * Remove the project library from the module.
             */
            void removeLibrary(Library library) {
                Integer i = moduleLibRefCount.get(modifiableRootModel.getModule());
                if (i == null) {
                    // skip
                } else if (i == 1) {
                    LOG.info("Removing library in module: " + modifiableRootModel.getModule().getName());
                    Optional<OrderEntry> entry = Arrays.stream(modifiableRootModel.getOrderEntries())
                            .filter(orderEntry -> orderEntry.getPresentableName().equals(library.getName())).findFirst();
                    entry.ifPresent(orderEntry -> modifiableRootModel.removeOrderEntry(orderEntry));
                    moduleLibRefCount.remove(modifiableRootModel.getModule());
                } else if (i > 1) {
                    moduleLibRefCount.put(modifiableRootModel.getModule(), i - 1);
                }
            }

            @Override
            public void close() {
                if (modifiableRootModel.isChanged()) {
                    ApplicationManager.getApplication().runWriteAction(() -> modifiableRootModel.commit());
                } else {
                    modifiableRootModel.dispose();
                }
            }
        }
    }

    void dispose() {
        Iterator<Map.Entry<VirtualFile, TestInfo>> entriesIt = testInfos.entrySet().iterator();
        while (entriesIt.hasNext()) {
            entriesIt.next().getValue().dispose();
            entriesIt.remove();
        }
        rootManager.dispose();
        project = null;
    }
}
