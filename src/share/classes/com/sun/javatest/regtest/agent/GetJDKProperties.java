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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;

/**
  * Get properties for the JDK under test.
  * Usage: {@code
  *   java GetJDKProperties [--system-properties] [--modules] <class-name>*
  * }
  */
public class GetJDKProperties {
    public static class ClientCodeException extends Exception {
        private static final long serialVersionUID = 0;
        ClientCodeException(String msg, Throwable t) {
            super(msg, t);
        }
    }

    public static final String JTREG_MODULES = "jtreg.modules";

    public static void main(String... args) {
        try {
            run(args);
        } catch (ClientCodeException e) {
            System.err.println(e.getMessage());
            e.getCause().printStackTrace(System.err);
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Internal error: please report to jtreg-dev@openjdk.java.net");
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public static void run(String... args) throws ClientCodeException, IOException {
        boolean needSystemProperties = false;
        String needModulesArg = null;
        Properties p = new Properties();
        for (String arg: args) {
            if (arg.equals("--system-properties")) {
                needSystemProperties = true;
            } else if (arg.startsWith("--modules=")) {
                needModulesArg = arg.substring(arg.indexOf("=") + 1);
            } else {
                try {
                    @SuppressWarnings("unchecked")
                    Class<Callable<Map<String, String>>> c = (Class<Callable<Map<String, String>>>) Class.forName(arg);
                    Callable<Map<String, String>> o = c.getDeclaredConstructor().newInstance();
                    p.putAll(o.call());
                } catch (Throwable t) {
                    throw new ClientCodeException("Exception while calling user-specified class: " + arg, t);
                }
            }
        }

        if (needModulesArg != null && getJDKVersion() >= 9) {
            String modules = null;
            if (needModulesArg.equals("boot-layer")) {
                modules = getModulesFromBootLayer();
            } else if (needModulesArg.equals("all-system")) {
                modules = getAllSystemModules();
            }
            if (modules != null)
                p.put(JTREG_MODULES, modules);
        }

        if (needSystemProperties) {
            p.putAll(System.getProperties());
        }

        p.store(System.out, "jdk properties");
    }

    private static int getJDKVersion() {
        String specVersion = System.getProperty("java.specification.version");
        if (specVersion == null)
            return -1;
        if (specVersion.startsWith("1."))
            specVersion = specVersion.substring(2);
        if (specVersion.contains("."))
            specVersion = specVersion.substring(0, specVersion.indexOf("."));
        try {
            return Integer.parseInt(specVersion);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String getModulesFromBootLayer() {
        try {
            /*
             * ModuleLayer bootLayer = ModuleLayer.boot();
             * for (Module m : bootLayer.modules()) {
             *     if (sb.length() > ) sb.append(" ");
             *     sb.append(m.getName());
             * }
             */
            Class<?> layerClass;
            try {
                layerClass = Class.forName("java.lang.ModuleLayer");
            } catch (ClassNotFoundException e) {
                layerClass = Class.forName("java.lang.reflect.Layer");
            }
            Method bootMethod = layerClass.getDeclaredMethod("boot");
            Method modulesMethod = layerClass.getDeclaredMethod("modules");

            Class<?> moduleClass;
            try {
                moduleClass = Class.forName("java.lang.Module");
            } catch (ClassNotFoundException e) {
                moduleClass = Class.forName("java.lang.reflect.Module");
            }
            Method getNameMethod = moduleClass.getDeclaredMethod("getName");

            StringBuilder sb = new StringBuilder();
            Object bootLayer = bootMethod.invoke(null);
            for (Object module : (Set<?>) modulesMethod.invoke(bootLayer)) {
                if (sb.length() > 0)
                    sb.append(" ");
                sb.append(getNameMethod.invoke(module));
            }
            return sb.toString();
        } catch (ClassNotFoundException
                 | IllegalAccessException
                 | IllegalArgumentException
                 | NoSuchMethodException
                 | InvocationTargetException e) {
            return null;
        }
    }

    private static String getAllSystemModules() {
        try {
            /* ModuleFinder f = ModuleFinder.ofSystem();
             * for (ModuleReference mr : f.findAll()) {
             * ModuleDescriptor md = mr.descriptor();
             * if (sb.length() > 0 )
             * sb.append(" ");
             * sb.append(md.name());
             * }
             */

            Class<?> moduleFinderClass = Class.forName("java.lang.module.ModuleFinder");
            Method ofSystemMethod = moduleFinderClass.getDeclaredMethod("ofSystem");
            Method findAllMethod = moduleFinderClass.getDeclaredMethod("findAll");

            Class<?> moduleReferenceClass = Class.forName("java.lang.module.ModuleReference");
            Method descriptorMethod = moduleReferenceClass.getDeclaredMethod("descriptor");

            Class<?> moduleDescriptorClass = Class.forName("java.lang.module.ModuleDescriptor");
            Method nameMethod = moduleDescriptorClass.getDeclaredMethod("name");

            StringBuilder sb = new StringBuilder();
            Object systemModuleFinder = ofSystemMethod.invoke(null);
            for (Object mr : (Set<?>) findAllMethod.invoke(systemModuleFinder)) {
                Object md = descriptorMethod.invoke(mr);
                if (sb.length() > 0)
                    sb.append(" ");
                sb.append(nameMethod.invoke(md));
            }
            return sb.toString();
        } catch (ClassNotFoundException
                 | InvocationTargetException
                 | NoSuchMethodException
                 | IllegalArgumentException
                 | IllegalAccessException e) {
            return null;
        }
    }
}
