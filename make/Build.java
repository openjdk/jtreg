/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
# This program will download/build the dependencies for jtreg and then
# build jtreg. Downloaded files are verified against known/specified
# checksums.
#
# The program can be executed directly as a single source-file program
# by the Java launcher, using JDK 12 or later.
#
#     $ /path/to/jdk  make/Build.java  options
#
# For help on command-line options, use the --help option.
# Note: jtreg itself requires JDK 11 or later.

# As a side effect, the program writes a file build/make.sh which
# can subsequently be used directly to build apidiff, bypassing
# the need to rerun this program if all the dependencies are still
# available.

# The default version to use when building jtreg can be found in the
# make/version-numbers file, where the default versions and
# corresponding known checksums for the dependencies are also
# specified. Almost all the defaults can be overridden by setting
# the properties on the command line, or in a properties file,
# or as environment variables.

# For each of the dependency the following steps are applied and the
# first successful one is used:
#
# 1. Check if the dependency is available locally
# 2. Download a prebuilt version of the dependency
# 3. Build the dependency from source, downloading the source archive
#    first
#
# In particular, when not found locally the dependencies will be
# handled as follows:
#
# * JUnit, TestNG, JCommander, Google Guice, and Ant jar are by default
#   downloaded from Maven central.
# * JT Harness and AsmTools are downloaded or built from source.


# Some noteworthy control variables:
#
# MAVEN_REPO_URL_BASE (e.g. "https://repo1.maven.org/maven2")
#     The base URL for the maven central repository.
#
# CODE_TOOLS_URL_BASE (e.g. "https://git.openjdk.java.net")
#     The base URL for the code tools source repositories.
#
# ANT_ARCHIVE_URL_BASE (e.g. "https://archive.apache.org/dist/ant/binaries")
#     The base URL for Ant dist binaries.
#
# JTREG_VERSION         (e.g. "5.2")
# JTREG_VERSION_STRING  (e.g. "jtreg-5.2+8"
# JTREG_BUILD_NUMBER    (e.g. "8")
# JTREG_BUILD_MILESTONE (e.g. "dev")
#     The version information to use for when building jtreg.
#
# RM, TAR, UNZIP
#     Paths to standard POSIX commands.

# The control variables for dependencies are on the following general
# form (not all of them are relevant for all dependencies):
#
# <dependency>_URL (e.g. JTHARNESS_ARCHIVE_URL)
#     The full URL for the dependency.
#
# <dependency>_URL_BASE (e.g. JTHARNESS_ARCHIVE_URL_BASE)
#     The base URL for the dependency. Requires additional dependency
#     specific variables to be specified.
#
# <dependency>_CHECKSUM (e.g. JTHARNESS_ARCHIVE_CHECKSUM)
#     The expected checksum of the download file.
#
# <dependency>_SRC_TAG (e.g. JTHARNESS_SRC_TAG)
#     The SCM tag to use when building from source. The special value
#     "tip" can be used to get the most recent version.
#
# <dependency>_SRC_ARCHIVE_CHECKSUM (e.g. JTHARNESS_SRC_ARCHIVE_CHECKSUM)
#     The checksum of the source archive.

# The below outlines the details of how the dependencies are
# handled. For each dependency the steps are tried in order and the
# first successful one will be used.
#
# Ant (required to build AsmTools and JT Harness)
#     Checksum variables:
#         ANT_ARCHIVE_CHECKSUM: checksum of binary archive
#
#     1. ANT
#         The path to the ant executable.
#     2a. ANT_ARCHIVE_URL
#         The full URL for the archive.
#     2b. ANT_ARCHIVE_URL_BASE + ANT_VERSION
#         The individual URL components used to construct the full URL.
#
# AsmTools
#     Checksum variables:
#         ASMTOOLS_ARCHIVE_CHECKSUM: checksum of binary archive
#         ASMTOOLS_SRC_ARCHIVE_CHECKSUM: checksum of source archive
#
#     1. ASMTOOLS_JAR + ASMTOOLS_LICENSE
#         The path to asmtools.jar and LICENSE respectively.
#     2a. ASMTOOLS_ARCHIVE_URL
#         The full URL for the archive.
#     2b. ASMTOOLS_ARCHIVE_URL_BASE + ASMTOOLS_VERSION + ASMTOOLS_BUILD_NUMBER + ASMTOOLS_FILE
#         The individual URL components used to construct the full URL.
#     3. ASMTOOLS_SRC_TAG
#         The SCM repository tag to use when building from source.
#
# Google Guice (required by TestNG)
#     Checksum variables:
#         GOOGLE_GUICE_JAR_CHECKSUM: checksum of jar
#
#     1. GOOGLE_GUICE_JAR
#         The path to guice.jar.
#     2a. GOOGLE_GUICE_JAR_URL
#         The full URL for the jar.
#     2b. GOOGLE_GUICE_JAR_URL_BASE + GOOGLE_GUICE_VERSION
#         The individual URL components used to construct the full URL.
#
# JCommander (required by TestNG)
#     Checksum variables:
#         JCOMMANDER_JAR_CHECKSUM: checksum of jar
#
#     1. JCOMMANDER_JAR
#         The path to jcommander.jar.
#     2a. JCOMMANDER_JAR_URL
#         The full URL for the jar.
#     2b. JCOMMANDER_JAR_URL_BASE + JCOMMANDER_VERSION
#         The individual URL components used to construct the full URL.
#
# JT Harness
#     Checksum variables:
#         JTHARNESS_ARCHIVE_CHECKSUM: checksum of binary archive
#         JTHARNESS_SRC_ARCHIVE_CHECKSUM: checksum of source archive
#
#     1. JTHARNESS_JAVATEST_JAR + JTHARNESS_LICENSE + JTHARNESS_COPYRIGHT
#         The path to javatest.jar, LICENSE, and copyright.txt respectively.
#     2a. JTHARNESS_ARCHIVE_URL
#         The full URL for the archive.
#     2b. JTHARNESS_ARCHIVE_URL_BASE + JTHARNESS_VERSION + JTHARNESS_BUILD_NUMBER + JTHARNESS_FILE
#         The individual URL components used to construct the full URL.
#     3. JTHARNESS_SRC_TAG
#         The SCM repository tag to use when building from source.
#
# JUnit
#     Checksum variables:
#         JUNIT_JAR_CHECKSUM: checksum of binary archive
#
#     1. JUNIT_JAR + JUNIT_LICENSE
#         The path to junit.jar and LICENSE respectively.
#     2a. JUNIT_JAR_URL
#         The full URL for the jar.
#     2b. JUNIT_JAR_URL_BASE + JUNIT_VERSION + JUNIT_FILE
#         The individual URL components used to construct the full URL.
#
# TestNG (requires JCommander, Google Guice)
#     Checksum variables:
#         TESTNG_JAR_CHECKSUM: checksum of binary archive
#         TESTNG_LICENSE_CHECKSUM: checksum of LICENSE file
#
#     1. TESTNG_JAR + TESTNG_LICENSE
#         The path to testng.jar and LICENSE.txt respectively.
#     2a. TESTNG_JAR_URL
#         The full URL for the jar.
#     2b. TESTNG_JAR_URL_BASE + TESTNG_VERSION + TESTNG_FILE
#         The individual URL components used to construct the full URL.
*/


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

