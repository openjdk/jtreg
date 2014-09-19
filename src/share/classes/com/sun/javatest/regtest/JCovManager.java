/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javatest.util.I18NResourceBundle;
import static com.sun.javatest.regtest.Option.ArgType.*;

/**
 * Manager to drive jcov code coverage tool.
 */
public class JCovManager {
    public JCovManager(File libDir) {
        String s = System.getProperty("jcov.jar");
        File f = (s != null) ? new File(s) : new File(libDir, "jcov.jar");
        if (f.exists())
            jcov_jar = f;
        s = System.getProperty("jcov_network_saver.jar");
        f = (s != null) ? new File(s) : new File(libDir, "jcov_network_saver.jar");
        if (f.exists())
            jcov_network_saver_jar = f;

        if (System.getProperty("jcov.port") != null)
            grabberPort = Integer.getInteger("jcov.port");
        if (System.getProperty("jcov.command_port") != null)
            grabberCommandPort = Integer.getInteger("jcov.command_port");
    }

    public static final String JCOV = "jcov";
    final List<? extends Option> options = Arrays.asList(
        new Option(STD, JCOV, "jcov/classes", "jcov/classes") {
            public void process(String opt, String arg) {
                classes = new File(arg);
            }
        },

        new Option(STD, JCOV, null, "jcov/include") {
            public void process(String opt, String arg) {
                includeOpts.add(arg);
            }
        },

        new Option(STD, JCOV, null, "jcov/include_list") {
            public void process(String opt, String arg) throws BadArgs {
                try {
                    includeOpts.addAll(splitLines(new File(arg)));
                } catch (FileNotFoundException e) {
                    throw new BadArgs(i18n, "jcov.file.not.found", arg);
                } catch (IOException e) {
                    throw new BadArgs(i18n, "jcov.error.reading.file", arg, e);
                }
            }
        },

        new Option(STD, JCOV, null, "jcov/exclude") {
            public void process(String opt, String arg) {
                excludeOpts.add(arg);
            }
        },

        new Option(STD, JCOV, "jcov/source", "jcov/source", "jcov/sourcepath") {
            public void process(String opt, String arg) {
                source = new SearchPath(arg);
            }
        },

        new Option(STD, JCOV, null, "jcov/patch") {
            public void process(String opt, String arg) {
                patch = new File(arg);
            }
        },

        new Option(NONE, JCOV, "jcov-verbose", "jcov/verbose") {
            public void process(String opt, String arg) {
                verbose = "-verbose";
            }
        },

        new Option(NONE, JCOV, "jcov-verbose", "jcov/verbosemore") {
            public void process(String opt, String arg) {
                verbose = "-verbosemore";
            }
        },

        new Option(NONE, JCOV, "jcov-print-env", "jcov/print-env") {
            public void process(String opt, String arg) {
                printEnv = true;
            }
        }
    );

    String version() {
        if (version == null) {
            List<String> opts = Arrays.asList("-version");
            Task t = new Task(opts) {
                @Override
                void processLine(String line) {
                    if (version == null)
                        version = line;
                }

            };
            t.run();
            if (version == null)
                version = "JCov version unknown";
        }
        return version;
    }

    boolean isJCovInstalled() {
        return (jcov_jar != null) && (jcov_network_saver_jar != null);
    }

    boolean isEnabled() {
        return (classes != null);
    }

    void setWorkDir(File workDir) {
        workDir = workDir.getAbsoluteFile();
        instrClasses = new File(workDir, "jcov/classes");
        template = new File(workDir, "jcov/template.xml");
        results = new File(workDir, "jcov/results.xml");
    }

    void setReportDir(File reportDir) {
        report = new File(reportDir, "jcov");
        patchReport = new File(reportDir, "text/patch.txt");
    }

    void setTestJDK(JDK testJDK) {
        this.testJDK = testJDK;
    }

