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

import com.sun.javatest.Status;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


/**
 * A Command to execute an arbitrary OS command.
 **/
public class ProcessCommand
{
    /**
     * Set a status to be returned for a specific exit code, overwriting any
     * previous setting for this exit code. If the default status has not yet
     * been initialized, it is set to Status.error("unrecognized exit code").
     *
     * @param exitCode The process exit code for which to assign a status.
     * @param status The status to associate with the exit code.
     */
    public void setStatusForExit(int exitCode, Status status) {
        if (statusTable == null) {
            statusTable = new HashMap();
            if (defaultStatus == null) {
                defaultStatus = Status.error("unrecognized exit code");
            }
        }
        statusTable.put(exitCode, status);
    }

    /**
     * Set the default status to be returned for all exit codes.
     * This will not affect any values for specific exit codes that
     * may have been set with setStatusForExit. If this method is
     * not called, the default value will be Status.failed (for
     * backwards compatibility) unless setStatusForExit has been
     * called, which sets the default value to Status.error.
     *
     * @param status The default status to use when a specific status
     * has not been set for a particular process exit code.
     */
    public void setDefaultStatus(Status status) {
        if (statusTable == null) {
            statusTable = new HashMap();
        }
        defaultStatus = status;
    }

    /**
     * Set the directory in which to execute the process.
     * Use null to indicate the default directory.
     * @param dir the directory in which to execute the process.
     * @see #getExecDir
     */
    public void setExecDir(File dir) {
        execDir = dir;
    }

    /**
     * Get the directory in which to execute the process,
     * or null if none set.
     * @return the directory in which to execute the process.
     * @see #setExecDir
     */
    public File getExecDir() {
        return execDir;
    }

    /**
     * Execute a command.
     * @param cmd       The command to be executed
     * @param env    The environment to be passed to the command
     * @param out       A stream for logging normal output.
     * @param err       A stream for logging error output.
     * @param timeout   Timeout (in milliseconds) to wait for the launched process.
     * @param timeoutHandler Handler to call in the case of a timeout,
     *                  or null if no handler.
     * @return          The result of the method is obtained by calling
     *                  <code>getStatus</code> after the command completes.
     * @see #run
     * @see #getStatus
     */
    public Status exec(String[] cmd, Map<String, String> env,
                       PrintWriter out, PrintWriter err,
                       long timeout, TimeoutHandler timeoutHandler) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(execDir);
            pb.environment().clear();
            pb.environment().putAll(env);
            Process p = pb.start();
            InputStream processIn = p.getInputStream();
            InputStream processErr = p.getErrorStream();

            // JDK 1.8 introduces a Process.waitFor(timeout) method which could
            // be used here. We need to be backwards compatible so using
            // interrupt() instead.
            TimeoutTask timeoutTask = new TimeoutTask(Thread.currentThread(), timeoutHandler, p);
            if (timeout > 0) {
                timer.schedule(timeoutTask, timeout);
            }

            OutputStream processOut = p.getOutputStream();  // input stream to process
            if (processOut != null) {
                processOut.close();
            }

            try {
                StatusScanner statusScanner = new StatusScanner();
                StreamCopier outCopier = new StreamCopier(processIn, out);
                StreamCopier errCopier = new StreamCopier(processErr, err, statusScanner);
                outCopier.start();
                errCopier.start();

                outCopier.join();
                errCopier.join();
                int exitCode = p.waitFor();

                // if the timeout hasn't fired, cancel it as quickly as possible
                timeoutTask.cancel();

                return getStatus(exitCode, statusScanner.exitStatus());

            } catch (InterruptedException e) {
                timeoutTask.cancel();
                p.destroy();
                String msg;
                if (timeoutTask.hasTimedOut()) {
                    msg = "Program `" + cmd[0] + "' timed out!";
                } else {
                    msg = "Program `" + cmd[0] + "' interrupted!";
                }
                return Status.error(msg);
            } finally {
                processIn.close();
                processErr.close();
                timeoutTask.cancel();
            }
        }
        catch (IOException e) {
            String msg = "Error invoking program `" + cmd[0] + "': " + e;
            return Status.error(msg);
        }
    }

    private static class TimeoutTask extends TimerTask {

        TimeoutTask(Thread victim, TimeoutHandler timeoutHandler, Process process) {
            this.victim = victim;
            this.timeoutHandler = timeoutHandler;
            this.process = process;
        }

        @Override
        public void run() {
            timedOut = true;
            if (timeoutHandler != null) {
                timeoutHandler.handleTimeout(process);
            }
            victim.interrupt();
        }

        public boolean hasTimedOut() {
            return timedOut;
        }

        private final Thread victim;
        private final TimeoutHandler timeoutHandler;
        private final Process process;
        private boolean timedOut = false;
    };

    private static class StatusScanner implements StreamCopier.LineScanner {

        private String lastStatusLine;

        public void scan(String line) {
            if (line.startsWith(Status.EXIT_PREFIX)) {
                line = Status.decode(line);
                lastStatusLine = line;
            }
        }

        /**
         * Return the status information from the child process if it returned
         * any on the log stream, otherwise return null.
         */
        public Status exitStatus() {
            if (lastStatusLine == null) {
                return null;
            } else {
                return Status.parse(lastStatusLine.substring(Status.EXIT_PREFIX.length()));
            }
        }
    }

    /**
     * Generate a status for the command, based upon the command's exit code
     * and a status that may have been passed from the command by using
     * <code>status.exit()</code>.
     *
     * @param exitCode          The exit code from the command that was executed.
     * @param logStatus         If the command that was executed was a test program
     *                          and exited by calling <code>status.exit()</code>,
     *                          then logStatus will be set to `status'.  Otherwise,
     *                          it will be null.  The value of the status is passed
     *                          from the command by writing it as the last line to
     *                          stdout before exiting the process.   If it is not
     *                          received as the last line, the value will be lost.
     * @return          Unless overridden, the default is
     *                  <code>Status.passed("exit code 0")</code>
     *                  if the command exited with exit code 0, or
     *                  <code>Status.failed("exit code " + exitCode)</code>
     *                  otherwise.
     **/
    protected Status getStatus(int exitCode, Status logStatus) {
        if (logStatus != null) {
            return logStatus;
        } else if (statusTable != null) {
            Status s = (Status)(statusTable.get(Integer.valueOf(exitCode)));
            return (s == null ? defaultStatus.augment("exit code: " + exitCode) : s);
        } else if (exitCode == 0) {
            return Status.passed("exit code 0");
        } else {
            return Status.failed("exit code " + exitCode);
        }
    }

    private HashMap statusTable;
    private Status defaultStatus = Status.error("unknown reason");
    private File execDir;

    private static final Timer timer = new Timer("ProcessCommand Timeouts", true);
}

