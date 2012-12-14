/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

import com.sun.javatest.Harness;
import com.sun.javatest.Parameters;
import com.sun.javatest.TestResult;
import com.sun.javatest.report.Report;

/**
 * Monitor the elapsed times of tests using a Harness.Observer and write a
 * summary to a file.
 */
public class ElapsedTimeHandler {
    ElapsedTimeHandler() {
        this(1);
    }

    ElapsedTimeHandler(int resolution) {
        this.resolution = resolution;
        table = new Table(resolution);
    }

    void register(Harness h) {
        h.addObserver(new BasicObserver() {
            @Override
            public void startingTestRun(Parameters p) {
                startTimes = new WeakHashMap<TestResult, Long>();
            }

            @Override
            public synchronized void startingTest(TestResult tr) {
                startTimes.put(tr, System.currentTimeMillis());
            }

            @Override
            public synchronized void finishedTest(TestResult tr) {
                Long start = startTimes.remove(tr);
                if (start == null)
                    return;
                table.record(start, System.currentTimeMillis());
            }

            @Override
            public void stoppingTestRun() {
                startTimes = null;
            }

            private Map<TestResult, Long> startTimes;
        });
    }

    public void report(Report report) throws IOException {
        File reportDir = report.getReportDir();
        File file = new File(reportDir, "text/timeStats.txt");
        report(file);
    }

    public void report(File file) throws IOException {
        Writer out = new BufferedWriter(new FileWriter(file));

        try {
            if (resolution == 1)
                out.write(String.format("%5s,%5s%n", "time", "count"));
            else
                out.write(String.format("%5s,%5s,%5s", "time", "blk", "count"));

            for (Map.Entry<Integer, Integer> e: table.entrySet()) {
                int k = e.getKey();
                int v = e.getValue();
                if (resolution == 1)
                    out.write(String.format("%5d,%5d%n", k, v));
                else
                    out.write(String.format("%5d,%5d,%5d%n", k*resolution, k, v));
            }
            out.write(String.format("%n"));
            out.write(String.format("Mean               %6.2fs%n", table.getMean()* resolution));
            out.write(String.format("Standard deviation %6.2fs%n", table.getStdDev() * resolution));
            int e = table.getElapsedTime();
            int mins = e / 60;
            int secs = e % 60;
            out.write(String.format("Total elapsed time %dm %ds%n", mins, secs));
        } finally {
            out.close();
        }
    }

    private int resolution;

    private Table table;

    class Table extends TreeMap<Integer, Integer> {
        private static final long serialVersionUID = 0;

        Table(int resolution) {
            this.resolution = resolution;
        }

        void record(long start, long end) {
            if (earliest == 0 || start < earliest)
                earliest = start;
            if (latest == 0 || end > latest)
                latest = end;
            int elapsed = (int) ((end - start) / 1000);
            int bucket = (elapsed / resolution);
            inc(bucket);
        }

        void inc(int bucket) {
            Integer value = get(bucket);
            put(bucket, (value == null) ? 1 : value + 1);
            count++;
            total += bucket;
            totalSquares += (bucket * bucket);
        }

        double getMean() {
            return total / count;
        }

        double getStdDev() {
            double mean = total / count;
            return Math.sqrt(totalSquares / count - mean * mean);
        }

        int getElapsedTime() {
            if (earliest == 0 && latest == 0)
                return 0;
            if (earliest == 0 || latest == 0)
                throw new IllegalStateException();
            return (int) ((latest - earliest) / 1000);
        }

        int resolution;

        int count;
        double total;
        double totalSquares;

        long earliest;
        long latest;
    }
}
