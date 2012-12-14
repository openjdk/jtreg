/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import com.sun.javatest.regtest.OS;

/**
 * Compare functionality of com.sun.javatest.regtest.OS against similar JPRT functionality.
 */
public class OSTest {
    public static void main(String[] args) {
        new OSTest().run();
    }

    void run() {
        String[] arches = {
            "amd64",
            "arm",
            "armsflt",
            "armvfp",
            "em64t",
            "i386",
            "i486",
            "i586",
            "i686",
            "ia64",
            "intel64",
            "powerpc",
            "ppc",
            "ppc64",
            "ppcsflt",
            "ppcv2",
            "sparc",
            "sparcv9",
            "x64",
            "x86",
            "x86_64"
        };

        String[] versions  = {
            "",
            "1",
            "1.2",
            "1.2 test",
            "1.2.3",
            "1.2.3 test"
        };

        for (String a: arches)
            testArch(a);

        for (String v: versions)
            testVersion(v);

        if (errors > 0)
            throw new Error(errors + " errors found");

    }

    void testArch(String arch) {
        System.err.println("Test arch " + arch);
        OS os = new OS("Test", arch, "0.0");
        check(os.simple_arch, JPRT.mapOsArch(arch));

    }

    void testVersion(String version) {
        System.err.println("Test version " + version);
        OS os = new OS("Test", "arch", version);
        check(os.simple_version, JPRT.findOsVersion(version));

    }

    void check(String found, String expect) {
        if (!equal(found, expect)) {
            error("Expected: " + expect + "; found: " + found);
        }
    }

    static <T> boolean equal(T t1, T t2) {
        return t1 == null ? t2 == null : t1.equals(t2);
    }

    void error(String msg) {
        System.err.println(msg);
        errors++;
    }

    int errors;


    // The following code is taken from JPRT PlatformID.java
    static class JPRT {
        private static final String x64 = "x64";
        private static final String sparc = "sparc";
        private static final String sparcv9 = "sparcv9";
        private static final String i586 = "i586";
        private static final String ia64 = "ia64";
        private static final String ppc = "ppc";
        private static final String ppcv2 = "ppcv2";
        private static final String ppcsflt = "ppcsflt";
        private static final String ppc64 = "ppc64";
        private static final String arm = "arm";
        private static final String armsflt = "armsflt";
        private static final String armvfp = "armvfp";

        private static String mapOsArch(String arch) {
            String validArch = null;
            if ("amd64".equalsIgnoreCase(arch)
                    || "intel64".equalsIgnoreCase(arch)
                    || "em64t".equalsIgnoreCase(arch)
                    || "x86_64".equalsIgnoreCase(arch)
                    || x64.equalsIgnoreCase(arch)) {
                validArch = x64;
            } else if ("x86".equalsIgnoreCase(arch)
                    || "i386".equalsIgnoreCase(arch)
                    || "i486".equalsIgnoreCase(arch)
                    || "i686".equalsIgnoreCase(arch)
                    || i586.equalsIgnoreCase(arch)) {
                validArch = i586;
            } else if (sparc.equalsIgnoreCase(arch)) {
               validArch = sparc;
            } else if (sparcv9.equalsIgnoreCase(arch)) {
                validArch = sparcv9;
            } else if (ia64.equalsIgnoreCase(arch)) {
                validArch = ia64;
            } else if (ppc64.equalsIgnoreCase(arch)) {
                validArch = ppc64;
            } else if (ppc.equalsIgnoreCase(arch)
                    || "powerpc".equalsIgnoreCase(arch)) {
                validArch = ppc;
            } else if (ppcv2.equalsIgnoreCase(arch)) {
                validArch = ppcv2;
            } else if (ppcsflt.equalsIgnoreCase(arch)) {
                validArch = ppcsflt;
            } else if (arm.equalsIgnoreCase(arch)) {
                validArch = arm;
            } else if (armsflt.equalsIgnoreCase(arch)) {
                validArch = armsflt;
            } else if (armvfp.equalsIgnoreCase(arch)) {
                validArch = armvfp;
            }
            return validArch;
        }

        private static String findOsVersion(String os_version) {
            //String os_version = Globals.OS_VERSION;
            if (os_version == null || "".equals(os_version)) {
                os_version = "99.99";
            } else {
                int nums[] = /*StringUtils.*/splitNumbers(os_version);
                if (nums.length == 1) {
                    os_version = Integer.toString(nums[0]) + "." + "0";
                } else if (nums.length >= 2) {
                    os_version = Integer.toString(nums[0]) + "."
                            + Integer.toString(nums[1]);
                } else {
                    os_version = "88.88";
                }
            }
            return os_version;
        }

        public static int[] splitNumbers(String versionNumber) {
            int numbers[] = null;
            if (versionNumber != null) {
                StringBuilder subver = new StringBuilder(20);
                for (int i = 0; i < versionNumber.length(); i++) {
                    char c = versionNumber.charAt(i);
                    if (c == '.' || Character.isDigit(c)) {
                        subver.append(c);
                    } else {
                        break;
                    }
                }
                List<String> values = splitValues(subver.toString(),
                        ".", false);
                String words[] = toArray(values);
                numbers = new int[words.length];
                for (int i = 0; i < words.length; i++) {
                    numbers[i] = Integer.parseInt(words[i]);
                }
            }
            return numbers;
        }

        /**
         * Split the value separated by commas into a List of strings.
         *
         * @param listValue String containing space or comma separated items
         * @return A List of strings or null if null, or empty list if empty string
         */
        public static List<String> splitValues(String listValue) {
            return splitValues(listValue, null, true /* doesn't really matter */);
        }

        /**
         * Split the value separated by a separator into a List of strings.
         * The isolation of the String.split() method calls was considered a
         * safe way of protecting from errors in it's use. It's a bit strange.
         *
         * @param listValue String containing space or comma separated items
         * @param separator A regular expression describing the separator
         * @param whitespaceSeparates True if whitespace is also a separator
         * @return A List of strings or null if null, or empty list if empty string
         */
        public static List<String> splitValues(String listValue, String separator,
                boolean whitespaceSeparates) {
            List<String> list = new ArrayList<String>();
            if (listValue != null) {
                listValue = listValue.trim();
                if (listValue.length() > 0) {
                    String realSep = makeSeparator(separator, whitespaceSeparates);
                    String words[] = listValue.split(realSep);
                    for (String word : words) {
                        String realword = word.trim();
                        list.add(realword);
                    }
                }
            }
            return list;
        }

        private static String makeSeparator(String separator,
                boolean whitespaceSeparates) {
            StringBuilder sb = new StringBuilder(80);
            if (separator == null) {
                sb.append("\\p{Space}+");
            } else {
                if (separator.length() == 1) {
                    sb.append("((\\p{Space}*)\\");
                    sb.append(separator);
                    sb.append("(\\p{Space}*))");
                } else {
                    sb.append("(");
                    sb.append(separator);
                    sb.append(")");
                }
                if (whitespaceSeparates) {
                    sb.append("|(\\p{Space}+)");
                }
            }
            return sb.toString();
        }

        /**
         * Convert a List of strings to an Array of strings.
         *
         * @param cmd A list of strings
         * @return An Array of strings
         */
        public static String[] toArray(List<String> cmd) {
            String ar[];
            if (cmd != null && !cmd.isEmpty()) {
                ar = new String[cmd.size()];
                int i = 0;
                for (String s : cmd) {
                    ar[i++] = s;
                }
            } else {
                ar = new String[0];
            }
            return ar;
        }


    }

}
