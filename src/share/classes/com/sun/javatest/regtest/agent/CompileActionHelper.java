/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.sun.javatest.Status;

import static com.sun.javatest.regtest.agent.RStatus.error;
import static com.sun.javatest.regtest.agent.RStatus.failed;
import static com.sun.javatest.regtest.agent.RStatus.normalize;
import static com.sun.javatest.regtest.agent.RStatus.passed;

public class CompileActionHelper extends ActionHelper {

    public static Status runCompile(String testName,
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
        // Setup streams for the test
        // to catch sysout and syserr
        PrintByteArrayOutputStream sysOut = new PrintByteArrayOutputStream();
        PrintByteArrayOutputStream sysErr = new PrintByteArrayOutputStream();

        // for direct use with RegressionCompileCommand
        PrintStringWriter out = new PrintStringWriter();
        PrintStringWriter err = new PrintStringWriter();

        Status status = error("");
        try {
            Status stat = redirectOutput(sysOut, sysErr);
            if (!stat.isPassed()) {
                return stat;
            }

            Alarm alarm = Alarm.NONE;
            if (timeout > 0) {
                PrintWriter alarmOut = outputHandler.createOutput(OutputHandler.OutputKind.LOG);
                alarm = Alarm.schedule(timeout, TimeUnit.SECONDS, alarmOut, Thread.currentThread());
            }
            try {
                RegressionCompileCommand jcc = new RegressionCompileCommand() {
                    @Override
                    protected Status getStatus(int exitCode) {
                        JDK_Version v = JDK_Version.forThisJVM();
                        return getStatusForJavacExitCode(v, exitCode);
                    }
                };
                String[] c = cmdArgs.toArray(new String[cmdArgs.size()]);
                status = normalize(jcc.run(c, err, out));
            } finally {
                alarm.cancel();
            }

        } finally {
            status = saved.restore(testName, status);
        }

        out.close();
        String outOutput = out.getOutput();
        if (outOutput.length() > 0) {
            outputHandler.createOutput(OutputHandler.OutputKind.DIRECT, outOutput);
        }

        err.close();
        String errOutput = err.getOutput();
        if (errOutput.length() > 0) {
            // should never happen -- only if JavaCompilerCommand kicked into verbose mode
            outputHandler.createOutput(OutputHandler.OutputKind.DIRECT_LOG, errOutput);
        }

        sysOut.close();
        String sysOutOutput = sysOut.getOutput();
        sysErr.close();
        String sysErrOutput = sysErr.getOutput();

        if (sysOutOutput.length() > 0 || sysErrOutput.length() > 0) {
            // should never happen -- only if somehow using JDK 1.3 (but JavaTest assumes 1.4.2+)
            outputHandler.createOutput(OutputHandler.OutputKind.STDOUT, sysOutOutput);
            outputHandler.createOutput(OutputHandler.OutputKind.STDERR, sysErrOutput);
        }

        return status;
    }

    public static Status getStatusForJavacExitCode(JDK_Version v, int exitCode) {
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

    private static final Status passed = passed("Compilation successful");
    private static final Status failed = failed("Compilation failed");
}
