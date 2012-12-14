/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

package com.sun.javatest.regtest;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import com.sun.javatest.Status;
import com.sun.javatest.lib.ProcessCommand;

/**
 * Info about a JDK
 */
public class JDK {
    enum Version {
        V1_1("1.1"),
        V1_2("1.2"),
        V1_3("1.3"),
        V1_4("1.4"),
        V1_5("1.5"),
        V1_6("1.6"),
        V1_7("1.7");
        Version(String name) {
            this.name = name;
        }
        final String name;
    }

    /** Creates a new instance of JDK */
    public JDK(String jdk) {
        this(new File(jdk));
    }

    public JDK(File jdk) {
        this.jdk = jdk;
        absJDK = jdk.getAbsoluteFile();
    }

    public File getFile() {
        return jdk;
    }

    public File getAbsoluteFile() {
        return absJDK;
    }

    public File getCanonicalFile() {
        try {
            return jdk.getCanonicalFile();
        } catch (IOException e) {
            return absJDK;
        }
    }

    public File getJavaProg() {
        return new File(new File(absJDK, "bin"), "java");
    }

    public File getJavacProg() {
        return new File(new File(absJDK, "bin"), "javac");
    }

    public File getToolsJar() {
        return new File(new File(absJDK, "lib"), "tools.jar");
    }

    public boolean exists() {
        return jdk.exists();
    }

    public String getPath() {
        return jdk.getPath();
    }

    // only used for JDK 1.1
    public Path getJavaClassPath() {
        File jh = absJDK;
        File jh_lib = new File(jh, "lib");

        return new Path(
            new File(jh, "classes"),
            new File(jh_lib, "classes"),
            new File(jh_lib, "classes.zip"));
    }

    public Path getJDKClassPath() {
        return new Path(getToolsJar());
    }

    boolean isVersion(Version v, RegressionParameters params) {
        return (getVersion(params).equals(v.name));
    }

    public String getVersion(RegressionParameters params) {
        if (version == null) {
            final String VERSION_PROPERTY = "java.specification.version";
            version = "unknown";
            if (params.getExecMode() == ExecMode.SAMEVM) {
                version = System.getProperty(VERSION_PROPERTY);
            } else {
                // TODO: move to JDK
                Status status = null;
                // since we are trying to determine the Java version, we have to assume
                // the worst, and use CLASSPATH.
                String[] cmdArgs = new String[] {
                    "CLASSPATH=" + params.getJavaTestClassPath(),
                    getJavaProg().getPath(),
                    "com.sun.javatest.regtest.GetSystemProperty",
                    VERSION_PROPERTY
                };

                // PASS TO PROCESSCOMMAND
                StringWriter outSW = new StringWriter();
                StringWriter errSW = new StringWriter();

                ProcessCommand cmd = new ProcessCommand();
                //cmd.setExecDir(scratchDir());
                status = cmd.run(cmdArgs, new PrintWriter(errSW), new PrintWriter(outSW));

                // EVALUATE THE RESULTS
                if (status.isPassed()) {
                    // we sent everything to stdout
                    String[] v = StringArray.splitEqual(outSW.toString().trim());
                    if (v.length == 2 && v[0].equals(VERSION_PROPERTY))
                        version = v[1];
                }
            }

            // java.specification.version is not defined in JDK1.1.*
            if (version == null || version.length() == 0)
                version = "1.1";
        }
        return version;
    }

    public String getFullVersion(List<String> vmOpts) {
        if (fullVersions == null)
            fullVersions = new HashMap<List<String>,String>();

        String fullVersion = fullVersions.get(vmOpts);
        if (fullVersion == null) {
            List<String> cmdArgs = new ArrayList<String>();
            cmdArgs.add(getJavaProg().getPath());
            cmdArgs.addAll(vmOpts);
            cmdArgs.add("-version");

            // PASS TO PROCESSCOMMAND
            StringWriter outSW = new StringWriter();
            StringWriter errSW = new StringWriter();

            ProcessCommand cmd = new ProcessCommand();
            // no need to set execDir for "java -version"
            Status status = cmd.run(cmdArgs.toArray(new String[cmdArgs.size()]),
                            new PrintWriter(errSW), new PrintWriter(outSW));

            // EVALUATE THE RESULTS
            if (status.isPassed()) {
                // some JDK's send the string to stderr, others to stdout
                String out = errSW.toString().trim();
                fullVersion = "(" + jdk + ")" + LINESEP;
                if (out.length() == 0)
                    fullVersion += outSW.toString().trim();
                else
                    fullVersion += out;
            } else {
                fullVersion = jdk.getPath();
            }

            fullVersions.put(vmOpts, fullVersion);
        }

        return fullVersion;
    }

    @Override
    public String toString() {
        return getPath();
    }

    @Override
    public int hashCode() {
        return absJDK.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof JDK))
            return false;
        JDK other = (JDK) o;
        return absJDK.equals(other.absJDK);
    }

    private final File jdk;
    private final File absJDK;

    private String version;
    private Map<List<String>,String> fullVersions;

    private static final String LINESEP  = System.getProperty("line.separator");
}
