/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/**
 * This is the default timeout handler. It will run jstack on the process that
 * has timed out.
 */
public class DefaultTimeoutHandler extends TimeoutHandler {

    public DefaultTimeoutHandler(PrintWriter log, File outputDir, File testJdk) {
        super(log, outputDir, testJdk);
    }

    @Override
    protected void runActions(Process proc, long pid) throws InterruptedException {
        runJstack(pid);
    }

    /**
     * Run jstack on the specified pid.
     * @param pid Process Id
     */
    private void runJstack(long pid) throws InterruptedException {
        try {
            log.println("Running jstack on process " + pid);

            File jstack = findJstack();
            if (jstack == null) {
                log.println("Warning: Could not find jstack in: " + testJdk.getAbsolutePath());
                log.println("Will not dump jstack output.");
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(jstack.getAbsolutePath(),
                pid + "");
            pb.redirectErrorStream(true);

            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.println(line);
                }
                p.waitFor();
            } finally {
                reader.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace(log);
        }
    }

    private File findJstack() {
        File jstack = new File(new File(testJdk, "bin"), "jstack");
        if (!jstack.exists()) {
            jstack = new File(new File(testJdk, "bin"), "jstack.exe");
            if (!jstack.exists()) {
                return null;
            }
        }
        return jstack;
    }
}
