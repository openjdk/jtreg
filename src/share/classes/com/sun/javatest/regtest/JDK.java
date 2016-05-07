/*
 * Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.sun.javatest.regtest.agent.GetJDKProperties;
import com.sun.javatest.regtest.agent.GetSystemProperty;
import com.sun.javatest.regtest.agent.JDK_Version;
import com.sun.javatest.regtest.agent.SearchPath;

/**
 * Info about a JDK
 */
public class JDK {
    /**
     * Used to report problems that are found.
     */
    static class Fault extends Exception {
        private static final long serialVersionUID = 1L;
        Fault(String msg) {
            super(msg);
        }

        Fault(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    public static JDK of(String javaHome) {
        return of(new File(javaHome));
    }

    public static synchronized JDK of(File javaHome) {
        JDK jdk = cache.get(javaHome);
        if (jdk == null)
            cache.put(javaHome, jdk = new JDK(javaHome));
        return jdk;
    }

    private static final Map<File, JDK> cache = new HashMap<File, JDK>();

    private JDK(File jdk) {
        this.jdk = jdk;
        absJDK = jdk.getAbsoluteFile();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof JDK))
            return false;
        JDK other = (JDK) o;
        return absJDK.equals(other.absJDK);
    }

    @Override
    public int hashCode() {
        return absJDK.hashCode();
    }

    @Override
    public String toString() {
        return getPath();
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
        // for now, we always return the file, even if if does not exist;
        // it will automatically get filtered out if it is added to a SearchPath
        // and does not exist for this JDK.
        return new File(new File(absJDK, "lib"), "tools.jar");
    }

    public boolean exists() {
        return jdk.exists();
    }

    public String getPath() {
        return jdk.getPath();
    }

    public String getAbsolutePath() {
        return absJDK.getPath();
    }

    // only used for JDK 1.1
    public SearchPath getJavaClassPath() {
        File jh = absJDK;
        File jh_lib = new File(jh, "lib");

        return new SearchPath(
            new File(jh, "classes"),
            new File(jh_lib, "classes"),
            new File(jh_lib, "classes.zip"));
    }

    public SearchPath getJDKClassPath() {
        // will return an empty path if tools.jar does not exist
        return new SearchPath(getToolsJar());
    }

    // params just used for javatestClassPath
    // could use values from getProperties if available
    JDK_Version getVersion(RegressionParameters params) {
        return getVersion(params.getJavaTestClassPath());
    }

    // could use values from getProperties if available
    JDK_Version getVersion(SearchPath getSysPropClassPath) {
        return JDK_Version.forName(getVersionAsString(getSysPropClassPath));
    }

    // could use values from getProperties if available
    private synchronized String getVersionAsString(SearchPath getSysPropClassPath) {
        if (version == null) {
            final String VERSION_PROPERTY = "java.specification.version";
            version = "unknown"; // default
            ProcessBuilder pb = new ProcessBuilder();
            // since we are trying to determine the Java version, we have to assume
            // the worst, and use CLASSPATH.
            pb.environment().put("CLASSPATH", getSysPropClassPath.toString());
            pb.command(getJavaProg().getPath(), GetSystemProperty.class.getName(), VERSION_PROPERTY);
            pb.redirectErrorStream(true);
            try {
                Process p = pb.start();
                String out = getOutput(p);
                int rc = p.waitFor();
                if (rc == 0) {
                    String[] v = StringUtils.splitEqual(out.trim());
                    if (v.length == 2 && v[0].equals(VERSION_PROPERTY))
                        version = v[1];
                }
            } catch (InterruptedException e) {
                // ignore, leave version as default
            } catch (IOException e) {
                // ignore, leave version as default
            }

            // java.specification.version is not defined in JDK1.1.*
            if (version == null || version.length() == 0)
                version = "1.1";
        }
        return version;
    }

