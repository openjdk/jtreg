/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*   ;
import java.util.*;

public class RetainTest
{
    public static void main(String[] args) {
        RetainTest t = new RetainTest(args);
        t.run();
    }

    RetainTest(String[] args) {
        testSuiteDir = new File(args[0]);
        workDir = new File(args[1]);
        reportDir = new File(args[2]);

        PASS = new File(workDir, "PassTest/Pass.txt");
        FAIL = new File(workDir, "FailTest/Fail.txt");
        ERROR = new File(workDir, "ErrorTest/Error.txt");
        APASS = new File(workDir, "a/APassTest/tmp/APass.txt");
        AFAIL = new File(workDir, "a/AFailTest/tmp/AFail.txt");
        AERROR = new File(workDir, "a/AErrorTest/tmp/AError.txt");

        ignoreSet = new HashSet<File>();
        ignoreSet.add(new File(workDir, "logfile.log"));
        ignoreSet.add(new File(workDir, "logfile.log.log.index"));
        ignoreSet.add(new File(workDir, "logfile.log.rec.index"));
        ignoreSet.add(new File(workDir, "jtreg.policy"));
    }

    void run() {
        test("-svm");
        test("-ovm");

        if (errors > 0)
            throw new Error(errors + " errors found");

    }

    void test(String mode) {
        String tsp = testSuiteDir.getPath();
        Set<File> expectSet = new HashSet<File>();

        jtreg(mode, tsp);
        verify(false);

        jtreg(mode, "-retain", tsp);
        verify(true, PASS, FAIL, ERROR, APASS, AFAIL, AERROR);

        jtreg(mode, "-retain:all", tsp);
        verify(true, PASS, FAIL, ERROR, APASS, AFAIL, AERROR);

        jtreg(mode, "-retain:pass", tsp);
        verify(true, PASS, APASS);

        jtreg(mode, "-retain:fail", tsp);
        verify(true, FAIL, AFAIL);

        jtreg(mode, "-retain:error", tsp);
        verify(true, ERROR, AERROR);

        jtreg(mode, "-retain:*a*", tsp);
        verify(true, PASS, FAIL, APASS, AFAIL);

        jtreg(mode, "-retain:fail,error", tsp);
        verify(true, FAIL, ERROR, AFAIL, AERROR);

        jtreg(mode, "-retain:none", tsp);
        verify(true);

    }

    void jtreg(String... args) {
        clear(workDir, true);

        List<String> l = new ArrayList<String>();
        l.add("-w");
        l.add(workDir.getPath());
        l.add("-r");
        l.add(reportDir.getPath());
        l.addAll(Arrays.asList(args));

        System.err.println();
        System.err.println("jtreg " + l);
        try {
            com.sun.javatest.regtest.Main m = new com.sun.javatest.regtest.Main();
            m.run((String[])l.toArray(new String[l.size()]));
        } catch (Throwable t) {
            error(t.toString());
        }
    }

    void verify(boolean expectEmptyScratch, File... files) {
        verify(expectEmptyScratch, new HashSet<File>(Arrays.asList(files)));
    }

    void verify(boolean expectEmptyScratch, Set<File> files) {
        Set<File> s = new HashSet<File>();
        scan(workDir, s);
        for (File f: files) {
            if (!s.contains(f))
                error("expected file not found: " + f);
        }
        for (File f: s) {
            if (ignoreSet.contains(f)) {
                System.err.println("unexpected file ignored: " + f);
                continue;
            }

            if (!files.contains(f))
                error("unexpected file found: " + f);
        }

        File[] scratchFiles = new File(workDir, "scratch").listFiles();
        if (expectEmptyScratch) {
            if (scratchFiles.length != 0)
                error("unexpected files in scratch dir: " + Arrays.asList(scratchFiles));
        } else {
            if (scratchFiles.length == 0)
                error("scratch dir empty");
        }
    }

    void scan(File dir, Set<File> files) {
        for (File f: dir.listFiles()) {
            String name = f.getName();
            if (f.isDirectory()) {
                if (!(name.equals("scratch")
                      || name.equals("classes")
                      || name.equals("jtData")))
                    scan(f, files);
            }
            else {
                if (!name.endsWith(".jtr"))
                    files.add(f);
            }
        }
    }

    void clear(File dir, boolean clearSelf) {
        if (!dir.exists())
            return;

        for (File f: dir.listFiles()) {
            if (f.isDirectory())
                clear(f, true);
            f.delete();
        }
        if (clearSelf)
            dir.delete();
    }

    void error(String msg) {
        System.err.println(msg);
        errors++;
    }

    File testSuiteDir;
    File tmpDir;
    File workDir;
    File reportDir;
    int errors;
    File PASS, FAIL, ERROR, APASS, AFAIL, AERROR;
    Set<File> ignoreSet;

}
