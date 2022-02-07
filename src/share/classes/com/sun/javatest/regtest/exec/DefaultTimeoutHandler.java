/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.exec;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.javatest.regtest.TimeoutHandler;

/**
 * This is the default timeout handler. It will run jstack on the process that
 * has timed out.
 */
public class DefaultTimeoutHandler extends TimeoutHandler {

    public DefaultTimeoutHandler(PrintWriter log, File outputDir, Path testJdk) {
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

            Path jstack = findJstack();
            if (jstack == null) {
                log.println("Warning: Could not find jstack in: " + testJdk.toAbsolutePath());
                log.println("Will not dump jstack output.");
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(jstack.toAbsolutePath().toString(), pid + "");
            pb.redirectErrorStream(true);

            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.println(line);
                }
                p.waitFor();
            }
        } catch (IOException ex) {
            ex.printStackTrace(log);
        }
    }

    private Path findJstack() {
        Path jstack = testJdk.resolve("bin").resolve("jstack");
        if (!Files.exists(jstack)) {
            jstack = testJdk.resolve("bin").resolve("jstack.exe");
            if (!Files.exists(jstack)) {
                return null;
            }
        }
        return jstack;
    }
}
