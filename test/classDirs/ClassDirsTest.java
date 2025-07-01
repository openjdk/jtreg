/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

/**
 * A test to create and run tests with jtreg, and to verify the
 * set of class files that is generated.
 *
 * @author jjg
 */
public class ClassDirsTest {
    public static void main(String... args) throws Exception {
        new ClassDirsTest().runTests();
    }

    /**
     * Verifies the set of expected classes when the tests use
     * explicit build tags for library classes.
     *
     * @param base a directory for the test suite and results
     * @throws Exception if an error occurs
     */
    @Test
    void testNoImplicitCompilation(Path base) throws Exception {
        TestSuite ts = createTests(base, "p.*", false);
        ts.run(base.resolve("work"), base.resolve("report"), ts.dir.resolve("std"));
        checkClassFiles(base.resolve("work/classes"),
            "std/Test1.d/lib/p/Lib1.class",
            "std/Test1.d/lib/p/Lib2.class",
            "std/Test2.d/lib/p/Lib1.class",
            "std/Test2.d/lib/p/Lib2.class",
            "std/Test1.d/Test1.class",
            "std/Test2.d/Test2.class"
        );
    }

    /**
     * Verifies the set of expected classes when the tests use
     * explicit build tags for library classes.
     * The property shareLibraries is set to true.
     *
     * @param base a directory for the test suite and results
     * @throws Exception if an error occurs
     */
    @Test
    void testNoImplicitCompilationLegacy(Path base) throws Exception {
        TestSuite ts = createTests(base, "p.*", true);
        ts.run(base.resolve("work"), base.resolve("report"), ts.dir.resolve("std"));
        checkClassFiles(base.resolve("work/classes"),
            "lib/p/Lib1.class",
            "lib/p/Lib2.class",
            "std/Test1.d/Test1.class",
            "std/Test2.d/Test2.class"
        );
    }


    /**
     * Verifies the set of expected classes when the tests use
     * incomplete explicit build tags for library classes,
     * thus relying on implicit compilation within the library.
     *
     * @param base a directory for the test suite and results
     * @throws Exception if an error occurs
     */
    @Test
    void testPartialImplicitCompilation(Path base) throws Exception {
        TestSuite ts = createTests(base, "p.Lib2", false);
        ts.run(base.resolve("work"), base.resolve("report"), ts.dir.resolve("std"));
        checkClassFiles(base.resolve("work/classes"),
            "std/Test1.d/lib/p/Lib1.class",
            "std/Test1.d/lib/p/Lib2.class",
            "std/Test2.d/lib/p/Lib1.class",
            "std/Test2.d/lib/p/Lib2.class",
            "std/Test1.d/Test1.class",
            "std/Test2.d/Test2.class"
        );
    }

    /**
     * Verifies the set of expected classes when the tests do not use
     * explicit build tags for library classes, thus relying on implicit
     * compilation of the library when the test itself is compiled.
     *
     * @param base a directory for the test suite and results
     * @throws Exception if an error occurs
     */
    @Test
    void testImplicitCompilation(Path base) throws Exception {
        TestSuite ts = createTests(base, null, false);
        ts.run(base.resolve("work"), base.resolve("report"), ts.dir.resolve("std"));
        checkClassFiles(base.resolve("work/classes"),
            "std/Test1.d/Test1.class",
            "std/Test1.d/p/Lib1.class",
            "std/Test1.d/p/Lib2.class",
            "std/Test2.d/Test2.class",
            "std/Test2.d/p/Lib1.class",
            "std/Test2.d/p/Lib2.class"
        );
    }

    /**
     * Verifies the set of expected classes when the tests are in a TestNG group.
     * In this case, all the library classes are automatically compiled.
     *
     * @param base a directory for the test suite and results
     * @throws Exception if an error occurs
     */
    @Test
    void testTestNG(Path base) throws Exception {
        TestSuite ts = createTests(base, null, false);
        ts.run(base.resolve("work"), base.resolve("report"), ts.dir.resolve("testng"));
        checkClassFiles(base.resolve("work/classes"),
            "testng/lib/p/Lib1.class",
            "testng/lib/p/Lib2.class",
            "testng/testng/Test1.class",
            "testng/testng/Test2.class"
        );
    }

