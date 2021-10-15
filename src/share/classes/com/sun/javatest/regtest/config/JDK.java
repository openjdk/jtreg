/*
 * Copyright (c) 2007, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
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
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.sun.javatest.regtest.agent.Flags;
import com.sun.javatest.regtest.agent.GetJDKProperties;
import com.sun.javatest.regtest.agent.GetSystemProperty;
import com.sun.javatest.regtest.agent.JDK_Version;
import com.sun.javatest.regtest.agent.SearchPath;
import com.sun.javatest.regtest.util.StringUtils;

/**
 * Info about a JDK.
 *
 * Some information can be statically determined given its $JAVA_HOME.
 * Additional information requires code to be executed in a running instance
 * of the JDK, which may encounter errors that can be reported to
 * a "logger". The information is typically cached, and so once the information
 * has been obtained the first time, there will not be errors on subsequent
 * attempts to access the information.
 */
public class JDK {
    /**
     * Used to report problems that are found.
     */
    public static class Fault extends Exception {
        private static final long serialVersionUID = 1L;
        Fault(String msg) {
            super(msg);
        }

        Fault(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /**
     * Creates a JDK object, given its "$JAVA_HOME" path.
     *
     * @param javaHome the "home" directory for the JDK
     *
     * @return the JDK object
     */
    public static JDK of(String javaHome) {
        return of(new File(javaHome));
    }

    /**
     * Creates a JDK object, given its "$JAVA_HOME" path.
     *
     * @param javaHome the "home" directory for the JDK
     *
     * @return the JDK object
     */
    public static synchronized JDK of(File javaHome) {
        JDK jdk = cache.get(javaHome);
        if (jdk == null)
            cache.put(javaHome, jdk = new JDK(javaHome));
        return jdk;
    }

    private static final Map<File, JDK> cache = new HashMap<>();

    /**
     * Creates a JDK object, given its "$JAVA_HOME" path.
     *
     * @param jdk the "home" directory for the JDK
     */
    private JDK(File jdk) {
        this.jdk = jdk;
        absJDK = jdk.getAbsoluteFile();
    }

    /**
     * {@inheritDoc}
     *
     * Equality is defined in terms of equality of the absolute path for the JDK.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JDK))
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

    /**
     * Returns the home directory for the JDK, as specified when the object was created.
     *
     * @return the home directory for the JDK
     */
    public File getFile() {
        return jdk;
    }

    /**
     * Returns the absolute path of the home directory for the JDK.
     *
     * @return the absolute path of the home directory for the JDK
     */
    public File getAbsoluteFile() {
        return absJDK;
    }

    /**
     * Returns a path for the Java launcher for this JDK.
     *
     * @return a path for the Java launcher for this JDK
     */
    public File getJavaProg() {
        return getProg("java", false);
    }

    /**
     * Returns a path for the Java compiler for this JDK.
     *
     * @return a path for the Java compiler for this JDK
     */
    public File getJavacProg() {
        return getProg("javac", false);
    }

    /**
     * Returns a path for a command in the bin directory of this JDK,
     * optionally including an explicit ".exe" extension, if appropriate.
     *
     * @param command  the name of the command
     * @param checkExe whether to include the name of the extension
     *
     * @return the path
     */
    public File getProg(String command, boolean checkExe) {
        File bin = new File(absJDK, "bin");
        File prog = new File(bin, command);
        if (!prog.exists() && checkExe) {
            File prog_exe = new File(bin, command + ".exe");
            if (prog_exe.exists()) {
                return prog_exe;
            }
        }
        return prog;
    }

    /**
     * Checks whether or not the home directory for this JDK exists.
     *
     * @return whether or not the home directory for this JDK exists
     */
    public boolean exists() {
        return jdk.exists();
    }

    /**
     * Returns the home directory for the JDK as a string, as specified when the object was created.
     *
     * @return the home directory for the JDK
     */
    public String getPath() {
        return jdk.getPath();
    }

    /**
     * Returns the absolute path of the home directory for the JDK, as a string.
     *
     * @return the absolute path of the home directory for the JDK
     */
    public String getAbsolutePath() {
        return absJDK.getPath();
    }

    /**
     * Returns a search path to access JDK classes.
     * {@implNote The result contains tools.jar if it exists, and is empty otherwise.}
     *
     * @return a search path used to access JDK classes
     */
    public SearchPath getJDKClassPath() {
        // will return an empty path if tools.jar does not exist
        return new SearchPath(new File(new File(absJDK, "lib"), "tools.jar"));
    }

    /**
     * Returns the version of this JDK, as determined from the
     * {@code java.specification.version} found when the JDK is run.
     *
     * @param params parameters used to locate the class to determine the value of the
     *               system property
     * @param logger an object to which to write logging messages
     *
     * @return the version of this JDK
     */
    // params just used for javatestClassPath
    // could use values from getProperties if available
    public JDK_Version getVersion(RegressionParameters params, Consumer<String> logger) {
        return getJDKVersion(params.getJavaTestClassPath(), logger);
    }

    /**
     * Returns the version of this JDK, as determined from the
     * {@code java.specification.version}found when the JDK is run.
     *
     * @param classpath used to locate the class to determine the value of the
     *                  system property
     * @param logger    an object to which to write logging messages
     *
     * @return the version of this JDK
     */
    public synchronized JDK_Version getJDKVersion(SearchPath classpath, Consumer<String> logger) {
        if (jdkVersion == null) {
            jdkVersion = JDK_Version.forName(getJavaSpecificationVersion(classpath, logger));
        }

        return jdkVersion;
    }

    /**
     * Returns the value of the {@code java.specification.version} property
     * found when this JDK is run.
     *
     * @implNote The value is cached.
     *
     * @param getSysPropClassPath used to locate the class to determine the value of the
     *                            system property
     * @param logger              an object to which to write logging messages
     *
     * @return the value of the {@code java.specification.version} property
     */
    private synchronized String getJavaSpecificationVersion(SearchPath getSysPropClassPath,
                                                            Consumer<String> logger) {
        if (javaSpecificationVersion != null)
            return javaSpecificationVersion;

        final String VERSION_PROPERTY = "java.specification.version";

        for (Info info : infoMap.values()) {
            if (info.jdkProperties != null) {
                javaSpecificationVersion = info.jdkProperties.getProperty(VERSION_PROPERTY);
                if (javaSpecificationVersion != null)
                    return javaSpecificationVersion;
            }
        }

        javaSpecificationVersion = "unknown"; // default
        ProcessBuilder pb = new ProcessBuilder();
        // since we are trying to determine the Java version, we have to assume
        // the worst, and use CLASSPATH.
        pb.environment().put("CLASSPATH", getSysPropClassPath.toString());
        pb.command(getJavaProg().getPath(), GetSystemProperty.class.getName(), VERSION_PROPERTY);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            List<String> lines = getOutput(p, logger);
            int rc = p.waitFor();
            if (rc == 0) {
                for (String line : lines) {
                    String[] v = line.trim().split("=", 2);
                    if (v.length == 2 && v[0].equals(VERSION_PROPERTY)) {
                        javaSpecificationVersion = v[1];
                        break;
                    }
                }
                if (javaSpecificationVersion.equals("unknown")) {
                    logger.accept("Error getting " + VERSION_PROPERTY + " for " + jdk + ": property not found in output");
                    lines.forEach(logger::accept);
                }
            } else {
                logger.accept("Error getting " + VERSION_PROPERTY + " for " + jdk + ": exit code " + rc);
                lines.forEach(logger::accept);
            }
        } catch (InterruptedException e) {
            // ignore, leave version as default
            logger.accept("Error getting " + VERSION_PROPERTY + " for " + jdk + ": " + e);
        } catch (IOException e) {
            // ignore, leave version as default
            logger.accept("Error getting " + VERSION_PROPERTY + " for " + jdk + ": " + e);
        }

        // java.specification.version is not defined in JDK1.1.*
        if (javaSpecificationVersion == null || javaSpecificationVersion.length() == 0)
            javaSpecificationVersion = "1.1";

        return javaSpecificationVersion;
    }

