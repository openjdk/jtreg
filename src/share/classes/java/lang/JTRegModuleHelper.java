/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.lang.reflect.Method;

/**
 * Provide access to internal addExports support.
 *
 * <i>This code is temporary, and should eventually be replaced by
 * injecting code into the relevant modules.</i>
 */
public final class JTRegModuleHelper {

    // Non-instantiable helper
    private JTRegModuleHelper() {
    }

    /*
     * Use reflection to simulate:
     *      module.implAddExports(packageName, targetModule);
     */
    public static void addExports(Object module, String packageName, Object targetModule)
            throws ReflectiveOperationException {
        if (addExportsMethod == null) {
            addExportsMethod = getModuleMethod("implAddExports");
        }
        addExportsMethod.invoke(module, packageName, targetModule);
    }

    /*
     * Use reflection to simulate:
     *      module.implAddOpens(packageName, targetModule);
     */
    public static void addOpens(Object module, String packageName, Object targetModule)
            throws ReflectiveOperationException {
        if (addOpensMethod == null) {
            addOpensMethod = getModuleMethod("implAddOpens");
        }
        addOpensMethod.invoke(module, packageName, targetModule);
    }

    private static Method getModuleMethod(String name) throws ReflectiveOperationException {
        Class<?> moduleClass = Class.forName("java.lang.Module");
        return moduleClass.getDeclaredMethod(name, String.class, moduleClass);
    }

    // on java.lang.Module
    private static Method addExportsMethod;
    private static Method addOpensMethod;
}
