/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest;

import com.sun.javatest.regtest.agent.JDK_Version;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 *
 * @author Dmitry Fazunenko
 * @author jjg
 */
public class RegressionContext implements Expr.Context {
    static RegressionContext getDefault() {
        try {
            return new RegressionContext(null);
        } catch (JDK.Fault f) {
            throw new IllegalStateException();
        }
    }

    RegressionContext(RegressionParameters params) throws JDK.Fault {
        this.params = params;
        validPropNames = null;

        values = new HashMap<String, String>();
        values.put("null", "null");

        JDK_Version jdkVersion;
        OS os;
        if (params == null) {
            jdkVersion = null;
            os = OS.current();
        } else {
            JDK jdk = params.getTestJDK();
            Properties jdkProps = jdk.getProperties(params);
            for (Map.Entry<?, ?> e: jdkProps.entrySet()) {
                values.put((String) e.getKey(), (String) e.getValue());
            }

            jdkVersion = JDK_Version.forName(jdkProps.getProperty("java.specification.version"));
            os = OS.forProps(jdkProps);
        }

        values.put("jdk.version", jdkVersion != null ? jdkVersion.name : "unknown");
        values.put("jdk.version.major", jdkVersion != null ? jdkVersion.major : "0");

        // profile... (JDK 8)
        // modules... (JDK 9)

        values.put("os.name", os.name );
        values.put("os.arch", os.arch );
        values.put("os.simpleArch", os.simple_arch);
        values.put("os.version", os.version);
        values.put("os.simpleVersion", os.simple_version);
        values.put("os.family", os.family);

        values.put("os.processors", String.valueOf(os.processors));
        values.put("os.maxMemory", String.valueOf(os.maxMemory));
        values.put("os.maxSwap", String.valueOf(os.maxSwap));

        processVMOptions((params == null) ? Collections.<String>emptyList() : params.getTestVMJavaOptions());
    }

    RegressionContext(RegressionContext base, Set<String> validPropNames) {
        params = base.params;
        values = base.values;
        this.validPropNames = validPropNames;
    }

    public boolean isValidName(String name) {
        // Names are validated on the first pass, when the set of valid
        // test-specific property names is available.  On the second pass,
        // we assume invalid names were detected on the first pass, and so
        // now all names can be assumed to be valid
        if (validPropNames == null)
            return true;
        if (values.containsKey(name))
            return true;
        if (name.startsWith("vm.opt."))
            return true;
        return validPropNames.contains(name);
    }

    public String get(String name) {
        String v = values.get(name);
        return (v == null) ? "null" : v;
    }

    @Override
    public String toString() {
        return values.toString();
    }

    private void processVMOptions(List<String> vmOptions) {
        String gc = null;
        String bit = null;
        String vm = null;
        String compMode = null;
        Map<String, Boolean> vmBools = new HashMap<String, Boolean>();
        Map<String, String> vmProps = new HashMap<String, String>();

        for (String opt: vmOptions) {
            if (opt.equals("-" + D64)) {
                bit = D64;
            } else if (opt.equals("-" + D32)) {
                bit = D32;
            } else if (opt.equals("-" + VM_SERVER)) {
                vm = VM_SERVER;
            } else if (opt.equals("-" + VM_CLIENT)) {
                vm = VM_CLIENT;
            } else if (opt.equals("-" + VM_MINIMAL)) {
                vm = VM_MINIMAL;
            } else if (opt.equals("-" + MODE_MIXED)) {
                compMode = MODE_MIXED;
            } else if (opt.equals("-" + MODE_INT)) {
                compMode = MODE_INT;
            } else if (opt.equals("-" + MODE_COMP)) {
                compMode = MODE_COMP;
            } else if (opt.startsWith(GC_PREFIX) && opt.endsWith(GC_SUFFIX)) {
                gc = opt.substring(GC_PREFIX.length(), opt.length() - GC_SUFFIX.length());
                vmBools.put(opt.substring(ON_PREFIX.length()), true);
            } else if (opt.startsWith(ON_PREFIX)) {
                vmBools.put(opt.substring(ON_PREFIX.length()), true);
            } else if (opt.startsWith(OFF_PREFIX)) {
                vmBools.put(opt.substring(OFF_PREFIX.length()), false);
            } else if (opt.startsWith(VM_PREFIX)) {
                int eq = opt.indexOf('=');
                if (eq > 0) {
                    String vmPropName = opt.substring(VM_PREFIX.length(), eq);
                    String vmPropValue = opt.substring(eq+1);
                    vmProps.put(vmPropName, vmPropValue);
                }
            }
        }

        String NULL = "null";
        putIfAbsent(values, "vm.flavor", (vm != null) ? vm : NULL);
        putIfAbsent(values, "vm.bits", (bit != null) ? bit : NULL);
        putIfAbsent(values, "vm.gc", (gc != null) ? gc : NULL);
        putIfAbsent(values, "vm.compMode", (compMode != null) ? compMode : NULL);

        for (Map.Entry<String,Boolean> e: vmBools.entrySet()) {
            putIfAbsent(values, "vm.opt." + e.getKey(), String.valueOf(e.getValue()));
        }

        for (Map.Entry<String,String> e: vmProps.entrySet()) {
            putIfAbsent(values, "vm.opt." + e.getKey(), String.valueOf(e.getValue()));
        }
    }

    // replace with Map.putIfAbsent when jtreg uses JDK 1.8
    private void putIfAbsent(Map<String, String> map, String key, String value) {
        if (!map.containsKey(key))
            map.put(key, value);
    }

    private static final String VM_PREFIX  = "-XX:";
    private static final String ON_PREFIX  = VM_PREFIX + "+";
    private static final String OFF_PREFIX = VM_PREFIX + "-";
    private static final String GC_PREFIX  = ON_PREFIX + "Use";
    private static final String GC_SUFFIX  = "GC";

    private static final String D64 = "D64";
    private static final String D32 = "D32";

    private static final String VM_SERVER  = "server";
    private static final String VM_CLIENT  = "client";
    private static final String VM_MINIMAL = "minimal";

    private static final String MODE_MIXED = "Xmixed";
    private static final String MODE_INT   = "Xint";
    private static final String MODE_COMP  = "Xcomp";

    private final RegressionParameters params;
    private final Map<String, String> values;
    private final Set<String> validPropNames;
}
