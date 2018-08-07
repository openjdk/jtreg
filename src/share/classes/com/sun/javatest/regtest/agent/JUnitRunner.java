/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;


/**
 * TestRunner to run JUnit tests.
 *
 * @author John R. Rose
 */
public class JUnitRunner implements MainActionHelper.TestRunner {
    private static final String
        JUNIT_NO_DRIVER        = "No JUnit 4 driver (install junit.jar next to jtreg.jar)";

    public static void main(String... args) throws Exception {
        main(null, args);
    }

    public static void main(ClassLoader loader, String... args) throws Exception {
        if (args.length != 2) {
            throw new Error("wrong number of arguments");
        }
        // String testName = args[0];  // not used
        String moduleClassName = args[1];
        int sep = moduleClassName.indexOf('/');
        String moduleName = (sep == -1) ? null : moduleClassName.substring(0, sep);
        String className = (sep == -1) ? moduleClassName : moduleClassName.substring(sep + 1);
        //            Class<?> mainClass = (loader == null) ? Class.forName(className) : loader.loadClass(className);
        ClassLoader cl;
        if (moduleName != null) {
            Class<?> layerClass;
            try {
                layerClass = Class.forName("java.lang.ModuleLayer");
            } catch (ClassNotFoundException e) {
                layerClass = Class.forName("java.lang.reflect.Layer");
            }
            Method bootMethod = layerClass.getMethod("boot");
            Object bootLayer = bootMethod.invoke(null);
            Method findLoaderMth = layerClass.getMethod("findLoader", String.class);
            cl = (ClassLoader) findLoaderMth.invoke(bootLayer, new Object[]{moduleName});
        } else if (loader != null) {
            cl = loader;
        } else {
            cl = JUnitRunner.class.getClassLoader();
        }
        Class<?> mainClass = Class.forName(className, false, cl);
        org.junit.runner.Result result;
        try {
            result = org.junit.runner.JUnitCore.runClasses(mainClass);
        } catch (NoClassDefFoundError ex) {
            throw new Exception(JUNIT_NO_DRIVER, ex);
        }
        if (!result.wasSuccessful()) {
            for (org.junit.runner.notification.Failure failure : result.getFailures()) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                try {
                    pw.println("JavaTest Message: JUnit Failure: " + failure);
                    failure.getException().printStackTrace(pw);
                } finally {
                    pw.close();
                }
                System.err.println(sw.toString());
            }
            throw new Exception("JUnit test failure");
        }
    }

}
