# Building The Regression Test Harness for the OpenJDK platform: `jtreg`

(This information is also available at <http://openjdk.org/jtreg/build.html>)

`jtreg` depends on a number of external components:
    JT Harness, TestNG, JUnit, and AsmTools.

The fundamental way to build `jtreg` is with GNU make, specifying where to find
those external components, but a script is also available that will download
appropriate copies of those components before building `jtreg`.

## Building `jtreg` with the `build.sh` script

This is the recommended way to build `jtreg`, for those that want a simple,
basic way to build `jtreg`.

*Note:* The _build.sh_ script supersedes the earlier _build-all.sh_ script.

The script is intended to be run in a Unix-like shell, such as `bash` on Linux or
Mac OS X, or with Cygwin, MSYS2, or WSL on Windows. At a minimum, you must either
set the `JAVA_HOME` environment variable or specify the location of the JDK to be
used to build `jtreg` with the `--jdk` command-line option. It must be a
recent build of JDK 17 or later.

    % cd jtreg-root-directory
    % sh make/build.sh --jdk JDK-directory

If your shell is not compatible with `bash`, you may need to invoke `bash`
explicitly:

    % bash make/build.sh --jdk JDK-directory

The script will create a `build` sub-directory, download and build dependencies,
and finally build `jtreg` itself. The resulting image will be in
_build/images/jtreg_ .

If you have access to the public Internet, no environment variables need to be
specified to get a standard build of `jtreg`. However, you can set environment
variables used to define the location of dependencies to be downloaded.
These are documented in _make/build.sh_ and are normally specified in
_make/build-support/version-numbers_ and _make/build-support/*/version-numbers_ .

### Rebuilding faster with `build/make.sh`

After you have run `make/build.sh` once, if you wish to rebuild after making some
changes, you can run `build/make.sh`. It skips the steps to download and build
the dependencies, and so should be significantly faster.

## Building `jtreg` with GNU Make

If you don't want to use `build.sh` to build `jtreg`, you can invoke the
makefile directly, or by writing and using your own alternative wrapper.

The makefiles require a  number of variables to be set, identifying the parts of
all the dependencies. In general, there are two kinds of variable to be set for
each dependency: the location of any jar files that may be required to use the
component, and the location of any "legal notices" (such as license files) that
may need to be included in the `jtreg` image.

There are five dependencies that need to be made available.  The following
lists the variables that need to be set for each dependency.

1. [JT Harness] (JavaTest)
   * `JAVATEST_JAR`: a jar file containing the classes for JT Harness
   * `JTHARNESS_NOTICES`: any legal notices that may be required to use JT Harness

2. [AsmTools]
   * `ASMTOOLS_JAR`: a jar file containing the classes for AsmTools
   * `ASMTOOLS_NOTICES`: any legal notices that may be required to use AsmTools

3. [JUnit]
   * `JUNIT_JARS`: a list of one or more jar files containing the classes
     for JUnit and its dependencies: the list may be a series of jar files or
     a singleton "uber-jar"
   * `JUNIT_NOTICES`: any legal notices that be required to use JUnit

   Consult the JUnit documentation to see if there are any additional
   dependencies that may be required when running JUnit.

4. [TestNG]
   * `TESTNG_JARS`: a list of one or more jar files containing the classes
     for TestNG and its dependencies: the list may be a series of jar files or
     a singleton "uber-jar"
   * `TESTNG_NOTICES`: any legal notices that be required to use TestNG

   Consult the TestNG documentation to see if there are any additional
   dependencies that may be required when running TestNG.

In general, any jar files identified by `*_JAR` or `*_JARS` variables will be
copied to the `lib` directory in the generated image.  Any files identified by
`*_NOTICES` variables will be copied to a component-specific subdirectory
of the `legal` directory in the generated image.

[AsmTools]: https://github.com/openjdk/asmtools
[JT Harness]: https://github.com/openjdk/jtharness
[JUnit]: https://junit.org/
[TestNG]: https://testng.org/


## Running `jtreg` Self-Tests

The tests can be invoked with individual make targets, or collectively via the
`test` target. Individual make targets for self-tests are explained
[here](../test/README.md#makefiles). For example, the
[ControlTest.gmk](../test/ctrl/ControlTest.gmk) makefile has a `$(BUILDTESTDIR)/ControlTest.ok`
target which runs one of the self-tests. In order to run that individual test, 
use a command such as the following:

```shell
bash build/make.sh $(pwd)/build/test/ControlTest.ok
```

Some tests depend on specific versions of JDK being available, specified
by the following variables: `JDK8HOME`, `JDK9HOME`, `JDK14HOME`, `JDK18HOME`, `JDK25HOME`.
A test that requires any of these version of JDK will be skipped if the
variable is not set.

Some of the tests need to pop up windows while they execute. No interaction with
these windows is normally required. Since this can be a problem on a headless
server machine, and an annoyance on a personal workstation, the tests will
attempt to use VNC to create a dummy X-server for use by the tests while they
are running. Various implementations of VNC are available, such as from
<http://www.realvnc.com/>. Using VNC is optional; it is not required in order to
run the tests.

By default, VNC will be used if `vncserver` is found on your execution path, or
if VNC_HOME points to an installed copy of VNC. To explicitly disable the use of
VNC, set the VNC environment variable to one of false, off, no, or 0. Unless
explicitly disabled, the tests will check the following:

*   You must have a password set in _$HOME/.vnc/passwd_. This is the standard
    location used by the vncserver command.
*   If you set the environment variable `VNC_HOME`, it will be prepended to your
    execution path to find vncserver.
*   vncserver must be on your execution path, after `VNC_HOME` has been added,
    if set.

If the tests find any issue with using VNC, it will not be used. If VNC is used
to create a dummy X server, the server will be terminated when the test is
complete.

The logic for using VNC is encapsulated within the script _make/display.sh_.

# Contribution guidelines

Contributors are encouraged to follow code style conventions in [Java Style Guidelines](https://cr.openjdk.org/~alundblad/styleguide/index-v6.html) 
where reasonable. Existing `jtreg` command-line options have a certain style due to their
age, but new options should strive to follow [JEP 293: Guidelines for JDK Command-Line Tool Options](https://openjdk.org/jeps/293). 
For backwards compatibility, `jtreg` option names are case-insensitive.

The `jtreg` codebase is very dependent on (jtharness)[https://github.com/openjdk/jtharness]. 
The two repos should most often be viewed together. This also places constraints 
on what changes can (easily) be made in jtreg. 
