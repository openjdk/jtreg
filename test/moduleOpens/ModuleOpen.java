/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @summary test reflective access to private API not permitted without :open
 * @modules jdk.compiler/com.sun.tools.javac.main
 * @run main/othervm --illegal-access=deny ModuleOpen -fail
 */

/*
 * @test
 * @summary test reflective access to private API is permitted with :+open
 * @modules jdk.compiler/com.sun.tools.javac.main:+open
 * @run main/othervm --illegal-access=deny ModuleOpen
 */

/*
 * @test
 * @summary test reflective access to private API is permitted by default in JDK 9
 * @modules jdk.compiler/com.sun.tools.javac.main
 * @run main ModuleOpen
 */

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import com.sun.tools.javac.main.Main;

public class ModuleOpen {
    public static void main(String... args) throws Exception {
        new ModuleOpen().run(args);
    }

    void run(String... args) throws Exception {
        boolean expectFail = args.length > 0 && args[0].equals("-fail");

        try {
            Main m = new Main("jtreg test");
            Field f = Main.class.getDeclaredField("fileManager");
            f.setAccessible(true);
            System.err.println(f.get(m));
            if (expectFail) {
                throw new Exception("expected exception not thrown");
            }
        } catch (Throwable e) {
            if (expectFail && e instanceof InaccessibleObjectException) {
                System.err.println("caught expected exception: " + e);
            } else {
                throw new Exception("unexpected exception: " + e, e);
            }
        }
    }
}


