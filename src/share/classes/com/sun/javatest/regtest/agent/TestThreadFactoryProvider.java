/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ThreadFactory;

/**
 * A provider which loads thread factory for threads used to run tests.
 * By default, jtreg creates a new thread for each test using {@code new Thread(ThreadGroup tg, Runnable task);},
 * but this may be overridden by providing an implementation of {@link java.util.concurrent.ThreadFactory},
 * which might provide user-defined threads for test execution.
 * An implementation might provide user-defined threads for test execution.
 * <p>
 * Example:
 * <pre>
 * new Thread(() -> { ....; task.run(); ....; });
 * or
 * new VirtualThread(task);
 * </pre>
 * Implementation may be specified on the {@code jtreg} command line
 * using {@code -testThreadFactory} and {@code -testThreadFactoryPath} arguments.
 * It is executed by tested JDK in {@code agentvm} and {@code othervm} modes.
 */
public final class TestThreadFactoryProvider {
    static ThreadFactory loadThreadFactory(String className, String path) {
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
            Class<? extends ThreadFactory> clz = loader.loadClass(className).asSubclass(ThreadFactory.class);
            Constructor<? extends ThreadFactory> ctor = clz.getDeclaredConstructor();
            ThreadFactory factory = ctor.newInstance();
            return factory;
        } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException
                 | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
