/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * @test
 * @run main T6519296 0
 */

/*
 * @test
 */

/*
 * @test
 * @run main T6519296 1
 */

/*
 * @test
 */

/*
 * @test
 * @run main T6519296 2
 */

/*
 * @test
 */

/*
 * @test
 * @run main T6519296 3
 */

/*
 * @test
 */

/*
 * @test
 * @run main T6519296 4
 */

/*
 * @test
 */

import java.util.Properties;

public class T6519296 {
    public static void main(String... args) {
        if (args.length == 0) {
            if (System.getProperty("os.name") == null)
                throw new Error("os.name not set");
            if (System.getProperty("os.name").equals(NOT_AN_OS))
                throw new Error("os.name set incorrectly");
            if (System.getProperty("xyzzy") != null)
                throw new Error("xyzzy is set");
            if (System.getProperty("line.separator").length() > 2)
                throw new Error("line.separator set badly");
        }
        else {
            switch (Integer.parseInt(args[0])) {
            case 0:
                System.err.println("set properties to null");
                System.setProperties(null);
                break;
            case 1:
                System.err.println("set properties to empty properties");
                System.setProperties(new Properties());
                break;
            case 2:
                System.err.println("set standard property to bad value");
                System.getProperties().put("os.name", NOT_AN_OS);
                break;
            case 3:
                System.err.println("set user property");
                System.getProperties().put("xyzzy", "fubar");
                break;
            case 4:
                System.err.println("set line separator");
                System.getProperties().put("line.separator", "JTNL");
                break;
            }
        }
    }

    private static final String NOT_AN_OS = "invalid OS";
}
