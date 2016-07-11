/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static java.util.Arrays.asList;

import com.sun.javatest.regtest.BadArgs;
import com.sun.javatest.regtest.Option;
import com.sun.javatest.regtest.Option.ArgType;
import com.sun.javatest.regtest.OptionDecoder;

import static com.sun.javatest.regtest.Option.ArgType.*;

/**
 * Unit test for jtreg command-line option decoding,
 * including positive and negative tests for the different
 * styles of options and the different types of arguments
 * they may take.
 *
 * The test is primarily about ensuring that the argument
 * for an option (if any) is correctly recognized and made
 * available to the option handling code. This is done by
 * analyzing the input array of strings, and building a
 * corresponding output list, containing pairs of option
 * name and it's argument, or NULL,  The output list is
 * compared against a golden list.
 *
 * If the test is run with no args, it will execute the
 * built in test cases.
 * If it is run with args, it will execute a single test
 * case based on those args.
 */
public class OptionDecoderTest {
    public static void main(String... args) throws Exception {
        new OptionDecoderTest().run(args);
    }

    private static final String BADARGS = "BadArgs";
    private static final String FILE = "FILE";
    private static final String NULL = "NULL";

    // ArgType.FILE: file

    @Test
    void testFile() {
        test(asList("file"),
                asList(FILE, "file"));

        test(asList("-o", "file", "-splodge"),
                asList("-o", NULL, FILE, "file", FILE, "-splodge"));
    }

    // ArgType.GNU: --opt arg, --opt=arg, -o arg, -oarg

    @Test
    void testGnu1() {
        test(asList("--long-only", "abc"),
                asList("--long-only", "abc"));

        test(asList("--long-only=abc"),
                asList("--long-only=abc", "abc"));
    }

    @Test
    void testGnu2() {
        test(asList("--long-or-short", "abc"),
                asList("--long-or-short", "abc"));

        test(asList("--long-or-short=abc"),
                asList("--long-or-short=abc", "abc"));

        test(asList("-l", "abc"),
                asList("-l", "abc"));

        test(asList("-labc"),
                asList("-labc", "abc"));

        // no value
        test(asList("--long-or-short"),
                asList(BADARGS, "No value given for option --long-or-short"));

        // wrong separator
        test(asList("--long-or-short:abc"),
                asList(BADARGS, "Bad format for option: --long-or-short:abc"));

        // no separator
        test(asList("--long-or-shortabc"),
                asList(BADARGS, "Invalid option: --long-or-shortabc"));
    }

    // ArgType.NONE: -opt (includes --opt -o)

    @Test
    void testNoArg() {
        test(asList("-noarg"),
                asList("-noarg", NULL));

        test(asList("--no-arg"),
                asList("--no-arg", NULL));

        test(asList("-n"),
                asList("-n", NULL));

        test(asList("-n=abc"),
                asList(BADARGS, "Unexpected value for option -n=abc"));

        test(asList("-n:abc"),
                asList(BADARGS, "Unexpected value for option -n:abc"));
    }

    // ArgType.OLD: -opt:arg or -opt arg

    @Test
    void testOld() {
        test(asList("-old", "abc"),
                asList("-old", "abc"));

        test(asList("-old:abc"),
                asList("-old:abc", "abc"));

        // no value
        test(asList("-old"),
                asList(BADARGS, "No value given for option -old"));

        // wrong separator
        test(asList("-old=abc"),
                asList(BADARGS, "Bad format for option: -old=abc"));
    }

    // ArgType.OPT: -opt or -opt:arg

    @Test
    void testOpt() {
        test(asList("-optional"),
                asList("-optional", NULL));

        test(asList("-optional:arg"),
                asList("-optional:arg", "arg"));

        // wrong separator
        test(asList("-optional=arg"),
                asList(BADARGS, "Bad format for option: -optional=arg"));
    }

    // ArgType.REST:   -opt rest of args

    @Test
    void testRest() {
        test(asList("--rest", "abc", "-def"),
                asList("--rest", "abc -def"));

        test(asList("--rest:arg", "abc", "-def"),
                asList("--rest:arg", "arg abc -def"));

        test(asList("--rest=arg", "abc", "-def"),
                asList("--rest=arg", "arg abc -def"));
    }

    // ArgType.SEP:  -opt arg