/**
 * Utility to download the dependencies needed to build jtreg,
 * based on command-line parameters and info in
 * make/build-support/version-numbers.
 *
 * <p>The class can be executed directly by the Java source code launcher,
 * using JDK 12 or later.
 */
public class Build {
    public enum Exit {
        OK, BAD_OPTION, ERROR
    }

    /**
     * Execute the main program.
     *
     * @param args command-line arguments
     */
    public static void main(String... args) {
        try {
            PrintWriter outWriter = new PrintWriter(System.out);
            PrintWriter errWriter = new PrintWriter(System.err, true);
            try {
                try {
                    new Build().run(outWriter, errWriter, args);
                } finally {
                    outWriter.flush();
                }
            } finally {
                errWriter.flush();
            }
            System.exit(Exit.OK.ordinal());
        } catch (BadOption e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(Exit.BAD_OPTION.ordinal());
        } catch (Fault e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(Exit.ERROR.ordinal());
        }
    }

    /**
     * The root directory for the repo containing this class.
     */
    private final Path rootDir;

    /**
     * The minimum version of JDK required to build jtreg.
     */
    private static final int requiredJDKVersion = 11;

    /**
     * Creates an instance of the utility.
     *
     * @throws Fault if an unrecoverable error occurs while determining the root directory
     */
    Build() throws Fault {
        rootDir = getRootDir();
    }

    /**
     * The main worker method for the utility.
     *
     * @param out the stream to which to write any requested output
     * @param err the stream to which to write any logging or error output
     * @param args any command-line arguments
     * @throws BadOption if there is an error in any of the command-line arguments
     * @throws Fault if there is an unrecoverable error
     */
    public void run(PrintWriter out, PrintWriter err, String... args) throws BadOption, Fault {

        // The collection of values specified by the command-line options.
        var options = Options.handle(rootDir, List.of(args));

        // The collection of values derived from command-line options,
        // the make/build-support/version-numbers file, and default values.
        var config = new Config(rootDir, options, out, err);

        var done = false;

        if (options.help) {
            options.showCommandHelp(config.out);
            done = true;
        }

        if (options.showDefaultVersions) {
            showProperties(config.properties, config.out);
            done = true;
        }

        if (options.showConfigDetails) {
            if (config.properties.isEmpty()) {
                config.out.println("no custom configuration values");
            } else {
                showProperties(config.properties, config.out);
            }
            done = true;
        }

        if (done) {
            return;
        }

        Tools tools = new Tools(config);
        Ant ant = new Ant(config, tools);

        var dependencies = List.of(
                new BuildInfo(config, tools),
                new AsmTools(config, tools, ant),
                new JTHarness(config, tools, ant),
                new JUnit(config, tools),
                new TestNG(config, tools)
        );

        if (dependencies.stream().anyMatch(Dependency::isAntRequired)) {
            ant.setup();
            ant.verify();
        }

        for (var d : dependencies) {
            d.setup();
        }

        for (var d : dependencies) {
            d.verify();
        }

        var makeScript = config.buildDir.resolve("make.sh");
        new MakeScript(config).writeFile(makeScript, dependencies);

        if (!options.skipMake) {
            config.log("Building");
            config.out.flush();
            config.err.flush();
            tools.runScript(makeScript, config.options.makeArgs);
        }
    }

    /**
     * Writes a set of properties to a given output stream.
     *
     * @param p the properties
     * @param out the output stream
     */
    private static void showProperties(Properties p, PrintWriter out) {
        p.stringPropertyNames().stream()
                .sorted()
                .forEach(k -> out.println(k + "=" + p.getProperty(k)));
    }

    /**
     * Forms a single list from a string and a list of strings.
     *
     * @param cmd the string
     * @param args the list of strings
     * @return a list formed from the string and list of strings
     */
    private static List<String> join(String cmd, List<String> args) {
        if (args.isEmpty()) {
            return List.of(cmd);
        }
        var list = new ArrayList<String>();
        list.add(cmd);
        list.addAll(args);
        return list;
    }

