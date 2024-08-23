/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Utilities for handling Processes.
 */
public class ProcessUtils {

    private static final Method DESTROY_FORCIBLY_METHOD;
    private static final Method PID_METHOD;

    private static final long UNKNOWN_PID = -1;

    static {
        Method destroyMethod;
        try {
            destroyMethod = Process.class.getDeclaredMethod("destroyForcibly");
        } catch (NoSuchMethodException e) {
            // expected on pre-1.8 JDKs
            destroyMethod = null;
        }
        DESTROY_FORCIBLY_METHOD = destroyMethod;

        Method pidMethod = null;
        try {
            pidMethod = Process.class.getDeclaredMethod("pid"); // only available in Java 9+
        } catch (NoSuchMethodException e) {
            pidMethod = null;
        }
        PID_METHOD = pidMethod;
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
        if (DESTROY_FORCIBLY_METHOD != null) {
            try {
                return (Process) DESTROY_FORCIBLY_METHOD.invoke(process);
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

    /**
     * Returns the process id of the {@code process}. If the process id cannot be determined
     * or if there was some exception when determining the process id, then this method returns
     * {@code -1}.
     *
     * @param process the process
     * @throws NullPointerException if {@code process} is null
     * @return the process id or -1
     */
    public static long getProcessId(Process process) {
        Objects.requireNonNull(process);
        if (PID_METHOD == null) {
            return UNKNOWN_PID;
        }
        try {
            return (long) PID_METHOD.invoke(process);
        } catch (Exception e) {
            return UNKNOWN_PID;
        }
    }
}
