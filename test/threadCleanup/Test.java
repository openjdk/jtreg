/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @run main/timeout=20 Test
 */

/*
 * @test
 * @run main/timeout=20 Test exception
 */
public class Test {
    public static void main(String... args) throws Exception {
        new Test().run(args);
    }

    void run(String... args) throws Exception {
        String name = getClass().getName();
        System.err.println(name + ": starting");
        synchronized (this) {
            new Thread(this::blocker0, "blocker0").start();
            wait();
            new Thread(this::blocker1, "blocker1").start();
            wait();
            new Thread(this::blocker2, "blocker2").start();
            wait();
        }
        if (args.length == 0) {
            System.err.println(name + ": exiting normally");
        } else {
            throw new Exception(name + ": throwing exception");
        }
    }

    void blocker0() {
        blocker("blocker0", 0);
    }

    void blocker1() {
        blocker("blocker1", 1);
    }

    void blocker2() {
        blocker("blocker2", 2);
    }

    synchronized void blocker(String name, int depth) {
        if (depth > 0) {
            blocker(name, depth - 1);
            return;
        }

        System.err.println(name + ": started");
        notifyAll();
        while (true) {
            try {
                System.err.println(name + ": waiting");
                wait();
            } catch (InterruptedException e) {
                System.err.println(name + ": ignored " + e);
            }
        }
    }
}

