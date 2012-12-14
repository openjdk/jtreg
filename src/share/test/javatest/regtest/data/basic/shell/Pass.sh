# @test
# @summary Passed: Execution successful

# @test
# @summary Passed: Execution successful
# @run shell Pass.sh

# @test
# @summary Passed: Execution successful
# @run shell/timeout=4 Pass.sh

# @test
# @summary Failed: Execution passed unexptectedly: exit code 0
# @run shell/fail Pass.sh

# @test
# @summary Failed: Execution passed unexptectedly: exit code 0
# @run shell/fail/timeout=8 Pass.sh

# @test
# @summary Passed: Execution successful
# @run shell Pass.sh arg0 arg1 arg2 arg3

echo "I should pass"
echo "My args: " $@
