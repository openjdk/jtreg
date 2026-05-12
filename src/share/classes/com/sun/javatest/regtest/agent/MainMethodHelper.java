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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        Object mainInstance = createMainInstanceOrNull(mainClass, mainMethod);
        if (mainMethod.getParameterCount() == 0) {
            mainMethod.invoke(mainInstance);
        } else {
            mainMethod.invoke(mainInstance, (Object) mainArgs);
        }
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

    /**
     * Return the first method that meets the requirements of an application main method.
     * This behaves the same as {@link #findMainMethod(Class)}, except that:
     * <ul>
     *     <li>If no main method is found, then this method throws
     *     a {@link NoSuchMethodException}</li>
     *     <li>If a main method is found, then this method tries to set that method as accessible
     *     by calling {@link Method#trySetAccessible()} on it</li>
     * </ul>
     *
     * @param cls the class on which main method is being searched for
     * @return the first method that meets the requirements of an application main method
     * @throws NoSuchMethodException if the class doesn't have any method that qualifies as an
     *                               application main method
     */
    static Method requireMainMethod(Class<?> cls) throws NoSuchMethodException {
        Method mainMethod = findMainMethod(cls);
        if (mainMethod != null) {
            try {
                LazyHolder.TRY_SET_ACCESSIBLE_METHOD.invoke(mainMethod);
            } catch (ReflectiveOperationException ignored) {
            }
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
     * <p>
     * This method may be called only if {@link #isModernMainSupported()} returns {@code true}.
     *
     * @param cls the class on which main method is being searched for
     * @return the first method that meets the requirements of an application main method, or null
     * if none was found
     */
    // Similar to jdk.internal.misc.MethodFinder#findMainMethod
    static Method findMainMethod(Class<?> cls) {
        Method mainMethod = null;
        // find the most widely used "public static void main(String[])" first
        try {
            mainMethod = cls.getMethod("main", String[].class);
        } catch (NoSuchMethodException ignored) {
        }

        if (mainMethod == null) {
            // if not public method, try to lookup a non-public one which takes
            // a String[] parameter
            mainMethod = findMainMethod(cls, false, String[].class);
        }

        if (mainMethod == null || !isValidMainMethod(cls, mainMethod)) {
            // if not found, then look for public/non-public main method that
            // doesn't take any parameters
            mainMethod = findMainMethod(cls, false);
        }

        if (mainMethod == null || !isValidMainMethod(cls, mainMethod)) {
            // no eligible main method found
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


    private static String getPackageName(final Class<?> klass) {
        try {
            return (String) LazyHolder.PACKAGE_NAME_METHOD.invoke(klass);
        } catch (IllegalAccessException | InvocationTargetException e) {
            // should never happen
            throw new Error(e);
        }
    }

    private static Method findMainMethod(final Class<?> klass, final boolean publicOnly,
                                         final Class<?>... parameterTypes) {
        final List<Method> mainMethods = getMainMethodsRecursive(klass, parameterTypes,
                true, publicOnly);
        return mainMethods.isEmpty() ? null : mainMethods.get(0);
    }

    // this implementation is heavily borrowed from the JDK's Class.getMethodsRecursive()
    private static List<Method> getMainMethodsRecursive(final Class<?> klass,
                                                        final Class<?>[] parameterTypes,
                                                        final boolean includeStatic,
                                                        final boolean publicOnly) {
        final List<Method> res = new ArrayList<>();
        // first check declared methods
        final Method[] methods = getDeclaredMethods(klass, publicOnly);
        for (final Method m : methods) {
            if (Modifier.isStatic(m.getModifiers()) && !includeStatic) {
                // not an eligible method
                continue;
            }
            if (!m.getName().equals("main")) {
                // not an eligible method
                continue;
            }
            if (!Arrays.equals(m.getParameterTypes(), parameterTypes)) {
                // not an eligible method
                continue;
            }
            // eligible method
            res.add(m);
        }
        // if there is at least one match among declared methods, we need not
        // search any further as such match surely overrides matching methods
        // declared in superclass(es) or interface(s).
        if (!res.isEmpty()) {
            return res;
        }

        // if there was no match among declared methods,
        // we must consult the superclass (if any) recursively
        Class<?> superClass = klass.getSuperclass();
        if (superClass != null) {
            res.addAll(getMainMethodsRecursive(superClass, parameterTypes,
                    includeStatic, publicOnly));
        }
        // now from directly implemented interfaces excluding static methods
        for (final Class<?> intf : klass.getInterfaces()) {
            res.addAll(getMainMethodsRecursive(intf, parameterTypes, false, publicOnly));
        }
        return res;
    }

    private static Method[] getDeclaredMethods(final Class<?> klass, final boolean publicOnly) {
        final Method[] methods = klass.getDeclaredMethods();
        if (!publicOnly) {
            return methods;
        }
        final List<Method> publicMethods = new ArrayList<>();
        for (final Method m : methods) {
            if (Modifier.isPublic(m.getModifiers())) {
                publicMethods.add(m);
            }
        }
        return publicMethods.toArray(new Method[0]);
    }

    // Direct reference to Class.getPackageName() and AccessibleObject.trySetAccessible()
    // isn't possible because this MainMethodHelper class may run in Java 8 environments where those
    // methods aren't available.
    // This is a convenience holder class which provides reflective access to those methods and
    // delays the reflective lookup to them until after it's certain that the runtime
    // environment is Java 25 or higher (where modern main methods are applicable)
    private static final class LazyHolder {
        // reflective access to Class.getPackageName() method which is only available on Java 9+
        private static final Method PACKAGE_NAME_METHOD;
        // reflective access to AccessibleObject.trySetAccessible() method which
        // is only available on Java 9+
        private static final Method TRY_SET_ACCESSIBLE_METHOD;

        static {
            try {
                PACKAGE_NAME_METHOD = Class.class.getMethod("getPackageName");
            } catch (NoSuchMethodException e) {
                // This should never happen because this static initializer will only
                // be called on Java versions 25 or higher and those versions are
                // expected to have the Class.getPackageName() method (it's there since Java 9)
                throw new Error("Missing Class.getPackageName() method");
            }

            try {
                TRY_SET_ACCESSIBLE_METHOD = AccessibleObject.class.getMethod("trySetAccessible");
            } catch (NoSuchMethodException e) {
                // This should never happen because this static initializer will only
                // be called on Java versions 25 or higher and those versions are
                // expected to have the AccessibleObject.trySetAccessible() method
                // (it's there since Java 9)
                throw new Error("Missing AccessibleObject.trySetAccessible() method");
            }
        }
    }
}
