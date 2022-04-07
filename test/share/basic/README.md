# Tests in `test/share/basic`

The tests in this test suite are designed to exercise the basic abilities of `jtreg` to
find, execute, and report on a full variety of tests. The tests also contain
a variety of errors, to help verify they are correctly handled by `jtreg`.

Note that the tests in this test suite have atypical and very stylized names and titles.
The title of each test in the test suite is the expected text of the status message
when the test executes as expected. The tests are grouped according to the primary
action in the test, and the filename is a short description of the primary
characteristic of the test.  The underlying intent is that when tests do _not_
execute as expected, a review of the generated report files will help identify
any unexpected results, such as a test named `Pass.java` in the list of tests that failed,
or a test named `Fail.java` in the list of tests that passed.

If you add new tests into this collection, you will also need to update
the code in the method `setExpectedTestStats` in `test/basic/Basic.java`.
For each new action that you add, you must update the count of expected number
of such actions, and the count for the expected outcome.

This test suite is used in the following tests

* `test/basic/Basic.gmk`
        -- tests the ability to find and execute tests, in both `agentvm` and `othervm` modes

* `test/basic/ReportOnlyTest.gmk`
        -- tests the ability to report on previously executed tests

* `test/statsTests/StatsTests.gmk`
        -- tests the ability to format the statistics reported at the end of a run

If you add new tests into this collection you will probably have to update the
makefile rules for the first two, and you may have to update the makefile rules
for the third, depending on what kind of new test you add.