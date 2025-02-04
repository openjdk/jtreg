% jtreg FAQ

--------

## Overview

### What's the purpose of this test framework?

The test framework described here is intended primarily for unit and regression  tests.
It can also be used for functional tests, and even simple
product tests -- in other words, just about any type of test except a
conformance test.  (Conformance tests belong in a Technology
Compatibility Kit (TCK), such as the Java Compatibility Kit (JCK)).
Tests can often be written as small standalone Java programs, although
in some cases an applet or a shell-script might be required.

### What's a regression test?

A regression test is a test written specifically to check that a bug has
been fixed and remains fixed.  A regression test should fail when run against a
build with the bug in question, and pass when run against a build in which the
bug has been fixed.

### But can I also write non-regression tests using this framework?

Yes.  Suppose, for example, that you evaluate a bug that turns out not
to be a bug, but in the course of doing the evaluation you write a test that
checks for correct behavior.  You can help improve the quality of the JDK by
checking this test into the test directory.

### What is the difference between a unit test and a regression test?

*   A unit test is one you write as insurance against having to write a regression
    test later on.
*   A regression test is the penalty you pay for not having written better
    unit tests in the first place.

### What is the JavaTest&trade; harness?

The JavaTest harness is a set of tools designed to execute test programs.
It was originally designed to execute tests in the Java Compatibility Kit (JCK).
Among other things, the harness has evolved the ability to execute
non-JCK testsuites.  The JDK regression test suite is one such suite.

