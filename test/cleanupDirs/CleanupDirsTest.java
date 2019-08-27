/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @run main CleanupDirsTest writable writable writable
 */

/*
 * @test
 * @run main CleanupDirsTest writable writable readonly
 */
/*
 * @test
 * @run main CleanupDirsTest writable readonly writable
 */

/*
 * @test
 * @run main CleanupDirsTest writable readonly readonly
 */

/*
 * @test
 * @run main CleanupDirsTest readonly writable writable
 */

/*
 * @test
 * @run main CleanupDirsTest readonly writable readonly
 */
/*
 * @test
 * @run main CleanupDirsTest readonly readonly writable
 */

/*
 * @test
 * @run main CleanupDirsTest readonly readonly readonly
 */


import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;

public class CleanupDirsTest {
    public static void main(String... args) throws Exception {
        new CleanupDirsTest().run(args);
    }

    void run(String... args) throws Exception {
        File userDir = new File(System.getProperty("user.dir"));
        createFiles(userDir, Arrays.asList(args));
        listFiles(userDir, 0);
    }

    void createFiles(File dir, List<String> names) throws Exception {
        File f = new File(dir, names.get(0) + (names.size() > 1 ? ".d" : ".f"));

        if (names.size() == 1) {
            try (FileOutputStream out = new FileOutputStream(f)) { }
        } else {
            if (!f.mkdir()) {
                throw new Exception("can't create directory " + f);
            }
            createFiles(f, names.subList(1, names.size()));
        }

        if (f.getName().startsWith("r")) {
            if (!f.setReadOnly()) {
                throw new Exception("can't set file readonly " + f);
            }
        }
    }

    void listFiles(File f, int depth) {
        System.out.println(repeat("  ", depth)
            + (depth == 0 ? f.getPath() : f.getName()) + " "
            + (f.canWrite() ? "w" : "r"));
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                listFiles(c, depth + 1);
            }
        }
    }

    String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
