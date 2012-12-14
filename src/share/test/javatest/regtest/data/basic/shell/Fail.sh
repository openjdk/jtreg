# @test
# @summary Failed: Execution failed: exit code 1

# @test
# @summary Failed: Execution failed: exit code 1
# @run shell Fail.sh

# @test
# @summary Failed: Execution failed: exit code 1
# @run shell/timeout=3 Fail.sh

# @test
# @summary Passed: Execution failed as exptected
# @run shell/fail Fail.sh

# @test
# @summary Passed: Execution failed as expected
# @run shell/fail/timeout=9 Fail.sh

# @test
# @summary Passed: Execution failed as expected
# @run shell/fail Fail.sh arg0 arg1 arg2 arg3

echo "I should fail"
echo "My args: " $@

exit 1
