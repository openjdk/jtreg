/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.security.Provider;
import java.security.Security;

/*
 * This class provides the common code for the tests A, B, C
 */
public class Test {

    static class TestProvider extends Provider {
        TestProvider(String name, double version, String info) {
            super(name, version, info);
        }
    }

    public static void main(String... args) throws Exception {
        new Test().run(args[0], Double.parseDouble(args[1]), args[2]);
    }

    public void run(String name, double version, String info) throws Exception {
        TestProvider tp = new TestProvider(name, version, info);

        System.out.println("installing provider " + tp);
        Security.addProvider(tp);

        for (Provider p: Security.getProviders()) {
            System.out.println(p);

            if (p == tp)
                continue;

            if (p.getClass().getName().equals(TestProvider.class.getName()))
                error("unexpected provider found: " + p);
        }

        if (errors > 0)
            throw new Exception(errors + " errors occurred");
    }

    void error(String msg) {
        System.err.println("Error: " + msg);
        errors++;
    }

    int errors = 0;
}
