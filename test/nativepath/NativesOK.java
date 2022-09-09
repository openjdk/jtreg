/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @run main/native NativesOK
 */

import java.io.File;
import java.util.Arrays;

public class NativesOK {
    public static void main(String[] args) {
        String j_l_path = System.getProperty("java.library.path");
        String t_native = System.getProperty("test.nativepath");
        String c_native = System.getProperty("correct.nativepath").replace("/", File.separator);

        System.out.println("java.library.path: " + j_l_path);
        System.out.println("test.nativepath: " + t_native);
        System.out.println("correct.nativepath: " + c_native);

        if (!t_native.equals(c_native))
            throw new Error("System property 'test.nativepath' not set correctly");
        if (j_l_path == null)
            throw new Error("System property 'java.library.path' not set");

        String[] paths = j_l_path.split("\\"+File.pathSeparator);
        if (!Arrays.asList(paths).contains(t_native))
            throw new Error("System property 'test.nativepath' is not part of 'java.library.path'");
    }
}