    @Test
    void testSep() {
        test(asList("-wsarg", "arg"),
                asList("-wsarg", "arg"));

        // no value
        test(asList("-wsarg"),
                asList(BADARGS, "No value given for option -wsarg"));

        // non-ws separator
        test(asList("-wsarg=arg"),
                asList(BADARGS, "Bad format for option: -wsarg=arg"));

        // non-ws separator
        test(asList("-wsarg:arg"),
                asList(BADARGS, "Bad format for option: -wsarg:arg"));
    }

    // ArgType.STD:   -opt:arg

    @Test
    void testStd() {
        test(asList("-std:arg"),
                asList("-std:arg", "arg"));

        // no value
        test(asList("-std"),
                asList(BADARGS, "No value given for option -std"));

        // white space separator
        test(asList("-std", "arg"),
                asList(BADARGS, "No value given for option -std"));

        // wrong separator
        test(asList("-std=arg"),
                asList(BADARGS, "Bad format for option: -std=arg"));
    }

    // ArgType.WILDCARD:   -optarg

    @Test
    void testWildcard() {
        test(asList("-Xarg"),
                asList("-Xarg", "arg"));

        test(asList("-X:arg"),
                asList("-X:arg", ":arg"));

        test(asList("-X=arg"),
                asList("-X=arg", "=arg"));
    }

    // Errors

    @Test
    void testUnknown() {
        test(asList("-unknown"),
                asList(BADARGS, "Invalid option: -unknown"));
    }



    // -----------------------------------------------------------------

    class SimpleOption extends Option {
        SimpleOption(ArgType a, String g, String ln, String... names) {
            super(a, g, ln, names);
        }

        @Override
        public void process(String opt, String arg) {
            results.add(opt);
            results.add(arg == null ? NULL : arg);
        }
    }

    private final String GROUP = "g";

    List<Option> options = Arrays.<Option>asList(
        new SimpleOption(GNU, GROUP, null, "--long-only") { },
        new SimpleOption(GNU, GROUP, null, "--long-or-short", "-l") { },

        new SimpleOption(NONE, GROUP, null, "-noarg", "--no-arg", "-n") { },

        new SimpleOption(OLD, GROUP, null, "-old") { },

        new SimpleOption(OPT, GROUP, null, "-optional", "-o") { },

        new SimpleOption(OPT, GROUP, null, "-optionalChoice", "-oc") {
            @Override
            public String[] getChoices() {
                return new String[] { "a", "b", "c" };
            }
        },

        new SimpleOption(REST, GROUP, null, "--rest") { },
        new SimpleOption(REST, GROUP, null, "--help", "-help", "-h") { },

        new SimpleOption(SEP, GROUP, null, "-wsarg") { },

        new SimpleOption(STD, GROUP, null, "-standard", "-std") { },

        new SimpleOption(WILDCARD, GROUP, null, "-X") { },

        new SimpleOption(ArgType.FILE, GROUP, null) {
            @Override
            public void process(String opt, String arg) {
                results.add(FILE);
                results.add(arg);
            }
        }
    );

    /** Marker annotation for test cases. */
    @Retention(RetentionPolicy.RUNTIME)
    @interface Test { }

    List<String> results = new ArrayList<>();

    void run(String... args) throws Exception {
        if (args.length == 0) {
            runTests();
        } else {
            OptionDecoder d = new OptionDecoder(options);
            try {
                d.decodeArgs(args);
            } catch (BadArgs e) {
                results.add(BADARGS);
                results.add(e.getMessage());
            }
            System.out.println("Out: " + results);
        }
    }

    /**
     * Combo test to run all test cases in all modes.
     */
    void runTests() throws Exception {
        for (Method m : getClass().getDeclaredMethods()) {
            Annotation a = m.getAnnotation(Test.class);
            if (a != null) {
                try {
                    System.out.println("Test: " + m.getName());
                    m.invoke(this);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    throw (cause instanceof Exception) ? ((Exception) cause) : e;
                }
                System.err.println();
            }
        }
        System.err.println(testCount + " tests" + ((errorCount == 0) ? "" : ", " + errorCount + " errors"));
        if (errorCount > 0) {
            throw new Exception(errorCount + " errors found");
        }
    }

    void test(List<String> opts, List<String> expect) {
        testCount++;
        results.clear();
        OptionDecoder d = new OptionDecoder(options);
        try {
            d.decodeArgs(opts);
        } catch (BadArgs e) {
            results.add(BADARGS);
            results.add(e.getMessage());
        }
        System.out.println("Options: " + opts);
        System.out.println("Expect:  " + expect);
        System.out.println("Found:   " + results);
        if (!results.equals(expect)) {
            System.out.println("ERROR");
            errorCount++;
        }
    }

    int testCount;
    int errorCount;
}


