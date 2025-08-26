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

import java.util.Map;
import java.util.Set;

/*
 * @test
 * @run main EnvVarAssertTest
 */
public class EnvVarAssertTest {

    private static final Set<String> UNIX_EXPECTED_ENV_VARS = Set.of(
            "XAUTHORITY", "PRINTER", "DESKTOP_SESSION"
    );

    private static final Set<String> WINDOWS_EXPECTED_ENV_VARS = Set.of(
            "SystemRoot", "windir"
    );

    public static void main(final String[] args) throws Exception {
        final String os = System.getProperty("os.name");
        if (os.startsWith("Linux") || os.startsWith("Mac")) {
            assertEnvVars(UNIX_EXPECTED_ENV_VARS);
        } else if (os.startsWith("Windows")) {
            assertEnvVars(WINDOWS_EXPECTED_ENV_VARS);
        } else {
            throw new jtreg.SkippedException("Skipping test on OS: " + os);
        }
    }

    private static void assertEnvVars(final Set<String> expected) {
        final Map<String, String> actual = System.getenv();
        for (final String envVar : expected) {
            if (!actual.containsKey(envVar)) {
                throw new AssertionError("environment variable \"" + envVar + "\" is not set");
            }
            final String val = actual.get(envVar);
            if (val == null) {
                throw new AssertionError("null value for environment variable \"" + envVar);
            }
            System.out.println("found expected environment variable \""
                    + envVar + "\", set to \"" + val + "\"");
        }
    }
}
