/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Handles stdout/stderr process output from the agent.
 */
public class AgentProcessLogger {

    /**
     * Constructs a thread pool to handle agent process output
     * and creates stdout and stderr readers
     *
     * @param p agent process
     */
    public AgentProcessLogger(Process p) {
        executorService = Executors.newFixedThreadPool(2, runnable -> {
                Thread th = new Thread(runnable);
                th.setDaemon(true);
                return th;
            });
        stdOut = new BufferedReader(new InputStreamReader(p.getInputStream()));
        stdErr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
    }

    /**
     * Starts logging output and error streams to the specified consumer
     *
     * @param logConsumer log consumer, has two parameters - stream name and
     *                    the log line
     */
    public void startLogging(BiConsumer<String, String> logConsumer,
                             Map<String, PrintWriter> processStreamWriters,
                             Function<String, PrintWriter> mappingFunction) {
        if (inputDone != null || errorDone != null) {
            throw new RuntimeException("call stopLogging first");
        }
        if (processStreamWriters != null) {
            processStreamWriters.computeIfAbsent("stdout", mappingFunction);
            processStreamWriters.computeIfAbsent("stderr", mappingFunction);
        }
        inputDone = executorService.submit(() -> captureLog("stdout", stdOut, logConsumer));
        errorDone = executorService.submit(() -> captureLog("stderr", stdErr, logConsumer));
    }


    /**
     * Waits for the logging tasks to finish
     *
     * @param timeout shutdown timeout
     * @param timeUnit shutdown time unit
     *
     * @throws ExecutionException the logger threw an unexpected exception
     * @throws InterruptedException the logger was interrupted
     * @throws TimeoutException     logging task failed to stop within 60 seconds
     */
    public void stopLogging(int timeout, TimeUnit timeUnit) throws ExecutionException, InterruptedException, TimeoutException {
        inputDone.get(timeout, timeUnit);
        errorDone.get(timeout, timeUnit);
        inputDone = null;
        errorDone = null;
    }

    /**
     * Wait for logging tasks to finish and shutdown the thread pool
     *
     * @param timeout shutdown timeout
     * @param timeUnit shutdown time unit
     */
    public void shutdown(int timeout, TimeUnit timeUnit) {
        try {
            stopLogging(timeout, timeUnit);
        } catch (ExecutionException | InterruptedException | TimeoutException ex) {
            // ignore exception, the process is terminating
        }
        executorService.shutdown();
    }

    /**
     * Forward log lines to the consumer, stop forwarding on the separator
     * line
     *
     * @param streamName name of the stream
     * @param reader     process's stream reader
     */
    private Void captureLog(String streamName, BufferedReader reader, BiConsumer<String, String> consumer) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                int endMarker  = line.indexOf(AgentServer.PROCESS_OUTPUT_SEPARATOR);
                if (endMarker < 0) {
                    consumer.accept(streamName, line);
                    continue;
                }
                if (endMarker > 0) {
                    line = line.substring(0, endMarker);
                    consumer.accept(streamName, line);
                }
                break;
            }
        } catch (IOException ex) {
            // ignore the exception, the reader might be closed
        }
        return null;
    }

    private final ExecutorService executorService;
    private final BufferedReader stdOut;
    private final BufferedReader stdErr;
    private Future<Void> inputDone;
    private Future<Void> errorDone;
}