    /**
     * Verifies the set of expected classes when some tests that cause a
     * library to be built are executed before some tests that rely on
     * implicit compilation.
     *
     * @param base a directory for the test suite and results
     * @throws Exception if an error occurs
     */
    @Test
    void testTestNGImplicit(Path base) throws Exception {
        TestSuite ts = createTests(base, null, false);
        ts.run(base.resolve("work"), base.resolve("report1"), ts.dir.resolve("testng"));
        ts.run(base.resolve("work"), base.resolve("report2"), ts.dir.resolve("std"));
        checkClassFiles(base.resolve("work/classes"),
            "std/Test1.d/p/Lib1.class",
            "std/Test1.d/p/Lib2.class",
            "std/Test2.d/p/Lib1.class",
            "std/Test2.d/p/Lib2.class",
            "std/Test1.d/Test1.class",
            "std/Test2.d/Test2.class",
            "testng/lib/p/Lib1.class",
            "testng/lib/p/Lib2.class",
            "testng/testng/Test1.class",
            "testng/testng/Test2.class"
        );
    }

    /**
     * Verifies the set of expected classes when some tests that rely on
     * implicit compilation are executed before some tests that cause a
     * library to be built.
     *
     * @param base a directory for the test suite and results
     * @throws Exception if an error occurs
     */
    @Test
    void testImplicitTestNG(Path base) throws Exception {
        TestSuite ts = createTests(base, null, false);
        ts.run(base.resolve("work"), base.resolve("report1"), ts.dir.resolve("std"));
        ts.run(base.resolve("work"), base.resolve("report2"), ts.dir.resolve("testng"));
        checkClassFiles(base.resolve("work/classes"),
            "testng/lib/p/Lib1.class",
            "testng/lib/p/Lib2.class",
            "std/Test1.d/Test1.class",
            "std/Test1.d/p/Lib1.class",
            "std/Test1.d/p/Lib2.class",
            "std/Test2.d/Test2.class",
            "std/Test2.d/p/Lib1.class",
            "std/Test2.d/p/Lib2.class",
            "testng/testng/Test1.class",
            "testng/testng/Test2.class"
        );
    }

    /**
     * Creates a test suite, varying the @build directive for some tests.
     *
     * @param base the parent directory to contain the test suite
     * @param build an optional argument for an @build directive
     * @return the test suite
     * @throws Exception if an error occurs
     */
    private TestSuite createTests(Path base, String build, boolean shareLibraries) throws Exception {
        String shareLibrariesLine = shareLibraries ? "shareLibraries = true" : "";
        TestSuite ts = new TestSuite(base.resolve("tests"), "requiredVersion = 4.2 b08\n" + shareLibrariesLine)
            .addLibraryFile("lib", "package p; public class Lib1 { void m() { } }")
            .addLibraryFile("lib", "package p; public class Lib2 { void m(Lib1 l1) { } }");

        ts.new TestBuilder("std/Test1.java")
            .addLibrary("/lib")
            .addBuild(build)
            .addRun()
            .source("public class Test1 {",
                "    public static void main(String... args) {",
                "        new p.Lib2();",
                "    }",
                "}")
            .write();

        ts.new TestBuilder("std/Test2.java")
            .addLibrary("/lib")
            .addBuild(build)
            .addRun()
            .source("public class Test2 {",
                "    public static void main(String... args) {",
                "        new p.Lib2();",
                "    }",
                "}")
            .write();

        ts.new TestNGGroupBuilder("testng")
            .addLibrary("/lib")
            .source("package testng;",
                "import org.testng.annotations.*;",
                "public class Test1 {",
                "    @Test",
                "    public void test() { }",
                "}")
            .source("package testng;",
                "import org.testng.annotations.*;",
                "public class Test2 {",
                "    @Test",
                "    public void test() { }",
                "}")
            .write();

        return ts;

    }

    /**
     * Checks the class files found in a given directory against a list
     * of expected files.
     *
     * @param dir the directory
     * @param expect the list of expected files, each being specified
     *      relative to the directory
     * @throws IOException if a problem occurs while reading the directory
     */
    private void checkClassFiles(final Path dir, String... expect) throws IOException {
        boolean ok = true;

        Set<Path> expectSet = new TreeSet<>();
        for (String e : expect)
            expectSet.add(Paths.get(e));

        final Set<Path> foundSet = new TreeSet<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".class")) {
                    foundSet.add(dir.relativize(file));
                }
                return super.visitFile(file, attrs);
            }
        });

        for (Path e : expectSet) {
            if (!foundSet.contains(e)) {
                error("expected file not found: " + e);
                ok = false;
            }
        }

        for (Path f : foundSet) {
            if (!expectSet.contains(f)) {
                error("unexpected file found: " + f);
                ok = false;
            }
        }

        if (ok) {
            out.println("Found " + foundSet.size() + " files, as expected.");
        }
    }


