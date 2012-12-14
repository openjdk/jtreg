#!/bin/sh

#
# Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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

# This is a script to submit a job to the JDK team's JPRT build-and-test
# system.

mkdir -p build/jprt.submit/timeouts

rsync -az src/share/test/javatest/regtest/data/timeouts/ build/jprt.submit/timeouts/
rsync -az build/images/jtreg/                            build/jprt.submit/timeouts/test/jtreg/

/java/svc/jprt/dist/bin/jprt submit \
        -listtargets \
        -testenv JT_HOME=jtreg \
        -testenv JTREG_HOME=jtreg \
        -testenv JPRT_JTREG_HOME=jtreg \
        -stree build/jprt.submit/timeouts

