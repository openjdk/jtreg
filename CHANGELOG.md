

## [Unreleased](https://git.openjdk.org/jtreg/compare/jtreg-7+1...master)

* Improved support for JUnit Jupiter.
  * Avoid using TestNG mixed mode. 
    2bddb406f907072b6951b8b02a0c1a7cf640d7bf [7903264](https://bugs.openjdk.org/browse/CODETOOLS-7903264)
  * Support JUnit tests in a system module. 
    ccb05af578e522f2c331e4a20d7537e9a91a898d [7903260](https://bugs.openjdk.org/browse/CODETOOLS-7903260)
  * Support executing a single method. 
    7c7737d06b35980f7ea7c1f058999f350e19e717 [7903267](https://bugs.openjdk.org/browse/CODETOOLS-7903267)

* Support a group of "all tests", represented by `.`.
  * f8c3879bb0398b7e242d5b1997fbaaafbf37c5ae [7903331](https://bugs.openjdk.org/browse/CODETOOLS-7903331)

* Improve performance when writing reports; new reporting option `-report:files`.
  * de2c21676fb20317e153005c44d645b1cc76f2c2 [7903323](https://bugs.openjdk.org/browse/CODETOOLS-7903323)

* Updates for building jtreg with recent JDKs.
  * f4f1a41e4add3187b2c73f95c06591ae2de475a4 [7903346](https://bugs.openjdk.org/browse/CODETOOLS-7903346)

* Improve OS detection on Mac.
  * Use `sw_vers`. 1a03cc237fefc205a3703f8e035ebb72e75221c6 [7903294](https://bugs.openjdk.org/browse/CODETOOLS-7903294)
  * Check process exit code. aeb552e6df73e039e20de59b3ec847f36ab6e202 [7903325](https://bugs.openjdk.org/browse/CODETOOLS-7903325)

* Trace reasons to recompile extra property definition files.
  * 66efe4bb6a1da13e9e4e6e15bd8859164265efad [7903329](https://bugs.openjdk.org/browse/CODETOOLS-7903329)

* FAQ updates.
  * Time taken to run tests. 5e988da8622d1f244d449c25905badfdb80715c9 [7903261](https://bugs.openjdk.org/browse/CODETOOLS-7903261)
  * Accessing TestNG and JUnit libraries. 22ac452062be5ddd0bc8ca07c2c472b524d8bd6e [7903244](https://bugs.openjdk.org/browse/CODETOOLS-7903244)


## [7+1](https://git.openjdk.org/jtreg/compare/jtreg-6.2+1...jtreg-7+1)

* Improved support for JUnit Jupiter.
  * Use JUnit Platform Launcher. 7b94214c124da33e208b35571b52b39a03e5bc0a [7903047](https://bugs.openjdk.org/browse/CODETOOLS-7903047)
  * Use JUnit uber-jar. e7a8de66693c3bca74e5b581ab8c460f3ddff580 [7903055](https://bugs.openjdk.org/browse/CODETOOLS-7903055)

* Support MSYS2 for building jtreg on Windows.
  * d60381de853f9e17d358b6a5c745be595f71e5c9 [7903206](https://bugs.openjdk.org/browse/CODETOOLS-7903206)

* os.simpleArch is x64 for linux-loongarch64/mips64/mips64el in @require context.
  * d8422babd6e90992c81af66d1a96c8a5c9bee521 [7903120](https://bugs.openjdk.org/browse/CODETOOLS-7903120)

* Log start time for every action.
  * 7767297ca1946d2752cdb8a92b9659f8bae77d81 [7903183](https://bugs.openjdk.org/browse/CODETOOLS-7903183)

* Update OS version check.
  * 9c4029fea0e0dde22814542f0bc35ae17ac3f590 [7903184](https://bugs.openjdk.org/browse/CODETOOLS-7903184)

* Support invocation via ToolProvider.
  * 1433968b29d65ca7ce340ee11ae2b874132c0527 [7903097](https://bugs.openjdk.org/browse/CODETOOLS-7903097)

* Report `os.*` system properties in .jtr file.
  * ed747d4d9570cf9972211687dad1db720cd33bd5 [7903044](https://bugs.openjdk.org/browse/CODETOOLS-7903044)


## [6.2+1](https://git.openjdk.org/jtreg/compare/jtreg-6.1+1...jtreg-6.2+1)

* Provide system property or option to override timeout.
  * 17bbd21a805ff0ee3a7b197613a899bd9b4b45c5 [7903083](https://bugs.openjdk.org/browse/CODETOOLS-7903083)

* Updates for building jtreg with recent JDKs.
  * b2594d9cceb7cb52bea0caefb2b2acdedfdde6aa [7903073](https://bugs.openjdk.org/browse/CODETOOLS-7903073)

* Add an FAQ entry for `javatest.maxOutputSize`.
  * 65f5852f1be969c86401777c3a1ee908b5b12014 [7903050](https://bugs.openjdk.org/browse/CODETOOLS-7903050)

* Allow Subtest ids with dashes and underscores
  * 9e9c9b14a2318bd91b11c6699075c746b873fa6d [7903037](https://bugs.openjdk.org/browse/CODETOOLS-7903037)

* jtreg should print stdout if JVM gathering properties fails
  * 832f368ee06cdd8e8d7e185a618972385c60d839 [7903030](https://bugs.openjdk.org/browse/CODETOOLS-7903030)


## [6.1+1](https://git.openjdk.org/jtreg/compare/jtreg-6+1...jtreg-6.1+1)

* Elapsed time of MainAction is including serialization wait time (#11)
  * 1360423a1cbea21bf02a138c4f5489696d469c02 [7902942](https://bugs.openjdk.org/browse/CODETOOLS-7902942)

* Support building jtreg with recent JDKs.
  * 6583a8c6b1dd1b9ac310196bccb18de8502603ee [7902966](https://bugs.openjdk.org/browse/CODETOOLS-7902966)
  * ca884e321728f1fd02365a592e294fd8096a6ae4 [7902991](https://bugs.openjdk.org/browse/CODETOOLS-7902991)

* Update/improve jcheck settings for jtreg repo.
  * 1aabb70ce3e0170ca1ed456b6a841b23b4ab0c25 [7902995](https://bugs.openjdk.org/browse/CODETOOLS-7902995)

* Introduce support for HEADLESS to disable tests that require a display
  * 6cd5bbfc7cd302b9de6e2692648f7bd2e57c224b

* jtreg should not set a security manager for JDK 18
  * 4e7aea30ec26f5d1addb7c98277d009b6c488527 [7902990](https://bugs.openjdk.org/browse/CODETOOLS-7902990)


## [6+1](https://git.openjdk.org/jtreg/compare/jtreg5.1-b01...jtreg-6+1)


* Add support for Automatic-Module-Name in jar files
  * 12c623d0f7791cf0655693d345178636825297a4
  
* Update versions of jtreg dependencies
  * 66480207378d22bd1ebf46e00256ca4889233269 [7902791](https://bugs.openjdk.org/browse/CODETOOLS-7902791)
  * b98cff115d4bd3888a2243faccf3725c42db9baa
  * 717c3d154b0bf7277a5d272512c8360889e3fed3
  * 8c63eb71d928c7394386719498b12fa328e8b9ee

* User modules can be used only in othervm.
  * 786d0f534e3fd8bd5f34173245441d970eec9ead [7902707](https://bugs.openjdk.org/browse/CODETOOLS-7902707)

* Improve diagnostic output when failing to get version for JDK under test
  * 986c02d9928fe2153accce91fd4527403c1f2d97 [7902748](https://bugs.openjdk.org/browse/CODETOOLS-7902748)

* Initial support for new-style version numbers for jtreg.
  * fa3f6c6182ad48c73aaa1dc138815d603c1d575e

* Improve support for `@enablePreview`.
  * c4ce87019f997019bccd61d536c06ca94d3823dc
  * fb5c2c42796609fddc7c1a614a83540fe7724a94 [7902754](https://bugs.openjdk.org/browse/CODETOOLS-7902754)

* Move details of environment variables to new appendix.
  * feeb047e7b5e91c2b0fd76d87b953ddf4503c641

* Add FAQ reference to doc/testing.md.
  * f592a29e015c4f523e7ac5d9a52b978cc7af5b84

* Add support for explicit -retain:lastRun.
  * 34cd4dc40fbbbd41c6634bb0009bb169cc87ba73



## [5.1-b01](https://git.openjdk.org/jtreg/compare/jtreg5.0-b01...jtreg5.1-b01)

* Update AsmTools to 7.0 b08; update JT Harness to 6.0-b11
  * 527cc92a49014deb863d9d067a9e7995a328872a

* Add test.name to properties given to test
  * 391bab8e7edca6bc1f1e9f9dbc755f885c05b915 [7902671](https://bugs.openjdk.org/browse/CODETOOLS-7902671)

* Pass test.* to requires.extraPropDefns classes
  * 37635f2daf894f43f53738537c319223f47cd145 [7902336](https://bugs.openjdk.org/browse/CODETOOLS-7902336)

* Add mean, standard deviation to agent stats
  * c40b77e1ac8527526882ac99b3f82e24540a45ac

* Report jtreg version info to work directory
  * 66a255be0bdb299f1dc1fd26556464c7fc57cdfb

* Report agent pool statistics
  * 7478bf1f8b6b6eb94c298215b38b495e405cd8bb

* Improve version details for JT Harness and AsmTools
  * 9d44cf60f718d16858325569591f2f3e205ed9e8

* Log Agent Pool activity to agent.trace file
  * 2a0dbc8944cb1c01d1c216c3979308151131d3fe

* Catch output written to agent stdout (fd1) and stderr (fd2)
  * e9b1fd3daf3c8b59714d558df312df34cdc08131 [7902657](https://bugs.openjdk.org/browse/CODETOOLS-7902657)

* Log agent activity to files in the work directory
  * 6218b1972fe7614b67cb8d5f536adf238cfb6b9a [7902656](https://bugs.openjdk.org/browse/CODETOOLS-7902656)

* Propagate client-side "id" to agent server
  * b26bcadddb25a28c784bb827fead411f94767e2b [7902655](https://bugs.openjdk.org/browse/CODETOOLS-7902655)

* Support @enablePreview
  * e34a6ab89ce2c122582532ed1ab69c5f7bd99061 [7902654](https://bugs.openjdk.org/browse/CODETOOLS-7902654)

* Use https://git.openjdk.java.net for CODE_TOOLS_URL
  * 0b4ed41c8f08abfb1ab9cbb091b5ca90079ea504 [7902637](https://bugs.openjdk.org/browse/CODETOOLS-7902637)

* Ignore specified lines in @compile/fail/ref=<file>
  * 064791921695be3cc0f5fcbea6a199032f07fc49 [7902633](https://bugs.openjdk.org/browse/CODETOOLS-7902633)

* Validate test group names
  * 0081124aabdc692c896978712ab6a3f4e5f4d9f8 [7902606](https://bugs.openjdk.org/browse/CODETOOLS-7902606)


## [5.0-b01](https://git.openjdk.org/jtreg/compare/jtreg4.2-b16...jtreg5.0-b01)

* Improve cygwin detection by relaxing constraints on expected installation directory
  * d3d322efbee33d8de3a71f802bf035b874323c5b

* Incorrect handling of paths in smart action args for Windows
  * 02b8a4997254b2b765abfa48e09e00e07b64c965 [7902571](https://bugs.openjdk.org/browse/CODETOOLS-7902571)

* Introduce test.file
  * dfbe4c73451126226dc3e983ae979df3830320b1 [7902545](https://bugs.openjdk.org/browse/CODETOOLS-7902545)


