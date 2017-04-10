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

// Note:
// This test currently relies on being run with a clean work/classes directory

/* @test
 * @library /lib
 * @run main Test Test
 * @build A LA
 * @run main Test Test A LA
 * @build *
 * @run main Test Test A B C LA LB LC
 * @build p.PA p.LPA
 * @run main Test Test A B C LA LB LC p.PA p.LPA
 * @build p.*
 * @run main Test Test A B C LA LB LC p.PA p.PB p.PC p.LPA p.LPB p.LPC
 */

import java.io.*;
import java.util.*;

public class Test {
    public static void main(String... args) throws Exception {
        Set<String> expect = new LinkedHashSet<String>(Arrays.asList(args));
        Set<String> found = listClasses();
        if (found.equals(expect))
            System.err.println("expected files found: " + found);
        else {
            System.err.println("expect: " + expect);
            System.err.println("found: " + found);
            throw new Exception("expected files not found");
        }
    }

    static Set<String> listClasses() {
        Set<String> classes = new LinkedHashSet<String>();
        String tcp = System.getProperty("test.class.path");
        for (String d: tcp.split(File.pathSeparator)) {
                System.err.println("listing " + d);
            File dir = new File(d);
            if (dir.exists())
                listClasses(dir, dir, classes);
        }
        return classes;
    }

    static void listClasses(File base, File dir, Set<String> classes) {
                System.err.println("listing " + base + " " + dir + " " + classes);
        for (File f: dir.listFiles()) {
                System.err.println("checking " + f);
            if (f.isDirectory()) {
                listClasses(base, f, classes);
            } else if (f.isFile() && f.getName().endsWith(".class")) {
                String c = base.toURI().relativize(f.toURI()).getPath()
                    .replace(".class", "")
                    .replace(File.separator, ".");
                classes.add(c);
            }
        }
    }

}
