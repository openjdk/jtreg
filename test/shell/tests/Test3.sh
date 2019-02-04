#!/bin/sh
#
# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

# @test

# Shell tests: option 3: use case statement in shared script

# Chicken and egg here: can't use ${FS} before it is set by env.sh
# But, in general, it is OK to use "/" on all platforms anyway.
source "$TESTROOT"/libenv/env.sh

echo "Environment variables in shell"
env | sort

mkdir lib1-classes lib2-classes classes

# compile library classes
"$TESTJAVA"${FS}bin${FS}javac$EXE_SUFFIX \
        -d lib1-classes \
        "$TESTSRC"${FS}..${FS}lib1${FS}p1${FS}C1.java

"$TESTJAVA"${FS}bin${FS}javac$EXE_SUFFIX \
        -d lib2-classes \
        "$TESTSRC"${FS}..${FS}lib2${FS}p2${FS}C2.java

# compile test class
"$TESTJAVA"${FS}bin${FS}javac$EXE_SUFFIX \
        -d classes \
        -cp lib1-classes${PS}lib2-classes \
        -sourcepath "$TESTSRC" \
        "$TESTSRC"${FS}Test234.java

# run test class
"$TESTJAVA"${FS}bin${FS}java$EXE_SUFFIX \
        -cp classes${PS}lib1-classes${PS}lib2-classes \
        Test234

