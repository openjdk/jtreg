/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.agent;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static com.sun.javatest.regtest.agent.AStatus.error;
import static com.sun.javatest.regtest.agent.AStatus.failed;
import static com.sun.javatest.regtest.agent.AStatus.passed;

public class CompileActionHelper extends ActionHelper {

    public static AStatus runCompile(String testName,
            Map<String, String> props,
            List<String> cmdArgs,
            int timeout,
            OutputHandler outputHandler) {
        SaveState saved = new SaveState();

        Properties p = System.getProperties();
        for (Map.Entry<String, String> e : props.entrySet()) {
            String name = e.getKey();
            String value = e.getValue();
            if (name.equals("test.class.path.prefix")) {
                SearchPath cp = new SearchPath(value, System.getProperty("java.class.path"));
                p.put("java.class.path", cp.toString());
            } else {
                p.put(e.getKey(), e.getValue());
            }
        }
        System.setProperties(p);

        // RUN THE COMPILER
        // Setup streams for the test:
        // ... to catch sysout and syserr
        PrintStream sysOut = outputHandler.getPrintStream(OutputHandler.OutputKind.STDOUT, true);
        PrintStream sysErr = outputHandler.getPrintStream(OutputHandler.OutputKind.STDERR, true);

        // ... for direct use with RegressionCompileCommand
        PrintWriter out = outputHandler.getPrintWriter(OutputHandler.OutputKind.DIRECT, false);
        PrintWriter err = outputHandler.getPrintWriter(OutputHandler.OutputKind.DIRECT_LOG, false);

        AStatus status = error("");
        try {
            AStatus stat = redirectOutput(sysOut, sysErr);
            if (!stat.isPassed()) {
                return stat;
            }

            Alarm alarm = Alarm.NONE;
            if (timeout > 0) {
                PrintWriter alarmOut = outputHandler.getPrintWriter(OutputHandler.OutputKind.LOG, true);
                alarm = Alarm.schedulePeriodicInterrupt(timeout, TimeUnit.SECONDS, alarmOut, Thread.currentThread());
            }
            try {
                RegressionCompileCommand jcc = new RegressionCompileCommand() {
                    @Override
                    protected AStatus getStatus(int exitCode) {
                        JDK_Version v = JDK_Version.forThisJVM();
                        return getStatusForJavacExitCode(v, exitCode);
                    }
                };
                String[] c = cmdArgs.toArray(new String[cmdArgs.size()]);
                status = jcc.run(c, err, out);
            } finally {
                alarm.cancel();
            }

        } finally {
            out.close();
            err.close();
            sysOut.close();
            sysErr.close();
            status = saved.restore(testName, status);
        }

        return status;
    }

    public static AStatus getStatusForJavacExitCode(JDK_Version v, int exitCode) {
        if (v == null || v.compareTo(JDK_Version.V1_6) < 0)
            return (exitCode == 0 ? passed : failed);

        // The following exit codes are standard in JDK 6 or later
        switch (exitCode) {
            case 0:  return passed;
            case 1:  return failed;
            case 2:  return error("command line error (exit code 2)");
            case 3:  return error("system error (exit code 3)");
            case 4:  return error("compiler crashed (exit code 4)");
            default: return error("unexpected exit code from javac: " + exitCode);
        }
    }

    private static final AStatus passed = passed("Compilation successful");
    private static final AStatus failed = failed("Compilation failed");
}
