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

package com.sun.javatest.regtest.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/**
 * A thread to copy an input stream/reader to an output stream/writer.
 */
public final class StreamCopier extends Thread {

    public StreamCopier(InputStream in, PrintWriter out, LineScanner scanner) {
        super(Thread.currentThread().getName() + "_StreamCopier_" + (serial++));
        this.in = new BufferedReader(new InputStreamReader(in));
        this.out = out;
        this.scanner = scanner;
        setDaemon(true);
    }

    public StreamCopier(InputStream in, PrintWriter out) {
        this(in, out, null);
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (scanner != null) {
                    scanner.scan(line);
                }
                out.println(line);
            }
        } catch (IOException ignore) {
        }
    }

    public interface LineScanner {
        public void scan(String line);
    }

    private final BufferedReader in;
    private PrintWriter out;
    private LineScanner scanner;

    private static int serial;
}
