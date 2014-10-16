/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle,Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.javatest.regtest;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import com.sun.management.OperatingSystemMXBean;

/**
 * Utilities for handling OS name, arch and version.
 */
public class OS {
    public final String name;
    public final String arch;
    public final String version;

    public final String family;
    public final String simple_arch;
    public final String simple_version;

    public final int processors;

    public final long maxMemory;
    public final long maxSwap;

    private static OS current;

    public static OS current() {
        if (current == null) {
            String name = System.getProperty("os.name");
            String arch = System.getProperty("os.arch");
            String version = System.getProperty("os.version");
            current = new OS(name, arch, version);
        }
        return current;
    }

    // On JPRT, we see the following various types of values for these properties
    //    os.arch              amd64
    //    os.arch              i386
    //    os.arch              sparc
    //    os.arch              x86
    //    os.name              Linux
    //    os.name              SunOS
    //    os.name              Windows 2003
    //    os.name              Windows XP
    //    os.version           2.6.27.21-78.2.41.fc9.i686
    //    os.version           2.6.27.21-78.2.41.fc9.x86_64
    //    os.version           5.1
    //    os.version           5.10
    //    os.version           5.2
    //
    // On a Mac, we see the following types of values
    //    os.arch              x86_64
    //    os.arch              universal
    //    os.name              Darwin
    //    os.name              Mac OS X
    //    os.version           10.6.7
    //    os.version           10.7.4
    //
    // The JPRT source code also lists the following values for os.arch
    //    sparc, sparcv9, ia64, ppc64, ppc,  powerpc,
    //    ppcv2, ppcsflt, arm, armsflt, armvfp

    public OS(String name, String arch, String version) {
        this.name = name;
        this.arch = arch;
        this.version = version;

        if (name.startsWith("Linux"))
            family = "linux";
        else if (name.startsWith("Mac") || name.startsWith("Darwin"))
            family = "mac";
        else if (name.startsWith("SunOS") || name.startsWith("Solaris"))
            family = "solaris";
        else if (name.startsWith("Windows"))
            family = "windows";
        else
            family = name.replaceFirst("^([^ ]+).*", "$1"); // use first word of name

        if (arch.contains("64") && !arch.equals("ia64") && !arch.equals("ppc64"))
            simple_arch = "x64";
        else if (arch.contains("86"))
            simple_arch = "i586";
        else if (arch.equals("ppc") || arch.equals("powerpc"))
            simple_arch = "ppc";
        else
            simple_arch = arch;

        final String UNKNOWN = "99.99";
        int index;
        for (index = 0; index < version.length(); index++) {
            char c = version.charAt(index);
            if (!Character.isDigit(c) && c != '.')
                break;
        }
        List<Integer> v = new ArrayList<Integer>();
        for (String s: version.substring(0, index).split("\\.")) {
            if (s.length() > 0)
                v.add(Integer.valueOf(s));
        }
        switch (v.size()) {
            case 0:  simple_version = UNKNOWN;                      break;
            case 1:  simple_version = v.get(0) + ".0";              break;
            default: simple_version = v.get(0) + "." + v.get(1);    break;
        }

        processors = Runtime.getRuntime().availableProcessors();

        OperatingSystemMXBean osMXBean =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        maxMemory = osMXBean.getTotalPhysicalMemorySize();
        maxSwap = osMXBean.getTotalSwapSpaceSize();
    }

}
