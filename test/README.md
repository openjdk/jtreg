# Testing `jtreg`

This note describes the mechanisms used to test `jtreg` itself.

The test infrastructure uses GNU make and makefiles.

While `jtreg` is a test harness for running tests, it is not particularly well-suited
to hosting a general set of tests for testing the operation of the `jtreg` tool itself.
For a start, you would have recursive invocations of `jtreg`, an outer one to run the
tests and then often an inner invocation as the subject of a particular test.
Also, many tests of the `jtreg` tool are negative tests, designed to test whether
specific error conditions are detected and reported correctly, so we need an easy way
to check the outcome and output from an invocation of `jtreg`. However, makefile targets,
dependencies and rules provide a suitably flexible environment to support the code
needed to test jtreg.

## Makefiles

At the top level, all tests have an associated `make` target, which by convention
is of the form _testName_`.ok`. This corresponds to the path for a "marker file",
which is updated when the test has been executed successfully.

With only a few exceptions, the files for each test are grouped in a subdirectory
of the main `test` directory. Each such directory contains a `*.gmk` file defining
one or more `*.ok` targets for tests in that directory. The `*.ok` targets are added
into a cumulative `TESTS.jtreg` variable, so that the top-level Makefile can have
code of the form:

```makefile
include $(TESTDIR)/*/*.gmk      # include all the tests' makefiles

test: $(TESTS.jtreg)            # the main test target depends on all the individual test targets

```

Each `*.ok` target for a test specifies any dependencies, so that the test will
be rerun if any of the dependencies are updated and become newer than the target itself.

The rules for a test target form a short makefile "script" that executes the
steps of the test. The last rule typically updates the `*.ok` target, with
a command like

```makefile
        echo "Test passed at `date`" > $@
```

This rule will only be executed if all the preceding steps succeed. implying that
the test has behaved as expected.  If any part of the test execution fails, the
corresponding rule should return a non-zero exit code.

If a test depends on resources or environment that may or may not be available,
the test target can be conditionally included in `TESTS.jtreg` using `ifdef` or similar
mechanisms.

Any individual test can be run in isolation by running `make` with the full
absolute pathname for the marker file (that is, the `*.ok` target for the test)
as the target to be built.

## Tests

Tests of `jtreg` functionality generally come in one of two forms:

1. The test executes `jtreg` on a small associated test suite, and verifies the output
   is as expected. It may be the case that some tests in the test suite are expected
   to fail, and so it is common the catch the output from `jtreg` written to the console
   stream and to `grep` it for the expected results.

   Some tests may use `*.ok` targets that encode `agentvm` or `othervm` in their name
   and then use makefile macros to extract that token from the target name, and use
   it to construct an option to pass to `jtreg`. This allows one set of rules to
   be used to run `jtreg` in the two different modes.

    * _Example_: See `test/libBuildArgs/LibBuildArgsTest.gmk`

2. The test is a standalone Java program to be compiled and executed as a unit
   test for some specific functionality within jtreg.

   * _Example:_ See `test/osTest.OSTest.gmk` and `test/osTest.OSTest.gmk`

   * _Note:_ now that jtreg relies on using JDK >= 11, it may be possible to use
     the "source-code launcher" feature introduced in JDK 9 to compile and run
     some of these tests.



