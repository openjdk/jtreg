#!/bin/sh
# @test

env

if [ $TESTJAVACOPTS != -XDfailcomplete=java.lang.String ]; then
    exit 1
fi
