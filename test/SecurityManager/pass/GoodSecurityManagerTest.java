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
 * @run main GoodSecurityManagerTest 1
 */

/*
 * @test
 * @run main GoodSecurityManagerTest 2
 */

/*
 * @test
 * @run main GoodSecurityManagerTest 3
 * @run main GoodSecurityManagerTest 4
 * @run main GoodSecurityManagerTest 5
 */

import java.security.Permission;

/*
 * Verify that a well behaved security manager can be installed.
 * Additional tests are run with different security managers, to verify
 * that different security managers can be installed for different tests.
 */
public class GoodSecurityManagerTest {
    public static void main(String... args) throws Exception {
        String id = args[0];
        System.setSecurityManager(new GoodSecurityManager(id));

        SecurityManager sm = System.getSecurityManager();
        if (sm instanceof GoodSecurityManager) {
            GoodSecurityManager gsm = (GoodSecurityManager) sm;
            System.out.println("Security manager: " + gsm.id);
            if (gsm.id != id)
                throw new Exception("Unexpected instance of GoodSecurityManager found: "
                                        + gsm.id + ", expected: " + id);
        } else {
            System.out.println("Security manager: " + sm.getClass().getName());
            throw new Exception("Unexpected security manager found: " + sm.getClass().getName());
        }
    }

    static class GoodSecurityManager extends SecurityManager {
        GoodSecurityManager(String id) {
            this.id = id;
        }

        @Override
        public void checkPermission(Permission perm) {
        }

        public final String id;
    }
}