    /**
     * Return the JDK -version output
     */
    public synchronized String getFullVersion(Collection<String> vmOpts) {
        if (fullVersions == null)
            fullVersions = new HashMap<Set<String>, String>();

        Set<String> vmOptsSet = new LinkedHashSet<String>(vmOpts);
        String fullVersion = fullVersions.get(vmOptsSet);
        if (fullVersion == null) {
            fullVersion = "";  // default
            List<String> cmdArgs = new ArrayList<String>();
            cmdArgs.add(getJavaProg().getPath());
            cmdArgs.addAll(vmOpts);
            cmdArgs.add("-version");

            try {
                Process p = new ProcessBuilder(cmdArgs)
                        .redirectErrorStream(true)
                        .start();
                String out = getOutput(p);
                int rc = p.waitFor();
                if (rc == 0) {
                    fullVersion = out;
                }
            } catch (InterruptedException e) {
                // ignore, leave version as default
            } catch (IOException e) {
                // ignore, leave version as default
            }

            fullVersions.put(vmOptsSet, fullVersion);
        }

        return fullVersion;
    }

    public boolean hasOldSymbolFile() {
        if (hasOldSymbolFile == null) {
            if (version != null) {
                JDK_Version v = JDK_Version.forName(version);
                if (v.compareTo(JDK_Version.V1_5) <= 0 || v.compareTo(JDK_Version.V10) >= 0) {
                    hasOldSymbolFile = false;
                    return hasOldSymbolFile;
                }
            }
            File ctSym = new File(new File(absJDK, "lib"), "ct.sym");
            if (ctSym.exists()) {
                try {
                    // convert to try-with-resources when possible
                    JarFile jar = new JarFile(ctSym);
                    try {
                        JarEntry e = jar.getJarEntry("META-INF/sym/rt.jar/java/lang/Object.class");
                        hasOldSymbolFile = (e != null);
                    } finally {
                        jar.close();
                    }
                } catch (IOException e) {
                    hasOldSymbolFile = false;
                }
            } else {
                hasOldSymbolFile = false;
            }
        }
        return hasOldSymbolFile;
    }

    private Boolean hasOldSymbolFile = null;

    public boolean hasModules() {
        // whether or not a JDK has modules is independent of the params used,
        // so arbitrarily use the first (and typically only) one.
        for (RegressionParameters p: jdkPropsMap.keySet()) {
            return !getModules(p).isEmpty();
        }
        // jdk.getProperties should be called early on, to avoid this happening
        throw new IllegalStateException();
    }

    /**
     * Get the set of installed modules for this JDK.
     * This is determined by running {@link GetJDKProperties}, which will include
     * a property giving the set of installed modules, if any.
     * @param params to help run GetJDKProperties
     * @return the set of installed modules
     */
    public synchronized Set<String> getModules(RegressionParameters params) {
        if (modulesMap == null)
            modulesMap = new HashMap<RegressionParameters, Set<String>>();

        Set<String> modules = modulesMap.get(params);
        if (modules == null) {
            try {
            Properties props = getProperties(params);
            String m = props.getProperty(GetJDKProperties.JTREG_INSTALLED_MODULES);
            if (m == null)
                modules = Collections.emptySet();
            else {
                modules = new LinkedHashSet<String>(Arrays.asList(m.split(" +")));
            }
            modulesMap.put(params, modules);
            } catch (Fault f) {
                throw new IllegalStateException(f);
            }
        }

        return modules;
    }