    void instrumentClasses() {
        List<String> opts = new ArrayList<String>();
        opts.add("instr");

        delete(instrClasses);
        instrClasses.mkdirs();

        opts.add("-output");
        opts.add(instrClasses.getPath());

        delete(template);
        opts.add("-template");
        opts.add(template.getPath());

        for (String i: includeOpts) {
            opts.add("-include");
            opts.add(i);
        }

        for (String e: excludeOpts) {
            opts.add("-exclude");
            opts.add(e);
        }

        if (verbose != null) {
            opts.add("-verbose");
        }

        if (printEnv)
            opts.add("-print-env");

        if (containsClass(classes, "java/lang/Shutdown")) {
            opts.add("-include");
            opts.add("java/lang/Shutdown");
            opts.add("-saveatend");
            opts.add("java/lang/Shutdown.runHooks");
        }

        opts.add(classes.getPath());

        // Uugh, best to run Instr on the test JDK because it may try to read
        // platform classes from the bootclasspath
        new Task(testJDK, opts).run();

        if (classes.isFile()) {
            File result = new File(instrClasses, classes.getName());
            if (result.exists()) {
                // work around weird Instr behavior for -output
                File instrJar = new File(instrClasses.getParent(), "classes.jar");
                instrJar.delete();
                result.renameTo(instrJar);
                instrClasses.delete();
                instrClasses = instrJar;
            }
        }
    }

    private boolean containsClass(File file, String name) {
        if (file.isDirectory())
            return new File(file, name.replace('/', File.separatorChar) + ".class").exists();
        try {
            JarFile j = new JarFile(file);
            try {
                return (j.getEntry(name + ".class") != null);
            } finally {
                j.close();
            }
        } catch (IOException ex) {
            return false;
        }
    }

    void startGrabber() {
        if (grabber != null)
            throw new IllegalStateException();

        results.delete();

        List<String> opts = new ArrayList<String>();
        opts.add("grabber");
        opts.add("-port");
        opts.add("0");
        opts.add("-command");
        opts.add("0");

        opts.add("-output");
        opts.add(results.getPath());

        opts.add("-template");
        opts.add(template.getPath());

        if (verbose != null)
            opts.add(verbose);

        if (printEnv)
            opts.add("-print-env");

        Task t = new Task(opts) {
            Pattern skip = Pattern.compile("[A-Z]+\\s*:.*");
            Pattern ports = Pattern.compile("Server started .*:([0-9]+)\\. .*? ([0-9]+)\\. .*");
            @Override
            void processLine(String line) {
                super.processLine(line);
                if (!skip.matcher(line).matches()) {
                    Matcher m = ports.matcher(line);
                    if (m.matches()) {
                        synchronized (JCovManager.this) {
                            grabberPort = Integer.parseInt(m.group(1));
                            grabberCommandPort = Integer.parseInt(m.group(2));
                            JCovManager.this.notify();
                        }
                    }
                }
            }

        };
        grabber = new Thread(t);
        grabber.start();

        try {
            long now = System.currentTimeMillis();
            long endTime = now + 30 * 1000; // wait 30 seconds for grabber to start
            synchronized (this) {
                while (now < endTime && grabberPort == 0) {
                    wait(endTime - now);
                }
            }
        } catch (InterruptedException e) {
            System.err.println("Interrupted while waiting for jcov grabber to initialize");
        }

        if (showJCov)
            System.err.println("jcov grabber port: " + grabberPort + ", command port: " + grabberCommandPort);

        if (grabberPort == 0 || grabberCommandPort == 0)
            System.err.println("Warning: jcov grabber not initialized correctly");
    }

    void stopGrabber() {
        List<String> opts = new ArrayList<String>();
        opts.add("grabberManager");

        if (printEnv)
            opts.add("-print-env");

        if (grabberCommandPort != 0) {
            opts.add("-command_port");
            opts.add(String.valueOf(grabberCommandPort));
        }

        opts.add("-stop");

        new Task(opts).run();

        final int MAX_JOIN_TIME = 30*1000; // 30 seconds
        try {
            grabber.join(MAX_JOIN_TIME);
        } catch (InterruptedException e) {
            System.err.println("Interrupted while waiting for jcov grabber to exit");
        }
    }