An open source version of the harness is available at
[http://openjdk.org/projects/code-tools/jtharness/](http://openjdk.org/projects/code-tools/jtharness/).

### What are the JDK regression extensions to the JavaTest harness? What is "regtest"?

For the harness to execute tests in a given test suite, it needs
specialized code which knows how to find test descriptions and how to interpret
those descriptions.  The
[JDK Test Framework: Tag Language Specification](tag-spec.html)
provides the needed descriptions.
"regtest" refers to extensions for the JavaTest harness that
implement this specification, and is an older name for what is now known as
"jtreg".

### What are the system requirements for using the JDK regression extensions?

It is recommended that you run jtreg using JDK 11 or later.

### Where can I find a copy of jtreg?

Information on downloading and building the source code, as well as publicly
available binaries, is given on the [OpenJDK jtreg home page](http://openjdk.org/jtreg).

### Where do I find additional supporting documentation?

Beyond the Java&trade; Platform documentation, the following are
relevant documentation resources.

*   [JDK Test Framework: Tag Language Specification](tag-spec.html) -
    The definitive document defining the test description tags (syntax and behavior).

*   The `-help` option to jtreg offers brief
    documentation for the complete set of currently available options.

### There's functionality missing from the tag specification. I can't write my test, or it would vastly improve the life of people writing tests if it was added. What do I need to do?

See the [OpenJDK jtreg home page](http://openjdk.dev.java.net/jtreg)
for a suitable forum or mailing list.

### The spec is fine, but there's some functionality that I'd like to get from the regression extensions, or I still can't run it on a basic test. Who do I contact?

Send email to `jtreg-discuss(at)openjdk.org`

### Why not use JUnit or TestNG?

JUnit and TestNG not around when we started writing tests for JDK.
And, the test tag specification has been specifically designed for testing
JDK, with support for testing applets, command-line interfaces,
and so on, as well as simple API tests.

And by now, there are many thousands of tests written for jtreg,
so it would not be practical to convert them all to some other test framework.

However, note that jtreg now includes support for collections of tests
written in JUnit and TestNG.

--------

## Getting Started

### What does a typical invocation of `jtreg` look like?  How can I make sure that I can even run the JavaTest harness?

You may verify that the JavaTest harness  can be properly invoked by using
`jtreg` to run this sample test.

~~~~java
/*
 * @test
 * @bug 2718282
 * @summary Hello test
 */

public class Hello {
    public static void main(String [] args) throws Exception {
        if (true)
            System.out.println("Hello World!");
        else
            throw new Exception("??");
    }
}
~~~~

A typical invocation of `jtreg` on that test is:

    ribbit$ jtreg -verbose:all -testjdk:/usr/local/java/jdk1.4/solsparc Hello.java

where

* `-verbose:all` is a verbose option which causes output
  from all tests (regardless of whether they passed or failed) to be
  printed at completion of the test.
* `-testjdk` specifies the location of the JDK version which
  should be used to run the test.

Modulo the line numbers, output for the successful invocation of `jtreg` will look like:

     1   --------------------------------------------------
     2   TEST: Hello.java
     3   JDK under test: (/usr/local/java/jdk1.4/solsparc/jre)
     4   java version "1.4.0-beta"
     5   Java(TM) 2 Runtime Environment, Standard Edition (build 1.4.0-beta-b56)
     6   Java HotSpot(TM) Client VM (build 1.4-beta-B56, mixed mode)
     7
     8   ACTION: build -- Passed. Compilation successful
     9   REASON: Named class compiled on demand
    10   TIME:   3.024 seconds
    11
    12   ACTION: compile -- Passed. Compilation successful
    13   REASON: .class file out of date or does not exist
    14   TIME:   3.023 seconds
    15   STDOUT:
    16   STDERR:
    17
    18   ACTION: main -- Passed. Execution successful
    19   REASON: Assumed action based on file name: run main Hello
    20   TIME:   0.862 seconds
    21   STDOUT:
    22   Hello World!
    23   STDERR:
    24   STATUS:Passed.
    25
    26   TEST RESULT: Passed. Execution successful
    27   --------------------------------------------------
    28   test results: passed: 1
    29   Report written to /u/iag/jtw/JTreport/report.html
    30   Results written to /u/iag/jtw/JTwork

The test was compiled and executed. No exception was thrown during
execution, thus the test passed.

Interpretation of this output is as follows:

* line 2 - The name of the test that was run.
* line 3 - The JDK under test (should be identical to the value passed via
  the `-testjdk` option).
* line 4-6 - The product version produced when `java [-JVMOptions] -version` is called
  for the JDK under test.  Valid `[-JVMOptions]` include `-client`, `-server`, `-hotspot`,
  `-d64`, and `-d32`, as applicable to the current platform and test JDK.
* lines 8-10, 12-16, 18-24 - The set of actions that were run according to
  the test description provided. Each action contains five parts.

    * the name of the action and its final status
    * the reason the action was taken
    * the amount of time to run the test
    * standard output (note line 22 of the `main` action contains the string "Hello World!")
    * standard error

* line 26 - The final result of the test.
* lines 28-30 - Summary information about all the tests that were run.

    * line 28 - One test passed. This line would also indicate the number of
      tests that failed, or that produced errors, as applicable.
    * line 29 - Location for `.html` reports.
    * line 30 - Location for auxiliary files generated during the test
      execution. Of particular note are the results files (`.jtr`)
      which contain information about the individual tests that were run.

### Bleah! That verbose output is so long!  Can I have something shorter?

Yes. Several options provided with `jtreg` influence the output per test.
Here are a few verbose settings in order of decreasing average output per test.

* [`-verbose:fail`](#V.0) (and related `-verbose:pass`, `-verbose:error`, and `-verbose:all`)
* [`-verbose`](#V.1)
* [`-verbose:summary`](#V.2)
* [no verbose option](#V.3)

The following samples of output correspond to each of the above settings.
Each sample is run with three tests: `Pass.java`, `Fail.java`, and `Error.java` .
Note that in some cases, the output varies substantially depending on whether the test
passed or failed.

<a id="V.0">**`-verbose:fail`**</a> - Full output for failed
tests only.  Two lines for tests that passed or produced errors (related
options: `-verbose:pass`, `-verbose:fail`, and `-verbose:all`).

    ribbit$ jtreg -verbose:fail Pass.java Fail.java Error.java
    --------------------------------------------------
    TEST: Pass.java
    TEST RESULT: Passed. Execution successful
    --------------------------------------------------
    TEST: Fail.java
    JDK under test: (/usr/local/java/jdk1.4/solsparc)
    java version "1.4.0-beta"
    Java(TM) 2 Runtime Environment, Standard Edition (build 1.4.0-beta-b56)
    Java HotSpot(TM) Client VM (build 1.4-beta-B56, mixed mode)

    ACTION: build -- Passed. Compilation successful
    REASON: Named class compiled on demand
    TIME:   3.649 seconds

    ACTION: compile -- Passed. Compilation successful
    REASON: .class file out of date or does not exist
    TIME:   3.637 seconds
    STDOUT:
    STDERR:

    ACTION: main -- Failed. Execution failed: `main' threw exception: java.lang.Exception: Fail
    REASON: Assumed action based on file name: run main Fail
    TIME:   1.219 seconds
    STDOUT:
    STDERR:
    java.lang.Exception: I failed
     at Fail.main(Fail.java:5)
     at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
     at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:30)
     at sun.reflect.InflatableMethodAccessorImpl.invoke(InflatableMethodAccessorImpl.java:46)
     at java.lang.reflect.Method.invoke(Method.java:306)
     at com.sun.javatest.regtest.MainWrapper$MainThread.run(MainWrapper.java:94)
     at java.lang.Thread.run(Thread.java:579)

    JavaTest Message: Test threw exception: java.lang.Exception: I failed
    JavaTest Message: shutting down test

    STATUS:Failed.`main' threw exception: java.lang.Exception: I failed

    TEST RESULT: Failed. Execution failed: `main' threw exception: java.lang.Exception: I failed
    --------------------------------------------------
    TEST: Error.java
    TEST RESULT: Error. Parse Exception: Unexpected length for bugid: 31415926,
    --------------------------------------------------
    test results: passed: 1; failed: 1; error: 1
    Report written to /u/iag/jtw/JTreport/report.html
    Results written to /u/iag/jtw/JTwork
    Error: some tests failed or other problems occurred

<a id="V.1">**`-verbose`**</a> - This option produces three
lines of output per test: start, end, final status.

    ribbit$ jtreg -verbose Pass.java Fail.java Error.java
    runner starting test: Pass.java
    runner finished test: Pass.java
    Passed. Execution successful
    runner starting test: Fail.java
    runner finished test: Fail.java
    Failed. Execution failed: `main' threw exception: java.lang.Exception: I failed
    runner starting test: Error.java
    runner finished test: Error.java
    Error. Parse Exception: Unexpected length for bugid: 31415926,
    test results: passed: 1; failed: 1; error: 1
    Report written to /u/iag/jtw/JTreport/report.html
    Results written to /u/iag/jtw/JTwork
    Error: some tests failed or other problems occurred

<a id="V.2">**`-verbose:summary`**</a> - A single line of
output per test: final status and name of file.

    ribbit$ jtreg -verbose:summary Pass.java Fail.java Error.java
    Passed: Pass.java
    FAILED: Fail.java
    Error:  Error.java
    test results: passed: 1; failed: 1; error: 1
    Report written to /u/iag/jtw/JTreport/report.html
    Results written to /u/iag/jtw/JTwork
    Error: some tests failed or other problems occurred

<a id="V.3">**No verbose option**</a> provides only general summary
information about all the tests run.

    ribbit$ jtreg Pass.java Fail.java Error.java
    test results: passed: 1; failed: 1; error: 1
    Report written to /u/iag/jtw/JTreport/report.html
    Results written to /u/iag/jtw/JTwork
    Error: some tests failed or other problems occurred

If there is information that you find lacking in all of these options,
please contact the developer to determine if it is possible to make it
available.

### Are there abbreviations for these long options?

Yes.  The `-help` option to `jtreg` lists the long and abbreviated forms of all options.

### What is a `.jtr` file? {#jtr-file}

As each test is run, it produces a JavaTest Results (`.jtr`)
file which contains information about how the test was run, the name of the
test, standard output, standard input, final status, etc.  The name of the file
is the basename of the file containing the test description followed by the
`.jtr` extension.  For example, `Hello.java` produces a
file called `Hello.jtr`.  These files reside in the
work directory which contains a directory hierarchy that
parallels the test source structure.

Blocks of text within a .jtr file use `\` to [escape](#jtr-encoding) certain characters
(including `\` itself). This needs to be taken into account if you
view the contents of the file directly. If you use the GUI, or use
the jtreg `-show` option, the escapes are automatically taken into account.

### What's the difference between the "fail" and "error" return status?

If a test *failed*, then the harness was able to actually run the
test code.  The failure could either indicate that the test truly failed
(i.e. it threw an exception) or that there was some problem running the test
due to security restrictions, lack of resources, etc.

If _error_ is the final result of a test, then the harness was unable to
run the test.  An error is most commonly associated with problems in the test
description (typos, missing required arguments, etc.).

In either case the result contains a short message intended to provide hints
as to where the problem lies.

### If a test fails, I'd like to put all of my debugging information into the final result.  How do I do that?

The final result string is composed by the harness.  For tests that fail
because an exception is thrown, the result string will contain some header
string such as `'main' threw exception: ` followed by the
exception's type and detail message.  This detail message should contain
sufficient information to provide the test user a starting point to investigate
the unexpected failure.  It should _not_ contain full debugging
information.

The harness makes no guarantees as to the availability of any detail message
longer than 128 characters.

### I've heard that the `jtreg` has a GUI. How do I access that?

The complete JavaTest harness GUI is available via the `-gui` option
to `jtreg`.

### Can I test a JRE?

Yes. Use the `-testJDK` option to specify the JRE or other image to be
tested, and use the `-compileJDK` option to specify a matching version
of JDK, containing all the standard JDK modules and tools.

For OpenJDK version 9 and later, this relies on the tests being
correctly marked up with appropriate `@modules` tags. For earlier
versions, the appropriate subset of tests will need to be determined
and specified on the command line. Some tests may fail if they are
not correctly marked up, or if they are inappropriately selected.

### How do I run `jtreg` under Windows?

`jtreg` is normally invoked by a wrapper script, written
for the Bourne family of shells. On Windows, you can use
any of the following:

*   [MKS](http://www.mkssoftware.com/),
    which uses `ksh`;
*   [Cygwin](http://www.cygwin.com/), which uses `ash` or `bash`,
    depending on which version you are using;
*   [Windows Subsystem for Linux (WSL)](https://docs.microsoft.com/en-us/windows/wsl/about),
    which uses `bash`.

You can also start `jtreg` directly, with a command of
the form `java -jar jtreg.jar` _options_, but you
will still need to install one of MKS, Cygwin or WSL to be able to run shell tests.

### Which should I use? MKS, Cygwin or WSL?

`jtreg` supports all, equally. However, the tests in recent versions
of OpenJDK test suites assume the use of Cygwin. So, when you are writing
tests for one of those test suites, you should make sure that your test
at least works with Cygwin.

Older versions of OpenJDK assumed the use of MKS to run shell
tests, with Cygwin being a secondary option. So, when you
are writing tests for those test suites, you should make sure that your
test at least works with MKS. If you prefer to use Cygwin, and can make
your test run with both, so much the better.

Support for Windows Subsystem for Linux is being added to the build system
and tests for JDK 13. If jtreg detects Cygwin on the execution path, jtreg will
use Cygwin to run shell tests; otherwise, if jtreg detect WSL on the path,
jtreg will use WSL to run shell tests. You can override the automatic selection
by using either the `-wsl` or `-cygwin` command-line options.

### Does jtreg provide command-line help?

jtreg provides command line help, which lists all the available options
and any arguments expected by those options.  You can get help on all
the options by using the `-help` option by itself:

    $ jtreg -help

You may also get command line help for any option or topic by giving
arguments to the `-help` option, in which case jtreg will just print
the sections containing any of the specified terms. For example:

    $ jtreg -help -workDir
    $ jtreg -help jdk

--------

## Using jtreg

### How do I specify which tests to run?

The most basic way to specify which tests to run is to give one or more paths
directly on the command line, for directories and files containing tests.

If a file contains multiple tests, you can specify the name of a test within
that file by appending `#`_ID_ to the file path, where _ID_ is either defined
in the test itself after the `@test` tag, or the string `id`_N_  if no id is
explicitly defined, where N is the number of the test within the file,
and where `0` identifies the first test.

If you specify `?`_string_ after the name of a test, the _string_ will be
passed down to the test, for the test to filter the parts of the test to be
executed. For any tests executed by JUnit Platform, the string is by default interpreted
as the name of a single method in the test to be executed. However, it is also
possible to use other JUnit selectors by prefixing the query string with `junit-select:`.
The rest of the string can then be any selector identifier as listed in the left-most
column of the table found here: https://junit.org/junit5/docs/current/user-guide/#running-tests-discovery-selectors 
If you give conflicting values for the string, including not setting any value, the last
one specified will be used.

If you wish to specify a long list of arguments, you can put the list in a file
and specify that file using the `@`_file_ option.

You may also specify the name of a group of tests. Groups are defined
in the test suite, and define a collection of tests to be run.

To summarise, you can use the following to specify tests to be run:

Table: Kinds of Supported Arguments

| Argument                | Description                                        |
|-------------------------|----------------------------------------------------|
| _directory_             | All tests found in files in and under the directory |
| _file[#id]_             | All tests in a file, or a specific test in a file  |
| _file[#id]?string_      | Parts of a test in a file                          |
| _[directory]_`:`_group_ | All tests in a group defined for a testsuite       |
| `@`_file_               | Expand arguments in a file                         |


You can further refine the set of tests to be run in various ways.

### How do I refine the set of sets to run?

You can refine the set of tests to be run in various ways:

* You can filter the tests using keywords, using the `-k` option.
  The option can be given multiple times, and are combined conjunctively.
  Keywords may be defined explicitly in tests using `@key` or may
  be defined implicitly from other properties of the test, such as
  whether it is a "shell" test or a "manual" test.

* You can filter the tests by their prior execution status, using the `-status`
  option. For example, you can select the tests which previously failed or
  had an error using `-status:fail,error`

* You can filter the tests by providing a list of tests which should _not_
  be run on some or all platforms, perhaps because it is known they are
  likely to fail, using the `-exclude` option.

Note that in addition to the command-line options just listed, a test
may contain tags such as `@requires` and `@modules` that determine whether
a test should be run on any particular system.

### How do I run a single test method or class in a JUnit test?

Specify the test and method name on the command-line with the `?` syntax:

    path-to-test?method-name

This will run a method called `method-name`, having no parameters, in the top-level test class.

To run a parameterized test method, the extended selector syntax has to be used. For example:

    path-to-test?junit-select:method:class-name#method-name(param-type, ...param-type)

The format supported by the `method` selector is described in greater detail in the documentation of
[DiscoverySelectors::selectMethod](https://junit.org/junit5/docs/current/api/org.junit.platform.engine/org/junit/platform/engine/discovery/DiscoverySelectors.html#selectMethod(java.lang.String)).

To run a specific nested test class, annotated with the `@Nested` annotation, the following can be used:

    path-to-test?junit-select:class:enclosing-class-name$nested-class-name

Note that in this case, the string after `class:` is the binary name of the nested class, as returned by `Class::getName`.

See [How do I specify which tests to run?](#how-do-i-specify-which-tests-to-run).

### How do I specify the JDK to use?

First, it is important to distinguish the JDK you use to run jtreg itself
from the JDK you use to run the tests. It is generally recommended to use
a released version of JDK to run jtreg; it must be at least version 1.7.

If you use the standard `bin/jtreg` script to run jtreg, you can specify the version
using the `JT_JAVA` environment variable.

The JDK that jtreg will use to run the tests is normally specified with the
jtreg `-jdk` option. This JDK will be used to both compile and run the tests.
It need not be the same version of JDK that is used to run jtreg; it must be
at least JDK 1.5 to run tests in agentVM mode, and can be even older to run tests
in otherVM mode.

#### Cross-compilation {.unnumbered}

jtreg provides some (limited) support for using different versions of
JDK to compile tests and to run them. This is useful when testing
small version of JDK, such as compact profiles, or images built with `jlink`
that do not contain all the modules of a standard JDK.

To use this feature, use the `-compileJDK` option to specify the JDK used
to compile the tests (that is, used by the `@build` and `@compile` tags),
and use the `-testJDK` option to specify the JDK used to execute the
compiled classes (for example, as used by `@run main`).

_Note:_ the `@compile` tag has a dual use: it is used in compiler tests
(that is, to test the JDK compiler, `javac`) and less commonly, it is
used to compile classes for API tests when the simple `@build` tag is not
sufficient. It is currently not possible to distinguish these uses
except by context. It does not make sense to use the cross-compilation
feature for compiler tests.

### What are the work and report directories? {#report-work-dirs}

The work directory contains any files which are generated during a test run.
These include compiled (`.class`) files, individual test result (`.jtr`) files,
and any files which are saved from the scratch directory, as directed by the
`-retain` option.
By default, the name of the work directory is `JTwork`.
The name may be selected via the `-workDir` option to jtreg.

The report directory contains all final reports in HTML and/or plain text format.
By default, the name of the report directory is `JTreport`.
The name may be selected via the `-reportDir` option.

The HTML files in the report directory may contain links to the individual
test result (`.jtr`) files in the work directory. These links will be relative
links if the report and work directories share a nearby common ancestor,
such as the same parent or grandparent. A recommended practice is to create
a single directory, containing subdirectories simply named `report` and `work`.

The plain text files in the report directory include the following:

* `cmdArgs.txt`: the jtreg command-line args used when the report was written
* `stats.txt`: summary statistics
* `summary.txt`: summary of test results: one test per line, suitable for use with `grep`
* `timeStats.txt`: some statistics regarding test execution times

Reports can be disabled with the `-noreport` option; the set of tests included
in the report can be selected with the `-report:`_value_ option.

It is generally recommended that the work and report directories should _not_ be
placed anywhere in the test suite itself. Since jtreg may scan the entire test suite
looking for tests, it is unnecessary work for jtreg to have the scan these directories
looking for the unlikely possibility of tests. This implies a corollary that you should
not rely on the default settings for these directories (`./JTwork` and `./JTreport`)
if you choose to execute jtreg in any directory in the test suite.

### What is a scratch directory? {#scratch-directory}

When jtreg executes a test, the current directory for the test is set to a
_scratch directory_ so that the test can easily write any temporary files.
jtreg will ensure that the directory is always empty when the test begins,
so that the test does not have to worry about deleting any pre-existing files.
These directories are placed in the work directory.

* If tests are not being run concurrently, the directory will simply be named `scratch`.
* If tests are being run concurrently, the directories will be numbered subdirectories
  of `scratch`, such as `scratch/0`, `scratch/1`, and so on.
* If at any point jtreg cannot clean up files in a scratch directory,
  jtreg will report an error, and create and use a new directory, named using
  a numbered suffix, such as `_1`, `_2`, and so on.

As a special case, if all the tests are being run in otherVM mode,
and if `-retain` is specified, the scratch directory for each test
will be the unique test-specific directory in the work directory
where the test's files will be retained.  In this situation, if
any file that is not to be retained cannot be deleted, an error will
be reported and it will simply be left in place.

### What is a ProblemList.txt file?

`ProblemList.txt` is the name given by convention to a list of tests in
a test suite that should not be run. Use the `-exclude` option to specify
the file to jtreg.  The file is a simple text file in which
blank lines and lines beginning `#` are treated as comments and ignored,
and other lines are of the form:

  _test-name_ _list-of-bug-numbers_ _list-of-platforms_ _description_

The two list items must each be comma-separated lists with no embedded whitespace.

The list of bug numbers should identify one or more bugs that justify why the
test is in the list and should not be run. It is not used by jtreg, but may be
used by other tools to warn when a test should be removed from the list because
the underlying issue has been resolved.

The list of platforms should identify one or more platforms on which the test
should not be run. Each entry in this list should be in one of the following forms:

* `generic-all`:
    if the problem is on all platforms

* `generic-`_ARCH_:
    if the problem is specific to a platform architecture,
    where _ARCH_ is one of: `sparc`, `sparcv9`, `x64`, `i586`,
    or the value of the `os.arch` system property

* _OSNAME_`-all`:
    if the problem is specific to a class of operating systems,
    where _OSNAME_ is one of: `linux`, `macosx`, `solaris`, `windows`
    or the value of the `os.name` system property

* _OSNAME_`-`_ARCH_:
    if the problem is specific to an operating system and architecture;
    for example, `solaris-x64`

* _OSNAME_`-`_REV_:
    if the problem is specific to a version of an operating system,
    where _REV_ is the value of the `os.version` system property;
    for example, `solaris-5.8`

### What are the agentVM and otherVM modes?

In _otherVM_ mode, jtreg will start a new JVM to perform each action
(such as `@compile`, `@run main`, and so on) in a test. When the action has
been completed, the JVM will exit, relying on the system to clean up
any resources that may have been in use by the action.

In _agentVM_ mode, jtreg will maintain a pool of available JVMs,
grouped according to the VM options specified when the JVM was created.
When jtreg needs to execute an action for a test, it will obtain
a suitable JVM from the pool, or create a new one if necessary.
jtreg will use that JVM, and when it has been completed, it will
attempt to restore some well-known global values to a standard
state (such as the system properties, standard IO streams, security
manager, etc). If that cleanup is successful, depending on the
VM options used to create the JVM, jtreg may put the JVM in the
pool for later reuse. Otherwise, the JVM is allowed to exit.

#### Which mode should I use?  {.unnumbered}

OtherVM mode provides the maximum isolation between the actions
of tests, at the cost of performance, since a new JVM is started for
each action.

In contrast, agentVM mode has higher performance, but it requires
that any code that runs in an agent VM must ensure that the VM
can easily be reset to a standard state when the action has completed.
For any state that cannot be reset by jtreg, such as closing any open
files, the code must perform its own cleanup, or the action should be
changed to use otherVM mode.

#### How do I specify which mode to use? {.unnumbered}

The default for all actions is to use the mode specified on the
command line, using either the `-agentvm` or `-othervm` options
or their shorter-form aliases. If no option is specified,
`-othervm` is the default.

_Note:_ the JDK `make run-test` feature uses the `-agentvm` option,
so that agentVM mode is the default when using `make run-test`.

The default action can be overridden for the tests in a
directory (and its subdirectories) by using the `othervm.dirs`
property in either the `TEST.ROOT` or a `TEST.properties` file.

The default action can be overridden for any individual action
by using the `/othervm` option for the action.  For example,

    @compile/othervm MyTest.java
    @run main/othervm -esa MyTest

#### How do I find which mode was used to run an action? {.unnumbered}

Look for a line beginning "Mode:" in the "messages" section for
the action in the test's result (_.jtr_) file. This will specify
the mode used to execute the action, together with a reason
if the default mode was not used.  For example,

````
    ----------messages:(4/152)----------
    command: main MainOtherVM
    reason: User specified action: run main/othervm MainOtherVM
=>  Mode: othervm [/othervm specified]
    elapsed time (seconds): 0.141
````

#### jtreg has trouble starting agents; what can I do?

When running in agent mode, JTReg creates agent VMs as and when necessary.
Agent VM creation involves launching a process and communicating with it over
a socket. The initial handshake between the newly launched process and JTReg
can sometimes timeout if the system is under heavy load. This then causes the
agent creation to fail. By default, JTReg does not re-attempt creation of the
agent VM and instead the failure is propagated as a test action failure and
that action gets reported as a failed.

This default behaviour can be overridden by passing the `--agent-attempts` option
to `jtreg` command. This option takes an integer value which represents the number
of attempts to make when attempting to get an agent for a test action. By default,
the value of this option is `1`, implying JTReg will not re-attempt a failed
attempt. Passing a higher value for this option will allow JTReg to re-attempt a
failed attempt. For example, a value of `2` will allow JTReg to re-attempt once
for each failed attempt.

### How do I specify whether to run tests concurrently?

jtreg provides the ability to run tests in parallel, using multiple
JVMs running at once. (jtreg never runs multiple tests at the same
time in any one JVM.)

Running tests in parallel can significantly speed up the overall time
to run the selected tests in the test suite on big server-class machines,
but it is also important not to overload the machine, because that
can cause spurious test failures, caused by resource starvation, leading
to test timeouts.

You can specify the number of tests to run in parallel with the
`-concurrency` option.  The default value is 1, meaning that only
one test is being executed at any one time.

It is difficult to give a hard and fast rule for choosing higher
values; it depends on the characteristics of the machine being
used: the number of cores, hyperthreading and the amount of available
memory. The general rule is to determine a value that is high enough
to use most of the system resources, without overloading the system
and pegging the resource utilization meter at 100%. It is also
advisable to avoid swapping virtual memory as much as possible.

One important consideration is how the specified concurrency
can affect the number of JVMs that are instantiated:

*   One JVM is created to run jtreg itself.

*   In otherVM mode, there will typically be one JVM for each test
    that is currently executing.

*   In agentVM mode, it depends on whether there are different VM
    options specified for the VMs used to compile tests and to run tests.

    * If the same VM options are used, there will be one JVM created for
      each test that can run at once.

    * If different VM options are used, there will be two JVMs created
      for each test that can run at once: one to compile the test code,
      and another to execute the test code.

    In addition, in agentVM mode, any action that overrides the default
    set of VM options specified on the command line will cause an
    additional JVM to be created, for the duration of that action.

_Note:_ the preceding rules describe the current behavior of jtreg.
Alternate behaviors have been proposed, such as a fixed-size pool
with a least-recently-used (LRU) replacement strategy.

Another important consideration when many JVMs are active on a
single machine is the amount of memory that may be used by each JVM.
Use VM options such as `-Xmx` and `-XX:MaxRAMPercentage` to control the
amount of memory allocated to each JVM.

If you run JDK tests using `make run-test`, suitable values for
`-concurrency`, `-Xmx` and`-XX:MaxRAMPercentage` are determined automatically.

#### Can I run tests on multiple machines? {.unnumbered}

jtreg does not natively support running tests on multiple machines
at once. However, you can partition the test suite into sections,
and use different instances of jtreg to each run a section of the
test suite on a different machine. You can then use jtreg to generate
a single report based of the results of executing each of the sections.

### How do I specify additional options for compiling and running tests?

You can specify additional VM options for all JVMs started by jtreg,
using the `-vmoption` and `-vmoptions` options.

*   `-vmoption` can be used multiple times and can be used to add an
    option with any value, including one containing whitespace characters.
*   `-vmoptions` can be used to set a whitespace-separated list of
    options. There is no support for quoting any values that may contain
    whitespace; use `-vmoption` instead.

In addition, for historical reasons, jtreg supports a number of VM
options which can be specified directly on the command-line.
Some of the more useful such options include `-enableassertions` and
related options, `-D`_name_`=`_value_ to set a system property,
and `-Xmx`_value_ to limit the memory usage.

You can specify additional VM options for all JVMs used to execute tests,
using the `-javaoption` and `-javaoptions` options. These options apply to
the JVMs used for the `@run main` and `@run applet` actions. (They do _not_
apply to the JVMs used for the `@build` and `@compile` actions.)

*   `-javaoption` can be used multiple times and can be used to add an
    option with any value, including one containing whitespace characters.
*   `-javaoptions` can be used to set a whitespace-separated list of
    options. There is no support for quoting any values that may contain
    whitespace; use `-javaoption` instead.

Although not common to do so, you can specify additional options for
use whenever javac is invoked. You can do this with the `-javacoption`
and `-javacoptions` options.

*   `-javacoption` can be used multiple times and can be used to add an
    option with any value, including one containing whitespace characters.
*   `-javacoptions` can be used to set a whitespace-separated list of
    options. There is no support for quoting any values that may contain
    whitespace; use `-javacoption` instead.

It is not possible to set VM options just for the JVMs used to run
javac. In particular, the javac option `-J`_vm-option_ is not supported.

### What do I need to know about test timeouts?

Because most users do not closely monitor the execution of all the tests
in a test run, jtreg will monitor the time take for various events during its
overall execution, to ensure that those events do not take longer than
anticipated.  This includes the following:

* The time taken to execute each of the actions in a test.
  The default time is 120 seconds (2 minutes) but can be changed
  using the `/timeout` option for the action. To allow for reasonable
  variations in the speed of execution, it is recommended that the typical
  execution time for an action should be not more than half of the default or
  specified time when the test is executed on a reasonable modern system
  such as may be used by developer or in use in a continuous build and test
  system.

* The time taken to clean up after each individual action.
  This includes any time that spent waiting for any threads to complete,
  that may have been started by the code in the action, for which the
  maximum time is 10 seconds.

* The time taken to clean up after a test, when all the actions
  that will be executed have been executed. This includes any time
  that may be spent waiting for any files in the scratch directory to
  be deleted, for which the maximum time is 15 seconds per file on Windows
  and 0 otherwise.

* The time taken to run a "timeout handler" (see below).
  The default time is 300 seconds (5 minutes), which can be changed
  by using the `-timeoutHandlerTimeout` option.

To allow for running tests on slow hardware, or when using VM options that
might adversely affect system performance, the timeout intervals can be
scaled by using the `-timeoutFactor` option.

Ideally, any test code that uses timeouts internally should take the
current timeout factor into account.  Java code can access the current
timeout value using the `test.timeout.factor` system property. Shell tests
can access the value using the `TESTTIMEOUUTFACTOR` environment variable.

It may be convenient to run code in a JVM when an action for a test is
about to be timed out: such code may perform a thread dump or some other
detailed analysis of the JVM involved. jtreg allows such code to be provided
using the `-timeoutHandler` and `-timeoutHanderDir` options. The default
timeout handler will try and call `jstack` to generate stack traces of all
the Java threads running in the JVM being used for the action.

Test timeouts are automatically disabled when a test is being debugged,
as indicated by the use of the `-debug` option.

For all timeout-related options, use `jtreg -help timeout`.

### How do I run only tests which were written for a specific bugid?

The `-bug` option to `jtreg` will run only those tests which define the given bugid using the `@bug` tag.

### How do I run only tests NOT requiring manual intervention?

The `-automatic` option to `jtreg` will ignore all tests which contain the `/manual` tag option.

### How do I run only tests requiring manual intervention?

The `-manual` option to `jtreg` will run only those tests which contain the `/manual` tag option.

### How do I view what a test sends to `System.out` or `System.err`?

You have several alternatives.

1.  Use the `-verbose:all` option, or the related result-sensitive
    options `-verbose:pass`, `-verbose:fail`, `-verbose:error`.
2.  Use the JavaTest (JT Harness) harness GUI.
3.  View the test's `.jtr` file.
    _Note: some characters in the file may be [encoded](#jtr-encoding)._
4.  Use the `-show` option to display the unencoded content of a stream. For example,
    * `jtreg -w` _work-dir_ `-show:System.out` _test-name_

### How do I control the statistics reported on the console at the end of a test run?

By default, `jtreg` reports simple execution statistics at the end of a test run.
These are given in a line beginning `Test results:`, followed by a series of
labeled values, including the following:

 * the number of tests that passed; for historical reasons, this includes any
   tests that were skipped by throwing `jtreg.SkippedException`
 * the number of tests that failed
 * the number of tests that could not be executed and which reported an error
 * the number of filtered out by the exclude list (problem list) filter
 * the number of tests that were filtered out by the keyword filter
 * the number of skipped tests

You may override the format of this line by setting the system property
`jtreg.stats.format` to a `printf`-like format string. The following format
specifiers are supported:

* `%f`         &mdash; number of failed tests
* `%F`         &mdash; number of failed and error tests
* `%e`         &mdash; number of error tests
* `%e`         &mdash; number of error tests
* `%p`         &mdash; number of passed tests, including skipped tests
* `%P`         &mdash; number of passed tests, excluding skipped tests
* `%n`         &mdash; number of tests not run
* `%r`         &mdash; number of tests run
* `%s`         &mdash; number of skipped tests
* `%x`         &mdash; number of tests filtered out by the exclude list (problem list) filter
* `%i` or `%k` &mdash; number of tests filtered out by the keyword filter
* `%m`         &mdash; number of tests not meeting module requirements
* `%R`         &mdash; number of tests not meeting platform requirements
* `%S`         &mdash; number of tests not matching the prior status requirements
* `%t`         &mdash; number of tests not meeting time-limit requirements
* `%o`         &mdash; number of tests filtered out for other (unknown) reasons
* `%,`         &mdash; conditional comma
* `%<space>`   &mdash; conditional space
* `%%`         &mdash; %
* `%?X`        &mdash; prints given number if not zero, where X is one of f, F, e, p, P, s, x, i
* `%?{textX}`  &mdash; prints text and given number if number is not zero, where X is one of f, F, e, p, s, x, i

A _conditional_ comma or space is only generated if it is not at the beginning of the line.

### What do all those numbers in the "Test results" line mean?

After running tests, `jtreg` prints out a line beginning `Test results:` followed
by a series of labeled numbers. The numbers give details about the number of
tests that were run as well as the number of tests that were not. What do they all mean?

_passed_
:   The number of tests that were executed and which indicated that the test passed.
    Note that some parts of the test may not have been executed. Some tests may be "skipped",
    meaning that the test determined that it could not be executed as intended.

_failed_
:   The number of tests that were executed and which indicated that the test failed.

_error_
:   The number of tests that were executed and which indicated that an error
    occurred before it could be determined whether the test passed or failed.

_skipped_
:   The number of tests that "passed" but which indicated that the test
    could not be executed as expected. This only applies to tests that throw
    `jtreg.SkippedException`. Some tests, such as "combo-tests" or those that use
    a test framework like TestNG or JUnit , may provide additional mechanisms to
    skip parts of a test. Such results are not included here. See the results
    for an individual test, or for any summary files generated for the framework.

_excluded_
:   The number of tests that were present in an exclude list, such as a "problem list".

_not in match-list_
:   The number of tests that were not in a match list specified on the command line
    with the `-match` option, which may be used to select and run _only_ those tests
    that appear in an exclude list, such as a "problem list".

_did not match keywords_
:   The number of tests that did not match the keyword expression used to filter the
    set of tests to be executed.

    The keywords for each test may be user-defined, with the `@key` tag, or implicitly
    defined by various tags and/or their options in the test description.
    The full set of keywords for each test can be seen in the _testdescription_
    section of the test's result file (`.jtr` file).

    The keyword expression is a combination of any expression specified with the `-k` 
    option and any expressions derived from other command-line expressions, like 
    `-manual` (to select tests that require manual interaction), or `-bug` (to select 
    tests that declare being a test for a given bug with the `@bug` tag.)
    The full keyword expression can be seen in the _Keywords_ section in the file
   `html/config.html` in the overall report for the test run.

_did not meet module requirements_
:   The number of tests that declared the need for specific modules to be available in
    the JDK being tested, but which were not available.
    See the `@modules` tag in the test description to see the modules that are
    required by the test.
    Examine the test JDK and any relevant JDK options to determine the set of
    modules that will be availble. For example, see the JDK `--list-modules` option.

_did not meet platform requirements_
:   The number of tests that did not meet the requirements given in each test's
    `@requires` tag. The values in the expression may be "standard" values, as
    defined in the [Tag Specification](tag-spec.html#requires_names), or custom
    values as defined in the `requires.extraPropsDefns` properties in the
    test suite's `TEST.ROOT` file.

_did not match prior status_
:  The number of tests that were not executed because they did not meet the
   "prior status" conditions given in the `jtreg` `-status` option.

_did not meet time-limit requirements_
: The number of tests that were not executed because they did not meet the
  time-limit conditions given in the `jtreg` `-timelimit` option.

Note that to avoid clutter, only non-zero values are given.

### How do I see what groups are defined in my test suite?

Use the `showGroups` option. To see all groups, specify the name of the test suite;
to see the details for a specific group, specify the test suite and group.

    $ jtreg -showGroups test/langtools
    $ jtreg -showGroups test/langtools:tier2

### How do I see what tests will be executed, without actually executing them?

Use the `-listtests` option.

    $ jtreg -listtests test/langtools/jdk/javadoc/doclet

### Why are there extra `\` characters in the output from a test in a .jtr file? {#jtr-encoding}

By design, the contents of a `.jtr` file, including any output from tests, is represented in a way
that can be read back in again by `jtreg` and related tools. To that end, characters that are not
standard ASCII characters (printable characters, and `CR`, `LF`, `SP`, `HT`) are encoded with escape sequences.
Characters outside that set are represented by `\uXXXX`, and `\` itself as written as `\\`.
Anyone viewing the contents of a `.jtr` directly, such as in a plain-text editor, or using
command-line tools like `grep` need to be aware of that encoding and take it into account.

To view the unencoded output from a test that has been recorded in a `.jtr` file,
use the `jtreg` `-show:name` option.

    $ jtreg -w:/path/to/work-dir -show:System.out /path/to/test

The `-show` option can also be used to see the `rerun` script that is provided in the `.jtr` file,
and which may also contain escape sequences. This script allows you to [rerun the test stand-alone](#rerun),
without the use of the `jtreg` infrastructure.

### What names can I use with the `-show` option

All recent versions of `jtreg` accept the name of an output stream as the name.

    $ jtreg ...  -show:stream-name ...

The name of each output stream appears after a series of dashes and before a colon `:`
in the `.jtr` file.  For example, here is a heading for a stream named `System.out`:

    ----------System.out:(1/501)----------

More recent versions (6.2 onwards) support an optional section name as well.
See the command-line help for specific details in the version you are using.

    $ jtreg ... -show:section-name/stream-name

The name of all the sections appear in the `testresult` part of the `.jtr` file,
and individually after `#section:` at the beginning of each section. For example,

    sections=script_messages build compile build main

    #section:compile

### Can I verify the correctness of test descriptions without actually running the tests?

Yes!  The `-check` option to `jtreg` will find
test descriptions and will determine whether they are written according to the
[tag specification](tag-spec.html). Tests will _not_ be executed.

The following sample output illustrates use of this option.

    ribbit$ jtreg -verbose:summary -check Pass.java Fail.java Error.java
    Passed: Pass.java
    Passed: Fail.java
    Error:  Error.java
    test results: passed: 2; error: 1
    Report written to /u/iag/jtw/JTreport/report.html
    Results written to /u/iag/jtw/JTwork
    Error: some tests failed or other problems occurred

### I'd like to run my test standalone, without using jtreg: how do I do that? {#rerun}

All tests are generally designed so that they can be run without using jtreg.
Tests either have a `main` method, or can be run using a framework like TestNG or JUnit.

If you have already executed the test, jtreg can provide a sample command-line to
rerun it; you can find this in the `rerun` section of the test result (`.jtr`) file,
or you can use the `-show` option to display the information to the console.
For example,

* `jtreg -show:rerun` _test-name_

### Can I generate reports for tests that have already been run?

Yes!  The `-reportOnly` option to `jtreg` will generate the standard HTML
and plain text reports for tests which have been previously executed.
Tests will _not_ be executed.  A [work directory](#report-work-dirs)
containing the results of the executed tests must be provided.
The default location is `./JTwork`.  An alternate directory may be
specified using `-workDir`.

### What happens when jtreg runs a test?

In preparation for running a test, jtreg will ensure that the scratch directory
to be used for the test is empty. If any files are found, jtreg will attempt to
delete them; if it cannot, it will report an error and not run the test.

_Note:_ it can be difficult for jtreg to identify the test that created
a file that cannot be deleted, which may result in less-than-helpful error messages.
See [below](#cleanup-files) for a way around this problem.

Once the scratch directory has been prepared, jtreg will execute each
action (@build, @compile, @run, and so on) in order.  For each action:

*   If the action is to be executed in otherVM mode, jtreg will start a new
    JVM to perform the action; if the action is to be executed in agentVM mode,
    jtreg will attempt to get a suitable JVM from the pool, or create a new
    JVM if necessary.

*   For Java tests (`@run main`, `@run applet`) jtreg will run the action
    in a newly-created thread group. The action is over when one of the following
    occurs:
    * when the `main` method returns (for `@run main`)
    * when the user clicks on one of `Pass`, `Fail` or `Done`(for `@run applet`)
    * when any thread in the thread group throws an exception,
      which is detected by using an uncaught exception handler for the thread group

*   If the action was executed in agentVM mode, jtreg will try and reset
    some well-known global values to the state before the action was performed.
    This includes:

    *   Wait for all threads started in the test's main thread group to
        terminate, calling `Thread.interrupt` periodically on any threads
        that have not yet terminated.

    *   Reset the security manager, if it was changed during the action.

    *   Reset the set of security providers, if it was changed during the action.

    *   Reset the system properties, if any were modified during the action.

    *   Reset the system streams (`System.out` and `System.err`).

    *   Reset the default locale, if it was changed during the action.

    Note that jtreg cannot close any files or sockets that may have been left open;
    that is the responsibility of the test itself. Any other significant
    global state that is modified during the course of an action is
    also the responsibility of the test to clean up.

*   If any action does not complete successfully, no subsequent actions
    will be executed.

<a id="cleanup-files">When jtreg has completed executing the actions for a test</a>,
it may try and retain or delete and files that may have been created in the
scratch directory.

The default behavior is for jtreg to leave any files created by the test
in the scratch directory after a test completes, but to subsequently
delete any such files before the next test begins.
This behavior conveniently means that any files are left in the scratch
directory for inspection by the user when running a single test,
such as when developing and running a new test, but the downside is that
if there are subsequently any problems deleting those files,
the identity of the test that created them is not easily determined.

The default behavior can be changed by using the `--retain` option,
which is used to specify which files, if any, are to be retained
when a test completes. Any file in the scratch directory that is not
one of those specified to be retained will be deleted.

If there are any problems deleting any file, it will be reported as an
error against the test; if the scratch directory is associated
with a JVM in the agent pool, that JVM will be discarded. Another will
be created to replace it, using a different scratch directory, if the
need arises.

On Windows, a file will not be deleted until any open handles on the
file have been [closed](https://docs.microsoft.com/en-us/windows/desktop/fileio/closing-and-deleting-files).
This will mean that files that have accidentally been left open by a test
cannot be deleted.
jtreg will try hard to delete files in the scratch directory, and will wait
awhile in case the files go away in a timely manner.

### My tests take a long time to run: how do I find where the time goes?

The [`.jtr`](#jtr-file) file for each test contains timing information
for each action executed by the test and for the test as a whole.

* There is a section in each `.jtr` file for each action executed as
  part of the test, and in each section there is a line of the form:

      elapsed time (seconds): NNNN

  where _NNNN_ is the elapsed time (wall-clock time) taken to execute the
  action.

* In the _testresult_ data near the top of each `.jtr` file, there is
  a line of the form:

      elapsed=MILLIS HH:MM:SS.MIL

  giving the time in two forms: first as the total number of milliseconds,
  and then the same value expressed in hours, minutes, and seconds including
  fractions of a second.

  This line can easily be extracted by external tools, to aggregate
  information about any desired set of tests.

jtreg writes a simple summary of test execution times to a file in
the [report directory](#report-work-dirs), called `text/timeStats.txt`.
This file contains data to create a histogram giving the number of
tests taking a given time to execute, rounded to the nearest second.
These values are then followed by the mean and standard deviation
of the test execution times.  If there are any tests taking an
unexpectedly long time to execute, they can be determined by examining
the `elapsed` entries in the `.jtr` files.

### Why is there a delay after the tests have been run, before jtreg exits?

By default, jtreg reports on all the tests that have been executed
and which have results in the work directory. It may take a few seconds
to find the set of tests for the report. You can use the `-report:`_value_
option to specify which tests should be in the report. If you are just
running a single test or a few tests, you may want to use `-report:files`,
to just report on the tests specified in the files and/or groups given on the
command line.

### How do I find the tests that took longest to run?

Using the [`elapsed`](#my-tests-take-a-long-time-to-run-how-do-i-find-where-the-time-goes)
time written into each `.jtr` file, you can find the slowest tests with
a command such as the following:

    grep -r "^elapsed=" DIRS | sed -e 's/\\:/:/g' | sort -t = -k 2 -n -r

where _DIRS_ is one or more directories containing `.jtr` files to be
examined. The command scans the input files and directories looking for
the `elapsed` lines, and then sorts the lines by the millisecond value
in descending order.

You can also use the `head` command to limit the output to the desired
number of the slowest tests, represented here by _COUNT_:

    grep -r "^elapsed=" DIRS | sed -e 's/\\:/:/g' | sort -t = -k 2 -n -r | head -n COUNT

_Note:_ there is also an entry named `totalTime` in the _testresult_ data in
each `.jtr` file, giving the execution time in seconds. As such, it is
approximately equivalent to the value given in the `elapsed` value, after allowing
for the different units (seconds compared to milliseconds) in the two values.

### My system is unusable while I run tests. How do I fix that?

* If you are using the `-conc` or `-concurrency` option to run tests in parallel,
  try reducing the number of tests to be run at the same time.
* Use VM options, like `-Xmx`, to limit the amount of memory available to each process.
* Try reducing the priority used to run `jtreg` and the processes it runs.
  On POSIX systems, you can use the `nice` command to control the priority of a process.

The JDK [`make test`](#how-do-i-run-jdk-jtreg-tests-using-make-test-and-the-jdk-makefile-infrastructure)
framework automatically uses these techniques to reduce the load on a system.

### What is the agent pool?

The agent pool is a collection of reusable VMs that can be used to run
test actions, like `@compile` and `@run main`, when it is not required to
run the action in a separate VM. VMs are started automatically as needed,
and after each use, if they can be reset to a standard state, they are saved
for potential reuse by subsequent actions that require a VM with similar
characteristics.

The characteristics used to select a VM from the pool are:

* the execution directory
* the JDK
* the VM options

### How do I control the agent pool?

There is a limit to the number of VMs in the pool at any one time.
The default is double the number of tests that may run
[concurrently](#how-do-i-specify-whether-to-run-tests-concurrently).
The value can be overridden with the `--max-pool-size` option.
Setting a larger number will mean more system resources are used
to keep idle VMs available for potential reuse;
setting a smaller number will save system resources but will reduce
the chance of being able to reuse a JVM.

There is also a time limit on how long an idle VM will remain in the pool,
The default is 30 seconds.
The value can be overridden with the `--pool-idle-timeout` option.
Setting a larger number will mean more system resources are used
to keep idle VMs available for potential reuse;
setting a smaller number will save system resources but will reduce
the chance of being able to reuse a JVM.
The value is used as given; it is not subject to the modification
by the [`-timeoutFactor`](#what-do-i-need-to-know-about-test-timeouts)
option.

### How do I run JDK jtreg tests using `make test` and the JDK makefile infrastructure

This is described in detail in the file `doc/testing.md` in all recent
versions of the main JDK repository.  You can see the latest version,
in HTML, here:
[Testing the JDK](https://htmlpreview.github.io/?https://raw.githubusercontent.com/openjdk/jdk/master/doc/testing.html).

--------

## Writing a JDK Regression Test

### How do I write a test?

The simplest test is an ordinary Java program with the usual static
main method.  If the test fails, it should throw an exception; if it succeeds,
it should return normally.

Here's an example:

~~~~java
/* @test
   @bug 1234567
   @summary Make sure that 1 != 0
*/

public class OneZero {

    public static void main(String[] args) throws Exception {
        if (1 == 0) {
            throw new Exception("1 == 0");
        }
    }

}
~~~~

This test could be run on its own with the command

    $JDK/bin/java OneZero

where $JDK is the location of the JDK build that you're testing.

### What does the `@test` tag mean?

The `@test` tag identifies a source file that defines a test.
the harness will automatically run any `.java`, `.sh`, and
`.html` file containing an `@test` tag within the
appropriate comment; it ignores any file not containing such a tag or not
utilizing one of the expected extensions.

If necessary the harness will compile the source file, if the class files are
older than the corresponding source files.  Other files which the test depends
on must be specified with the `@run build` action.

The arguments to the `@test` tag are ignored by the harness.  For
identification, it may be useful to put information such as SCCS ID keywords after the `@test` tag.

While not part of the tag specification, some tests use the
string "`/nodynamiccopyright/`" after `@test`
to indicate that the file should not be subject to automated
copyright processing that might affect the operation of the test,
for example, by affecting the line numbers of the test source code.

### What do the other tags mean?

The other tags shown above are optional.

The `@bug` tag should be followed by one or more bug numbers,
separated by spaces.  The bug number is useful in diagnosing test failures.
It is OK to write tests that don't have bug numbers, but if you're writing a
test for a specific bug please include the bug number in an `@bug` tag.

The `@summary` tag describes the condition that is checked by the
test.  It is especially useful for non-regression tests, which by definition
don't have bug numbers, but even if there's a bug number it's helpful to
include a summary.  Note that a test summary is generally not the same thing as
a Bugtraq synopsis, since the latter describes the bug rather than the
condition that the bug violates.

### How are tag arguments delimited?

The arguments of a tag are the words between that tag and the next tag,
if there is one, or the end of the comment enclosing the tags.

### Can I put comments in a test description?

Yes, use the `@comment` tag. It can be followed with any text,
excluding `@` characters, up to the next `@` or the end of the comment
containing the test description.

### If a test fails, do I have to throw my own exception?

No.  In cases like the above example in which you must check a condition
in order to decide whether the test is to pass or fail, you have no choice but
to construct an exception.  `RuntimeException` is a convenient
choice since it's unchecked, so you don't have to sprinkle your code with
throws clauses.

On the other hand, if the test would naturally cause an exception to be
thrown when it fails, it suffices to let that exception be propagated up
through the main method.  If the exception you expect to be thrown in the
failure case is a checked exception, then you'll need to provide the
appropriate throws clauses in your code.

In general, the advantage of throwing your own exception is that often you
can provide better diagnostics.

It is _strongly_ recommended that you not catch general exceptions
such as `Throwable`, `Exception`, or `Error`.
Doing so can be potentially [problematic](#write-catch-exceptions).

### Should a test call the `System.exit` method?

No. Depending on how you run the tests, you may get a security exception
from the harness.

### Can a test write to `System.out` and `System.err`?

Yes. The harness will capture everything sent to both streams.

### Can a test check the output of `System.out` and `System.err`?

Yes, compiler tests using the `@compile` tag can use
the `/ref=`_file_ option.
Such tests are generally not recommended, since the output can be
sensitive to the locale in which they are run, and may contain
other details which may be hard to maintain, such as line numbers.

While not part of the tag specification, some tests use the
string "`/nodynamiccopyright/`" after the `@test` tag
to indicate that the file should not be subject to automated
copyright processing that might affect the operation of the test.

### My test opens files and sockets: do I have to close them before the test exits?

If you want to be able to run the action in agentVM mode, then
any open files and sockets must be closed before the action
is completed: jtreg cannot do it for you.

If the action will always be run in otherVM mode, then it may
be good practice to close any open files and sockets, but the
operating system will probably close the open items for you
if you do not.

### My test changes observable system state like system properties or the default locale: do I have to reset it?

If you want to be able to run the action in agentVM mode, then
you may need to reset the value before the action is completed.
When an action is run in agentVM mode, jtreg will try and reset some
commonly used values to their state at the beginning of the action.
If you modify any other values, you must either reset them in the
test code before the action exits, or ensure the use of otherVM mode
for the action.

If the action will always be run in otherVM mode, there is no need to reset
any system state that was modified during the action.

### My test creates and uses additional threads: do I have to clean them up?

If you want to be able to run the action in agentVM mode, then you
either must ensure that any threads have been terminated, or that they
can be terminated by being interrupted.
jtreg will run the main test code in a new thread in a new thread group.
When that thread exits, or when any thread in the thread group
throws an exception, jtreg will try to ensure that all threads in
that thread group have exited. It will periodically interrupt those
threads for a short period, to give them a chance to terminate
themselves, and will report an error if they do not terminate in a
timely fashion. An action should clean up for itself any threads that
will not respond to being interrupted, or which are created in some
other thread group.

If the action will always be run in othervm mode, there is no need
to help ensure that all threads have terminated.

### What is a combo test? {#combo-test}

"Combo test" is an informal term used by OpenJDK developers to describe a style
of writing a test so that it executes many test cases within the test, often by
iterating over all combinations of a set of parameters. Such a technique can
lead to a combinatorial explosion in the number of test cases, yielding hundreds
or even thousands of test cases; when writing a test like this, it is important
to consider [how much time](#how-much-time) it will take to execute and
[how much output](#how-much-output) it may generate.

### How much output can a test generate? {#how-much-output}

While a test can generate as much output as it wants, jtreg limits the amount
that is saved of output written to any stream used by an action. This includes
the standard output and error streams, and the "direct" output written by the
compiler (javac) when run in agentVM mode.

The default for the maximum amount that is saved is 100K characters. If a test
exceeds this limit, the first recourse should be to try and reduce the amount of
output.  Ask yourself whether all the information being written by the test is
actually useful and if anyone is likely to read it all?  Writing an unnecessary
amount of detail affects everyone who runs the test and stores the output for
any length of time.

Consider the following possibilities, to reduce the amount written by a test:

*   In a [combo test](#combo-test), reduce the amount of information written by
    test cases that pass, and just generate more details when a test case fails.
*   Make the amount of output be  [configurable](#configurable), so that it can
    be less verbose by default and more verbose when needed, such as when a
    developer might be developing or debugging the test.

If you really need to have jtreg save more of the output, you can override the
limit in two ways:

*   To override the limit for a single test run, set the jtreg system property
    `javatest.maxOutputSize` to an integer giving the new size.  If you are
    running jtreg using the normal script, remember to use `-J`, to set the
    system property in the jtreg JVM, and not the system property in a test JVM.

        jtreg -J-Djavatest.maxOutputSize=250000 ...

*   To override the limit test runs for some or all tests in a test suite,
    set the property `maxOutputSize` in either the top-level `TEST.ROOT`
    file (to affect all tests) or in a `TEST.properties` file (to affect
    tests in that directory and its subdirectories.)

If the limit is exceeded, jtreg will discard the middle of the output, so that
it can save the beginning of the output, which often contains configuration
details written by the action or the top of a long stacktrace, and can save the
end of the output, which contains the output that was written most recently by
the action.

The discarded output will be replaced with a message like the following:

        Output overflow:
        JT Harness has limited the test output to the text
        at the beginning and the end, so that you can see how the
        test began, and how it completed.
        If you need to see more of the output from the test,
        set the system property javatest.maxOutputSize to a higher
        value. The current value is 100000.


### How do I set `javatest.maxOutputSize`? {#how-to-set-javatest.maxOutputSize}

See the [previous entry](#how-much-output).

TL;DR:  If you're trying to set `javatest.maxOutputSize`, it may be because you have seen a
message in the middle of some very long output in a `.jtr` file.  You can either
set the default value with a system property for the JVM running jtreg (_not_ the JVM(s)
used to run tests), or you can override the default value for some or all tests with the
`maxOutputSize` property in the `TEST.ROOT` or `TEST.properties` configuration files.

### How much time can a test take? {#how-much-time}

jtreg limits the amount of time that may be used to execute each action  of the
test. The default limit is 120 seconds (2 minutes). If a test exceeds this
limit, the first recourse should be to try and reduce the  time that is being
used. If a test takes an excessive amount of time to run, that affects everyone
who runs the test.

*   Try to avoid executing code in a subprocess when it could be done
    equivalently and faster within the same JVM, perhaps using library code.
    A good example of that is compiling code on the fly: it is much faster to
    execute javac via the `java.util.spi.ToolProvider` API or Java Compiler API
    than it is to run `javac` in a separate process.
*   In a [combo test](#combo-test), consider reducing the number of test cases
    that are executed.
*   Make the amount of execution be [configurable](#configurable), so that it
    executes less by default and more when needed, such as when a developer
    might be developing or debugging the test.

If you really need to increase the time allowed for an action, use the
`/timeout` option for the action.

If you want to scale all the timeouts in a test run, use the `-timeoutFactor`
command-line option.

### How can I make a test configurable? {#configurable}

It can sometimes be useful to make a test behave differently when so
required. One way to do so is to make the test code read the value of
a system property or environment variable.  You can set a system property
on the jtreg command-line (for all tests) with `-D` or any of the ways
to set a VM option. If you want to use an environment variable, you
must declare that using the jtreg `-e` option. (If you don't, jtreg
will not pass the environment variable into any JVMs that it starts.)

### What should I do with a test once I've written it?

[Test it](#do-i-need-to-test-a-test) and then when you're satisfied,
check it into the test directory in your repository.

Yes, it really is that simple.

### Do I need to test a test?

Yes! While there are procedures in place to routinely run tests to detect any
issues in the product, that presumes that each test will work as expected. It is
not practical to routinely test that every test is working as intended, because
to do so would require a malfunctioning product that will help exercise the code
pathways that are typically not executed. It is therefore especially important
to ensure the test functions correctly (meaning, it will actually detect the
errors it is designed to catch)  at the time it is being written and reviewed.

### How do I test a test?

There is no "one size fits all" solution, but here are some guidelines.

If a test is a regression test, meaning it is designed to test the fix for
a bug that was found in the product, then check the following:

* Does the test detect the failure on a recent version of the product that
  does _not_ include the fix?
* Does the test demonstrate the absence of the failure on a version of the
  product that _does_ include the fix?

If the test is a unit test or system test for new functionality that has
been added to the system, there is no prior version of the system that would
demonstrate any errors, but you can try temporarily injecting some errors into
the product code, in order to verify that the test will correctly detect those
errors. One way to indirectly achieve this goal is to develop and use the test
while developing the new feature
(so-called [test-driven development](https://en.wikipedia.org/wiki/Test-driven_development)),
instead of leaving it until afterwards.

For any type of test, ask yourself if the test is designed to cover all the
lines of code that were affected in the corresponding change to the product.
If code was changed, but is not exercised by any existing test or the new test,
then that code is effectively untested. There may be minor exceptions to this
rule for code that will really difficult to exercise, such as code to detect
"out of memory" or "disk full" conditions, but the general principle holds.
If you have access to code coverage tools, check that all the modified lines
of code in the product have been executed: if code has not been executed, it has
definitely not been tested.

For some types of test, it may be possible to build in some amount of
self-testing.  For example, if a test is designed to exercise a number
of different behaviors in the product, where those behaviors can be
externally monitored, ensure that all those behaviors were actually
exercised, and report an error if any were not.

### Where are the OpenJDK tests?

Within a recent version of an OpenJDK repo, the `test/` directory
contains three separate test suites:

* `test/hotspot/jtreg`: tests for the HotSpot JVM
* `test/jdk`: tests for the main JDK API and related tools
* `test/langtools`: tests for the javac, javadoc, javap and jshell tools

In older versions of OpenJDK, these directories were in
`test` subdirectories of the `hotspot`, `jdk` and `langtools`
repositories.

### Why not have separate test workspaces that only contain tests?

Checking a test into the workspace against which it's first written
helps prevent spurious test failures.  In the case of a regression test, this
process ensures that a regression test will migrate upward in the integration
hierarchy along with the fix for the corresponding bug.  If tests were managed
separately from fixes then it would be difficult to distinguish between a true
test failure and a failure due to version skew because the fix hadn't caught up
with the test.

### How should I name a test?

In general, try to give tests names that are as specific and descriptive
as possible.  If a test is checking the behavior of one or a few methods, its
name should include the names of those methods.  For example, a test written
for a bug in the skip method of FileInputStream could be placed in
`test/jdk/java/io/FileInputStream/Skip.java`.  A test written for a bug
that involves both the skip and available methods could be named
`SkipAvailable.java`.

Tests that involve many methods require a little more creativity in naming,
since it would be unwieldy to include the names of all the methods.  Just
choose a descriptive word or short phrase.  For example, a test that checks the
general behavior of a FileInputStream after end-of-file has been reached could
be named `AfterEOF.java`.

It can be helpful to add more information to the test name to help further
describe the test.  For example, a test that checks the skip method's behavior
when passed a negative count could be named `SkipNegative.java`.

You might find that the name you want to give your test has already been
taken.  In this case either find a different name or, if you're just not in a
creative mood, append an underscore and a digit to an existing name.  Thus, if
there were already a `Skip.java` file, a new test for the skip
method could be named `Skip_1.java`.

Some tests require more than one source file, or may need access to data
files.  In this case it's best to create a subdirectory in order to keep
related files together.  The subdirectory should be given a descriptive
mixed-case name that begins with a lowercase letter.  For example, a
FileInputStream test that needs an input file as well as its Java source file
in order to test the interaction of the read and available methods could be
placed in the subdirectory
`test/jdk/java/io/FileInputStream/readAvailable`.

Some tests involve more than one class in a package, in which case a new
subdirectory in the relevant package directory should be created.  For example,
a set of general tests that exercise the character streams in the
`java.io` package could be placed in the
`test/jdk/java/io/charStreams` directory.

### What about tests that don't fit into the API structure?

In addition to a `java` directory for API-related tests, the
test directory contains a `javax` directory,
`vm` directory, a `tools`
directory, and `com` and `sun` directories.

### Can tests in different directories have the same name?

Yes.  When a test is run by the harness, a special classloader is used so
that the classpath is effectively set to include just the directory containing
the test, plus the standard system classes.  Thus name
clashes between tests in different directories are not a problem.

An alternative approach would be to associate a different package with each
test directory.  This is done, for example, in the JCK test suite.  The
difficulty with this idea is that in order to debug a test (under dbx or
workshop or jdb or whatever) you must set up your classpath in just the right
way.  This makes it difficult to diagnose bugs that are reported against
specific tests.

### How do I write a test for an AWT bug or a Swing bug?

Bugs in the graphical facilities of the JDK generally require
manual interaction with applets.  Applet tests are written in much the
same way as the simple `main` tests described above.  The
primary differences are that a second "@" tag is given to indicate
that the test is an applet test, and an appropriate HTML file is
needed.  For example, an AWT test named `Foo.java` would
have the form:

~~~~java
/* @test
 * @bug 9876543
 * @run applet/manual Foo.html
 */

public class Foo extends java.awt.Applet { ... }
~~~~

or

~~~~java
public class Foo extends javax.swing.JApplet { ... }
~~~~

The `@run` tag tells the harness how to run the test.  The first
argument is the run type, `applet`, followed by an option,
`/manual`, that flags this test as a manual test requiring user
interaction.  The remaining arguments to the `@run` tag are passed
to the program in a manner appropriate to the run type.  In this case, the test
will be run just as if the `appletviewer` had been invoked on
`Foo.html`.  Thus `Foo.html` must contain, at least, an
HTML `applet` tag with any necessary parameters.

### How does the user know what to do for a manual applet test?

When the harness runs a manual applet test, it will display the contents of
the HTML file that defines the applet.  Include instructions in the HTML file
so that the person running the test can figure out what to do if any
interaction is required.

### Exactly what does the`/manual` option mean?

The `/manual` option indicates to the harness that this is a
manual test.  This allows the harness to distinguish manual from automatic
tests, which is important since the latter can be run without user interaction.

There are actually three kinds of applet manual tests: Self-contained tests,
`yesno` tests, and `done` tests.

* A self-contained manual test handles all user interaction itself.  If the
    test fails, whether this is determined by the user or by the applet, then the
    applet must throw an exception.  Self-contained tests specify
    `applet/manual` for the first `@run` argument.

* A `yesno` test requests the harness to ask the user whether the test
    passes or fails.  To do this, the harness will put up `pass` and
    `fail` buttons, and it's up to the user to inspect the screen and
    click one of the buttons.  The harness will take care of shutting down the applet.
    The test will also fail if the applet throws an exception.  `Yesno`
    tests specify `applet/manual=yesno` for the first `@run`
    argument.

* A `done` test requests the harness to put up a `done`
    button.  After the user has completed whatever actions are required by the
    test, the user clicks `done` and the harness shuts down the applet.
    The program must itself determine whether the test is to pass or fail, and
    throw an exception in the latter case.  `Done` tests specify
    `applet/manual=done` for the first `@run` argument.

`main` and `shell` may also specify the `manual` option using `main/manual` and
`shell/manual` respectively.  These tests must be completely self-contained.

### How does a manual applet test indicate success or failure?

Just as with `main` tests, an applet may throw an exception
at any time to indicate test failure.  A `done` test applet, for
example, can check for failure in the `java.applet.Applet.destroy`
method.  If an applet doesn't throw an exception, and if, in the case of a
`yesno` test, the user clicks `pass`, then the test is
considered to have passed.

Be very careful about where failure is checked.  The AWT event thread does
not propagate exceptions!

### Can I (and should I) write shell tests?

Yes (and no). While jtreg supports the use of shell tests (that is, tests using
`@run shell` actions), and while it was convenient at one time to be able to write
such tests, the use of such tests is now deprecated. In practice, it is difficult
to write tests that behave correctly and as intended, under all conditions on all
platforms, especially when the item being tested may not be behaving as expected.
There is also some amount of a performance penalty, since many of the operations
that may be performed by a shell script can be performed faster, by relatively
simple API calls, in a Java program. Therefore, it is now recommended to write
Java code to perform the same work that might otherwise be done with a shell script,
perhaps using JDK API or shared test library code to perform commonly-used operations
such as file manipulation, executing commands in sub-processes when necessary,
and analyzing the results of those commands.

jtreg provides a variant of `@run main` that can be useful in such situations:
`@run driver`. This is the same as `@run main` with the exception that any VM
options specified on the command line will not be used when running the specified class.
Such code can start processes using the standard `java.util.ProcessBuilder` API.
To build up the command line to be invoked, the code may want to reference details
about the test (such as the class path) or values that were given on the jtreg
command line (such as the JDK being tested, or the set of any  VM options that
were specified.)  All these values are available in system properties, most of
which have names beginning `test.`; the complete list is given in the
[tag specification](tag-spec.html#testvars).

If you still want to write a shell test, and if you wish to support the use of
Windows Subsystem for Linux (WSL) to run such tests on Windows, you should use a
suffix of `.exe` when specifying the path in the shell script for a JDK tool that
was built to be run directly on Windows. To help with that, `jtreg` will set an
environment variable `EXE_SUFFIX` that can be used when constructing the path
for a tool. For example:

    javac="${TESTJAVA+${TESTJAVA}/bin/}javac${EXE_SUFFIX}"

For more information on writing shell tests, see
[Shell Tests in jtreg](shellTests.html).

### When should I update the `@bug` entry in a test description?

When a new test is added to a test suite, it is generally considered good
practice to add an `@bug` entry that identifies one or more bug numbers
for the issue that the new test is addressing.

Sometimes, when fixing a bug, it may be more appropriate to modify an
existing test than to write a completely new one. In this case, it is
appropriate to add a bug number to the existing `@bug` entry, to indicate
that this test is both a test for the original issue and for one or
more additional issues.

If you're modifying an existing test, check whether the `@summary` entry
should be updated as well: if it is too specific to the original reason for
the test, you should generalize the summary.

It can also happen that when you're fixing a bug, you may break some
existing, unrelated tests, such that you need to update those tests to
work again. In this case, you should probably _not_ update the `@bug` entry.

These guidelines can be summarized as follows:

* If you're updating a test to be a regression test for a bug fix,
  then you should probably update the `@bug` entry.
* If you're updating a test because it was affected by a bug fix,
  but the test is not otherwise a regression test for the bug fix,
  then you should probably not update the `@bug` entry.

### When should I use the `intermittent` or `randomness` keyword in a test?"

The `intermittent` keyword should be used to mark tests that are
known to fail intermittently.

Extra care should be taken to handle test failures of such tests.

For more details, see these email threads:
[March 2015](http://mail.openjdk.org/pipermail/jdk9-dev/2015-March/001991.html),
[April 2015](http://mail.openjdk.org/pipermail/jdk9-dev/2015-April/002164.html).

### When should I use the `randomnness` keyword in a test?

The `randomness` keyword should be used to mark tests that use randomness
such that the test cases will differ from run to run. (A test using a
fixed random seed would not count as "randomness" by this definition.)

Extra care should be taken to handle test failures of tests using
random behavior.

For more details, see these email threads:
[March 2015](http://mail.openjdk.org/pipermail/jdk9-dev/2015-March/001991.html),
[April 2015](http://mail.openjdk.org/pipermail/jdk9-dev/2015-April/002164.html).

### What if a test does not apply in a given situation?

Sometimes a test should not be run in a particular situation.
For example, a test may require the presence of specific modules
in the JDK being tested, in order to function correctly;
or, a test may specifically verify a behavior on
one kind of platform (such as Windows), and not be relevant on other
kinds of platform (such as Linux or macOS).

A test may specify the modules that need to be present in the JDK
being tested using the `@modules` tag. A test may specify more
general conditions to determine whether it should be run using
the `@requires` tag, which can test the values of properties (such
as system properties) that are determined in a JVM running the
JDK being tested.

If jtreg determines that a test fails to meet the conditions expressed
in `@modules` and `@requires`, no additional processing of the test
will occur: in particular, none of the actions will be executed.

While `@modules` and `@requires` will cover many cases, there may
be cases where the determination of whether the test is applicable
needs to be determined by the test itself. For example, a test may
want to check the presence of a shared library containing compiled
native code, and to skip the main body of the test if the library
is not available. In such cases, a test may throw an exception
named `jtreg.SkippedException`. (To avoid any dependency on any
jtreg library, so that the code can be run standalone, the exception
should be defined in test library code.)  This exception will be
treated specially: unlike for any other exception, the test will be
deemed to have passed, but the reason will be set to a message
saying that the test was skipped instead of executing normally.

### Can I put more than one test in a file? {#multiple-tests}

Yes. You may place one or more separate test descriptions near the head
of a test source file. Each test description should be in its own comment block,
and begin with `@test`. By convention, the comment blocks should appear
after any leading legal header, and before any executable code in the
file. The feature is supported in normal Java tests, in shell tests,
and in legacy applet tests.
(It is not supported in JUnit or TestNG tests, which do not use explicit
test descriptions.)

The test descriptions are independent of each other, and may be used
to run a test class with different arguments, such as in the following
examples:

Example: MyJavaTest.java
<pre style="margin-left:0.25in; border:1px solid grey; padding: 5px; width: 50%">
/* Copyright (C) A.N.Other. */

/* @test
 * @run main MyJavaTest 1 2 3
 */

/* @test
 * @run main MyJavaTest a b c
 */

public class MyJavaTest {
    public static void main(String... args) {
        System.out.println(java.util.Arrays.toString(args));
    }
}
</pre>

Example: MyShellTest.sh
<pre style="margin-left:0.25in; border:1px solid grey; padding: 5px; width: 50%">
#!/bin/sh
# Copyright (C) A.N.Other

# @test
# @shell MyShellTest 1 2 3

# @test
# @shell MyShellTest a b c

echo $*
</pre>

#### How are the tests named? {.unnumbered}

If there is only one test description in a file, the test is named
according to the path of the file relative to the top level directory
of the test suite, that contains the `TEST.ROOT` file.

If there is more than one test description in a file, each one is named
by appending a URL-style "fragment identifier" to the path of the file
relative to the top level directory of the test suite. Each fragment
identifier is of the form `id`_N_, starting from _N_ equal to 0.

Examples:

    MyJavaTest.java#id0
    MyJavaTest.java#id1


#### How are the test results organized? {.unnumbered}

The fragment identifier is incorporated into the name of the result (_.jtr_)
file and any directory containing test-specific results, replacing the
`#` with `_`.

Examples:

    MyJavaTest_id0.jtr
    MyJavaTest_id1.jtr


#### How do I list the tests in an exclude file, such as `ProblemList.txt`? {.unnumbered}

Specify the test names, using the fragment identifier form.

Example:

    MyJavaTest.java#id0   1234567 generic-all This test is broken!


_Note:_ It is currently not possible to exclude all the tests in a file
with a single entry.  See
[CODETOOLS-7902265](https://bugs.openjdk.org/browse/CODETOOLS-7902265).

### Can I run tests differently, depending on the circumstances?

Yes; one way is to use a combination of putting multiple tests in a file,
and `@requires` to select which one will be run.
For example, the following simple, somewhat contrived, example shows how to run a
class with different parameters depending on the platform being tested:

<pre style="margin-left:0.25in; border:1px solid grey; padding: 5px; width: 50%">
/* @test
 * @requires os.family == windows
 * @run main MyJavaTest --root C:
 */

/* @test
 * @requires os.family != windows
 * @run main MyJavaTest --root /
 */
</pre>

jtreg does not support any sort of conditional flow within the sequence of actions.
You can use `@run driver` to run a class that provides more complex logic, if needed.

### My test uses "preview features": how do I specify the necessary options?

Tests that use [preview features](https://openjdk.org/jeps/12) must use the
`--enable-preview` to compile and run the code.  In addition, to compile the
code you must also specify the appropriate source level.

To provide these options, you can either do so explicitly, in `@compile` and `@run main`
actions, or you can use the `@enablePreview` declarative tag, in which case jtreg
will automatically add any necessary options.

Using explicit options in  `@compile` and `@run main` actions can be inconvenient
and disruptive to the test description when the test can otherwise be set up to use
implicit `@build` actions and the ensuing `@compile` actions.
In these situations, the use of `@enablePreview` is generally recommended.

The equivalent of `@enablePreview` can be set on all the tests in a directory
and its subdirectories by configuring an entry for `enablePreview` in the
`TEST.properties` file in an enclosing directory. Any value set in a `TEST.properties`
file can be overridden in individual tests by using `@enablePreview`.


--------

## Organizing tests

### How are the OpenJDK test suites organized?

For mostly historical reasons, the OpenJDK tests are grouped into
three test suites: `test/hotspot/jtreg`, `test/jdk` and `test/langtools`.

* Tests in the `test/hotspot/jtreg` test suite are organized
  according to the subsystem to be tested.

* Tests in the `test/jdk` test suite are generally organized following
  the structure of the Java API.  For example, the `test/jdk` directory
  contains a `java` directory that has subdirectories `lang`, `io`, `util`,
  and so on.

  Each package directory contains one subdirectory for each class in the
  package.  Thus tests for `java.io.FileInputStream` can be found in
  `test/jdk/java/io/FileInputStream`.

  Each class directory may contain a combination of single-file tests and
  further subdirectories for tests that require more than one source file.

* Tests in the `test/langtools` test suite are organized according to
  a combination of the tool or API being tested.

### What is the test root directory?

The _test root directory_, or _test suite root directory_ is the
root directory of the overall test suite.
In OpenJDK terms, this means a directory like `test/jdk` or
`test/langtools` in a recent OpenJDK repo.

The test root directory for any test is determined by finding the smallest
enclosing directory containing a marker file called `TEST.ROOT`.
The `TEST.ROOT` file can also be used to define some global properties
for the test suite.

### Why is the "test root directory" important?

Within the test suite, tests are uniquely identified by their path
relative to the test root directory.
This relative path is used to generate test-specific directories
and files within the work directory, and to identify test results
within the report directory.

However, note that tests can be specified on the command line
by any valid file system path, either absolute or relative to the
current directory.

### Can I have more than one `TEST.ROOT`?

In general, no.  Each test is uniquely associated with
exactly one test root directory, which is the smallest
enclosing directory containing a marker file called `TEST.ROOT`.
In general, a test run will consist of tests from a single
test suite, identified by a single test root directory.

It _is_ possible to run tests from multiple test suites
(such as `test/jdk` and `test/langtools`) in the same invocation
of jtreg, but this is not common.

### How should I organize tests, libraries, and other test-related files?

The directories within a test suite may be used for a number of
different purposes:

* The overall test hierarchy, as reflected in the name of each test.

* Java source code, organized in a module or package hierarchy.
  Such code may provide packages, modules or patches for modules
  to be used by tests, or may be a collection of TestNG or JUnit
  tests.

* Library code, as referenced by a `@library` directive in a test description.

* Additional test-specific files, such as resource files, configuration
  information, and even additional source files, such as may be used
  when testing the javac and javadoc tools.

Given all those possibilities: here are some guidelines to follow.

* Tests are normally written to be in the unnamed package (that is,
  with no `package` statement)

* Don't place too many tests in the same directory.

* If a test requires a number of test-specific additional files,
  consider placing the test and those files in a separate directory.
  It would be reasonable to colocate additional tests that also
  require access to those files.

* Library code should normally use named packages. This helps avoid
  name clashes that may occur if a test uses multiple libraries.

* A directory that is named as a library directory (such as with `@library`)
  should only contain the packages for that library, and nothing else.

Some guidelines follow from this one fundamental guideline:

* If a directory is part of a source-code package hierarchy,
  all the source files in that directory  should be part of that
  same package hierarchy.

  For example:

  * Don't use "nested" package hierarchies: that is, don't use
    a package hierarchy whose root is part of an enclosing package.
  * Don't place one library within another.
  * Don't place tests in a library.
  * Don't use the antipattern in which a test refers to a library
    in an enclosing directory, such as `@library ../..`.


### What is "tiered testing"?

"Tiered testing" is a policy used in OpenJDK test suites to categorize
tests according to their importance and reliability. The tiers are
implemented as jtreg groups:

* `tier1`: All tier 1 tests should always pass whenever they are run.
* `tier2`: All tier 2 tests should typically pass, although some
   failures may occasionally occur.
* `tier3` and above: mMre failures may be expected when these tests are
   run.

For more details, see these email threads:
[March 2015](http://mail.openjdk.org/pipermail/jdk9-dev/2015-March/001991.html),
[April 2015](http://mail.openjdk.org/pipermail/jdk9-dev/2015-April/002164.html),
[June 2015](http://mail.openjdk.org/pipermail/jdk9-dev/2015-June/002325.html).

--------

## TestNG and JUnit tests

### What is a "package root"?

"package root" refers the root of the package hierarchy in
 which a Java source is placed. It is the directory you would
 put on the javac source path if you wanted javac to compile
 the file explicitly.

Most jtreg tests are written in the "unnamed package"
 (i.e. without a package statement) and so for most jtreg tests,
 the directory directly containing the test is the package root
 for the test.  This is different from other test suites using
 other test harnesses (such as TestNG or JUnit) in which all the tests
 of a test suite are placed in a single package hierarchy,
 often mirroring the package hierarchy of API being tested.

### How does jtreg support TestNG and JUnit tests?

jtreg supports TestNG and Junit tests in two ways.

1. Tests can be written with a full test description
   (the comment beginning `/* @test....*/`) and can use one of
   the following action tags to run a test class with TestNG or JUnit:

       @run testng classname args
       @run junit classname args

   Such a test is otherwise similar to a standard test
   using `@run main`. You can use other tags such as `@library` and
   `@build`, just as you would for `@run main`.
   These tests should normally be in the unnamed package
   (i.e. no package statement.)
   These tests can be freely intermixed with other tests using
   `@run main`, `@run shell`, `@run applet` and so on.

   You may place [multiple tests](#multiple-tests) containing such actions
   in a file.

2. If you have a group of TestNG or JUnit tests written in their own
   package hierarchy, you can include that entire package
   hierarchy as a subdirectory under the main test root directory.
   To do this, you must identify the root directory of that
   package hierarchy to jtreg, so that it knows to treat all the
   files in that package hierarchy specially.

   In such a group of tests, you do not need to provide test
   descriptions in each test, but you may optionally provide a
   test description if you choose to do so, if you wish to specify
   information tags such as`@bug`, `@summary` and `@keyword`.
   You must not specify any action tags, such as `@run`, `@compile`,
   and so on, since the actions are implicit for every test in the group
   of tests.

   At most one such test description may be provided in each file
   in the group.

### How do I identify a group of TestNG or JUnit tests in their own directory?

Add a line specifying the directory to `TEST.ROOT`; the line is the same
for both TestNG and JUnit tests:

    TestNG.dirs = dir1 dir2 dir3 ...

Include the package root directory for each group of TestNG or JUnit
tests, and specify the package root directory relative to the test
root directory.

You can also override the value in `TEST.ROOT` by using the
`TEST.properties` file in any subdirectory of the test root directory
that contains the package root.
If you put the declaration in a `TEST.properties` file, you must
specify the path relative to the directory containing the
`TEST.properties` file.
In particular, instead of declaring the directory in `TEST.ROOT`,
you could place the declaration in a `TEST.properties` file in the
package root directory for the group of tests, in which case
the declaration would be simply:

    TestNG.dirs = .

You can mix TestNG and JUnit tests in the same group of tests,
although it may not be good style to do so.

###  How does jtreg run TestNG and JUnit tests?

Tests using `@run testng` are compiled in the standard way,
with TestNG libraries on the classpath.
The test is run using the class `org.testng.TestNG`.

Tests using `@run junit` are compiled in the standard way,
with JUnit libraries on the classpath.
The test is run using the class `org.junit.runner.JUnitCore`.

For any JUnit or TestNG tests in a group of TestNG or JUnit tests,
any tests in the group that need compiling are compiled together,
before any test in the group is run.
Then, the selected test classes are run one at a time using
`org.testng.TestNG`. If the class imports JUnit classes,
it will be run with TestNG "mixed mode" enabled.
Each test that is run will have its results stored in a
corresponding `*.jtr` file.  _Note:_ it is not possible to reliably
determine via static analysis which files may contain TestNG or
JUnit tests and which do not. As a result, jtreg may run some
classes only to have TestNG report that no tests were found in the
file. If there are many such classes in the group, you may want to
move those classes to a separate library that can be used by the
tests in the group; this will avoid jtreg running the library classes
in case they contain tests.

### How do I specify any libraries I want to use in TestNG and JUnit tests?

Tests using `@run testng` or `@run junit`can use `@library` and `@build`
in the standard way.For any test in a group of TestNG or JUnit tests,
you can specify the library by adding a line specifying the library in the
`TEST.properties` file in the package root directory for the group of
tests.

    lib.dirs = path-to-library ...

As with the `@library` tag, if the path begins with "/",
it will be evaluated relative to the test root directory;
otherwise, it will be evaluated relative to the directory
containing the `TEST.properties` file.

For any particular group of TestNG or JUnit tests, you can only
specify libraries for the entire group: you cannot specify one
library for some tests and another library for other tests.
This is because the all the source files in the group are
compiled together.

### What version of TestNG and JUnit does jtreg support?

Run the command `jtreg -version` to see the version of jtreg and available components.

For OpenJDK, the policy is to use a supported, older version
and not necessarily the latest and greatest version.

### How do I find the path for the TestNG or JUnit jar files?

It should not be necessary to determine the path for the TestNG or JUnit jar
files when using the jtreg built-in support to run TestNG or JUnit tests,
because jtreg will automatically set up all the necessary paths.  But sometimes
it may be desirable for test code, such as a "driver" test, to run a set of
TestNG or JUnit tests with some special set of options, perhaps in a separate
JVM. In such situations, in may be necessary to construct a class path
containing the paths for the necessary libraries.

The best way to determine the path of the jar file for a library is to use the
protection domain and code source for a representative class in the  library,
such as with the following code:

```java
import java.security.ProtectionDomain;
import java.security.CodeSource;

...

    public Path getPath(Class<?> libraryClass) {
        CodeSource cs = libraryClass.getProtectionDomain().getCodeSource();
        return Path.of(URI.create(cs.getLocation().toString()));
    }
```

Note that starting with jtreg version 7, the convention for naming the jar file
for the library is to use the base name of the jar file that was specified when
jtreg was built, and that this name may depend on the version of the library.
For this reason, you should not assume a fixed name for the library jar file.

--------

## General Problems

### My test only passes if I don't use jtreg to run it. Why does it fail in jtreg?

By default, tests run using `jtreg` are each run in a
separate JVM.  By design, only a limited number of shell
environment variables are passed down to the test's JVM.
This may affect how a test is run.

As per spec, the only shell environment variables that are
automatically propagated into the test's JVM are:

* Linux and Solaris:
    * `PATH` is set to `/bin:/usr/bin:/usr/sbin`
    * The following are propagated from the user's environment:
        `DISPLAY`,
        `HOME`
        `LANG`,
        `LC_ALL`,
        `LC_CTYPE`,
        `LPDEST`,
        `PRINTER`,
        `TZ` and
        `XMODIFIERS`

* Windows:

    * `PATH` is set to the MKS or Cygwin toolkit binary directory
    * The following are propagated from the user's environment:
        `SystemDrive`,
        `SystemRoot`
        `windir`

If your test needs to provide more environment variables or
to override any values, use the `-e` option.

### How do I set the `CLASSPATH` environment variable for my test?

The harness sets the `CLASSPATH` for the `compile`,
`main`, and `applet` actions to be the system class path
plus the test's source and classes directory.

It is possible to set the classpath for the `main/othervm` action
via the `-classpath` option to `java`.  Any other
modification of the `CLASSPATH` must be done using the
`shell` action.

### Why don't you just pass all of my shell environment variables to the JVM running the test?

The approach of passing down a list of pre-defined environment variables
helps guarantee consistent results across different people running the test(s).

### Why is the default to run tests in another JVM?

Insulation from other tests.  Most well-behaved tests do not modify the
JVM; however, if a test does modify the JVM it is possible that this change
will interfere with subsequent tests.  Running each test in another JVM allows
for the possibility of bad test suite citizens.

### Why would I ever want to run in the same JVM?

Speed.

### What is "agent VM" mode, and why would I want to use it?

It's like "same VM" mode, but better. By default, tests will run in
the same JVM. In between tests, jtreg will try to reset the JVM to
a standard state, and if it cannot, it will discard the JVM and
start another.

### Should a test call the `System.exit` method?

_NO!_ The default harness security manager prevents tests from
calling `System.exit`.  If the test is running in the same JVM as
the harness and the harness was not permitted to install its own security manager, a
call to `System.exit` will cause the harness itself to exit!

If the test is running in its own separate JVM, a call to
`System.exit` may not allow the harness to properly handle test
termination.

### My test only applies to one platform and it will fail/not run in others.  How do I prevent the harness from running it on the wrong platform?

The [tag specification](tag-spec.html) provides no way to indicate any platform requirements.
If the test only applies to a single platform, then the test itself must determine
the current platform and decide whether the test should be run there.  If the
test suite is running on the wrong platform, the test should pass (i.e. just
return) otherwise, the test should proceed. A significant benefit to this
approach is that the same number of tests in a testsuite will always be run if
the same arguments are given to `jtreg` regardless of the particular
platform.

For tests that are written in Java code (i.e. `applet` and
`main` tests), you may determine the platform via the system
properties.  The following code fragment may be used to distinguish between
SunOS sparc, SunOS x86, Windows, etc.

~~~~java
    String name = System.getProperty("os.name");
    if (name.equals("Linux")) {
        System.out.println("Linux");
    } else if (name.contains("OS X")) {
        System.out.println("(Mac) OS X");
    } else if (name.equals("SunOS")) {
        System.out.println("Solaris");
    } else if (name.startsWith("Windows")) {
        System.out.println("Windows");
    } else {
        throw new RuntimeException("unrecognized OS:" +
                " os.name == " + name);
    }
~~~~

This approach is not suitable for `shell` tests.  In this case,
you can determine the platform via `uname`.  The following code
accomplishes the same task as above.

    OS=`uname -s`
    case "$OS" in
        CYGWIN* )
            echo "Windows (Cygwin)" ;;
        Darwin )
            echo "(Mac) OS X" ;;
        Linux )
            echo "Linux" ;;
        SunOS )
            echo "Solaris" ;;
        Windows* )
            echo "Windows" ;;
        * )
            echo "unrecognized system: $OS" ; exit 1 ;;
    esac

### How can I make `applet` and `main` action tests read from data files?

When jtreg is executed, it `cd`'s into a scratch area to
ensure that a test can not alter the test suite.  Thus, a direct reference to a
data file without some sort of indicator as to where the test was originally
located will fail.

The system property `test.src` contains the name of the directory
where the test source resides.  The following example illustrates how to read
the data file `foo` which is contained in the test source directory.
Note that the default value of `"."` allows this test to run both
with the harness, and without (in the source directory).

    File f = new File(System.getProperty("test.src", "."), "foo");
    InputStream in = new File(f);

### Can I use `package` statements in my tests?

Yes&#8230; but you probably don't want to.  The harness  searches for class
files in a class directory with components which are parallel to the source
directory.  It will be unable to locate packaged class files when the test is
invoked via reflection.  Use of the `package` statement is not
recommended unless the test is intended to test `package` statements.

Tests which test the package mechanism may use package statements; however,
it will be the responsibility of the test writer to properly execute the
compiled class as necessary.

### Why can't multiple test source files in the same directory have package-private classes of the same name?

In the Java language, package private classes defined in different files
in the same directory are interpreted as duplicate class definitions.  The
contents of the class files depends on the order of compilation.  To avoid
compilation order dependency problems, we recommend that you define auxiliary
classes as inner classes.

For performance reasons, the harness does not automatically remove class files
between individual tests or build each test within its own unique subdirectory.
This allows us to cache class files across test runs and share code between tests
in the same directory.

### Should a test catch `Throwable`, `Exception`, or `Error`? {#write-catch-exceptions}

Ideally, only specific, anticipated exceptions should be caught by a
test.  Any other exception which is provoked during testing should be
propagated back to the harness.

In the event that a very general exception is caught in test code, a certain
amount of care will need to be exercised.  For example if a user wants to stop
the harness during a test run, an `InterruptedException` is used to
interrupt the test.  If that exception is caught by a test and not re-thrown to
the harness, the stop request will be lost and the tests will not stop!

Here is a list of exceptions that may need to be re-thrown:

* `InterruptedException` (from `Exception`)
* `InterruptedIOException` (from `IOException`)
* `ThreadDeath` (from `Error`)

### My test requires that I use information printed to `System.out` or `System.err` to determine whether a test passed or failed.  When I run my test in the harness, I can't seem to find these output streams.

Currently, information sent to `System.out` or
`System.err` is only available _after_ the test has finished
running.

Note that this question indicates that the test itself can not determine
whether it passed or failed (i.e. it needs human intervention).  Thus, the test
uses the `manual` option.  The suggestions provided for the [`applet` action](#applet-problems) may apply.

### My test does tricky things that are not supported by `jtreg`. Can I still write a regression test?

Yes.  Most tests can be written using a series of `main`,
`clean`, `build`, `applet`, and
`compile` actions.  However, there have been a few tests that need
to do things like run a specific application or access specific environment
variables.  The `shell` action allows a user to invoke a Bourne
shell-script which can run arbitrary commands, including running
`java` and `javac`.

**Warning!** All tests, including shell-script tests, may be run on
multiple platforms including Linux, Solaris, Windows and Mac OS X. The
shell-script should be written to with this in mind.  The following code
fragment may be useful in defining various platform-dependent variables.

    OS=`uname -s`
    case "$OS" in
        SunOS | Linux | *BSD | Darwin )
            NULL=/dev/null
            PATHSEP=":"
            FILESEP="/"
            TMP=/tmp
            ;;
        CYGWIN* )
            NULL=/dev/null
            PATHSEP=";"
            FILESEP="/"
            TMP=/tmp
            ;;
        Windows* )
            NULL=NUL
            PATHSEP=";"
            FILESEP="\\"
            TMP=$TEMP
            ;;
        * )
            echo "Unrecognized system!"
            exit 1;
            ;;
    esac

If the `shell` action still does not provide the flexibility
needed to write the regression test, then use the `ignore` action.
It is also advisable to include a comment with sufficient detail to allow a
person to run the test and verify its behavior.

### What happens if my test returns when there are still threads running?

The harness runs the `main` action's `main` method in
its own thread group.  The thread group will be destroyed by the harness when the
`main` method returns.  It is the responsibility of the test to
return only after the appropriate tasks have been completed by its subsidiary
threads.

### If my bug hasn't been fixed, and the test is run, the JVM crashes.  How do I make sure that the test doesn't cause the harness to crash?

If the symptom of a bug is a JVM crash, then the test's description
should include the `othervm` option. This will allow the harness to
continue running any subsequent tests, write its reports, and exit normally.

### The JavaTest harness is running into problems running the test because of issues with the JDK I'm trying to test. What can I do?

When the harness is used to run tests, two possibly different versions of
the JDK are used: the JDK version used to run the harness and the JDK version used
to run the test(s) themselves.

To run the harness with one version of the JDK and the tests with another, use
the `-othervm` option in conjunction with the `-testjdk`
option.  The `-testjdk` option will specify the version of the JDK
to run the tests.  The environment variables `JT_JAVA` or
`JAVA_HOME` will specify the version of the JDK for the harness.

### My test requires that I install my own security manager, but it appears that the JavaTest harness has already installed one. What do I do?

The harness normally installs its own rather permissive security manager in
self-defense to prevent tests from interfering with each other.  The harness'
security manager is designed to prevent changes to the JVM's state that would
impact other tests. Most tests will not find the standard harness security
manager a hindrance.

A test which must install its own security manager will always need to run
in its own separate JVM. To do this, add the `othervm` option to the
`main` action in the test description.

### Can I automate running regtests or can I run the tests on a regular basis?

Yes.  If you are using a UNIX system, `man crontab` is your
friend.  Other platforms have similar facilities (often third party) for
automated command scheduling.

### I run all (or a huge part) of the regression test suite as part of a cron job or other nightly process.  I'd like to generate my own reports or I'd like to send myself e-mail whenever a test fails.  Do I have to parse the verbose output or the `.jtr` file?

No. The harness supports an observer interface.  APIs exist to query test
results at the conclusion of the test's execution.  A user can write their own
observer to record and act on information as desired.

--------

## Tag Problems

### How do I decide whether my test should use the `compile` action or the `build` action?

The `build` action will compile the specified class only if
the classfile doesn't exist or is older than its source.  The
`compile` action _always_ invokes the compiler.  Typically,
the `compile` action is used only to test the compiler, while the
`build` action is used for tests that make use of multiple sources
or for API tests.

### When do I need to specify the `build` action?

Each `main` and `applet` action contains an
implied `build` action.  The harness will build the class specified by
the `main` or `applet` actions as needed without any
prompting.  If the test requires additional class(es), every additional class
must be associated with an explicit `build` action.

### How do I decide whether my applet test should use the `main` action or the `applet` action?

Ultimately, that decision is left to the person writing the test;
however, the following should be considered.

Tests which use the `applet` action are <i>not</i>
necessarily restricted to tests which must run in a browser. Any
Swing/AWT code which can be written such that it derives from
`java.applet.Applet` or `javax.swing.JApplet` is
a potential applet test.

For tests which test graphics functionality, there are three major
advantages to selecting the `applet` action over the
`main` action: expanded `manual` support leading to less
duplicated code per test, thread synchronization, and cleanup.

Frequently, tests which test graphics functionality need some sort of user
interaction to determine whether the test behaves as expected. The
`applet` action takes care of providing a user interface which
contains instructions for the user and the appropriate interface to indicate
`pass`, `fail`, or `done` as indicated by the
`manual` option.  User instructions are taken from the
`.html` file referenced in the `applet` action.  Each
`main` action which tests graphics functionality must implement
their own version of this interface.  This path leads to more code needed per
test and less consistency across tests in the test suite.

A `main` action test is deemed to be completed when the
`main` method returns. A test which requires multiple threads must
take care not to allow the main method to return before those other threads
have completed. The `applet` action handles basic AWT thread
synchronization.

Finally, the `applet` action handles test cleanup.  If a test can
not or does not dispose top-level windows or any AWT threads, they will be
eliminated by the harness after the test completes.

### I put in an `ignore` tag into my test description but my test wasn't ignored.
 The `ignore` tag should be used for tests that are too
complex for the currently defined set of tags or for tests that should be
temporarily ignored.  The `ignore` tag instructs the harness to ignore
that and any <i>subsequent</i> tags.  Check the location of the
`ignore` tag.

### Can I use the `@author`, `@run`, etc. tags in other files?

Yes. The tags may be used for documentation purposes in any file. Only
those comments whose leading tag is `@test` is considered a test
description

--------

## Applet Problems

### My `/manual` test sends events to `System.out/System.err` so that the user can determine whether the test behaved properly.  How do I write my test if I can't see these  output streams?

The test code should be written to determine whether a test has passed or
failed based on events generated during a given time-frame. Use the
`/manual=done` option of the `applet` action to set the
time frame.  If the user has not generated the expected event before the
`done` button has been pressed, the test should detect this in the
`destroy` method and throw an exception.

While this approach takes potentially more time to implement, it avoids user
error which may occur in checking the event.  This scheme also avoids string
comparison of events. (A much safer way to determine whether the expected event
has been received is to check the event type, coordinates, modifiers, etc.)

**Warning!** The AWT event thread does not propagate exceptions!  It is
recommended that all exceptions indicating failure of the test be thrown from
one of the methods called by the harness.  (i.e. `init()`,
`start()`, `stop()`, `destroy()`)

The following simple `applet` test illustrates the recommended
behavior.

Basic `.html` test description file.

    <html>
        <body>
            <!--
                @test
                @bug 2997924
                @summary Sample test that verifies an event
                @run applet/manual=done SampleTest.html
            -->
            <applet code=SampleTest width=200 height=50></applet>
            Select the "pick me" check box.
        </body>
    </html>

The sample test code.

    import java.applet.Applet;
    import java.awt.Checkbox;
    import java.awt.FlowLayout;
    import java.awt.Panel;
    import java.awt.event.ItemEvent;
    import java.awt.event.ItemListener;

    // WARNING! The AWT event thread does not propagate exceptions!
    // It is recommended that all exceptions indicating failure
    // of the test be thrown from one of the methods called by the harness.
    // (i.e. init(), start(), stop(), destroy())

    public class SampleTest extends Applet {
        public void init() {
            setLayout(new FlowLayout());
            add(new TestPanel(this));
        }

        public void destroy() {
            if (myEvent == null)
                throw new RuntimeException("no events");
            else {
                Checkbox cb = (Checkbox)(myEvent.getItemSelectable());
                if (!(cb.getLabel().equals("pick me!") &amp;&amp; cb.getState()))
                    throw new RuntimeException("unexpected last event");
            }
        }

        class TestPanel extends Panel {
            Checkbox pickMe, notMe;
            Listener listener = new Listener();
            Applet applet;

            public TestPanel(Applet myApplet) {
                applet = myApplet;
                pickMe = new Checkbox("pick me!");
                notMe  = new Checkbox("not me");

                pickMe.addItemListener(listener);
                notMe.addItemListener(listener);

                add(pickMe);
                add(notMe);
            }

            class Listener implements ItemListener {
                // Don't throw an exception here.  The awt event thread
                // will NOT propagate your exception!
                public void itemStateChanged(ItemEvent event) {
                    System.out.println("event: " + event);
                    myEvent = event;
                }
            }
        }

        private ItemEvent myEvent;
    }

### I threw an exception, the output was sent to `System.err`, but my test still passed.  What happened?

Verify that the exception was not thrown by the event thread.  The event
thread does not propagate exceptions.  Furthermore, the event thread is in a
separate thread group and the harness cannot catch exceptions thrown from there.
It is _strongly_ recommended that all exceptions indicating failure of
the test be thrown from one of the methods called by the harness.
(i.e. `init()`, `start()`, `stop()`,
`destroy()`)

### My `applet` action test didn't run my `main` method!

`applet` action tests do not call `main`. A test
which uses the `applet` action is run by invoking its
`init`, `start`, and `setVisible(true)`
methods. Depending on the value of `manual`, the harness will pause
either for a few seconds or until the user clicks on the `pass`,
`fail`, or `done` buttons. Finally, the harness will invoke
the `stop` and `destroy` methods.

The `main` method of an `applet` action will only be
used if the test was run directly, outside the harness.

### If I have an applet test, do I put the test description in the `.html` file or the `.java` file?

It doesn't matter. When `jtreg` is run on a test suite or
directory, the test description will be found regardless of the file
particulars.  When running a single test, `jtreg` must be invoked on
the file which contains the test description.

### For my `/manual` tests, how do I provide the user instructions to run the test?

User instructions should be provided in the applet's HTML file.  The
uninterpreted HTML file will be displayed by the `applet` action in
a TextArea labelled `html file instructions:`.

### For `/manual` tests, how is the initial size of the running applet determined?

The size of the applet is statically determined by the
`height` and `width` attributes provided to the HTML
`applet` tag.  The applet interface provides a way to dynamically
change the size of the applet by setting the `applet size:` to
"`variable`".

--------

## Deciphering Common Harness Errors

### `Failed. Unexpected exit from test`

**Answer:** The test has completed in an unexpected manner.
This could be caused by some sort of fault (e.g. a segmentation fault)
or because the harness has detected a call to `System.exit`
from the test.

Tests are not allowed to call `System.exit` because the
test must have the ability to run in the same JVM as the harness.
Calling `System.exit` while the test is running in this
manner would cause the harness itself to exit!  Instead of calling
`System.exit()`, throw an exception.

Be warned that the AWT event thread does not propagate exceptions,
so if the test was exiting from the event thread, it is not sufficient
to simply throw an exception.  The test must set some variable which
can be used to throw an exception from one of the methods called by
the harness. (i.e. `init()`, `start()`,
`stop()`, or `destroy()`)

### `Error. Can't find 'main' method`

**More symptoms**: In `System.err`, you get a stack trace
for an `java.lang.NoSuchMethodException` and some harness messages.

    java.lang.NoSuchMethodException
     at java.lang.Class.getMethod(Class.java)
     at com.sun.javatest.regtest.MainWrapper$MainThread.run(MainWrapper.java:89)
     at java.lang.Thread.run(Thread.java)

    JavaTest Message: main() method must be in a public class named
    JavaTest Message: ClassNotPublic in file ClassNotPublic.java

**Answer**: The class defining the test must be declared
`public` and the class name must be the basename of the
`.java` file; otherwise the harness will not be able to use reflection
to invoke the test.

### `Error. Parse Exception: No class provided for 'main'`

**Answer**: An `@run main` tag was provided
without the required class name.  Either provide the name of the class
or remove the line entirely if appropriate.

The line may be removed without impacting the test if all the following
criteria are met:

* The file containing the test description has the `.java` extension.
* This is the only `@run` tag in the test description.
* No options to `main` are required

In removing the line, we take advantage of the default action for `.java` files.

### `Error. Parse Exception: 'applet' requires exactly one file argument`

**Answer**: The applet action requires a single argument which should
be the name of the `.html` file which contains (at minimum) the HTML
`applet` tag.

### `Error. Parse Exception: 'archive' not supported in file:` &#8230;

**More Symptoms**: The test is an `applet` action test.
The HTML `applet` tag includes the `archive` attribute.

**Answer**: The regression extensions to the harness do not support the
`archive` attribute.

### `test results: no tests selected`

**More Symptoms**: At a terminal window, you get:

    test results: no tests selected
    Report written to /home/iag/work/doc/JTreport/report.html
    Results written to /home/iag/work/doc/JTwork

**Answer**: No test descriptions were found by
`jtreg` in the file or directory specified.  If the
`-automatic` option to `jtreg` was provided,
then there were no tests which did not include the
`/manual` tag option.

Verify that the first tag of each test description is `@test`.

### `Test does not have unique name within work directory`

**More Symptoms**: At a terminal window, you get:

    Error:
     -- Test does not have unique name within work directory
     -- Test name: Duplicate
     -- Used by test: Duplicate.java
     -- And by test:  Duplicate.html
     -- Overwriting results of first test

A single directory contains more than one file with the same basename.

**Answer**: The two files contain descriptions of tests and the harness is
unable to determine a unique [`.jtr`](#jtr-file) filename so
the harness will overwrite the results of the first test.  It is possible to have
a single directory with more than one file with the same basename; however,
only one of those files may have a test description (`@test` is the
first token of the comment).

If both files contain identical test descriptions, select one file to
contain the primary test description. Disable the other test description by
either removal of the entire comment or simply the `@test` tag.

If the files contain unique test descriptions, one of the basefile names must
be changed.

### `Error. JUnit not available`

To run JUnit tests within jtreg, you must have a copy of junit.jar
available. To do this, you should do one of the following:

* Put junit.jar on the classpath used to run jtreg.
* You can specify the location by setting the system property `junit.jar`
* Install a copy in the *jtreg*`/lib` directory if it is not already present.

If you do not have a copy of junit.jar on your system, you can download
it from the [JUnit home page](http://junit.org/).

_Note:_ recent builds of jtreg automatically include a copy of JUnit to run tests.

### `JavaTest Message: Problem cleaning up the following threads:`

**More symptoms**: After the message, jtreg lists the stacktrace for
one or more threads started by the test code.

**Answer**: When you run tests in agentVM mode, jtreg will try to
ensure that any threads started by the test have terminated, so that
they don't affect any code that might run subsequently in the same JVM.
It does this by checking for the presence of any threads in the
thread group that was created to run the main test code. If there
are any such threads, jtreg will periodically interrupt them, until
a timeout has been reached, at which point the message in question
will be reported and the test will be reported as having an error.

### Incompatible kind of JDK used to compile or run tests (...) with that used to run jtreg (...)

**Answer**:  When using Windows Subsystem for Linux (WSL) to run jtreg,
or to run shell tests within jtreg, it is possible to run tests on either
a Windows JDK or a Linux JDK. However, there is a restriction that to run
tests on a Linux JDK, you must also use a Linux JDK to run jtreg itself.
(It need not be the same instance or same version of JDK).
Likewise, to run tests on a Windows JDK, you must also use a Windows
JDK to run jtreg itself. (Again, it need not be the same instance or
the same version.)

If you see this message, you are trying to run jtreg on one kind of JDK,
and use a different kind of JDK to compile or run the tests.
