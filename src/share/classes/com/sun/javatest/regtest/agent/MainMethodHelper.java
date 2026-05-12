/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * Java program entry-point finder and runner.
 */
final class MainMethodHelper {
    /// @return {@code true}, if the current VM is capable of running compact
    /// source files and instance main methods.
    static boolean isModernMainSupported() {
        return JDK_Version.forThisJVM().compareTo(JDK_Version.V25) >= 0;
    }

    // Similar to sun.launcher.LauncherHelper#executeMainClass
    static void executeModernMainClass(Class<?> mainClass, String[] mainArgs) throws
            ReflectiveOperationException {
        Method mainMethod = requireMainMethod(mainClass);
        mainMethod.setAccessible(true);
        Object mainInstance = createMainInstanceOrNull(mainClass, mainMethod);
        if (mainMethod.getParameterCount() == 0) {
            mainMethod.invoke(mainInstance);
        } else {
            mainMethod.invoke(mainInstance, (Object) mainArgs);
        }
    }

    /**
     * Return the first method that meets the requirements of an application main method.
     * This behaves the same as {@link #findMainMethod(Class)}, except that if any main method
     * isn't found, then this method throws a {@link NoSuchMethodException}.
     *
     * @param cls the class on which main method is being searched for
     * @return the first method that meets the requirements of an application main method
     * @throws NoSuchMethodException if the class doesn't have any method that qualifies as an
     *                               application main method
     */
    static Method requireMainMethod(Class<?> cls) throws NoSuchMethodException {
        Method mainMethod = findMainMethod(cls);
        if (mainMethod != null) {
            return mainMethod;
        }
        throw new NoSuchMethodException("No main method found in " + cls);
    }

    /**
     * Return the first method that meets the requirements of an application main method
     * {@code JLS 12.1.4}. The method must:
     * <ul>
     *  <li>be declared in this class's hierarchy</li>
     *  <li>have the name "main"</li>
     *  <li>have a single argument of type {@code String[]}, {@code String...} or no argument</li>
     *  <li>have the return type of void</li>
     *  <li>be public, protected or package private</li>
     * </ul>
     *
     * @param cls the class on which main method is being searched for
     * @return the first method that meets the requirements of an application main method, or null
     * if none was found
     */
    // Similar to jdk.internal.misc.MethodFinder#findMainMethod
    static Method findMainMethod(Class<?> cls) {
        Method mainMethod = null;
        // first try "public static/non-static void main(String[])"
        try {
            mainMethod = cls.getMethod("main", String[].class);
        } catch (NoSuchMethodException ignored) {
        }

        if (mainMethod == null) {
            // if not public method, try to lookup a non-public one
            try {
                mainMethod = cls.getDeclaredMethod("main", String[].class);
            } catch (NoSuchMethodException ignored) {
            }
        }

        if (mainMethod == null || !isValidMainMethod(cls, mainMethod)) {
            // if not found, then ignore the param types and search for
            // any public/non-public method named "main"
            Method[] declaredMethods = cls.getDeclaredMethods();
            for (Method m : declaredMethods) {
                if (m.getName().equals("main")) {
                    mainMethod = m;
                    break;
                }
            }
        }
        if (mainMethod == null || !isValidMainMethod(cls, mainMethod)) {
            return null;
        }
        return mainMethod;
    }

    // Similar to jdk.internal.misc.MethodFinder#isValidMainMethod
    private static boolean isValidMainMethod(Class<?> initialClass, Method mainMethodCandidate) {
        return mainMethodCandidate.getReturnType() == void.class &&
                !Modifier.isPrivate(mainMethodCandidate.getModifiers()) &&
                (Modifier.isPublic(mainMethodCandidate.getModifiers()) ||
                        Modifier.isProtected(mainMethodCandidate.getModifiers()) ||
                        isInSameRuntimePackage(initialClass, mainMethodCandidate.getDeclaringClass()));
    }

    // Similar to jdk.internal.misc.MethodFinder#isInSameRuntimePackage
    private static boolean isInSameRuntimePackage(Class<?> c1, Class<?> c2) {
        String pkg1 = getPackageName(c1);
        String pkg2 = getPackageName(c2);
        return Objects.equals(pkg1, pkg2)
                && c1.getClassLoader() == c2.getClassLoader();
    }

    // Similar to sun.launcher.LauncherHelper#checkAndLoadMain
    static Object createMainInstanceOrNull(Class<?> mainClass, Method mainMethod) throws
            NoSuchMethodException,
            InvocationTargetException,
            InstantiationException,
            IllegalAccessException {
        boolean isStatic = Modifier.isStatic(mainMethod.getModifiers());
        if (isStatic) return null;
        Constructor<?> constructor = mainClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static String getPackageName(final Class<?> klass) {
        try {
            return (String) LazyHolder.PACKAGE_NAME_METHOD.invoke(klass);
        } catch (IllegalAccessException | InvocationTargetException e) {
            // should never happen
            throw new Error(e);
        }
    }

    // Direct reference to Class.getPackageName() isn't possible because
    // this MainMethodHelper class may run in Java 8 environments where that method isn't available.
    // This is a convenience holder class which provides reflective access to that method and
    // delays the reflective lookup to that method until after it's certain that the runtime
    // environment is Java 25 or higher (where modern main methods are applicable)
    private static final class LazyHolder {
        // reflective access to Class.getPackageName() method which is only available on Java 9+
        private static final Method PACKAGE_NAME_METHOD;

        static {
            Method m;
            try {
                m = Class.class.getMethod("getPackageName");
            } catch (NoSuchMethodException e) {
                // This should never happen because this static initializer will only
                // be called on Java versions 25 or higher and those versions are
                // expected to have the Class.getPackageName() method (it's there since Java 9)
                throw new Error("Missing Class.getPackageName() method");
            }
            PACKAGE_NAME_METHOD = m;
        }
    }
}
