/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.javatest.regtest.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import com.sun.javatest.TestEnvironment;

public class RegressionEnvironment extends TestEnvironment
{
    RegressionEnvironment(RegressionParameters params) throws Fault {
        super("regtest", new ArrayList<>(), new String[] { });
        this.params = params;
    }

    private RegressionEnvironment(RegressionEnvironment other) {
        super(other);
        this.params = other.params;
    }

    @Override
    public TestEnvironment copy() {
        return new RegressionEnvironment(this);
    }

    /**
     * {@return the hostname of the host in which this test harness is being run}
     */
    public final String getHostName() {
        return CachedCanonicalHostName.HOST_NAME;
    }

    public final RegressionParameters params;


    private static final class CachedCanonicalHostName {
        private static final String HOST_NAME;

        static {
            String hostname;
            try {
                hostname = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException e) {
                hostname = "127.0.0.1";
            }
            HOST_NAME = hostname;
        }
    }
}
