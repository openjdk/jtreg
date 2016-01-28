/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;

/**
  * Get properties for the JDK under test
  */
public class GetJDKProperties {
    public static final String JTREG_INSTALLED_MODULES = "jtreg.installed.modules";

    public static void main(String... args) {
        Properties p = new Properties();
        for (String arg: args) {
            try {
                Class<?> c = Class.forName(arg);
                @SuppressWarnings("unchecked")
                Callable<Map<String, String>> o = (Callable<Map<String, String>>) c.newInstance();
                p.putAll(o.call());
            } catch (Exception e) {
                System.err.println(e);
                System.exit(1);
            }
        }
        String modules = getModules();
        if (modules != null)
            p.put(JTREG_INSTALLED_MODULES, modules);
        p.putAll(System.getProperties());
        try {
            p.store(System.out, "jdk properties");
        } catch (IOException e) {
            System.err.println(e);
            System.exit(1);
        }
    }

    private static String getModules() {
        try {
            Class<?> layerClass = Class.forName("java.lang.reflect.Layer");
            Method bootMethod = layerClass.getDeclaredMethod("boot");
            Method modulesMethod = layerClass.getDeclaredMethod("modules");
            Class<?> moduleClass = Class.forName("java.lang.reflect.Module");
            Method getNameMethod = moduleClass.getDeclaredMethod("getName");

            StringBuilder sb = new StringBuilder();
            Object bootLayer = bootMethod.invoke(null);
            for (Object module : (Set<?>) modulesMethod.invoke(bootLayer)) {
                if (sb.length() > 0)
                    sb.append(" ");
                sb.append(getNameMethod.invoke(module));
            }
            return sb.toString();
        } catch (ClassNotFoundException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (SecurityException e) {
            return null;
        } catch (InvocationTargetException e) {
            return null;
        }
    }
}
