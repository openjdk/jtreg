/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utilities for handling Processes.
 */
public class ProcessUtils {

    private static Method destroyForciblyMethod;

    static {
        try {
            destroyForciblyMethod = Process.class.getDeclaredMethod("destroyForcibly");
        } catch (NoSuchMethodException e) {
            // expected on pre-1.8 JDKs
        }
    }

    /**
     * Call Process.destroyForcibly() if that method exists. If it does not,
     * then Process.destroy() will be called instead.
     *
     * Process.destroyForcibly() was introduced in 1.8.
     *
     * @param process the Process to destroy
     * @return the Process object
     */
    public static Process destroyForcibly(Process process) {
        if (destroyForciblyMethod != null) {
            try {
                return (Process) destroyForciblyMethod.invoke(process);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw (RuntimeException) e.getTargetException();
            }
        }
        // fallback
        process.destroy();
        return process;
    }
}
