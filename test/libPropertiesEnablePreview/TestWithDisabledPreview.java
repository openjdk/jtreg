/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7903813
 * @enablePreview false
 * @library lib-with-preview
 * @build TestWithDisabledPreview WithPreview
 * @run main TestWithDisabledPreview
 */

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestWithDisabledPreview {
    public static void main(String... args) throws Exception {
        var location = TestWithDisabledPreview.class.getProtectionDomain().getCodeSource().getLocation();
        var classFile = Path.of(location.toURI()).resolve(TestWithDisabledPreview.class.getSimpleName() + ".class");
        try (var dis = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(classFile)))) {
            dis.skipBytes(4); // 0xCAFEBABE
            var minor = dis.readUnsignedShort();
            if (minor != 0) throw new AssertionError("Unexpected minor version: " + minor);
        }
    }
}
