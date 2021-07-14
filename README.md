# Building The Regression Test Harness for the OpenJDK platform: jtreg

(This information is also available at <http://openjdk.java.net/jtreg/build.html>)

jtreg depends on a number of external components:
    JT Harness, TestNG, JUnit, AsmTools, Ant, and JCov.

The fundamental way to build jtreg is with GNU make, specifying where to find
those external components, but a script is also available that will download
appropriate copies of those components before building jtreg.

## Building jtreg with the build.sh script

This is the recommended way to build jtreg, for those that want a simple,
basic way to build jtreg.

*Note:* The _build.sh_ script supersedes the earlier _build-all.sh_ script.

The script is intended to be run in a Unix-like shell, such as `bash` on Linux or
Mac OS X, or with Cygwin or WSL on Windows. At a minimum, you must either set
the `JAVA_HOME` environment variable or specify the location of the JDK to be
used to build jtreg with the `--jdk` command-line option. It must be JDK 8 or later.

    % cd jtreg-root-directory
    % sh make/build.sh --jdk JDK-directory

If your shell is not compatible with `bash`, you may need to invoke `bash` explicitly:

    % bash make/build.sh --jdk JDK-directory

The script will create a build sub-directory, download and build dependencies,
and finally build jtreg itself. The resulting image will be in
_build/images/jtreg_.

If you have access to the public Internet, no environment variables need to be
specified to get a standard build of jtreg. However, you can set environment
variables used to define the location of dependencies to be downloaded.
These are documented in _make/build.sh_ and normally  specified in
_make/build-support/version-numbers_ and _make/build-support/*/version-numbers_.

## Building jtreg with GNU Make

The Makefile is in _make/Makefile_, and creates deliverables in the _build/_
directory, similar to OpenJDK. By default, the build file just builds an image
for jtreg. You can build jtreg from a Unix-like command shell with the following
commands:

    % cd <jtreg-root-directory>
    % make -C make

## Dependencies

jtreg has a number of build dependencies. These can be set with values on the
make command line or with values in _make/Defs.gmk_. You can also include the
appropriate license files in the jtreg image, by setting the appropriate make
variables.

* JDK 1.8 (or better):
    Set `JDKHOME` to the JDK or equivalent used to build jtreg. It must be
    equivalent to JDK 8 or later.

* JT Harness:
    See <https://wiki.openjdk.java.net/display/CodeTools/JT+Harness>.
    Set `JTHARNESS_HOME` to the installed copy of the version of JT Harness to be
    used. It should be version 6.0-b14 or better.

* Ant:
    See <http://ant.apache.org/>. Set `ANTHOME` to an installed copy of Ant. It
    should be version 1.10.x. or better.

* AsmTools:
    See <https://wiki.openjdk.java.net/display/CodeTools/asmtools>.
    Set `ASMTOOLS_HOME` to the installed copy of the version of AsmTools to be
    used. It should be version 7.0-b09 or better.

    Note: Do not confuse this component with ASM bytecode engineering library
    available at <http://asm.ow2.org/>

* JUnit:
    See <http://junit.org/>. The recommended version is currently JUnit 4.13.2.
    JUnit has a dependency on Hamcrest. The recommended version is currently 2.2.

* TestNG:
    See <http://testng.org/>. The recommended version is currently 7.3.0.
    (Do not use 7.4.0 to run OpenJDK tests.)
    TestNG has dependencies on JCommander and Google Guice.
    The recommended version of JCommander is 1.78.
    The recommended version of Google Guice is 4.2.3.

The following dependencies are optional.

* JCov:
    See <https://wiki.openjdk.java.net/display/CodeTools/jcov>.
    Set `JCOV_HOME` to the installed copy of the version of JCov to be used.
    It should be version 3.0-b07 or better.

* JDK 1.5:
    This is used when running some of the tests. Set `JDK5HOME` to run these
    tests. It need not be set if you are just building jtreg.

* JDK 1.6:
    This is used when running some of the tests. Set `JDK6HOME` to run these
    tests. It need not be set if you are just building jtreg.

* JDK 1.7:
    This is used when running some of the tests. Set `JDK7HOME` to run these
    tests. It need not be set if you are just building jtreg.

* JDK 1.8:
    This is used when running some of the tests. Set `JDK8HOME` to run these
    tests. It need not be set if you are just building jtreg.

* JDK 9:
    This is used when running some of the tests. Set `JDK9HOME` to run these
    tests. It need not be set if you are just building jtreg.

* JDK 14:
    This is used when running some of the tests. Set `JDK14HOME` to run these
    tests. It need not be set if you are just building jtreg.

* JDK 18:
  This is used when running some of the tests. Set `JDK18HOME` to run these
  tests. It need not be set if you are just building jtreg.
  
The recommended versions are also defined in `make/build-support/version-numbers`.

## Running jtreg Self-Tests

The tests can be invoked with individual make targets, or collectively via the
`test` target.

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

## Building jtreg with Ant

It is possible to build jtreg with Ant, but this is primarily intended as a
convenience while working on the jtreg source code. If you are building jtreg
to run OpenJDK tests, it is recommended that you build jtreg using the Makefile,
perhaps via the _build.sh_ wrapper script.

The build file is in _make/build.xml_; it creates intermediate files in the _build/_
directory and deliverables in the dist/ directory. By default, the build file
just builds jtreg, but does not run any tests. You can build jtreg from a
Unix-like command shell with the following commands:

    % cd jtreg-root-directory
    % ant -f make/build.xml

You can also use this build file when creating a NetBeans free form project with
an existing build file.

### Dependencies

Some of the tasks that are used are listed as _Optional Tasks_ in the Ant
manual. You may need to make sure that these tasks are available for use by Ant.
For example, on Ubuntu Linux these tasks are in the `ant-optional` package.

### Running Tests

Some of the tests can be invoked with individual targets beginning
`-jtreg-test-`, or collectively via the `jtreg-test` target. (The use of
`jtreg-test` rather than `test` is to protect against interactions with JUnit in
older versions of NetBeans.)

## Using the IntelliJ IDE

The jtreg repo also contains a plugin for the IntelliJ IDE.
This is a convenience plugin which adds jtreg capabilities to the IntelliJ IDE.
With this plugin, OpenJDK developers can write, run, and debug jtreg tests
without leaving their IDE environment.  For more details, see the file
_plugins/idea/README.md_ in this repo.
