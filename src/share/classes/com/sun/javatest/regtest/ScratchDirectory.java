/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import com.sun.javatest.Status;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestResult;

/**
 * Utilities for handling the scratch directory in which tests are executed.
 */
abstract class ScratchDirectory {
    /** Used to report serious issues while manipulating the scratch directory. */
    static class Fault extends Exception {
        private static final long serialVersionUID = 0;

        Fault(String msg) {
            super(msg);
        }
        Fault(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /** Get a scratch directory appropriate for the given parameters. */
    static ScratchDirectory get(RegressionParameters params, ExecMode mode, TestDescription td) {
        if (mode == ExecMode.SAMEVM)
            return new SimpleScratchDirectory(params, td);
        else if (mode == ExecMode.OTHERVM && params.isRetainEnabled())
            return new TestResultScratchDir(params, td);
        else
            return new ThreadSafeScratchDir(params, td);
    }

    private static final boolean verboseScratchDir = Action.show("verboseScratchDir");

    /** The execution parameters for the current test. */
    protected final RegressionParameters params;
    /** The current test. */
    protected final TestDescription td;
    /** The current location of the scratch directory. */
    File dir;

    protected ScratchDirectory(RegressionParameters params, TestDescription td, File dir) {
        this.params = params;
        this.td = td;
        this.dir = dir;
    }

    // <editor-fold defaultstate="collapsed" desc="create and initialize">

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
            try {
                deleteFiles(dir, null, false, log);
            } catch (Fault e) {
                addBadDir(dir);
                Agent.Pool.instance().close(dir);
                throw e;
            }
        } else {
            if (!dir.mkdirs()) {
                throw new Fault(CANT_CREATE + dir);
            }
        }
    }

    private static final Set<File> badDirs = new HashSet<File>();

    private static synchronized boolean isBadDir(File dir) {
        return badDirs.contains(dir);
    }

