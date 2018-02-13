/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @run main Test
 */

/*
 * @test
 * @run driver Test
 */

import java.util.*;

public class Test {
    public static void main(String... args) throws Exception {
        TreeMap<String,String> sortedMap = new TreeMap<>();
        for (Map.Entry<?,?> e : System.getProperties().entrySet()) {
            String k = (String) e.getKey(), v = (String) e.getValue();
            sortedMap.put(k, v);
        }

        for (Map.Entry<String,String> e : sortedMap.entrySet()) {
            String before = ">>> ", after = " <<<";
            if (e.getKey().startsWith("test.")) {
                if (e.getKey().contains("config")) {
                    before = "**> "; after = " <**";
                } else {
                    before = "*>> "; after = " <<*";
                }
            }
            System.out.println(before + e.getKey() + " : " + e.getValue() + after);
        }

        String v = System.getProperties().getProperty("test.config", "<unset>");
        if (!v.equals("config")) {
            throw new Exception("test.config not found or set correctly: " + v);
        }
    }
}

