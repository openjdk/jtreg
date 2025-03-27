/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @library /lib
 * @build Lib PreviewTest
 * @run main PreviewTest
 */

import java.io.ByteArrayOutputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;

public class PreviewTest {
  public static void main(String... args) {
    System.err.println(new Lib());
    var model = parseClassModel(Lib.class);
    if (model.minorVersion() != ClassFile.PREVIEW_MINOR_VERSION) {
      throw new AssertionError(Lib.class + " not in preview: " + model.toDebugString());
    }
  }

  static ClassModel parseClassModel(Class<?> type) {
    return parseClassModel(type, ClassFile.of());
  }

  static ClassModel parseClassModel(Class<?> type, ClassFile context) {
    var name = type.getName().replace('.', '/') + ".class";
    try (var in = type.getClassLoader().getResourceAsStream(name)) {
      if (in == null) throw new RuntimeException("Resource not found: " + name);
      var out = new ByteArrayOutputStream(1024);
      in.transferTo(out);
      return context.parse(out.toByteArray());
    } catch (Exception exception) {
      throw new RuntimeException("Reading resource failed: " + name, exception);
    }
  }
}