    /**
     * Returns the root directory for the repo containing this class,
     * as determined by checking enclosing directories for the marker
     * file make/Makefile.
     *
     * @return the root directory
     * @throws Fault if the root directory cannot be determined
     */
    private static Path getRootDir() throws Fault {
        Path dir = getThisClass().getParent();
        Path marker = Path.of("make").resolve("Makefile");
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve(marker))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new Fault("cannot determine root directory");
    }

    /**
     * Returns the path for this class, determined from the location in
     * the class' protection domain.
     *
     * @return the path
     * @throws Fault if an error occurs
     */
    private static Path getThisClass() throws Fault {
        try {
            return Path.of(Build.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new Fault("cannot determine location of this class");
        }
    }

    /**
     * Exception used to report a bad command-line option.
     */
    static class BadOption extends Exception {
        BadOption(String message) {
            super(message);
        }
        BadOption(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception used to report an unrecoverable error.
     */
    static class Fault extends Exception {
        Fault(String message) {
            super(message);
        }
        Fault(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * The set of allowable command-line options.
     */
    enum Option {
        @Description("Show this message")
        HELP("--help -h -help -?", null) {
            @Override
            void process(String opt, String arg, Options options) {
                options.help = true;
            }
        },

        @Description("Path to JDK; must be JDK " + requiredJDKVersion + " or higher")
        JDK("--jdk", "<jdk>") {
            @Override
            void process(String opt, String arg, Options options) throws BadOption {
                options.jdk = asExistingPath(arg);
            }
        },

        @Description("Reduce the logging output")
        QUIET("--quiet -q", null) {
            @Override
            void process(String opt, String arg, Options options) {
                options.quiet = true;
            }
        },

        @Description("Show default versions of external components")
        SHOW_DEFAULT_VERSIONS("--show-default-versions", null) {
            @Override
            void process(String opt, String arg, Options options) {
                options.showDefaultVersions = true;
            }
        },

        @Description("Show configuration details")
        SHOW_CONFIG_DETAILS("--show-config-details", null) {
            @Override
            void process(String opt, String arg, Options options) {
                options.showConfigDetails = true;
            }
        },

        @Description("Skip checksum check")
        SKIP_CHECKSUM_CHECK("--skip-checksum-check", null) {
            @Override
            void process(String opt, String arg, Options options) {
                options.skipChecksumCheck = true;
            }
        },

        @Description("Skip downloads if file available locally")
        SKIP_DOWNLOAD("--skip-download", null) {
            @Override
            void process(String opt, String arg, Options options) {
                options.skipDownloads = true;
            }
        },

        @Description("Skip running 'make' (just download dependencies if needed)")
        SKIP_MAKE("--skip-make", null) {
            @Override
            void process(String opt, String arg, Options options) {
                options.skipMake = true;
            }
        },

        @Description("Provide an alternate file containing dependency version information")
        VERSION_NUMBERS("--version-numbers", "<file>") {
            @Override
            void process(String opt, String arg, Options options) throws BadOption {
                options.versionNumbers = asExistingPath(arg);
            }
        },

        @Description("Provide an alternate file containing configuration details")
        CONFIG_FILE("--config", "<file>") {
            @Override
            void process(String opt, String arg, Options options) throws BadOption, Fault {
                var p = asExistingPath(arg);
                try (BufferedReader r = Files.newBufferedReader(p)) {
                    options.configProperties.load(r);
                } catch (IOException e) {
                    throw new Fault("error reading " + p + ": " + e, e);
                }
            }
        },

        @Description("Override a specific configuration value")
        CONFIG_VALUE("NAME=VALUE", null),

        @Description("Subsequent arguments are for 'make'")
        MAKE_ARGS("--", null);

        @Retention(RetentionPolicy.RUNTIME)
        @interface Description {
            String value();
        }

        final List<String> names;
        final String arg;

        Option(String names, String arg) {
            this.names = Arrays.asList(names.split("\\s+"));
            this.arg = arg;
        }

        void process(String opt, String arg, Options options) throws BadOption, Fault {
            throw new Error("internal error");
        }

        static Path asPath(String p) throws BadOption {
            try {
                return Path.of(p);
            } catch (InvalidPathException e) {
                throw new BadOption("File not found: " + p, e);
            }
        }

        static Path asExistingPath(String p) throws BadOption {
            var path = asPath(p);
            if (!Files.exists(path)) {
                throw new BadOption("File not found: " + p);
            }
            return path;
        }
    }

    /**
     * The set of values given by the command-line options.
     */
    static class Options {
        boolean help;
        Path jdk;
        boolean quiet;
        boolean showDefaultVersions;
        boolean showConfigDetails;
        boolean skipChecksumCheck;
        boolean skipDownloads;
        boolean skipMake;
        private Path versionNumbers;
        private List<String> makeArgs = List.of();

        final private Properties configProperties;

        Options(Path rootDir) {
            var dir = rootDir.resolve("make").resolve("build-support");
            versionNumbers = dir.resolve("version-numbers");
            configProperties = new Properties();
        }

        static Options handle(Path rootDir, List<String> args) throws BadOption, Fault {
            Options options = new Options(rootDir);

            Map<String, Option> map = new HashMap<>();
            for (Option o : Option.values()) {
                o.names.forEach(n -> map.put(n, o));
            }

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                // currently no support for positional args
                String optName, optValue;
                int eq = arg.indexOf("=");
                if (eq == -1) {
                    optName = arg;
                    optValue = null;
                } else {
                    optName = arg.substring(0, eq);
                    optValue = arg.substring(eq + 1);
                }
                if (optName.isEmpty()) {
                    throw new BadOption("bad argument: " + arg);
                } else {
                    Option opt = map.get(optName);
                    if (opt == null) {
                        if (optName.matches("[A-Z_]+")) {
                            options.configProperties.setProperty(optName, optValue);
                        } else {
                            throw new BadOption("unknown option: " + optName);
                        }
                    } else {
                        if (opt == Option.MAKE_ARGS) {
                            options.makeArgs = args.subList(i + 1, args.size());
                            i = args.size();
                        } else if (opt.arg == null) {
                            // no value for option required
                            if (optValue != null) {
                                throw new BadOption("unexpected value for " + optName + " option: " + optValue);
                            } else {
                                opt.process(optName, null, options);
                            }
                        } else {
                            // value for option required; use next arg if not found after '='
                            if (optValue == null) {
                                if (i + 1 < args.size()) {
                                    optValue = args.get(++i);
                                } else {
                                    throw new BadOption("no value for " + optName + " option");
                                }
                            }
                            opt.process(optName, optValue, options);
                        }
                    }
                }
            }

            return options;
        }

        void showCommandHelp(PrintWriter out) {
            out.println("Usage: java " + Build.class.getSimpleName() + ".java "
                    + "<options> [ -- <make options and target>]" );
            out.println("Options:");
            for (var o : Option.values()) {
                out.println(o.names.stream()
                        .map(n -> n + (o.arg == null ? "" : " " + o.arg))
                        .collect(Collectors.joining(", ", "  ", "")));
                try {
                    Field f = Option.class.getDeclaredField(o.name());
                    Option.Description d = f.getAnnotation(Option.Description.class);
                    out.println("      " + d.value());
                } catch (ReflectiveOperationException e) {
                    throw new Error(e);
                }
            }
        }
    }

    /**
     * The set of configuration values determined from command-line options,
     * the make/build-support/version-numbers file, and any defaults.
     */
    static class Config {
        final Path rootDir;
        final Options options;
        final PrintWriter out;
        final PrintWriter err;
        private final Path buildDir;
        private final Properties properties;
        private final Path jdk;
        private final Map<String, String>sysEnv;

        Config(Path rootDir, Options options, PrintWriter out, PrintWriter err) throws Fault {
            this.rootDir = rootDir;
            this.options = options;
            this.out = out;
            this.err = err;

            this.buildDir = rootDir.resolve("build");

            var versionNumbers = readProperties(options.versionNumbers);

            var asmToolsVersionNumbers = readProperties(
                    options.versionNumbers.getParent().resolve("asmtools").resolve("version-numbers"));
            versionNumbers.putAll(asmToolsVersionNumbers);

            var jtHarnessVersionNumbers = readProperties(
                    options.versionNumbers.getParent().resolve("jtharness").resolve("version-numbers"));
            versionNumbers.putAll(jtHarnessVersionNumbers);

            properties = new Properties(versionNumbers);
            properties.putAll(options.configProperties);

            sysEnv = System.getenv();

            var jdk = options.jdk;
            if (jdk == null) {
                jdk = getPath("JAVA_HOME");
            }
            if (jdk == null) {
                jdk = Path.of(System.getProperty("java.home"));
            }
            this.jdk = jdk;
        }

        void log(String line) {
            if (!options.quiet) {
                err.println(line);
            }
        }

        void error(String lines) {
            lines.lines().forEach(err::println);
        }

        private String getString(String key) {
            var v = properties.getProperty(key);
            if (v == null) {
                if (key.endsWith("_VERSION")
                        || key.endsWith("_CHECKSUM")
                        || key.endsWith("_SRC_TAG")
                        || key.contains("_LICENSE_")) {
                    v = properties.getProperty("DEFAULT_" + key);
                }

                if (v == null) {
                    v = sysEnv.get(key);
                }
            }
            return v;
        }

        private String getRequiredString(String key) throws Fault {
            var v = getString(key);
            if (v == null) {
                throw new Fault("no configuration value for " + key);
            }
            return v;
        }

        public Path getPath(String key) throws Fault {
            String v = getString(key);
            try {
                return v == null ? null : Path.of(v);
            } catch (InvalidPathException e) {
                throw new Fault("bad path: " + v + ": " + e);
            }
        }

        public URL getURL(String key) {
            var v = getString(key);
            try {
                return v == null ? null : new URL(v);
            } catch (MalformedURLException e) {
                throw new Error("Bad URL for " + key + ": " + v + ": " + e);
            }
        }

        private Properties readProperties(Path file) throws Fault {
            Properties p = new Properties();
            if (file != null) {
                try (Reader r = Files.newBufferedReader(file)) {
                    p.load(r);
                } catch (IOException e) {
                    throw new Fault("error reading " + file + ": " + e, e);
                }
            }
            return p;
        }
    }

    /**
     * Utility class to interact with host-system tools.
     */
    static class Tools {
        private final Config config;
        private final boolean isWindows;
        private final List<Path> sysPath;
        private final Map<String, Path> commandPaths;

        Tools(Config config) {
            this.config = config;
            isWindows = System.getProperty("os.name").startsWith("Windows");
            sysPath = Arrays.stream(System.getenv("PATH")
                            .split(Pattern.quote(File.pathSeparator)))
                    .filter(c -> !c.isEmpty())
                    .map(Path::of)
                    .collect(Collectors.toList());
            commandPaths = new HashMap<>();
        }

        void deleteDirectory(Path dir) throws Fault {
            if (isWindows) {
                exec("rmdir", "/Q", "/S", dir.toString());
            } else {
                exec(getCommandPath("rm"),"-rf", dir.toString());
            }
        }

        String java(Path javaHome, List<String> args, Predicate<String> filter) throws Fault {
            Path java = javaHome.resolve("bin").resolve("java" + (isWindows ? ".exe" : ""));
            return exec(java, args, filter);
        }

        void runScript(Path script, List<String> args) throws Fault {
            exec(getCommandPath("sh").toString(), join(script.toString(), args));
        }

        void tar(String... args) throws Fault {
            exec(getCommandPath("tar"), args);
        }

        void unzip(String... args) throws Fault {
            exec(getCommandPath("unzip"), args);
        }

        Path getCommandPath(String name) throws Fault {
            Path p = commandPaths.get(name);
            if (p == null) {
                p = config.getPath(name.toUpperCase(Locale.ROOT));
                if (p == null) {
                    p = which(name);
                }
                commandPaths.put(name, p);
            }
            return p;
        }

        private Path which(String command) throws Fault {
            var c = isWindows ? command + ".exe" : command;
            return sysPath.stream()
                    .map(p -> p.resolve(c))
                    .filter(f -> Files.exists(f) && Files.isExecutable(f))
                    .findFirst()
                    .orElseThrow(() -> new Fault("cannot find path for " + command + " command"));
        }

        public void exec(Path command, String... args) throws Fault {
            exec(command.toString(), List.of(args));
        }

        public void exec(String command, String... args) throws Fault {
            exec(command, List.of(args));
        }

        public void exec(String command, List<String> args) throws Fault {
            config.out.flush();
            config.err.flush();
            try {
                Process p = new ProcessBuilder(join(command, args))
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .start();
                p.waitFor();
                int rc = p.exitValue();
                if (rc != 0) {
                    throw new Fault("error running '" + command + "': rc=" + rc);
                }
            } catch (IOException | InterruptedException e) {
                throw new Fault("error running '" + command + "': " + e);
            }
        }

        public String exec(Path command, List<String> args, Predicate<String> filter) throws Fault {
            config.out.flush();
            config.err.flush();
            try {
                Process p = new ProcessBuilder(join(command.toString(), args))
                        .redirectErrorStream(true)
                        .start();
                String out;
                try (var r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    out = r.lines()
                            .filter(filter)
                            .findFirst()
                            .orElse("");
                }
                int rc = p.waitFor();
                if (rc != 0) {
                    throw new Fault("Error running '" + command + ": rc=" + rc);
                }
                return out;
            } catch (IOException | InterruptedException e) {
                throw new Fault("Error running '" + command + ": " + e, e);
            }
        }

        public void exec(Path command, List<String> args, Map<String, String> envExtras, Path outFile) throws Fault {
            config.out.flush();
            config.err.flush();
            try {
                var pb = new ProcessBuilder(join(command.toString(), args))
                        .redirectErrorStream(true);
                var env = pb.environment();
                env.putAll(envExtras);
                var p = pb.start();
                try (var in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                     var out = Files.newBufferedWriter(outFile)) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        out.write(line);
                        out.newLine();
                    }
                }
                int rc = p.waitFor();
                if (rc != 0) {
                    throw new Fault(command + " failed: rc=" + rc);
                }
            } catch (IOException | InterruptedException e) {
                throw new Fault("error while running " + command + ": " + e, e);
            }
        }
    }

    /**
     * Base class for a dependency to be made available for the build.
     */
    static abstract class Dependency {
        protected final String name;
        protected final Path depsDir;
        protected final Config config;
        protected final Tools tools;

        private static final String DEFAULT_MAVEN_URL = "https://repo1.maven.org/maven2";

        Dependency(String name, Config config, Tools tools) {
            this.name = name;
            this.config = config;
            this.tools = tools;
            this.depsDir = config.rootDir.resolve("build").resolve("deps").resolve(name);
        }

        public boolean isAntRequired() {
            return false;
        }

        public abstract void setup() throws Fault;

        public abstract void verify() throws Fault;

        public Map<String, String> getMakeArgs() {
            return Collections.emptyMap();
        }

        protected void createDepsDir() throws Fault {
            try {
                Files.createDirectories(depsDir);
            } catch (IOException e) {
                throw new Fault("Failed to create " + depsDir + ": " + e, e);
            }
        }

        protected Path download(URL url, Path file, String checksum) throws Fault {
            if (Files.isDirectory(file)) {
                file = file.resolve(baseName(url));
            }

            if (Files.isReadable(file) && config.options.skipDownloads) {
                return file;
            }

            config.log("Downloading " + url);
            try {
                Files.createDirectories(file.getParent());
            } catch (IOException e) {
                throw new Fault("Error creating directory for " + file + ": " + e);
            }

            try (var in = url.openStream()) {
                var md = MessageDigest.getInstance("SHA-1");
                try (var in2 = new DigestInputStream(in, md)) {
                    Files.copy(in2, file, StandardCopyOption.REPLACE_EXISTING);
                }
                var digest = toString(md.digest());
                if ((!config.options.skipChecksumCheck && !checksum.equals("--"))
                        && !checksum.equals(digest)) {
                    config.error("Checksum error for " + url + "\n"
                            + "  expect: " + checksum + "\n"
                            + "  actual: " + digest);
                }
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new Fault("Error downloading " + url + ": " + e, e);
            }

            return file;
        }

        protected Path downloadStandardJar(BiFunction<URL, String, String> makeDefaultURL) throws Fault {
            createDepsDir();
            var prefix = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z_]+", "");
            var jarURL = config.getURL(prefix + "_JAR_URL");
            if (jarURL == null) {
                var jarURLBase = config.getURL(prefix + "_JAR_URL_BASE");
                if (jarURLBase == null) {
                    jarURLBase = config.getURL("MAVEN_REPO_URL_BASE");
                    if (jarURLBase == null) {
                        jarURLBase = newURL(DEFAULT_MAVEN_URL);
                    }
                }
                var version = config.getString(prefix + "_VERSION");
                jarURL = newURL(makeDefaultURL.apply(jarURLBase, version));
            }
            var checksum = config.getString(prefix + "_JAR_CHECKSUM");
            return download(jarURL, depsDir, checksum);
        }

        protected Path unpack(Path archive, Path dir) throws Fault {
            try (var ds = Files.newDirectoryStream(depsDir, Files::isDirectory)) {
                for (var d : ds) {
                    tools.deleteDirectory(d);
                }
            } catch (IOException e) {
                throw new Fault("error listing " + depsDir +": " + e, e);
            }

            String s = archive.getFileName().toString();
            if (s.endsWith(".tar.gz")) {
                tools.tar("-xzf", archive.toString(), "-C", dir.toString());
            } else if (s.endsWith(".zip")) {
                // cannot extract files with permissions using standard ZipFile API
                // so resort to the unzip command
                tools.unzip("-q", archive.toString(), "-d", dir.toString());
            } else {
                throw new Fault("unrecognized archive type for file " + archive);
            }
            
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, Files::isDirectory)) {
                Path bestSoFar = null;
                FileTime bestSoFarTime = null;
                for (var p : ds) {
                    var pTime = Files.getLastModifiedTime(p);
                    if (bestSoFar == null || pTime.compareTo(bestSoFarTime) > 0) {
                        bestSoFar = p;
                    }
                    bestSoFarTime = pTime;
                }
                return bestSoFar;
            } catch (IOException e) {
                throw new Fault("Error listing contents of " + dir + ": " + e, e);
            }
        }

        protected Path extract(Path zipFile, String name, Path file) throws Fault {
            var outFile = Files.isDirectory(file) ? file.resolve(name) : file;
            try (var zf = new ZipFile(zipFile.toFile())) {
                var ze = zf.getEntry(name);
                try (var in = zf.getInputStream(ze)) {
                    Files.copy(in, outFile, StandardCopyOption.REPLACE_EXISTING);
                }
                return outFile;
            } catch (IOException e) {
                throw new Fault("error extracting " + name + " from " + zipFile + ": " + e, e);
            }
        }

        protected void checkFile(Path file) throws Fault {
            config.log("Checking " + file);
            if (!(Files.isRegularFile(file) && Files.isReadable(file))) {
                throw new Fault(file + " is not a readable file");
            }
        }

        private String toString(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (var b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }

        protected URL newURL(String u) throws Fault {
            try {
                return new URL(u);
            } catch (MalformedURLException e) {
                throw new Fault("Error creating URL " + u + ": " + e);
            }
        }

        protected String baseName(URL url) {
            var p = url.getPath();
            var lastSep = p.lastIndexOf("/");
            return lastSep == -1 ? p : p.substring(lastSep+ 1);
        }
    }

    /**
     * A pseudo-dependency to provide build version details.
     */
    static class BuildInfo extends Dependency {
        String version;
        String buildMileStone;
        String buildNumber;
        String versionString;

        BuildInfo(Config config, Tools tools) {
            super("jtreg", config, tools);
        }

        @Override
        public void setup() throws Fault {
            var prefix = name.toUpperCase(Locale.ROOT);
            version = config.getRequiredString(prefix + "_VERSION");

            buildMileStone = config.getString(prefix + "_BUILD_MILESTONE");
            if (buildMileStone == null) {
                buildMileStone = "dev";
            }

            buildNumber = config.getString(prefix + "_BUILD_NUMBER");
            if (buildNumber == null) {
                buildNumber = "0";
            }

            versionString = config.getString(prefix + "_VERSION_STRING");
            if (versionString == null) {
                versionString = version
                        + (buildMileStone.isEmpty() ? "" : "-" + buildMileStone)
                        + "+" + buildNumber;
            }
        }

        @Override
        public void verify() throws Fault {
            int version;
            if (config.jdk.equals(Path.of(System.getProperty("java.home")))) {
                version = Runtime.version().feature();
            } else {
                var v = tools.java(config.jdk, List.of("-version"),
                        l -> l.matches(".*(java|openjdk).*"));
                var m = Pattern.compile("\"(1.)?(?<v>[0-9]+)\"").matcher(v);
                if (m.find()) {
                    version = Integer.parseInt(m.group("v"));
                } else {
                    throw new Fault("version info not found in output from '" + config.jdk + "/bin/java -version'");
                }
            }

            if (version < requiredJDKVersion) {
                throw new Fault("JDK " + requiredJDKVersion + " or newer is required to build apidiff");
            }
        }

        @Override
        public Map<String, String> getMakeArgs() {
            return Map.of(
                    "BUILDDIR", config.buildDir.toString(),
                    "JDKHOME", config.jdk.toString(),
                    "BUILD_VERSION", version,
                    "BUILD_MILESTONE", buildMileStone,
                    "BUILD_NUMBER", buildNumber,
                    "BUILD_VERSION_STRING", versionString
            );
        }
    }

    /**
     * Ant, required to build AsmTools and JTHarness from source,
     * if pre-built binaries are not available.
     */
    static class Ant extends Dependency {
        Path ant;

        private static final String DEFAULT_ANT_ARCHIVE_URL_BASE =
                "https://archive.apache.org/dist/ant/binaries";

        public Ant(Config config, Tools tools) {
            super("ant", config, tools);
        }

        @Override
        public void setup() throws Fault {
            ant = config.getPath("ANT");
            if (ant == null) {
                createDepsDir();
                var archiveURL = config.getURL("ANT_ARCHIVE_URL");
                if (archiveURL == null) {
                    var archiveURLBase = config.getURL("ANT_ARCHIVE_URL_BASE");
                    if (archiveURLBase == null) {
                        archiveURLBase = newURL(DEFAULT_ANT_ARCHIVE_URL_BASE);
                    }
                    var version = config.getRequiredString("ANT_VERSION");
                    archiveURL = newURL(
                            archiveURLBase
                            + "/apache-ant-" + version + "-bin.zip");
                }
                var checksum = config.getRequiredString("ANT_ARCHIVE_CHECKSUM");
                var archive = download(archiveURL, depsDir, checksum);
                var unpackDir = unpack(archive, depsDir);
                ant = unpackDir.resolve("bin").resolve("ant");
            }
        }

        @Override
        public void verify() throws Fault {
            checkFile(ant);
        }

        void exec(Path file, List<String> args, Path outFile) throws Fault {
            config.log("Building " + file);
            var antArgs = new ArrayList<String>();
            antArgs.addAll(List.of("-f", file.toString()));
            antArgs.addAll(args);
            tools.exec(ant, antArgs, Map.of("JAVA_HOME", config.jdk.toString()), outFile);
        }
    }

    static class AsmTools extends Dependency {
        private final Ant ant;

        Path jar;
        Path license;

        public AsmTools(Config config, Tools tools, Ant ant) {
            super("asmtools", config, tools);
            this.ant = ant;
        }

        @Override
        public boolean isAntRequired() {
            return config.properties.stringPropertyNames().stream()
                    .noneMatch(k -> k.equals("ASMTOOLS_JAR") || k.contains("ASMTOOLS_ARCHIVE_URL"));
        }

        @Override
        public void setup() throws Fault {
            jar = config.getPath("ASMTOOLS_JAR");
            if (jar == null) {
                createDepsDir();
                var jarArchiveURL = config.getURL("ASMTOOLS_ARCHIVE_URL");
                if (jarArchiveURL == null) {
                    var jarArchiveURLBase = config.getURL("ASMTOOLS_ARCHIVE_URL_BASE");
                    if (jarArchiveURLBase != null) {
                        var version = config.getRequiredString("ASMTOOLS_VERSION");
                        var buildNumber = config.getRequiredString("ASMTOOLS_BUILD_NUMBER");
                        var file = config.getRequiredString("ASMTOOLS_FILE");
                        jarArchiveURL = newURL(
                                jarArchiveURLBase
                                + "/" + version
                                + "/" + buildNumber
                                + "/" + file);
                    }
                }

                if (jarArchiveURL != null) {
                    var checksum = config.getRequiredString("ASMTOOLS_ARCHIVE_CHECKSUM");
                    var jarArchive = download(jarArchiveURL, depsDir, checksum);
                    var unpackDir = unpack(jarArchive, depsDir);
                    jar = unpackDir.resolve("lib").resolve("asmtools.jar");
                    license = unpackDir.resolve("LICENSE");
                } else {
                    config.log("None of ASMTOOLS_JAR, ASMTOOLS_ARCHIVE_URL or ASMTOOLS_ARCHIVE_URL_BASE are set; "
                            + "building from source");
                    var codeToolsURLBase = config.getURL("CODETOOLS_URL_BASE");
                    if (codeToolsURLBase == null) {
                        codeToolsURLBase = newURL("https://git.openjdk.java.net");
                    }
                    var srcTag = config.getRequiredString("ASMTOOLS_SRC_TAG");
                    Path srcArchive;
                    if (srcTag.equals("tip") || srcTag.equals("master")) {
                        var branch = "master";
                        var srcArchiveURL = config.getURL(
                                codeToolsURLBase
                                + "/asmtools/archive/"
                                + branch + ".zip");
                        srcArchive = download(srcArchiveURL, depsDir, "--");
                    } else {
                        var srcArchiveURL = newURL(
                                codeToolsURLBase
                                + "/asmtools/archive/"
                                + srcTag + ".zip");
                        var checksum = config.getRequiredString("ASMTOOLS_SRC_ARCHIVE_CHECKSUM");
                        srcArchive = download(srcArchiveURL, depsDir, checksum);
                    }
                    var unpackDir= unpack(srcArchive, depsDir);
                    var buildDir = depsDir.resolve("build");
                    ant.exec(unpackDir.resolve("build").resolve("build.xml"),
                            List.of("-DBUILD_DIR=" + buildDir),
                            depsDir.resolve("build.log"));
                    var buildBinDir = buildDir.resolve("binaries");
                    jar = buildBinDir.resolve("lib").resolve("asmtools.jar");
                    license = buildBinDir.resolve("LICENSE");
                }
            }

            if (license == null) {
                license = config.getPath("ASMTOOLS_LICENSE");
                if (license == null) {
                    var licenseFile = "LICENSE";
                    license = extract(jar, licenseFile, depsDir);
                }
            }
        }

        @Override
        public void verify() throws Fault {
            checkFile(jar);
            checkFile(license);
        }

        @Override
        public Map<String, String> getMakeArgs() {
            return Map.of(
                    "ASMTOOLS_JAR", jar.toString(),
                    "ASMTOOLS_NOTICES", license.toString());
        }
    }

    static class JTHarness extends Dependency {
        private final Ant ant;

        Path jar;
        Path license;
        Path copyright;


        public JTHarness(Config config, Tools tools, Ant ant) {
            super("jtharness", config, tools);
            this.ant = ant;
        }

        @Override
        public boolean isAntRequired() {
            return config.properties.stringPropertyNames().stream()
                    .noneMatch(k -> k.equals("JTHARNESS_JAVATEST_JAR") || k.contains("JTHARNESS_ARCHIVE_URL"));
        }

        @Override
        public void setup() throws Fault {
            jar = config.getPath("JTHARNESS_JAVATEST_JAR");
            if (jar == null) {
                createDepsDir();
                var jarArchiveURL = config.getURL("JTHARNESS_ARCHIVE_URL");
                if (jarArchiveURL == null) {
                    var jarArchiveURLBase = config.getURL("JTHARNESS_ARCHIVE_URL_BASE");
                    if (jarArchiveURLBase != null) {
                        var version = config.getRequiredString("JTHARNESS_VERSION");
                        var buildNumber = config.getRequiredString("JTHARNESS_BUILD_NUMBER");
                        var file = config.getRequiredString("JTHARNESS_FILE");
                        jarArchiveURL = newURL(
                                jarArchiveURLBase
                                        + "/" + version
                                        + "/" + buildNumber
                                        + "/" + file);
                    }
                }

                if (jarArchiveURL != null) {
                    var checksum = config.getRequiredString("JTHARNESS_ARCHIVE_CHECKSUM");
                    var jarArchive = download(jarArchiveURL, depsDir, checksum);
                    var unpackDir = unpack(jarArchive, depsDir);
                    jar = unpackDir.resolve("lib").resolve("javatest.jar");
                    license = unpackDir.resolve("LICENSE");
                    copyright = unpackDir.resolve("legal").resolve("copyright.txt");
                } else {
                    config.log("None of JTHARNESS_JAR, JTHARNESS_ARCHIVE_URL or JTHARNESS_ARCHIVE_URL_BASE are set; "
                            + "building from source");
                    var codeToolsURLBase = config.getURL("CODETOOLS_URL_BASE");
                    if (codeToolsURLBase == null) {
                        codeToolsURLBase = newURL("https://git.openjdk.java.net");
                    }
                    var srcTag = config.getRequiredString("JTHARNESS_SRC_TAG");
                    Path srcArchive;
                    if (srcTag.equals("tip") || srcTag.equals("master")) {
                        var branch = "master";
                        var srcArchiveURL = config.getURL(
                                codeToolsURLBase
                                        + "/jtharness/archive/"
                                        + branch + ".zip");
                        srcArchive = download(srcArchiveURL, depsDir, "--");
                    } else {
                        var srcArchiveURL = newURL(
                                codeToolsURLBase
                                        + "/jtharness/archive/"
                                        + srcTag + ".zip");
                        var checksum = config.getRequiredString("JTHARNESS_SRC_ARCHIVE_CHECKSUM");
                        srcArchive = download(srcArchiveURL, depsDir, checksum);
                    }
                    var unpackDir = unpack(srcArchive, depsDir);
                    var buildDir = depsDir.resolve("build");
                    ant.exec(unpackDir.resolve("build").resolve("build.xml"),
                            List.of("-DBUILD_DIR=" + buildDir),
                            depsDir.resolve("build.log"));
                    jar = buildDir.resolve("binaries").resolve("lib").resolve("javatest.jar");
                    // The default build target for JTHarness build.xml does not build LICENSE and copyright.txt,
                    // and the dist target, which does build them, has too many other dependencies.
                    // So, pick up the license and copyright.txt from the source directory.
                    license = unpackDir.resolve("LICENSE");
                    copyright = unpackDir.resolve("legal").resolve("copyright.txt");
                }
            }
        }

        @Override
        public void verify() throws Fault {
            checkFile(jar);
            checkFile(license);
            checkFile(copyright);
        }

        @Override
        public Map<String, String> getMakeArgs() {
            var notices = List.of(license, copyright);
            return Map.of("JAVATEST_JAR", jar.toString(),
                    "JTHARNESS_NOTICES",  notices.stream()
                            .map(Path::toString)
                            .collect(Collectors.joining(" ")));
        }
    }

    static class JUnit extends Dependency {
        Path jar;
        Path license;

        public JUnit(Config config, Tools tools) {
            super("junit", config, tools);
        }

        @Override
        public void setup() throws Fault {
            jar = config.getPath("JUNIT_JAR");
            if (jar == null) {
                jar = downloadStandardJar(((urlBase, version) ->
                        urlBase
                        + "/org/junit/platform/junit-platform-console-standalone/"
                        + version
                        + "/junit-platform-console-standalone-" + version + ".jar"
                ));
            }

            license = config.getPath("JUNIT_LICENSE");
            if (license == null) {
                var licenseFile = config.getString("JUNIT_LICENSE_FILE");
                license = extract(jar, licenseFile, depsDir);
            }
        }

        @Override
        public void verify() throws Fault {
            checkFile(jar);
            checkFile(license);
        }

        @Override
        public Map<String, String> getMakeArgs() {
            return Map.of("JUNIT_JARS", jar.toString(),
                    "JUNIT_NOTICES", license.toString());
        }
    }

    static class TestNG extends Dependency {
        private Path jar;
        private Path license;
        private final Guice guice;
        private final JCommander jcommander;

        public TestNG(Config config, Tools tools) {
            super("testng", config, tools);
            this.guice = new Guice(config, tools);
            this.jcommander = new JCommander(config, tools);
        }

        @Override
        public void setup() throws Fault {
            guice.setup();
            jcommander.setup();

            jar = config.getPath("TESTNG_JAR");
            if (jar == null) {
                jar = downloadStandardJar(((urlBase, version) ->
                        urlBase
                        + "/org/testng/testng/"
                        + version
                        + "/testng-" + version + ".jar"
                ));
            }

            license = config.getPath("TESTNG_LICENSE");
            if (license == null) {
                var licenseVersion = config.getRequiredString("TESTNG_LICENSE_VERSION");
                var licenseURL = newURL(
                        "https://raw.githubusercontent.com/cbeust/testng/" + licenseVersion + "/LICENSE.txt");
                var checksum = config.getRequiredString("TESTNG_LICENSE_CHECKSUM");
                license = download(licenseURL, depsDir, checksum);
            }

        }

        @Override
        public void verify() throws Fault {
            guice.verify();
            jcommander.verify();

            checkFile(jar);
            checkFile(license);
        }

        @Override
        public Map<String, String> getMakeArgs() {
            var jars = List.of(jar, jcommander.jar, guice.jar);
            return Map.of(
                    "TESTNG_JARS", jars.stream()
                            .map(Path::toString)
                            .collect(Collectors.joining(" ")),
                    "TESTNG_NOTICES", license.toString());
        }
    }

    static class Guice extends Dependency {
        Path jar;

        public Guice(Config config, Tools tools) {
            super("google_guice", config, tools);
        }

        @Override
        public void setup() throws Fault {
            jar = config.getPath("GUICE_JAR");
            if (jar == null) {
                jar = downloadStandardJar(((urlBase, version) ->
                        urlBase
                                + "/com/google/inject/guice/"
                                + version
                                + "/guice-" + version + ".jar"
                ));
            }
        }

        @Override
        public void verify() throws Fault {
            checkFile(jar);
        }
    }

    static class JCommander extends Dependency {
        Path jar;

        public JCommander(Config config, Tools tools) {
            super("jcommander", config, tools);
        }

        @Override
        public void setup() throws Fault {
            jar = config.getPath("JCOMMANDER_JAR");
            if (jar == null) {
                jar = downloadStandardJar(((urlBase, version) ->
                        urlBase
                                + "/com/beust/jcommander/"
                                + version
                                + "/jcommander-" + version + ".jar"
                ));
            }
        }

        @Override
        public void verify() throws Fault {
            checkFile(jar);
        }
    }

    /**
     * Generates a script to run "make", based on the set of dependencies.
     */
    static class MakeScript {
        private final Config config;
        MakeScript(Config config) {
            this.config = config;
        }

        void writeFile(Path file, List<? extends Dependency> deps) throws Fault {
            var allMakeArgs = new TreeMap<String, String>();
            deps.forEach(d -> allMakeArgs.putAll(d.getMakeArgs()));

            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file))) {
                out.println("#!/bin/sh");
                out.println();
                out.println("cd \"" + config.rootDir.resolve("make") + "\"");
                out.println("make \\");
                allMakeArgs.forEach((name, value) ->
                        out.printf("    %s=\"%s\" \\%n", name, value));
                out.println("    \"$@\"");
            } catch (IOException e) {
                throw new Fault("Error writing make command script: " + file + ": " + e);
            }
        }
    }
}
