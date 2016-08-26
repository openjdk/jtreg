/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary test reflective access to private API not permitted without exports private
 * @modules jdk.compiler/com.sun.tools.javac.main:dynamic
 * @run main ExportsDynamicPrivate -fail
 */

/*
 * @test
 * @summary test reflective access to private API is permitted with exports private
 * @modules jdk.compiler/com.sun.tools.javac.main:dynamic,private
 * @run main ExportsDynamicPrivate
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class ExportsDynamicPrivate {
    public static void main(String... args) throws Exception {
        new ExportsDynamicPrivate().run(args);
    }

    void run(String... args) throws Exception {
        boolean expectFail = args.length > 0 && args[0].equals("-fail");

        try {
            Class<?> mainClass = Class.forName("com.sun.tools.javac.main.Main");
            Constructor constr = mainClass.getDeclaredConstructor(String.class);
            Object javacMain = constr.newInstance("jtreg test");
            System.err.println("Created main class: " + javacMain);

            Field f = mainClass.getDeclaredField("fileManager");
            System.err.println(f.get(mainClass));
            if (expectFail) {
                throw new Exception("expected exception not thrown");
            }
        } catch (Throwable e) {
            if (expectFail && e instanceof IllegalAccessException) {
                System.err.println("caught expected exception: " + e);
            } else {
                throw new Exception("unexpected exception: " + e, e);
            }
        }
    }
}


