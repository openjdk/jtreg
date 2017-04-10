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
 * @run main BadSecurityManagerTest pass
 */

/*
 * @test
 * @run main BadSecurityManagerTest fail
 */

/*
 * @test
 * @run main BadSecurityManagerTest pass
 */

import java.security.*;

public class BadSecurityManagerTest {
    public static void main(String... args) throws Exception {
        new BadSecurityManagerTest().run(args);
    }

    void run(String... args) throws Exception {
        if (args[0].equals("pass"))
            return;

        System.setSecurityManager(new BadSecurityManager());
    }

    static class BadSecurityManager extends SecurityManager {
        // this method is a hack to ensure that ExitTest can run
        // if this test runs first and prevents ExitTest's security
        // manager from being set
        @Override
        public void checkExit(int rc) {
        }

        @Override
        public void checkPermission(Permission perm) {
            if (perm instanceof RuntimePermission) {
                if (perm.getName().equals("setSecurityManager")) {
                    throw new SecurityException("Action restricted by BadSecurityManagerTest");
                }
            }
        }
    }
}
