/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
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

        clear(workDir, false);

        PASS = new File(workDir, "PassTest/Pass.txt");
        FAIL = new File(workDir, "FailTest/Fail.txt");
        ERROR = new File(workDir, "ErrorTest/Error.txt");
        APASS = new File(workDir, "a/APassTest/tmp/APass.txt");
        AFAIL = new File(workDir, "a/AFailTest/tmp/AFail.txt");
        AERROR = new File(workDir, "a/AErrorTest/tmp/AError.txt");

        ignoreSet = new HashSet<File>();
        ignoreSet.add(new File(workDir, "logfile.log"));
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

        clear(workDir, false);
        jtreg(mode, tsp);
        verify();

        jtreg(mode, "-retain", tsp);
        verify(PASS, FAIL, ERROR, APASS, AFAIL, AERROR);

        jtreg(mode, "-retain:all", tsp);
        verify(PASS, FAIL, ERROR, APASS, AFAIL, AERROR);

        jtreg(mode, "-retain:pass", tsp);
        verify(PASS, APASS);

        jtreg(mode, "-retain:fail", tsp);
        verify(FAIL, AFAIL);

        jtreg(mode, "-retain:error", tsp);
        verify(ERROR, AERROR);

        jtreg(mode, "-retain:*a*", tsp);
        verify(PASS, FAIL, APASS, AFAIL);

        jtreg(mode, "-retain:fail,error", tsp);
        verify(FAIL, ERROR, AFAIL, AERROR);

    }

    void jtreg(String... args) {
        List<String> l = new ArrayList<String>();
        l.add("-w");
        l.add(workDir.getPath());
        l.add("-r");
        l.add(reportDir.getPath());
        l.addAll(Arrays.asList(args));

        System.err.println("jtreg " + l);
        try {
            com.sun.javatest.regtest.Main m = new com.sun.javatest.regtest.Main();
            m.run((String[])l.toArray(new String[l.size()]));
        } catch (Throwable t) {
            error(t.toString());
        }
    }

    void verify(File... files) {
        verify(new HashSet<File>(Arrays.asList(files)));
    }

    void verify(Set<File> files) {
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
