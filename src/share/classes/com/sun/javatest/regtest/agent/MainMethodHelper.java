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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Java program entry-point finder and runner.
 */
final class MainMethodHelper {
    // JEP 512 was introduced with JDK 25
    static boolean isCompactSourceFileAndInstanceMainMethodSupported() {
        return JDK_Version.forThisJVM().compareTo(JDK_Version.V25) >= 0;
    }

    // Similar to sun.launcher.LauncherHelper#executeMainClass
    static void executeMainClass(Class<?> mainClass, String[] classArgs) throws
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException {
        Method mainMethod = findMainMethod(mainClass);
        mainMethod.setAccessible(true);
        Object mainInstance = createMainInstanceOrNull(mainClass, mainMethod);
        if (mainMethod.getParameterCount() == 0) {
            mainMethod.invoke(mainInstance);
        } else {
            mainMethod.invoke(mainInstance, (Object) classArgs);
        }
    }

    // Similar to jdk.internal.misc.MethodFinder#findMainMethod
    static Method findMainMethod(Class<?> cls) throws NoSuchMethodException {
        List<Method> methods = Stream.of(cls.getDeclaredMethods())
                .filter(m -> "main".equals(m.getName()))
                .collect(Collectors.toList()); // no .toList() with --release 8

        // JLA.findMethod(cls, true, "main", String[].class);
        Method mainMethod = methods.stream()
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> method.getParameterTypes()[0] == String[].class)
                .findAny()
                .orElse(null);

        if (mainMethod == null) {
            // JLA.findMethod(cls, false, "main", String[].class);
            mainMethod = methods.stream()
                    .filter(method -> method.getParameterCount() == 1)
                    .filter(method -> method.getParameterTypes()[0] == String[].class)
                    .findAny()
                    .orElse(null);
        }

        if (mainMethod == null || !isValidMainMethod(mainMethod)) {
            // JLA.findMethod(cls, false, "main");
            mainMethod = methods.stream()
                    .filter(method -> method.getParameterCount() == 0)
                    .findAny()
                    .orElse(null);
        }

        if (mainMethod != null && isValidMainMethod(mainMethod)) {
            return mainMethod;
        }
        throw new NoSuchMethodException("main() nor main(String[])");
    }

    // Similar to jdk.internal.misc.MethodFinder#isValidMainMethod
    private static boolean isValidMainMethod(Method mainMethodCandidate) {
        return mainMethodCandidate.getReturnType() == void.class &&
               !Modifier.isPrivate(mainMethodCandidate.getModifiers());
    }

    // Similar to sun.launcher.LauncherHelper#checkAndLoadMain
    static Object createMainInstanceOrNull(Class<?> mainClass, Method mainMethod)  {
        boolean isStatic = Modifier.isStatic(mainMethod.getModifiers());
        if (isStatic) return null;
        try {
            Constructor<?> constructor = mainClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            e.printStackTrace(System.err);
            System.err.println();
            System.err.println("JavaTest Message: cannot instantiate class " + mainClass.getName());
            System.err.println();
            AStatus.error("Can't create an instance of: " + e).exit();
            return null; // unreachable
        }
    }
}
