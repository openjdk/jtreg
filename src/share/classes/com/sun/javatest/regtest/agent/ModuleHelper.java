/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class ModuleHelper {
    static class Fault extends Exception {
        private static final long serialVersionUID = 1L;
        Fault(String message, Throwable t) {
            super(message, t);
        }
    }

    static void addExports(Set<String> exports, ClassLoader loader) throws Fault {
        for (String e : exports) {
            addExportsOrOpens(e, false, loader);
        }
    }

    static void addOpens(Set<String> opens, ClassLoader loader) throws Fault {
        for (String o : opens) {
            addExportsOrOpens(o, true, loader);
        }
    }

    static ClassLoader addModules(List<File> libs, Set<String> modules) throws Fault {
        init();
        List<Object> paths = new ArrayList<Object>();
        try {
            for (File lib : libs) {
                for (File file : lib.listFiles()) {
                    paths.add(toPathMethod.invoke(file));
                }
            }
            /*
            java.lang.module.ModuleFinder finder = ModuleFinder.of(paths);
            java.lang.module.ModuleFinder emptyFinder = ModuleFinder.of();
            java.lang.module.Configuration bootConfig = bootLayer.configuration();

            java.lang.module.Configuration config = bootConfig.resolve(finder, ModuleFinder.of(), modules);
            ModuleLayer layer = bootLayer.defineModulesWithOneLoader(configuration, ClassLoader.getSystemClassLoader());
            ClassLoader result = layer.findLoader(modules.iterator().next());
            */
            Object finder = moduleFinderOfMethod.invoke(null, new Object[] { paths.toArray(emptyArrayOfPaths) });
            Object emptyFinder = moduleFinderOfMethod.invoke(null,  new Object[] { emptyArrayOfPaths });
            Object bootConfig = configurationMethod.invoke(bootLayer);
            Object config = resolveMethod.invoke(bootConfig, finder, emptyFinder, modules);
            Object layer = defineModulesWithOneLoaderMethod.invoke(bootLayer, config, ClassLoader.getSystemClassLoader());

            return (ClassLoader) findLoaderMethod.invoke(layer, modules.iterator().next());
        } catch (SecurityException e) {
            throw new Fault("unexpected exception: " + e, e);
        } catch (IllegalAccessException e) {
            throw new Fault("unexpected exception: " + e, e);
        } catch (IllegalArgumentException e) {
            throw new Fault("unexpected exception: " + e, e);
        } catch (InvocationTargetException e) {
            throw new Fault("unexpected exception: " + e, e);
        }

    }

    /*
     * Use reflection to simulate:
     *      Optional<Module> opt_module = Layer.boot().findModule(moduleName);
     *      if (!opt_module.isPresent())
     *          throw new Fault();
     *      Module module = opt_module.get();
     *      Module targetModule = targetLoader.getUnnamedModule();
     * then one of:
     *      JTRegModuleHelper.implAddExports(module, packageName, targetModule);
     *      JTRegModuleHelper.implAddpens(module, packageName, targetModule);
     */
    private static void addExportsOrOpens(String modulePackageName,
            boolean isOpen, ClassLoader targetLoader)
            throws Fault {
        int sep = modulePackageName.indexOf("/");
        String moduleName = modulePackageName.substring(0, sep);
        String packageName = modulePackageName.substring(sep + 1);
        try {
            init();

            /*
             *  Optional<Module> opt_module = bootLayer.findModule(moduleName);
             *  if (opt_module.isPresent())
             *      throw new Fault();
             */
            Object opt_module = findModuleMethod.invoke(bootLayer, moduleName);
            if (!((Boolean) isPresentMethod.invoke(opt_module, new Object[0]))) {
                throw new Fault("module not found: " + moduleName, null);
            }

            /*
             *  Module module = opt_module.get();
             */
            Object module = getMethod.invoke(opt_module);

            /*
             *  Module targetModule = targetLoader.getUnnamedModule();
             */
            Object targetModule = getUnnamedModuleMethod.invoke(targetLoader);

            /*
             *  Call one of:
             *      JTRegModuleHelper.addExports(module, packageName, isPrivate, targetModule);
             *      JTRegModuleHelper.addOpens(module, packageName, isPrivate, targetModule);
             */
            try {
                Method m = isOpen ? addOpensMethod : addExportsMethod;
                m.invoke(null, module, packageName, targetModule);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof IllegalArgumentException) {
                    String msg = e.getCause().getMessage();
                    throw new Fault("package not found: " + packageName + " (" + msg + ")", null);
                } else {
                    throw new Fault("unexpected exception: " + e, e);
                }
            }
        } catch (SecurityException e) {
            throw new Fault("unexpected exception: " + e, e);
        } catch (IllegalAccessException e) {
            throw new Fault("unexpected exception: " + e, e);
        } catch (IllegalArgumentException e) {
            throw new Fault("unexpected exception: " + e, e);
        } catch (InvocationTargetException e) {
            throw new Fault("unexpected exception: " + e, e);
        }
    }

    private static synchronized void init() throws Fault {
        if (bootLayer != null)
            return;

        try {
            // new in Java SE 7
            Class<?> pathClass = Class.forName("java.nio.file.Path");
            toPathMethod = File.class.getDeclaredMethod("toPath");
            emptyArrayOfPaths = (Object[]) Array.newInstance(pathClass, 0);
            Class<?> pathArrayClass = emptyArrayOfPaths.getClass();

            // new in Java SE 8
            Class<?> optionalClass = Class.forName("java.util.Optional");
            isPresentMethod = optionalClass.getDeclaredMethod("isPresent");
            getMethod = optionalClass.getDeclaredMethod("get");

            // new in Jave SE 9
            Class<?> moduleFinderClass = Class.forName("java.lang.module.ModuleFinder");
            moduleFinderOfMethod = moduleFinderClass.getDeclaredMethod("of", pathArrayClass);

            Class<?> configurationClass = Class.forName("java.lang.module.Configuration");
            resolveMethod = configurationClass.getDeclaredMethod("resolve", moduleFinderClass, moduleFinderClass, Collection.class);

            Class<?> layerClass = Class.forName("java.lang.ModuleLayer");
            findModuleMethod = layerClass.getDeclaredMethod("findModule", String.class);
            configurationMethod = layerClass.getDeclaredMethod("configuration");
            defineModulesWithOneLoaderMethod = layerClass.getDeclaredMethod("defineModulesWithOneLoader", configurationClass, ClassLoader.class);
            findLoaderMethod = layerClass.getDeclaredMethod("findLoader", String.class);
            Method bootLayerMethod = layerClass.getDeclaredMethod("boot");

            /*
             *  Layer bootLayer = Layer.boot();
             */
            bootLayer = bootLayerMethod.invoke(null);

            Class<?> helperClass = Class.forName("java.lang.JTRegModuleHelper");
            addExportsMethod = helperClass.getDeclaredMethod("addExports",
                    Object.class, String.class, Object.class);
            addOpensMethod = helperClass.getDeclaredMethod("addOpens",
                    Object.class, String.class, Object.class);

            getUnnamedModuleMethod = ClassLoader.class.getDeclaredMethod("getUnnamedModule");

        } catch (ClassNotFoundException e) {
            throw new Fault("unexpected exception: " + e, e);
        } catch (NoSuchMethodException e) {
            throw new Fault("unexpected exception: " + e, e);
        } catch (SecurityException e) {
            throw new Fault("unexpected exception: " + e, e);
        } catch (IllegalAccessException e) {
            throw new Fault("unexpected exception: " + e, e);
        } catch (IllegalArgumentException e) {
            throw new Fault("unexpected exception: " + e, e);
        } catch (InvocationTargetException e) {
            throw new Fault("unexpected exception: " + e, e);
        }

    }

    // on java.lang.module.Layer
    private static Method findModuleMethod;
    private static Method configurationMethod;
    private static Method defineModulesWithOneLoaderMethod;
    private static Method findLoaderMethod;
    private static Object bootLayer;

    // on java.lang.module.ModuleFinder
    private static Method moduleFinderOfMethod;

    // on java.lang.module.Configuration
    private static Method resolveMethod;

    // on java.lang.ClassLoader
    private static Method getUnnamedModuleMethod;

    // on java.util.Optional
    private static Method isPresentMethod;
    private static Method getMethod;

    // on java.io.File
    private static Method toPathMethod;

    // on java.nio.file.Path
    private static Object[] emptyArrayOfPaths;

    // on java.lang.JTRegModuleHelper or java.lang.reflect.JTRegModuleHelper
    private static Method addExportsMethod;
    private static Method addOpensMethod;
}
