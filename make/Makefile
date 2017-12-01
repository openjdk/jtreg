#
# Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
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

TOPDIR = ..

include Defs.gmk
include Platform.gmk

default: build

all: build test

#----------------------------------------------------------------------

include jtdiff.gmk
include jtreg.gmk
include $(TOPDIR)/test/*.gmk
include $(TOPDIR)/test/*/*.gmk
include Rules.gmk

build: $(BUILDFILES)

test: $(INITIAL_TESTS) $(TESTS) $(FINAL_TESTS)

images: $(VERBOSEZIPFILES)

clean:
	$(RM) $(BUILDDIR)

.NO_PARALLEL: clean

sanity:
ifdef JDK15HOME
	@echo "JDK15HOME           = $(JDK15HOME)"
endif
ifdef JDK16HOME
	@echo "JDK16HOME           = $(JDK16HOME)"
endif
ifdef JDK17HOME
	@echo "JDK17HOME           = $(JDK17HOME)"
endif
ifdef JDK18HOME
	@echo "JDK18HOME           = $(JDK18HOME)"
endif
	@echo "JDKHOME             = $(JDKHOME)"
ifdef JAVAHELP_HOME
	@echo "JAVAHELP_HOME       = $(JAVAHELP_HOME)"
endif
	@echo "JAVATEST_HOME       = $(JAVATEST_HOME)"
ifneq ($(JTHARNESS_HOME), $(JAVATEST_HOME))
	@echo "JTHARNESS_HOME      = $(JTHARNESS_HOME)"
endif
	@echo "JTHARNESS_LICENSE   = $(JTHARNESS_LICENSE)"
	@echo "JTHARNESS_COPYRIGHT = $(JTHARNESS_COPYRIGHT)"
	@echo "JCOV_JAR            = $(JCOV_JAR)"
	@echo "JCOV_LICENSE        = $(JCOV_LICENSE)"
	@echo "ASMTOOLS_JAR        = $(ASMTOOLS_JAR)"
	@echo "ASMTOOLS_LICENSE    = $(ASMTOOLS_LICENSE)"
	@echo "ANTHOME             = $(ANTHOME)"
	@echo "ANT_JAR             = $(ANT_JAR)"
	@echo "JUNIT_JAR           = $(JUNIT_JAR)"
	@echo "JUNIT_LICENSE       = $(JUNIT_LICENSE)"
	@echo "TESTNG_HOME         = $(TESTNG_HOME)"
	@echo "TESTNG_JAR          = $(TESTNG_JAR)"
	@echo "TESTNG_LICENSE      = $(TESTNG_LICENSE)"
	@echo "JCOMMANDER_JAR      = $(JCOMMANDER_JAR)"

#----------------------------------------------------------------------


.PHONY: default all build test images clean sanity

