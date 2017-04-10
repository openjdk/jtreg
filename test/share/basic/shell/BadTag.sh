# @test
# @summary Error: Parse Exception: No script name provided for `shell'
# @run shell

# @test
# @summary Error: Parse Exception: Bad option for shell: bad_opt
# @run shell/bad_opt BadTag.sh

# @test
# @summary Error: Parse Exception: Bad integer specification: bad_int
# @run shell/timeout=bad_int BadTag.sh

echo "hi!"
