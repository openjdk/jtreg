/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.javatest.regtest.config.RegressionParameters;

/**
 * A lock to protect access to shared resources between tests
 *
 */
public abstract class Lock {

    static Map<RegressionParameters, Lock> locks = new WeakHashMap<>();

    public static synchronized Lock get(RegressionParameters params) {
        Lock lock = locks.get(params);
        if (lock == null) {
            Path el = params.getExclusiveLock();
            lock = (el == null) ? new SimpleLock() : new MultiVMLock(el.toFile());
            locks.put(params, lock);
        }
        return lock;
    }

    public abstract void lock();

    public abstract void unlock();

    public void close() { }

    private static class SimpleLock extends Lock {
        ReentrantLock lock = new ReentrantLock();

        @Override
        public void lock() {
            lock.lock();
        }

        @Override
        public void unlock() {
            lock.unlock();
        }
    }

    private static class MultiVMLock extends SimpleLock {
        private File file;
        private RandomAccessFile raf;
        private FileLock fileLock;

        MultiVMLock(File file) {
            try {
                this.file = file;
                while (!file.exists()) {
                    file.createNewFile();
                }
                raf = new RandomAccessFile(file, "rw");
            } catch (IOException e) {
                throw new Error(e);
            }
        }

        @Override
        public void lock() {
            super.lock();
            boolean acquired = false;
            try {
                fileLock = raf.getChannel().lock();
                acquired = true;
            } catch (IOException e) {
                throw new Error(e);
            } finally {
                if (!acquired)
                    super.unlock();
            }
        }

        @Override
        public void unlock() {
            try {
                fileLock.release();
            } catch (IOException e) {
                throw new Error(e);
            } finally {
                super.unlock();
            }
        }

        @Override
        public void close() {
            try {
                raf.close();
// don't delete file -- it might still be in use by other JVMs
//                file.delete();
            } catch (IOException e) {
                throw new Error(e);
            }
        }
    }
}
