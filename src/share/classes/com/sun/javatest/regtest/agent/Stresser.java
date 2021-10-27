/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class Stresser {
   
    private final int numOfThreads = Runtime.getRuntime().availableProcessors() * 10;
    private final AtomicBoolean finish = new AtomicBoolean(false);
    private final List<Thread> threads = new ArrayList<>();
    private final ThreadFactory threadFactory = MainWrapper.VirtualAPI.instance().factory(true);
    
    public void start() {     
        for (int i = 0; i < numOfThreads; i++) {
            threads.add(threadFactory.newThread(new Task()));
        }       
    }
    
    public void finish() throws InterruptedException {
        finish.set(true);
        for (Thread t : threads) {
            t.join();
        }
    }
    
    class Task implements Runnable {

        @Override
        public void run() {
            while(!finish.get()) {
                LockSupport.parkNanos(1000);
                try {
                    Thread.sleep(100);
                    throw new Exception();
                } catch (Exception ex) {
                }
            }
        }
    }
    
    public static void main(String[] args) throws Exception {
        Stresser s = new Stresser();
        s.start();
        Thread.sleep(1000);
        s.finish();
    }
}
