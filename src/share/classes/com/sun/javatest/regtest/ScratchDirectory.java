/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import com.sun.javatest.Status;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestResult;

/**
 * Utilities for handling the scratch directory in which tests are executed.
 */
abstract class ScratchDirectory {
    /** Used to resort serious issues while manipulating the scratch directory. */
    static class Fault extends Exception {
        Fault(String msg) {
            super(msg);
        }
        Fault(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /** Get a scratch directory appropriate for the given parameters. */
    static ScratchDirectory get(RegressionParameters params, ExecMode mode, TestDescription td) {
        if (params.isRetainEnabled() && mode == ExecMode.OTHERVM)
            return new TestResultScratchDir(params, td);
        else
            return new ThreadSafeScratchDir(params, td);
    }

    protected final RegressionParameters params;
    protected final TestDescription td;

    /** The current location of the scratch directory. */
    File dir;

    protected ScratchDirectory(RegressionParameters params, TestDescription td, File dir) {
        this.params = params;
        this.td = td;
        this.dir = dir;
    }

    /**
     * Initialize a scratch directory.
     * Normally, this means deleting the contents of the directory.
     * If any file cannot be deleted, it is retried a few times, after a
     * short interval.
     * @param log A stream to which to write messages about any issues encountered.
     * @throws com.sun.javatest.regtest.ScratchDirectory.Fault if the directory
     *      cannot be initialized.
     * @throws InterruptedException if the method is interrupted.
     */
    void init(PrintWriter log) throws Fault, InterruptedException {
        if (dir.exists()) {
            // On Windows, files cannot be deleted if they are still open.
            // So, if files cannot be deleted, we pause and try again.
            // In all cases, if we cannot delete all the files, we close
            // any agents that might be using this scratch directory.
            for (int count = 0; count < INIT_MAX_TRIES; count++) {
                if (deleteFiles(dir, log))
                    return;
                Agent.Pool.instance().close(dir);
                Thread.sleep(INIT_RETRY_DELAY);
            }
            throw new Fault(CANT_CLEAN + dir);
        } else {
            if (dir.mkdirs())
                return;
            throw new Fault(CANT_CREATE + dir);
        }
    }

    private static final int INIT_RETRY_DELAY;
    private static final int INIT_MAX_TRIES;
    static {
        boolean isWindows = System.getProperty("os.name").startsWith("Windows");
        INIT_RETRY_DELAY = isWindows ? 500 : 0; // millis
        INIT_MAX_TRIES   = isWindows ?   4 : 1;
    }

    abstract boolean retainFiles(Status status, PrintWriter log);

    /**
     * Delete the contents of a directory, reporting any issues to a log.
     * @param dir The directory whose contents are to be deleted.
     * @param log A stream to which to write errors.
     * @return true if and only if all the contents are successfully deleted.
     * @throws com.sun.javatest.regtest.ScratchDirectory.Fault if any unexpected
     *      issues occur while deleting the contents of the directory.
     */
    protected boolean deleteFiles(File dir, PrintWriter log) throws Fault {
        try {
            File[] children = dir.listFiles();
            if (children == null)
                return true;
            boolean ok = true;
            for (File child: children) {
                if (isDirectory(child)) {
                    ok &= deleteFiles(child, log);
                }
                if (!child.delete()) {
                    log.println(CANT_DELETE + child);
                    ok = false;
                }
            }
            return ok;
        } catch (SecurityException e) {
            throw new Fault(SECMGR_EXC + dir, e);
        }
    }

    /**
     * Delete all files in a directory that optionally match or don't
     * match a pattern.
     * @returns true if the selected contents of the directory are successfully deleted.
     */
    protected boolean deleteFiles(File dir, Pattern p, boolean match, PrintWriter log) {
        return deleteFiles(dir, p, match, false, log);
    }

    /**
     * Delete all files in a directory that optionally match or don't
     * match a pattern.  If deleteDir is set and all files in the directory
     * are deleted, the directory is deleted as well.
     * @returns true if the selected files and directories are deleted successfully.
     */
    protected boolean deleteFiles(File dir, Pattern p, boolean match, boolean deleteDir, PrintWriter log) {
        if (!dir.exists())
            return true;

        boolean ok = true;
        for (File file: dir.listFiles()) {
            if (file.isDirectory()) {
                ok &= deleteFiles(file, p, match, true, log);
            } else {
                boolean delete = (p == null) || (p.matcher(file.getName()).matches() == match);
                if (delete) {
                    if (!file.delete()) {
                        log.println("warning: failed to delete " + file);
                        ok = false;
                    }
                }
            }
        }
        if (ok && isEmpty(dir) && deleteDir) {
            ok = dir.delete();
        }
        return ok;
    }

    private static boolean isEmpty(File dir) {
        return dir.isDirectory() && (dir.listFiles().length == 0);
    }

    /**
     * Copy all files in a directory that optionally match or don't match a pattern.
     **/
    protected boolean saveFiles(File fromDir, File toDir, Pattern p, boolean match, PrintWriter log) {
        boolean result = true;
        boolean toDirExists = toDir.exists();
        if (toDirExists) {
            try {
                deleteFiles(toDir, log);
            } catch (Fault e) {
                log.println("warning: failed to empty " + toDir);
            }
        }

        for (File file: fromDir.listFiles()) {
            String fileName = file.getName();
            if (file.isDirectory()) {
                File dest = new File(toDir, fileName);
                result &= saveFiles(file, dest, p, match, log);
            } else {
                boolean save = (p == null) || (p.matcher(fileName).matches() == match);
                if (save) {
                    if (!toDirExists) {
                        toDir.mkdirs();
                        toDirExists = toDir.exists();
                    }
                    File dest = new File(toDir, fileName);
                    if (dest.exists())
                        dest.delete();
                    boolean ok = file.renameTo(dest);
                    if (!ok) {
                        log.println("error: failed to rename " + file + " to " + dest);
                        result = false;
                    }
                }
            }
        }
        return result;
    }

    private static boolean isDirectory(File dir) throws Fault {
        try {
            return (dir.isDirectory() && dir.equals(dir.getCanonicalFile()));
        } catch (IOException e) {
            throw new Fault(CANON_FILE_EXC + dir, e);
        }
    }

    protected static File getResultDir(RegressionParameters params, TestDescription td) {
        String wrp = TestResult.getWorkRelativePath(td);
        // assert wrp.endsWith(".jtr")
        if (wrp.endsWith(".jtr"))
            wrp = wrp.substring(0, wrp.length() - 4);
        return params.getWorkDirectory().getFile(wrp);
    }

    private static final String
        CANT_CLEAN            = "Can't clean ",
        CANT_CREATE           = "Can't create ",
        CANT_DELETE           = "Can't delete ",
        SECMGR_EXC            = "Problem deleting file: ",
        CANON_FILE_EXC        = "Problem deleting file: ";

    /**
     * Use a test-specific directory as the scratch directory for the test.
     * This is used in OtherVM mode when we want to retain some or all of the
     * files from the test's execution, since it avoids the need to move
     * files from a shared scratch directory to the test result directory.
     */
    static class TestResultScratchDir extends ScratchDirectory {
        TestResultScratchDir(RegressionParameters params, TestDescription td) {
            super(params, td, getResultDir(params, td));
        }

        @Override
        boolean retainFiles(Status status, PrintWriter log) {
            // if scratchDir is the same as resultDir, we just need to delete
            // the files we don't want to keep; the ones we want to keep are
            // already in the right place.
            if (params.getRetainStatus().contains(status.getType())) {
                // all files to be retained; no need to delete any files
                return true;
            } else {
                Pattern rp = params.getRetainFilesPattern();
                if (rp != null) {
                    // delete files which do not match pattern
                    // extend pattern so as not to delete *.jtr files
                    Pattern rp_jtr = Pattern.compile(".*\\.jtr|" + rp.pattern());
                    return deleteFiles(dir, rp_jtr, false, log);
                } else {
                    // test result doesn't match status set, no patterns specified:
                    // delete all except *.jtr files
                    Pattern jtr = Pattern.compile(".*\\.jtr");
                    return deleteFiles(dir, jtr, false, log);
                }
            }
        }
    }

    /**
     * Use a thread-specific series of directories for the scratch directory.
     * Each thread gets its own series of directories to reuse for tests
     * executed by that thread. If a directory in the series cannot be cleaned
     * up after previous tests, the next directory in the series is used.
     */
    static class ThreadSafeScratchDir extends ScratchDirectory {
        private static class ThreadInfo {
            private static AtomicInteger counter = new AtomicInteger();
            final int threadNum;
            int serial;

            ThreadInfo() {
                threadNum = counter.getAndIncrement();
                serial = 0;
            }

            File getDir(RegressionParameters params) {
                String name = "scratch";
                if (params.getConcurrency() > 1)
                    name += File.separator + threadNum;
                if (serial > 0)
                    name += "_" + serial;
                return params.getWorkDirectory().getFile(name);
            }

            File getNextDir(RegressionParameters params) {
                serial++;
                return getDir(params);
            }
        }

        private static ThreadLocal<ThreadInfo> threadInfo = new ThreadLocal<ThreadInfo>() {
            @Override
            public ThreadInfo initialValue() {
                return new ThreadInfo();
            }
        };

        ThreadSafeScratchDir(RegressionParameters params, TestDescription td) {
            super(params, td, threadInfo.get().getDir(params));
        }

        @Override
        void init(PrintWriter log) throws Fault, InterruptedException {
            try {
                super.init(log);
            } catch (Fault e) {
                useNextDir();
                super.init(log);
            }
        }

        @Override
        boolean retainFiles(Status status, PrintWriter log) {
            // The scratchDir is not the same as resultDir, so we need to
            // save the files we want and delete the rest.
            boolean ok;
            File resultDir = getResultDir(params, td);
            if (params.getRetainStatus().contains(status.getType())) {
                // save all files; no need to delete any files
                ok = saveFiles(dir, resultDir, null, false, log);
            } else {
                Pattern rp = params.getRetainFilesPattern();
                if (rp != null) {
                    // save files which need to be retained
                    ok = saveFiles(dir, resultDir, rp, true, log);
                } else {
                    // test result doesn't match status set, no patterns specified:
                    // no files need saving
                    ok = true;
                }
            }

            // delete any files remaining in the scratch dir
            if (ok)
                ok = deleteFiles(dir, null, false, log);

            if (!ok)
                useNextDir();

            return ok;
        }

        private void useNextDir() {
            dir = threadInfo.get().getNextDir(params);
        }
    }
}
