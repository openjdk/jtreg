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

public interface CustomMainWrapper {
    static CustomMainWrapper getInstance(String className, String path) {
        SearchPath classpath = new SearchPath(path);
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        if (classpath != null && !classpath.isEmpty()) {
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
            return ctor.newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    Thread createThread(ThreadGroup tg, Runnable task);

    List<String> getAdditionalVMOpts();
}


class TestThread extends Thread {
    public TestThread(ThreadGroup group, Runnable target) {
        super(group, target);
    }
}
class TestMainWrapper  implements CustomMainWrapper {
    private List<String>vmOpts = new ArrayList<>();

    public TestMainWrapper() {
        System.setProperty("main.wrapper", "Test");
        vmOpts.add("-Dtest.property=test");
    }

    @Override
    public Thread createThread(ThreadGroup tg, Runnable task) {
        return new TestThread(tg, task);
    }

    @Override
    public List<String> getAdditionalVMOpts() {
        return vmOpts;
    }
}
