/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @run main FileDescriptorTest
 */

import java.io.FileDescriptor;
import java.io.FileWriter;
import java.io.IOException;

/*
 * Write directly to the file descriptors.
 */
public class FileDescriptorTest {
    public static void main(String... args) throws IOException {
        write("stdout", FileDescriptor.out);
        write("stderr", FileDescriptor.err);
    }

    static void write(String s, FileDescriptor fd) throws IOException {
        // NOTE: IMPORTANT: do not close the underlying file descriptors!
        FileWriter out = new FileWriter(fd);
        out.write("This is being written to ");
        out.write(s);
        out.write("\n");
        out.flush();
    }
}
