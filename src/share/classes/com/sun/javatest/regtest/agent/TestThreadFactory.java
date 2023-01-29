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
 * {@code TestThreadFactory} allows some customization of test execution.
 * The jtreg creates new thread for each test using {@code new Thread(ThreadGroup tg, Runnable task);}.
 * The implementation of this interface might provide user-defined threads for test execution.
 * <p>
 * Example:
 * <pre>
 * new Thread(tg, () -> { ....; task.run(); ....; });
 * or
 * new VirtualThread(task);
 * </pre>
 * Implementation may be specified on the {@code jtreg} command line
 * using {@code -testThreadFactory} and {@code -testThreadFactoryPath} arguments.
 * It is executed by tested JDK in {@code agentvm} and {@code othervm} modes.
 */
public interface TestThreadFactory {
    static TestThreadFactory getInstance(String className, String path) {
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
            Class<? extends TestThreadFactory> clz = loader.loadClass(className).asSubclass(TestThreadFactory.class);
            Constructor<? extends TestThreadFactory> ctor = clz.getDeclaredConstructor();
            TestThreadFactory factory = ctor.newInstance();
            return factory;
        } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException
                 | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method should return unstarted thread which executes test task.
     * @param tg ThreadGroup to run test
     * @param task The test task
     */
    Thread newThread(ThreadGroup tg, Runnable task);

}