#!/bin/sh

# @test

echo TESTVMOPTS= $TESTVMOPTS

for i in $TESTVMOPTS ; do
    case $i in
    -Dfoo=* ) exit 0 ;;
    esac
done

echo "-Dfoo=* not found in TESTVMOPTS"
exit 1
