/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest;

import com.sun.javatest.TestResult.Section;
import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * This class keeps track of which TimeoutHandler implementation to use
 * and will create instances thereof.
 */
public class TimeoutHandlerProvider {

    private static String className;
    private static ClassLoader loader;

    /**
     * Set the class name of the TimeoutHandler sub-class.
     * @param name
     */
    public static void setClassName(String name) {
        className = name;
    }

    /**
     * Set the class path for loading the timeout handler.
     * @param path
     * @throws java.net.MalformedURLException
     */
    public static void setClassPath(List<File> path) throws MalformedURLException {
        URL[] urls = new URL[path.size()];
        int u = 0;
        for (File f: path) {
            urls[u++] = f.toURI().toURL();
        }
        loader = new URLClassLoader(urls);
    }

    /**
     * Load the class specified by setClassName and setClassPath.
     * @throws ClassNotFoundException
     */
    private static Class<TimeoutHandler> loadClass() throws ClassNotFoundException {
        Class<?> handlerClass;
        if (loader == null) {
            handlerClass = Class.forName(className);
        } else {
            handlerClass = Class.forName(className, true, loader);
        }
        return (Class<TimeoutHandler>) handlerClass.asSubclass(TimeoutHandler.class);
    }

    /**
     * Create an instance of the TimeoutHandler that has been configured.
     */
    public static TimeoutHandler createHandler(RegressionScript script, Section section) {
        PrintWriter log = section.getMessageWriter();
        File outDir = script.absTestScratchDir();
        File testJDK = script.getTestJDK().getAbsoluteFile();

        if (className != null) {
            try {
                Class<TimeoutHandler> clz = loadClass();
                Constructor ctor = clz.getDeclaredConstructor(PrintWriter.class, File.class, File.class);
                return (TimeoutHandler) ctor.newInstance(log, outDir, testJDK);
            } catch(Exception ex) {
                log.println("Failed to instantiate timeout handler: " + className);
                ex.printStackTrace(log);
                log.println("Reverting to the default timeout handler.");
            }
        }
        return new DefaultTimeoutHandler(log, outDir, testJDK);
    }
}