// <editor-fold defaultstate="collapsed" desc=" Test builder ">

    /**
     * An abstraction to help build and run a jtreg test suite.
     */
    static class TestSuite {

        final Path dir;

        TestSuite(Path dir, String... config) throws IOException {
            this.dir = dir;
            Files.createDirectories(dir);
            try (BufferedWriter out = Files.newBufferedWriter(dir.resolve("TEST.ROOT"),
                    Charset.defaultCharset())) {
                for (String line: config) {
                    out.write(line); out.newLine();
                }
            }
        }

        TestSuite addLibraryFile(String lib, String source) throws IOException {
            new JavaSource(source).write(dir.resolve(lib));
            return this;
        }

        void run(Path workDir, Path reportDir, Path tests) {
            List<String> args = new ArrayList<>();
            args.add("-w:" + workDir);
            args.add("-r:" + reportDir);
            args.add(tests.toString());

            try {
                Class<?> mainClass = Class.forName("com.sun.javatest.regtest.Main");
                Constructor<?> constr = mainClass.getConstructor();
                Object main = constr.newInstance();
                Method runMethod = mainClass.getDeclaredMethod("run", String[].class);
                runMethod.invoke(main, (Object) args.toArray(new String[args.size()]));
            } catch (Error | Exception e) {
                throw new Error("Exception running jtreg: " + e, e);
            }
        }

        /**
         * A builder for a standard jtreg test.
         */
        class TestBuilder {
            private final String name;
            private List<String> libraries = new ArrayList<>();
            private List<String> build = new ArrayList<>();
            private List<String> run = new ArrayList<>();
            private List<String> source = new ArrayList<>();

            TestBuilder(String name) {
                this.name = name;
            }

            TestBuilder addLibrary(String name) {
                libraries.add("@library " + name);
                return this;
            }

            TestBuilder addBuild(String items) {
                if (items != null) {
                    build.add("@build " + items);
                }
                return this;
            }

            TestBuilder addRun(String... args) {
                String cn = Paths.get(name).getFileName().toString().replace(".java", "");
                StringBuilder sb = new StringBuilder();
                sb.append("@run main ").append(cn);
                for (String arg: args)
                    sb.append(" ").append(arg);
                run.add(sb.toString());
                return this;
            }

            TestBuilder source(String... lines) {
                source = Arrays.asList(lines);
                return this;
            }

            TestBuilder write() throws IOException {
                Path file = dir.resolve(name);
                Files.createDirectories(file.getParent());
                try (BufferedWriter out = Files.newBufferedWriter(file,
                        Charset.defaultCharset())) {
                    out.write("/* @test");
                    out.newLine();
                    for (String l : libraries) {
                        out.write(" * "); out.write(l); out.newLine();
                    }
                    for (String b : build) {
                        out.write(" * "); out.write(b); out.newLine();
                    }
                    for (String r : run) {
                        out.write(" * "); out.write(r); out.newLine();
                    }
                    out.write("*/");
                    out.newLine();
                    for (String line: source) {
                        out.write(line); out.newLine();
                    }
                }
                return this;
            }
        }

        /**
         * A builder for a group of TestNG tests.
         */
        class TestNGGroupBuilder {
            private final String dir;
            private List<String> libraries = new ArrayList<>();
            private List<List<String>> sources = new ArrayList<>();

            TestNGGroupBuilder(String dir) {
                this.dir = dir;
            }

            TestNGGroupBuilder addLibrary(String name) {
                libraries.add(name);
                return this;
            }

            TestNGGroupBuilder source(String... lines) {
                sources.add(Arrays.asList(lines));
                return this;
            }

            void write() throws IOException {
                Path testNGRootDir = TestSuite.this.dir.resolve(dir);
                Files.createDirectories(testNGRootDir);
                Path props = testNGRootDir.resolve("TEST.properties");
                try (BufferedWriter out = Files.newBufferedWriter(props,
                        Charset.defaultCharset())) {
                    out.write("TestNG.dirs = ."); out.newLine();
                    if (!libraries.isEmpty()) {
                        out.write("lib.dirs =");
                        for (String l : libraries) {
                            out.write(" "); out.write(l);
                        }
                        out.newLine();
                    }
                }

                for (List<String> source : sources) {
                    StringBuilder sb = new StringBuilder();
                    for (String line : source) {
                        sb.append(line).append("\n");
                    }
                    new JavaSource(sb.toString()).write(testNGRootDir);
                }
            }
        }
    }

    /**
     * An in-memory Java source file.
     * It is able to derive the file name from simple source text using
     * regular expressions.
     */
    static class JavaSource extends SimpleJavaFileObject {

        private static final String lineSeparator = System.getProperty("line.separator");
        private final String source;

        /**
         * Creates a in-memory file object for Java source code.
         *
         * @param className the name of the class
         * @param source the source text
         */
        public JavaSource(String className, String source) {
            super(URI.create(className), JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        /**
         * Creates a in-memory file object for Java source code. The name of the
         * class will be inferred from the source code.
         *
         * @param source the source text
         */
        public JavaSource(String source) {
            super(URI.create(getJavaFileNameFromSource(source)),
                    JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        /**
         * Writes the source code to a file in a specified directory.
         *
         * @param dir the directory
         * @throws IOException if there is a problem writing the file
         */
        public void write(Path dir) throws IOException {
            Path file = dir.resolve(getJavaFileNameFromSource(source));
            Files.createDirectories(file.getParent());
            try (BufferedWriter out = Files.newBufferedWriter(file,
                    Charset.defaultCharset())) {
                out.write(source.replace("\n", lineSeparator));
            }
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }

        private static Pattern modulePattern
                = Pattern.compile("module\\s+((?:\\w+\\.)*)");
        private static Pattern packagePattern
                = Pattern.compile("package\\s+(((?:\\w+\\.)*)(?:\\w+))");
        private static Pattern classPattern
                = Pattern.compile("(?:public\\s+)?(?:class|enum|interface)\\s+(\\w+)");

        /**
         * Extracts the Java file name from the class declaration. This method
         * is intended for simple files and uses regular expressions, so
         * comments matching the pattern can make the method fail.
         */
        static String getJavaFileNameFromSource(String source) {
            String packageName = null;

            Matcher matcher = modulePattern.matcher(source);
            if (matcher.find()) {
                return "module-info.java";
            }

            matcher = packagePattern.matcher(source);
            if (matcher.find()) {
                packageName = matcher.group(1).replace(".", "/");
            }

            matcher = classPattern.matcher(source);
            if (matcher.find()) {
                String className = matcher.group(1) + ".java";
                return (packageName == null) ? className : packageName + "/" + className;
            } else if (packageName != null) {
                return packageName + "/package-info.java";
            } else {
                throw new Error("Could not extract the java class "
                        + "name from the provided source");
            }
        }
    }

// </editor-fold>

// <editor-fold defaultstate="collapsed" desc=" Test runner ">

    @Retention(RetentionPolicy.RUNTIME)
    @interface Test { }

    /**
     * Invoke all methods annotated with @Test.
     *
     * @throws java.lang.Exception if any errors occur
     */
    protected void runTests() throws Exception {
        for (Method m : getClass().getDeclaredMethods()) {
            Annotation a = m.getAnnotation(Test.class);
            if (a != null) {
                String testName = m.getName();
                try {
                    testCount++;
                    out.println("test: " + testName);
                    m.invoke(this, Paths.get(m.getName()));
                } catch (InvocationTargetException e) {
                    errorCount++;
                    Throwable cause = e.getCause();
                    out.println("Exception running test " + testName + ": " + e.getCause());
                    cause.printStackTrace(out);
                }
                out.println();
            }
        }

        if (testCount == 0) {
            throw new Error("no tests found");
        }

        StringBuilder summary = new StringBuilder();
        if (testCount != 1) {
            summary.append(testCount).append(" tests");
        }
        if (errorCount > 0) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(errorCount).append(" errors");
        }
        out.println(summary);
        if (errorCount > 0) {
            throw new Exception(errorCount + " errors found");
        }
    }

    protected void error(String message) {
        out.println("Error: " + message);
        errorCount++;
    }

    protected PrintStream out = System.err;
    protected int testCount = 0;
    protected int errorCount = 0;

// </editor-fold>
}