    /**
     * Returns the output from running {@code java -version} with a given set of VM options.
     *
     * @param vmOpts the VM options to be used when {@code java -version} is run
     * @param logger an object to which to write logging messages
     *
     * @return the output from "{@code java -version}"
     */
    public synchronized String getVersionText(Collection<String> vmOpts, Consumer<String> logger) {
        if (fullVersions == null)
            fullVersions = new HashMap<>();

        final String VERSION_OPTION = "-version";

        Set<String> vmOptsSet = new LinkedHashSet<>(vmOpts);
        String fullVersion = fullVersions.get(vmOptsSet);
        if (fullVersion == null) {
            fullVersion = "";  // default
            List<String> cmdArgs = new ArrayList<>();
            cmdArgs.add(getJavaProg().getPath());
            cmdArgs.addAll(vmOpts);
            cmdArgs.add(VERSION_OPTION);

            try {
                Process p = new ProcessBuilder(cmdArgs)
                        .redirectErrorStream(true)
                        .start();
                List<String> lines = getOutput(p, logger);
                int rc = p.waitFor();
                if (rc == 0) {
                    fullVersion = StringUtils.join(lines, "\n");
                } else {
                    logger.accept("Error running 'java " + VERSION_OPTION + "' for " + jdk + ": exit code " + rc);
                    lines.forEach(logger::accept);
                }
            } catch (InterruptedException e) {
                // ignore, leave version as default
                logger.accept("Error running 'java " + VERSION_OPTION + "' for " + jdk + ": " + e);
            } catch (IOException e) {
                // ignore, leave version as default
                logger.accept("Error running 'java " + VERSION_OPTION + "' for " + jdk + ": " + e);
            }

            fullVersions.put(vmOptsSet, fullVersion);
        }

        return fullVersion;
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
     *
     * @param params used to help invoke GetJDKProperties
     * @param logger an object to which to write logging messages
     *
     * @return the properties
     * @throws Fault if an error occurred while getting the properties
     */
    public synchronized Properties getProperties(RegressionParameters params,
                                                 Consumer<String> logger) throws Fault {
        Info info = getInfo(params);

        if (info.jdkProperties == null) {
            // get default modules as well
            info.jdkProperties = execGetProperties(params,
                    Collections.emptyList(),
                    Arrays.asList("--system-properties", "--modules=boot-layer"),
                    true,
                    logger);
        }

        return info.jdkProperties;
    }

    /**
     * Checks whether or not the JDK has modules.
     * Any errors will be logged to {@link System#err}.
     *
     * @implNote Eventually, this should be a simple property of the version.
     *
     * @return whether or not the JDK has modules
     */
    public boolean hasModules() {
        Consumer<String> logger = System.err::println;
        // whether or not a JDK has modules is independent of the params used,
        // so arbitrarily use the first (and typically only) one.
        for (RegressionParameters p: infoMap.keySet()) {
            return !getDefaultModules(p, logger).isEmpty();
        }
        // jdk.getProperties should be called early on, to avoid this happening
        throw new IllegalStateException();
    }

    /**
     * Get the set of default modules for this JDK, taking into account any VM options
     * like {@code --add-modules} or {@code --limit-modules}.
     * This is determined by running {@link GetJDKProperties}, which will include
     * a property giving the set of installed modules, if any.
     *
     * @param params to help run {@code GetJDKProperties}
     * @param logger an object to which to write logging messages
     *
     * @return the set of installed modules
     */
    public synchronized Set<String> getDefaultModules(RegressionParameters params, Consumer<String> logger) {
        Info info = getInfo(params);

        if (info.defaultModules == null) {
            try {
                Properties props = getProperties(params, logger);
                String m = props.getProperty(GetJDKProperties.JTREG_MODULES);
                if (m == null) {
                    info.defaultModules = Collections.emptySet();
                } else {
                    info.defaultModules = Collections.unmodifiableSet(
                                new LinkedHashSet<>(Arrays.asList(m.split(" +"))));
                }
            } catch (Fault f) {
                throw new IllegalStateException(f);
            }
            if (showModules) {
                System.err.println("default modules: " + new TreeSet<>(info.defaultModules));
            }
        }

        return info.defaultModules;
    }

    /**
     * Get the set of system modules for this JDK, taking into account any VM options
     * like {@code --add-modules} or {@code --limit-modules}.
     * This is determined by running {@link GetJDKProperties}, using {@code --add-modules=ALL-SYSTEM}
     * which will include a property giving the set of installed modules, if any.
     *
     * @param params to help run GetJDKProperties
     * @param logger an object to which to write logging messages
     *
     * @return the set of installed modules
     */
    public synchronized Set<String> getSystemModules(RegressionParameters params,
                                                     Consumer<String> logger) {
        Info info = getInfo(params);

        if (info.systemModules == null) {
            if (getVersion(params, logger).compareTo(JDK_Version.V9) >= 0) {
                try {
                    // Despite the name, --add-modules=ALL-SYSTEM does not
                    // resolve all system modules: it excludes those marked
                    // "do not resolve by default". This can cause tests
                    // to be incorrectly filtered out because of an @modules
                    // declaration that might refer to one of those modules.
                    // To work around this, by default we get all system modules
                    // using ModuleFinder.ofSystem() on the target platform,
                    // unless the user has explicitly used any --(add|limit)-modules
                    // options, in which case we get the standard modules that
                    // are resolved in the boot layer.
                    String modulesOpt = "--modules=all-system";
                    for (String vmOpt : params.getTestVMJavaOptions()) {
                        if (vmOpt.matches("--(add|limit)-modules(=.*)?")) {
                            modulesOpt = "--modules=boot-layer";
                            break;
                        }
                    }
                    Properties props = execGetProperties(params,
                            Collections.emptyList(),    // vm options
                            Arrays.asList(modulesOpt), false, logger);  // requested info from probe
                    String m = props.getProperty(GetJDKProperties.JTREG_MODULES);
                    if (m == null) {
                        info.systemModules = Collections.emptySet();
                    } else {
                        info.systemModules = Collections.unmodifiableSet(
                                new LinkedHashSet<>(Arrays.asList(m.split(" +"))));
                    }
                } catch (Fault f) {
                    throw new IllegalStateException(f);
                }
            } else {
                info.systemModules = Collections.emptySet();
            }
            if (showModules) {
                System.err.println("system modules: " + new TreeSet<>(info.systemModules));
            }
        }

        return info.systemModules;
    }

    /**
     * Checks whether or not the JDK has an "old-style" ct.sym file,
     * such that you might need to use {@code -XDignore.symbol.file=true} to
     * access hidden internal API.
     *
     * @return whether or not the JDK has an "old-style" ct.sym file.
     * @implNote Eventually, this should be a simple property of the version.
     */
    public boolean hasOldSymbolFile() {
        if (hasOldSymbolFile == null) {
            if (javaSpecificationVersion != null) {
                JDK_Version v = JDK_Version.forName(javaSpecificationVersion);
                if (v.compareTo(JDK_Version.V1_5) <= 0 || v.compareTo(JDK_Version.V10) >= 0) {
                    hasOldSymbolFile = false;
                    return hasOldSymbolFile;
                }
            }
            File ctSym = new File(new File(absJDK, "lib"), "ct.sym");
            if (ctSym.exists()) {
                try (JarFile jar = new JarFile(ctSym)) {
                    JarEntry e = jar.getJarEntry("META-INF/sym/rt.jar/java/lang/Object.class");
                    hasOldSymbolFile = (e != null);
                } catch (IOException e) {
                    hasOldSymbolFile = false;
                }
            } else {
                hasOldSymbolFile = false;
            }
        }
        return hasOldSymbolFile;
    }

    /**
     * Executes the {@link GetJDKProperties} utility class in the target JDK.
     *
     * @param params                parameters used to determine additional classes to run,
     *                              and how to compile them
     * @param extraVMOpts           additional VM options to be specified when the class is run
     * @param opts                  options to be passed to the utility class
     * @param includeExtraPropDefns whether or not to compile and run the "extraPropDefn"
     *                              classes specified in the test-suite TEST.ROOT file
     * @param logger                an object to which to write logging messages
     *
     * @return the properties returns from the utility class.
     * @throws Fault if any error occurs while getting the properties.
     */
    private Properties execGetProperties(RegressionParameters params,
            List<String> extraVMOpts, List<String> opts, boolean includeExtraPropDefns,
            Consumer<String> logger)
                throws Fault {

        ExtraPropDefns epd = includeExtraPropDefns ? params.getTestSuite().getExtraPropDefns() : new ExtraPropDefns();
        try {
            epd.compile(params, params.getCompileJDK(), params.getWorkDirectory().getFile("extraPropDefns"));
        } catch (ExtraPropDefns.Fault e) {
            throw new Fault(e.getMessage(), e);
        }

        JDKOpts jdkOpts = new JDKOpts();
        jdkOpts.add("--class-path");
        SearchPath cp = new SearchPath(params.getJavaTestClassPath());
        cp.append(epd.getClassDir());
        jdkOpts.add(cp.toString());

        SearchPath bcp = new SearchPath(epd.getBootClassDir());
        if (!bcp.isEmpty()) {
            jdkOpts.add("-Xbootclasspath/a:" + bcp);
        }

        params.getBasicTestProperties()
                .forEach((name, value) -> jdkOpts.add("-D" + name + "=" + value));
        List<String> vmOpts = params.getTestVMJavaOptions();
        jdkOpts.addAll(vmOpts);
        jdkOpts.addAll(extraVMOpts);
        jdkOpts.addAll(epd.getVMOpts());

        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add(getJavaProg().getPath());
        cmdArgs.addAll(jdkOpts.toList());
        cmdArgs.add(GetJDKProperties.class.getName());

        cmdArgs.addAll(opts);
        cmdArgs.addAll(epd.getClasses());

        try {
            File scratchDir = params.getWorkDirectory().getFile("scratch");
            // The scratch directory probably already exists, but just in case,
            // we ensure that it does.
            scratchDir.mkdirs();
            final Process p = new ProcessBuilder(cmdArgs)
                    .directory(scratchDir)
                    .start();
            asyncCopy(p.getErrorStream(), logger);
            List<String> lines = readLines(p.getInputStream());

            int rc = p.waitFor();
            if (rc != 0) {
                for (String line : lines) {
                    logger.accept(line);
                }
                String msg = "failed to get JDK properties for "
                        + getJavaProg() + " " + StringUtils.join(vmOpts, " ") + "; exit code " + rc;
                logger.accept(msg);
                throw new Fault(msg);
            }

            return loadProperties(lines, logger);

        } catch (InterruptedException e) {
            logger.accept("Error accessing extra property definitions: " + e);
            throw new Fault("Error accessing extra property definitions: " + e, e);
        } catch (IOException e) {
            logger.accept("Error accessing extra property definitions: " + e);
            throw new Fault("Error accessing extra property definitions: " + e, e);
        }
    }

    private List<String> readLines(InputStream is) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = in.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private Properties loadProperties(List<String> lines, Consumer<String> logger) throws Fault {
        Properties props = new Properties();
        try {
            props.load(new StringReader(String.join("\n", lines)));
        } catch (IOException e) {
            logger.accept("Error loading extra property definitions: " + e);
            lines.forEach(logger::accept);
            throw new Fault("Error loading extra property definitions: " + e);
        }
        return props;
    }

    private void asyncCopy(final InputStream in, final Consumer<String> logger) {
        new Thread(() -> {
            try {
                BufferedReader err = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = err.readLine()) != null) {
                    logger.accept(line);
                }
            } catch (IOException e) {
                logger.accept("Error reading stderr while accessing properties: " + e);
            }
        }).start();
    }

    private List<String> getOutput(Process p, Consumer<String> logger) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            logger.accept("Error getting output from process: " + e);
            logger.accept("Output so far:");
            lines.forEach(logger::accept);
            return Collections.singletonList(e.getMessage());
        }
    }

    private Info getInfo(RegressionParameters params) {
        Info info = infoMap.get(params);
        if (info == null) {
            infoMap.put(params, info = new Info());
        }
        return info;
    }

    private final File jdk;
    private final File absJDK;

    /** Value of java.specification.version for this JDK. Lazily evaluated as needed. */
    private String javaSpecificationVersion;

    /** Interpreted value of javaSpecificationVersion. Lazily evaluated as needed. */
    private JDK_Version jdkVersion;

    /** Value of java VMOPTS -version for this JDK. Lazily evaluated as needed. */
    private Map<Set<String>, String> fullVersions;

    private Boolean hasOldSymbolFile = null;

    private final Map<RegressionParameters, Info> infoMap = new HashMap<>();

    static class Info {
        Properties jdkProperties;
        Set<String> defaultModules;
        Set<String> systemModules;
    }

    private static final boolean showModules = Flags.get("showModules");
}
