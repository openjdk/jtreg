/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

public class ModuleHelper {
    static class Fault extends Exception {
        private static final long serialVersionUID = 1L;
        Fault(String message, Throwable t) {
            super(message, t);
        }
    }

    static void addModuleExports(Set<String> exports, ClassLoader loader) throws Fault {
        for (String e: exports) {
            int sep = e.indexOf("/");
            if (sep > 0) {
                int colon = e.indexOf(":", sep + 1);
                String moduleName = e.substring(0, sep);
                String packageName;
                boolean isOpen = false;
                if (colon == -1) {
                    packageName = e.substring(sep + 1);
                } else {
                    packageName = e.substring(sep + 1, colon);
                    String[] modifiers = StringArray.splitSeparator(",", e.substring(colon + 1));
                    for (String m : modifiers) {
                        if (m.equals("open") || m.equals("private")) {
                            isOpen = true;
                            break;
                        }
                    }
                }
                addModuleExport(moduleName, packageName, isOpen, loader);
            }
        }
    }

    /*
     * Use reflection to simulate:
     *      Optional<Module> opt_module = Layer.boot().findModule(moduleName);
     *      if (!opt_module.isPresent())
     *          throw new Fault();
     *      Module module = opt_module.get();
     *      Module targetModule = targetLoader.getUnnamedModule();
     *      JTRegModuleHelper.implAddExports(module, packageName, targetModule);
     */
    private static void addModuleExport(String moduleName, String packageName,
            boolean isOpen, ClassLoader targetLoader)
            throws Fault {
        try {
            init();

            /*
             *  Optional<Module> opt_module = bootLayer.findModule(moduleName);
             *  if (opt_module.isPresent())
             *      throw new Fault();
             */
            Object opt_module = findModuleMethod.invoke(bootLayer, new Object[] { moduleName });
            if (!((Boolean) isPresentMethod.invoke(opt_module, new Object[0]))) {
                throw new Fault("module not found: " + moduleName, null);
            }

            /*
             *  Module module = opt_module.get();
             */
            Object module = getMethod.invoke(opt_module, new Object[0]);

            /*
             *  Module targetModule = targetLoader.getUnnamedModule();
             */
            Object targetModule = getUnnamedModuleMethod.invoke(targetLoader, new Object[0]);

            /*
             *  JTRegModuleHelper.addExports(module, packageName, isPrivate, targetModule);
             */
            try {
                addExportsMethod.invoke(null, new Object[] { module, packageName, isOpen, targetModule });
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
            // new in Jave SE 9
            Class<?> layerClass;
            try {
                layerClass = Class.forName("java.lang.reflect.Layer");
            } catch (ClassNotFoundException e) {
                // temporary fallback
                layerClass = Class.forName("java.lang.module.Layer");
            }
            findModuleMethod = layerClass.getDeclaredMethod("findModule", new Class<?>[] { String.class });
            Method bootLayerMethod = layerClass.getDeclaredMethod("boot", new Class<?>[0]);

            /*
             *  Layer bootLayer = Layer.boot();
             */
            bootLayer = bootLayerMethod.invoke(null, new Object[0]);

            Class<?> helperClass = Class.forName("java.lang.reflect.JTRegModuleHelper");
            addExportsMethod = helperClass.getDeclaredMethod("addExports",
                    new Class<?>[] { Object.class, String.class, boolean.class, Object.class });

            getUnnamedModuleMethod = ClassLoader.class.getDeclaredMethod("getUnnamedModule", new Class<?>[0]);

            // new in Java SE 8
            Class<?> optionalClass = Class.forName("java.util.Optional");
            isPresentMethod = optionalClass.getDeclaredMethod("isPresent", new Class<?>[0]);
            getMethod = optionalClass.getDeclaredMethod("get", new Class<?>[0]);
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
    private static Object bootLayer;

    // on java.lang.ClassLoader
    private static Method getUnnamedModuleMethod;

    // on java.util.Optional
    private static Method isPresentMethod;
    private static Method getMethod;

    // on java.lang.reflect.JTRegModuleHelper
    private static Method addExportsMethod;
}
