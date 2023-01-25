/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.agent;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface which should be implemented to customize execution of test.
 * It is used by main and driver actions to execute test.
 */
public interface CustomMainWrapper {
    static CustomMainWrapper getInstance(String mainWrapper, String path) {
        String[] args = mainWrapper.split(":", 2);
        String className = args[0];
        String actionName = args[1].split("=")[1];
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        if (path != null) {
            SearchPath classpath = new SearchPath(path);
            List<URL> urls = new ArrayList<>();
            for (Path f : classpath.asList()) {
                try {
                    urls.add(f.toUri().toURL());
                } catch (MalformedURLException e) {
                }
            }
            loader = new URLClassLoader(urls.toArray(new URL[urls.size()]), loader);
        }
        try {
            Class<? extends CustomMainWrapper> clz = loader.loadClass(className).asSubclass(CustomMainWrapper.class);
            Constructor<? extends CustomMainWrapper> ctor = clz.getDeclaredConstructor();
            CustomMainWrapper wrapper = ctor.newInstance();
            wrapper.setAction(actionName);
            return wrapper;
        } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException
                 | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method should be implemented to run test task.
     * Default jtreg implementation is return new Thread(tg, task);
     * @param tg ThreadGroup to run test
     * @param task The task which executes test
     */
    Thread createThread(ThreadGroup tg, Runnable task);

    /**
     * This method is used to get information about current action.
     * @param actionName name of action
     */
    default void setAction(String actionName) {
    }
}