    /**
     * Get properties of the JDK under test.
     * The properties include:
     * <ul>
     * <li>any properties set up by any classes declared in the extraPropDefns entry in the
     *     TEST.ROOT file
     * <li>the system properties
     * <li>additional properties for internal use, such as jtreg.installed.modules
     * </ul>
     * @param params used to help invoke GetJDKProperties
     */
    public synchronized Properties getProperties(RegressionParameters params) throws Fault {
        if (jdkPropsMap == null)
            jdkPropsMap = new HashMap<RegressionParameters, Properties>();

        Properties jdkProps = jdkPropsMap.get(params);
        if (jdkProps == null) {
            ExtraPropDefns epd = params.getTestSuite().getExtraPropDefns();
            try {
                epd.compile(params, params.getCompileJDK(), params.getWorkDirectory().getFile("extraPropDefns"));
            } catch (ExtraPropDefns.Fault e) {
                throw new Fault(e.getMessage(), e);
            }

            JDKOpts jdkOpts = new JDKOpts(params.getTestSuite().useNewXpatch());
            jdkOpts.add("-classpath");
            SearchPath cp = new SearchPath(params.getJavaTestClassPath());
            cp.append(epd.getClassDir());
            jdkOpts.add(cp.toString());

            SearchPath bcp = new SearchPath(epd.getBootClassDir());
            if (!bcp.isEmpty()) {
                jdkOpts.add("-Xbootclasspath/a:" + bcp);
            }

            List<String> vmOpts = params.getTestVMJavaOptions();
            jdkOpts.addAll(vmOpts);
            jdkOpts.addAll(epd.getVMOpts());

            List<String> cmdArgs = new ArrayList<String>();
            cmdArgs.add(getJavaProg().getPath());
            cmdArgs.addAll(jdkOpts.toList());
            cmdArgs.add(GetJDKProperties.class.getName());

            cmdArgs.addAll(epd.getClasses());

            jdkProps = new Properties();
            try {
                File scratchDir = params.getWorkDirectory().getFile("scratch");
                // The scratch directory probably already exists, but just in case,
                // we ensure that it does.
                scratchDir.mkdirs();
                final Process p = new ProcessBuilder(cmdArgs)
                        .directory(scratchDir)
                        .start();
                asyncCopy(p.getErrorStream(), System.err);
                jdkProps.load(p.getInputStream());
                int rc = p.waitFor();
                if (rc != 0) {
                    throw new Fault("failed to get JDK properties for "
                            + getJavaProg() + " " + join(" ", vmOpts) + "; exit code " + rc);
                }
            } catch (InterruptedException e) {
                throw new Fault("Error accessing extra property definitions: " + e, e);
            } catch (IOException e) {
                throw new Fault("Error accessing extra property definitions: " + e, e);
            }

            jdkPropsMap.put(params, jdkProps);
        }

        return jdkProps;
    }

    // replace with String.join when jtreg uses JDK 1.8
    private String join(String sep, List<?> list) {
        StringBuilder sb = new StringBuilder();
        for (Object item: list) {
            if (sb.length() > 0)
                sb.append(sep);
            sb.append(item);
        }
        return sb.toString();
    }

    private <T> List<T> concat(List<T> l1, List<T> l2) {
        List<T> result = new ArrayList<T>();
        result.addAll(l1);
        result.addAll(l2);
        return result;
    }

    private void asyncCopy(final InputStream in, final PrintStream out) {
        new Thread() {
            @Override
            public void run() {
                try {
                    BufferedReader err = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while ((line = err.readLine()) != null) {
                        out.println(line);
                    }
                } catch (IOException e) {

                }
            }
        }.start();
    }

    private String getOutput(Process p) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return e.toString();
        }
    }

    private final File jdk;
    private final File absJDK;

    /** Value of java.specification.version for this JDK. Lazily evaluated as needed. */
    private String version;
    /** Value of java VMOPTS -version for this JDK. Lazily evaluated as needed. */
    private Map<Set<String>, String> fullVersions;
    /** JDK properties for this JDK. Lazily evaluated as needed. */
    private Map<RegressionParameters, Properties> jdkPropsMap;
    /** Modules for this JDK. Lazily evaluated as needed. */
    private Map<RegressionParameters, Set<String>> modulesMap;

    private static final String LINESEP  = System.getProperty("line.separator");
}
