/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @run main PropertiesTest  1 false
 */

/*
 * @test
 * @run main PropertiesTest  2 false
 */

/*
 * @test
 * @run main PropertiesTest  3 false
 */

/*
 * @test
 * @run main PropertiesTest  4 true
 */

/*
 * @test
 * @run main PropertiesTest  5 true
 */

/*
 * @test
 * @run main PropertiesTest  6 true
 */

/*
 * @test
 * @run main PropertiesTest  7 false
 * @run main PropertiesTest  8 false
 * @run main PropertiesTest  9 false
 * @run main PropertiesTest 10 true
 * @run main PropertiesTest 11 true
 * @run main PropertiesTest 12 true
 * @run main PropertiesTest 13 false
 * @run main PropertiesTest 14 true
 * @run main PropertiesTest 15 false
 */

import java.util.Properties;

/*
 * Verify that tests that set properties do not interfere
 * with one another, especially in samevm mode.
 */
public class PropertiesTest {
    public static void main(String... args) throws Exception {
        String id = args[0];
        boolean reset = Boolean.valueOf(args[1]);
        new PropertiesTest().run(id, reset);
    }

    void run(String id, boolean reset) throws Exception {
        System.err.println("Checking " + id);
        for (int i = 0; i < 20; i++) {
            String p = PREFIX + i;
            String v = System.getProperty(p);
            if (v != null)
                throw new Error("test " + id + ": unexpected property found: " +  p + "=" + v);
        }
        if (reset) {
            System.err.println("reset properties");
            System.setProperties(new Properties());
        }
        System.setProperty(PREFIX + id, id);
    }

    final String PREFIX = "test.property.";
}