    void writeReport() {
        List<String> opts = new ArrayList<String>();
        opts.add("repgen");

        opts.add("-source");
        opts.add(source.toString());

        opts.add("-output");
        opts.add(report.getPath());

        for (String i: includeOpts) {
            opts.add("-include");
            opts.add(i);
        }

        for (String e: excludeOpts) {
            opts.add("-exclude");
            opts.add(e);
        }

        if (printEnv)
            opts.add("-print-env");

        opts.add(results.getPath());

        new Task(opts).run();

        if (patch != null)
            writePatchReport();
    }

    void writePatchReport() {
        List<String> opts = new ArrayList<String>();
        opts.add("diffcoverage");

        opts.add("-replaceDiff");
        opts.add("[^ ]+/classes/:");

        opts.add(results.getPath());
        opts.add(patch.getPath());

        patchReport.getParentFile().mkdirs();
        final PrintWriter out;
        try {
            out = new PrintWriter(new BufferedWriter(new FileWriter(patchReport)));
            new Task(opts) {
                String lastLine;
                @Override
                public void run() {
                    super.run();
                    if (lastLine != null)
                        super.processLine(lastLine);
                }
                @Override
                void processLine(String line) {
                    out.println(line);
                    lastLine = line;
                }
            }.run();
            out.close();
        } catch (IOException e) {
            System.err.println("Cannot open " + patchReport + ": " + e);
        }
    }

    boolean delete(File file) {
        boolean ok = true;
        if (file.exists()) {
            if (file.isDirectory()) {
                for (File f: file.listFiles())
                    ok &= delete(f);
            }
            ok &= file.delete();
        }
        if (!ok)
            System.err.println("Warning: failed to delete " + file);
        return ok;
    }

    List<String> splitLines(File f) throws IOException {
        List<String> lines = new ArrayList<String>();
        BufferedReader in = new BufferedReader(new FileReader(f));
        String line;
        try {
            while ((line = in.readLine()) != null) {
                if (line.length() == 0 || line.startsWith("#"))
                    continue;
                lines.add(line);
            }
            return lines;
        } finally {
            in.close();
        }
    }

    private static final JDK thisJDK = JDK.of(System.getProperty("java.home"));

    class Task implements Runnable {
        Task(List<String> opts) {
            this(thisJDK, opts);
        }

        Task(JDK jdk, List<String> opts) {
            this.jdk = jdk;
            name = opts.get(0);
            this.opts = opts;
        }

        public void run() {
            try {
                List<String> args = new ArrayList<String>();
                args.add(jdk.getJavaProg().getPath());
                args.add("-jar");
                args.add(jcov_jar.getPath());
                args.addAll(opts);
                ProcessBuilder pb = new ProcessBuilder(args);
                if (showJCov)
                    System.err.println("EXEC: " + args);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    processLine(line);
                }
                int rc = p.waitFor();
                if (showJCov)
                    System.err.println("EXEC: " + name + " finished, rc=" + rc);
            } catch (InterruptedException e) {
                System.err.println("Error running jcov: " + e);
            } catch (IOException e) {
                System.err.println("Error running jcov: " + e);
            }
        }

        void processLine(String line) {
            System.out.println("[jcov:" + name + "] " + line);
        }

        JDK jdk;
        String name;
        List<String> opts;
    }

    File jcov_jar;
    File jcov_network_saver_jar;

    private File classes;
    private File patch;
    private SearchPath source;
    private String verbose;
    private boolean printEnv;
    private List<String> includeOpts = new ArrayList<String>();
    private List<String> excludeOpts = new ArrayList<String>();

    JDK testJDK;
    File instrClasses;
    File template;
    File results;
    File report;
    File patchReport;
    int grabberPort;
    int grabberCommandPort;
    String version;

    private Thread grabber;
    static final boolean showJCov = Action.show("showJCov");

    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(Main.class);
}
