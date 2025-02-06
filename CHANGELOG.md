## [Unreleased](https://git.openjdk.org/jtreg/compare/jtreg-7.5.1+1...master)

_nothing noteworthy, yet_

## [7.5.1](https://git.openjdk.org/jtreg/compare/jtreg-7.5+1...jtreg-7.5.1+1)

* Support running individual parameterized tests and @Nested test classes [CODETOOLS-7903930](https://bugs.openjdk.org/browse/CODETOOLS-7903930)

* Remove problematic implementation for `LIBRARY.properties` support [CODETOOLS-7903940](https://bugs.openjdk.org/browse/CODETOOLS-7903940)

* Add `test.thread.factory` property to filter tests based on test thread factory value [CODETOOLS-7903931](https://bugs.openjdk.org/browse/CODETOOLS-7903931)

* Fix and improve `--verify-exclude` feature
  [CODETOOLS-7903883](https://bugs.openjdk.org/browse/CODETOOLS-7903883)
  [CODETOOLS-7903935](https://bugs.openjdk.org/browse/CODETOOLS-7903935)

## [7.5](https://git.openjdk.org/jtreg/compare/jtreg-7.4+1...jtreg-7.5+1)

* Restore `jtdiff` tool [CODETOOLS-7903760](https://bugs.openjdk.org/browse/CODETOOLS-7903760)

* Add support for `LIBRARY.properties` file in the directory specified in the `@library` tag  [CODETOOLS-7903775](https://bugs.openjdk.org/browse/CODETOOLS-7903775)

* The verbose option given at the command-line is now propagated to test agents, including the JUnit test runner.
  [CODETOOLS-7903443](https://bugs.openjdk.org/browse/CODETOOLS-7903443)
  [CODETOOLS-7903745](https://bugs.openjdk.org/browse/CODETOOLS-7903745)

* Report test duration information in JUnit and TestNG-based tests
  [CODETOOLS-7903752](https://bugs.openjdk.org/browse/CODETOOLS-7903752)
  [CODETOOLS-7903753](https://bugs.openjdk.org/browse/CODETOOLS-7903753)

* Improve message when test times out in Agent VM mode [CODETOOLS-7902346](https://bugs.openjdk.org/browse/CODETOOLS-7902346)

* Log time spent waiting to acquire exclusive access lock [CODETOOLS-7903188](https://bugs.openjdk.org/browse/CODETOOLS-7903188)

* Speed-up error reporting on hosts with slow hostname lookups [CODETOOLS-7903746](https://bugs.openjdk.org/browse/CODETOOLS-7903746)

* Updated jtreg to bundle JUnit 5.11.0 [CODETOOLS-7903821](https://bugs.openjdk.org/browse/CODETOOLS-7903821)

## [7.4](https://git.openjdk.org/jtreg/compare/jtreg-7.3.1+1...jtreg-7.4+1)

* Remove support for `jtdiff` [CODETOOLS-7903622](https://bugs.openjdk.org/browse/CODETOOLS-7903622)

* jtreg now verifies ProblemList files [CODETOOLS-7903659](https://bugs.openjdk.org/browse/CODETOOLS-7903659)

* jtreg no longer ignores VM exit code when test process reports status with "STATUS: " line [CODETOOLS-7903621](https://bugs.openjdk.org/browse/CODETOOLS-7903621)

* Use SOURCE_BUILD_EPOCH to support reproducible builds
  [CODETOOLS-7903539](https://bugs.openjdk.org/browse/CODETOOLS-7903539)

* Updated jtreg to bundle JUnit 5.10.2 [CODETOOLS-7903578](https://bugs.openjdk.org/browse/CODETOOLS-7903578)

* jtreg, when communicating with the AgentServer in agentvm mode, will now bind to loopback address.
  [CODETOOLS-7903686](https://bugs.openjdk.org/browse/CODETOOLS-7903686)

* jtreg, in certain cases, would incorrectly report a test as PASSED when the test process would exit with a non-zero exit code.
  [CODETOOLS-7903621](https://bugs.openjdk.org/browse/CODETOOLS-7903621)

## [7.3.1](https://git.openjdk.org/jtreg/compare/jtreg-7.3+1...jtreg-7.3.1+1)

* Fixed setting default environment variables on Windows
  * [CODETOOLS-7903515](https://bugs.openjdk.org/browse/CODETOOLS-7903515)

## [7.3](https://git.openjdk.org/jtreg/compare/jtreg-7.2+1...jtreg-7.3+1)

* Updated set of default environment variables set for tests on Unix-like platforms.
  * Includes `DBUS_SESSION_BUS_ADDRESS`, `WAYLAND_DISPLAY`, and `XDG-*`
    [CODETOOLS-7903400](https://bugs.openjdk.org/browse/CODETOOLS-7903400)

* Updated external dependencies.
  * Guice to 5.1.0 [CODETOOLS-7903468](https://bugs.openjdk.org/browse/CODETOOLS-7903468)
  * JCommander to 1.82 [CODETOOLS-7903469](https://bugs.openjdk.org/browse/CODETOOLS-7903469)

* Fixed output-related issues.
  * AgentServer log() does not flush [CODETOOLS-7903470](https://bugs.openjdk.org/browse/CODETOOLS-7903470)
  * System.out and System.err messages are missing in jtr file when a test times out in agentvm mode [CODETOOLS-7903441](https://bugs.openjdk.org/browse/CODETOOLS-7903441)
  * Timeout refired %s times message confusing [CODETOOLS-7902485](https://bugs.openjdk.org/browse/CODETOOLS-7902485)

* Fixed race-condition when running tests with a multi-module setup
  * [CODETOOLS-7903507](https://bugs.openjdk.org/browse/CODETOOLS-7903507)

## [7.2](https://git.openjdk.org/jtreg/compare/jtreg-7.1.1+1...jtreg-7.2+1)

* Improved support for JUnit Jupiter.
  * Update jtreg to bundle JUnit 5.9.2 [CODETOOLS-7903406](https://bugs.openjdk.org/browse/CODETOOLS-7903406)
  * Improve reporting for errors in JUnit's lifecycle methods [CODETOOLS-7903430](https://bugs.openjdk.org/browse/CODETOOLS-7903430)

* Improved support for TestNG.
  * Support `test.query` for TestNG tests [CODETOOLS-7903414](https://bugs.openjdk.org/browse/CODETOOLS-7903414)

* Generate all debugging info when compiling `jtreg` [CODETOOLS-7903393](https://bugs.openjdk.org/browse/CODETOOLS-7903393)

* RISC-V Port has been integrated into JDK mainline
  * Set `simpleArch` to `riscv64` when `os.arch` is `riscv64` [CODETOOLS-7903138](https://bugs.openjdk.org/browse/CODETOOLS-7903138)

* Fix incorrect format for version in error message [CODETOOLS-7903398](https://bugs.openjdk.org/browse/CODETOOLS-7903398)

* Add options -testThreadFactory and -testThreadFactoryPath to allow
  the use of a custom thread factory, to be used to create the thread to run a test.
  * [CODETOOLS-7903373](https://bugs.openjdk.org/browse/CODETOOLS-79033734)

## [7.1.1](https://git.openjdk.org/jtreg/compare/jtreg-7.1+1...jtreg-7.1.1+1)

* Summary reporter getter is now thread-safe.
  * [CODETOOLS-7903390](https://bugs.openjdk.org/browse/CODETOOLS-7903390)

## [7.1](https://git.openjdk.org/jtreg/compare/jtreg-7+1...jtreg-7.1+1)

* Improved support for JUnit Jupiter.
  * Avoid using TestNG mixed mode.
    [CODETOOLS-7903264](https://bugs.openjdk.org/browse/CODETOOLS-7903264)
  * Support JUnit tests in a system module.
    [CODETOOLS-7903260](https://bugs.openjdk.org/browse/CODETOOLS-7903260)
  * Support executing a single method.
    [CODETOOLS-7903267](https://bugs.openjdk.org/browse/CODETOOLS-7903267)
  * Improve per-class reporting of JUnit tests, in `.jtr` file.
    [CODETOOLS-7903324](https://bugs.openjdk.org/browse/CODETOOLS-7903324)

* Support a group of "all tests", represented by `.`.
  * [CODETOOLS-7903331](https://bugs.openjdk.org/browse/CODETOOLS-7903331)

* Improve performance when writing reports; new reporting option `-report:files`.
  * [CODETOOLS-7903323](https://bugs.openjdk.org/browse/CODETOOLS-7903323)

* Updates for building jtreg with recent JDKs.
  * [CODETOOLS-7903346](https://bugs.openjdk.org/browse/CODETOOLS-7903346)

* Improve OS detection on Mac.
  * Use `sw_vers`. [CODETOOLS-7903294](https://bugs.openjdk.org/browse/CODETOOLS-7903294)
  * Check process exit code. [CODETOOLS-7903325](https://bugs.openjdk.org/browse/CODETOOLS-7903325)

* Trace reasons to recompile extra property definition files.
  * [CODETOOLS-7903329](https://bugs.openjdk.org/browse/CODETOOLS-7903329)

* FAQ updates.
  * Time taken to run tests. [CODETOOLS-7903261](https://bugs.openjdk.org/browse/CODETOOLS-7903261)
  * Accessing TestNG and JUnit libraries. [CODETOOLS-7903244](https://bugs.openjdk.org/browse/CODETOOLS-7903244)

## [7](https://git.openjdk.org/jtreg/compare/jtreg-6.2+1...jtreg-7+1)

* Improved support for JUnit Jupiter.
  * Use JUnit Platform Launcher. [CODETOOLS-7903047](https://bugs.openjdk.org/browse/CODETOOLS-7903047)
  * Use JUnit uber-jar. [CODETOOLS-7903055](https://bugs.openjdk.org/browse/CODETOOLS-7903055)

* Support MSYS2 for building jtreg on Windows.
  * [CODETOOLS-7903206](https://bugs.openjdk.org/browse/CODETOOLS-7903206)

* `os.simpleArch` is `x64` for `linux-loongarch64`/`mips64`/`mips64el` in `@require` context.
  * [CODETOOLS-7903120](https://bugs.openjdk.org/browse/CODETOOLS-7903120)

* Log start time for every action.
  * [CODETOOLS-7903183](https://bugs.openjdk.org/browse/CODETOOLS-7903183)

* Update OS version check.
  * [CODETOOLS-7903184](https://bugs.openjdk.org/browse/CODETOOLS-7903184)

* Support invocation via ToolProvider.
  * [CODETOOLS-7903097](https://bugs.openjdk.org/browse/CODETOOLS-7903097)

* Report `os.*` system properties in `.jtr` file.
  * [CODETOOLS-7903044](https://bugs.openjdk.org/browse/CODETOOLS-7903044)

## [6.2](https://git.openjdk.org/jtreg/compare/jtreg-6.1+1...jtreg-6.2+1)

* Provide system property or option to override timeout.
  * [CODETOOLS-7903083](https://bugs.openjdk.org/browse/CODETOOLS-7903083)

* Updates for building jtreg with recent JDKs.
  * [CODETOOLS-7903073](https://bugs.openjdk.org/browse/CODETOOLS-7903073)

* Add an FAQ entry for `javatest.maxOutputSize`.
  * [CODETOOLS-7903050](https://bugs.openjdk.org/browse/CODETOOLS-7903050)

* Allow subtest ids with dashes and underscores.
  * [CODETOOLS-7903037](https://bugs.openjdk.org/browse/CODETOOLS-7903037)

* jtreg should print stdout if JVM gathering properties fails.
  * [CODETOOLS-7903030](https://bugs.openjdk.org/browse/CODETOOLS-7903030)

## [6.1](https://git.openjdk.org/jtreg/compare/jtreg-6+1...jtreg-6.1+1)

* Elapsed time of `MainAction` is including serialization wait time
  * [CODETOOLS-7902942](https://bugs.openjdk.org/browse/CODETOOLS-7902942)

* Support building jtreg with recent JDKs.
  * [CODETOOLS-7902966](https://bugs.openjdk.org/browse/CODETOOLS-7902966)
  * [CODETOOLS-7902991](https://bugs.openjdk.org/browse/CODETOOLS-7902991)

* Update/improve jcheck settings for jtreg repo.
  * [CODETOOLS-7902995](https://bugs.openjdk.org/browse/CODETOOLS-7902995)

* Introduce support for `HEADLESS` to disable tests that require a display.

* jtreg should not set a security manager for JDK 18.
  * [CODETOOLS-7902990](https://bugs.openjdk.org/browse/CODETOOLS-7902990)

## [6](https://git.openjdk.org/jtreg/compare/jtreg5.1-b01...jtreg-6+1)

* Add support for `Automatic-Module-Name` in jar files.

* Update versions of jtreg dependencies.
  * [CODETOOLS-7902791](https://bugs.openjdk.org/browse/CODETOOLS-7902791)

* User modules can be used only in othervm.
  * [CODETOOLS-7902707](https://bugs.openjdk.org/browse/CODETOOLS-7902707)

* Improve diagnostic output when failing to get version for JDK under test.
  * [CODETOOLS-7902748](https://bugs.openjdk.org/browse/CODETOOLS-7902748)

* Initial support for new-style version numbers for jtreg.

* Improve support for `@enablePreview`.
  * [CODETOOLS-7902754](https://bugs.openjdk.org/browse/CODETOOLS-7902754)

* Move details of environment variables to new appendix.

* Add FAQ reference to `doc/testing.md`.

* Add support for explicit `-retain:lastRun`.

## [5.1](https://git.openjdk.org/jtreg/compare/jtreg5.0-b01...jtreg5.1-b01)

* Update AsmTools to 7.0 b08; update JT Harness to 6.0-b11.

* Add `test.name` to properties given to test.
  * [CODETOOLS-7902671](https://bugs.openjdk.org/browse/CODETOOLS-7902671)

* Pass `test.*` to `requires.extraPropDefns` classes.
  * [CODETOOLS-7902336](https://bugs.openjdk.org/browse/CODETOOLS-7902336)

* Add mean, standard deviation to agent stats.

* Report jtreg version info to work directory.

* Report agent pool statistics.

* Improve version details for JT Harness and AsmTools.

* Log Agent Pool activity to `agent.trace` file.

* Catch output written to agent stdout (fd1) and stderr (fd2).
  * [CODETOOLS-7902657](https://bugs.openjdk.org/browse/CODETOOLS-7902657)

* Log agent activity to files in the work directory.
  * [CODETOOLS-7902656](https://bugs.openjdk.org/browse/CODETOOLS-7902656)

* Propagate client-side "id" to agent server.
  * [CODETOOLS-7902655](https://bugs.openjdk.org/browse/CODETOOLS-7902655)

* Support `@enablePreview`.
  * [CODETOOLS-7902654](https://bugs.openjdk.org/browse/CODETOOLS-7902654)

* Use https://git.openjdk.org for CODE_TOOLS_URL.
  * [CODETOOLS-7902637](https://bugs.openjdk.org/browse/CODETOOLS-7902637)

* Ignore specified lines in `@compile/fail/ref=<file>`.
  * [CODETOOLS-7902633](https://bugs.openjdk.org/browse/CODETOOLS-7902633)

* Validate test group names.
  * [CODETOOLS-7902606](https://bugs.openjdk.org/browse/CODETOOLS-7902606)

## [5](https://git.openjdk.org/jtreg/compare/jtreg4.2-b16...jtreg5.0-b01)

* Improve Cygwin detection by relaxing constraints on expected installation directory.

* Incorrect handling of paths in smart action args for Windows.
  * [CODETOOLS-7902571](https://bugs.openjdk.org/browse/CODETOOLS-7902571)

* Introduce `test.file`.
  * [CODETOOLS-7902545](https://bugs.openjdk.org/browse/CODETOOLS-7902545)
