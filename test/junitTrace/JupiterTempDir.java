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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/*
 * @test
 * @bug 7903953
 * @run junit JupiterTempDir
 */
class JupiterTempDir {
    @Test
    void currentWorkingDirectoryIsParentOfTemporaryDirectory(@TempDir Path temporary) {
        // Verify that this test was launched through jtreg launcher, which sets the `test.file`
        // system property. Find a list of them at https://openjdk.org/jtreg/tag-spec.html#testvars
        assumeTrue(System.getProperty("test.file") != null, "jtreg launched this test");

        assertTrue(Files.isDirectory(temporary), "temporary directory expected");

        var currentWorkingDirectory = Path.of("");
        var expected = currentWorkingDirectory.toAbsolutePath().toString();
        var actual = temporary.getParent().toAbsolutePath().toString();

        assertEquals(expected, actual, "unexpected parent directory for temporary files");
    }
}