    private static synchronized void addBadDir(File dir) {
        badDirs.add(dir);
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="retain files">

    abstract void retainFiles(Status status, PrintWriter log) throws Fault, InterruptedException;

    boolean retainFile(File file, File dest) {
        File f = new File(dir, file.getPath());
        File d = params.getWorkDirectory().getFile(dest.getPath());
        return f.renameTo(d);
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="delete files">

    /**
     * Delete all files in a directory that optionally match or don't
     * match a pattern.
     * @throws Fault if any unexpected issues occur while deleting the contents
     *      of the directory.
     * @throws InterruptedException
     */
    protected void deleteFiles(File dir, Pattern p, boolean match, PrintWriter log)
            throws Fault, InterruptedException {
        if (isBadDir(dir))
            throw new Fault(CANT_CLEAN + dir);
        try {
            if (!dir.exists()) {
                if (verboseScratchDir)
                    log.println("WARNING: dir " + dir + " already deleted.");
                return;
            }
            deleteFilesWithRetry(dir, p, match, log);
        } catch (SecurityException e) {
            throw new Fault(SECMGR_EXC + dir, e);
        }
    }

    /**
     * Delete all files in a directory that optionally match or don't
     * match a pattern.
     * On Windows, files cannot be deleted if they are still open.
     * So, if files cannot be deleted, we pause and try again.
     * @throws Fault
     * @throws InterruptedException
     */
    private void deleteFilesWithRetry(File dir, Pattern p, boolean match, PrintWriter log)
            throws Fault, InterruptedException {
        long startTime = System.currentTimeMillis();
        Set<File> cantDelete = new LinkedHashSet<File>();

        do {
            if (deleteFiles(dir, p, match, false, cantDelete, log)) {
                return;
            }
            Thread.sleep(RETRY_DELETE_MILLIS);
        } while ((System.currentTimeMillis() - startTime)
                <= MAX_RETRY_DELETE_MILLIS);

        // report the list of files that could not be deleted
        for (File f: cantDelete)
            log.println("Can't delete " + f);
        throw new Fault(CANT_CLEAN + dir);
    }

    private static final int RETRY_DELETE_MILLIS;
    private static final int MAX_RETRY_DELETE_MILLIS;

    static {
        boolean isWindows = System.getProperty("os.name").startsWith("Windows");
        RETRY_DELETE_MILLIS = isWindows ? 500 : 0;
        MAX_RETRY_DELETE_MILLIS = isWindows ? 15 * 1000 : 0;
    }

    /**
     * Delete all files in a directory that optionally match or don't
     * match a pattern.  If deleteDir is set and all files in the directory
     * are deleted, the directory is deleted as well.
     * @throws Fault
     * @returns true if the selected files and directories are deleted successfully.
     */
    private boolean deleteFiles(File dir, Pattern p, boolean match, boolean deleteDir,
            Set<File> badFiles, PrintWriter log)
            throws Fault, InterruptedException {
        if (!dir.exists())
            return true;

        boolean ok = true;
        for (File file: dir.listFiles()) {
            if (isDirectory(file)) {
                ok &= deleteFiles(file, p, match, true, badFiles, log);
            } else {
                boolean deleteFile = (p == null) || (p.matcher(file.getName()).matches() == match);
                if (deleteFile && !delete(file, badFiles, log)) {
                    ok = false;
                }
            }
        }
        if (ok && isEmpty(dir) && deleteDir) {
            ok = delete(dir, badFiles, log);
        }
        return ok;
    }

    boolean delete(File f, Set<File> cantDelete, PrintWriter log) {
        if (f.delete()) {
            cantDelete.remove(f);
            return true;
        } else {
            if (verboseScratchDir) {
                log.println("warning: failed to delete "
                        + (f.isDirectory() ? "directory " : "")
                        + f);
            }
            cantDelete.add(f);
            return false;
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="save files">

    /**
     * Copy all files in a directory that optionally match or don't match a pattern.
     * @throws InterruptedException
     **/
    protected boolean saveFiles(File fromDir, File toDir, Pattern p, boolean match, PrintWriter log)
            throws InterruptedException {
        boolean result = true;
        boolean toDirExists = toDir.exists();
        if (toDirExists) {
            // clean out the target directory before doing the copy to it
            try {
                deleteFiles(toDir, null, false, log);
            } catch (Fault e) {
                log.println("warning: could not empty " + toDir + ": " + e.getMessage());
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
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="utility methods">

    private static boolean isDirectory(File dir) throws Fault {
        try {
            return (dir.isDirectory() && dir.equals(dir.getCanonicalFile()));
        } catch (IOException e) {
            throw new Fault(CANON_FILE_EXC + dir, e);
        }
    }

    private static boolean isEmpty(File dir) {
        return dir.isDirectory() && (dir.listFiles().length == 0);
    }

    protected static File getResultDir(RegressionParameters params, TestDescription td) {
        String wrp = TestResult.getWorkRelativePath(td);
        // assert wrp.endsWith(".jtr")
        if (wrp.endsWith(".jtr"))
            wrp = wrp.substring(0, wrp.length() - 4);
        return params.getWorkDirectory().getFile(wrp);
    }
    // </editor-fold>

    private static final String
        CANT_CLEAN            = "Can't clean ",
        CANT_CREATE           = "Can't create ",
        CANT_SAVE             = "Can't save files in ",
        SECMGR_EXC            = "Problem deleting file: ",
        CANON_FILE_EXC        = "Problem determining canonical file: ";

    /**
     * Simple, basic scratch directory, in workdir/scratch, for samevm mode
     */
    static class SimpleScratchDirectory extends ScratchDirectory {

        SimpleScratchDirectory(RegressionParameters params, TestDescription td) {
            this(params, td, params.getWorkDirectory().getFile("scratch"));
        }

        protected SimpleScratchDirectory(RegressionParameters params, TestDescription td, File dir) {
            super(params, td, dir);
        }

        @Override
        void retainFiles(Status status, PrintWriter log)
                throws InterruptedException, Fault {
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

            if (!ok)
                throw new Fault(CANT_SAVE + dir);

            // delete any files remaining in the scratch dir
            deleteFiles(dir, null, false, log);
        }

    }

    /**
     * Use a thread-specific series of directories for the scratch directory.
     * Each thread gets its own series of directories to reuse for tests
     * executed by that thread. If a directory in the series cannot be cleaned
     * up after previous tests, the next directory in the series is used.
     */
    static class ThreadSafeScratchDir extends SimpleScratchDirectory {
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
                if (params.getExecMode() == ExecMode.SAMEVM)
                    throw new AssertionError();
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
        void retainFiles(Status status, PrintWriter log)
                throws InterruptedException, Fault {
            try {
                super.retainFiles(status, log);
            } catch (Fault f) {
                useNextDir();
                throw f;
            }
        }

        private void useNextDir() {
            dir = threadInfo.get().getNextDir(params);
        }
    }


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
        void retainFiles(Status status, PrintWriter log)
                throws Fault, InterruptedException {
            // if scratchDir is the same as resultDir, we just need to delete
            // the files we don't want to keep; the ones we want to keep are
            // already in the right place.
            if (params.getRetainStatus().contains(status.getType())) {
                // all files to be retained; no need to delete any files
            } else {
                Pattern rp = params.getRetainFilesPattern();
                if (rp != null) {
                    // delete files which do not match pattern
                    // extend pattern so as not to delete *.jtr files
                    Pattern rp_jtr = Pattern.compile(".*\\.jtr|" + rp.pattern());
                    deleteFiles(dir, rp_jtr, false, log);
                } else {
                    // test result doesn't match status set, no patterns specified:
                    // delete all except *.jtr files
                    Pattern jtr = Pattern.compile(".*\\.jtr");
                    deleteFiles(dir, jtr, false, log);
                }
            }
        }
    }

}
