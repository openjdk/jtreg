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
 * @modules jdk.compiler/com.sun.tools.javac.main
 * @run main ExportsPrivate -fail
 */

/*
 * @test
 * @summary test reflective access to private API is permitted with exports private
 * @modules jdk.compiler/com.sun.tools.javac.main:private
 * @run main ExportsPrivate
 */

import java.lang.reflect.Field;
import com.sun.tools.javac.main.Main;

public class ExportsPrivate {
    public static void main(String... args) throws Exception {
        new ExportsPrivate().run(args);
    }

    void run(String... args) throws Exception {
        boolean expectFail = args.length > 0 && args[0].equals("-fail");

        try {
            Main m = new Main("jtreg test");
            Field f = Main.class.getDeclaredField("fileManager");
            System.err.println(f.get(m));
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


