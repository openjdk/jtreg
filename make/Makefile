#
# Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

test: checkJavaOSVersion $(BUILDTESTDIR) $(INITIAL_TESTS) $(TESTS) $(FINAL_TESTS)
	@count=`echo $+ | wc -w` ; \
	echo "All ($${count}) selected tests completed successfully"
ifndef JDK8HOME
	@echo "Warning: JDK8HOME not set; some tests may not have been executed"
endif
ifndef JDK9HOME
	@echo "Warning: JDK9HOME not set; some tests may not have been executed"
endif
ifndef JDK10HOME
	@echo "Warning: JDK10HOME not set; some tests may not have been executed"
endif
ifndef JDK14HOME
	@echo "Warning: JDK14HOME not set; some tests may not have been executed"
endif
ifndef JDK18HOME
	@echo "Warning: JDK18HOME not set; some tests may not have been executed"
endif
ifdef HEADLESS
	@echo "Warning: HEADLESS is set; some tests may not have been executed"
endif
ifeq ($(OS_NAME), windows)
	@echo "Warning: OS is Windows; some tests may not have been executed"
endif

quick-test: checkJavaOSVersion $(BUILDTESTDIR) $(INITIAL_TESTS)
	count=`echo $+ | wc -w` ; \
	echo "All ($${count}) selected tests completed successfully"

images: $(VERBOSEZIPFILES)

new-images: $(NEWVERBOSEZIPFILES)

clean:
	$(RM) $(BUILDDIR)

clean-except-deps:
	for i in $(BUILDDIR)/* ; do \
	    case $$i in \
		*/deps ) ;; \
		* ) $(RM) $$i ;; \
	    esac \
	done

clean-deps:
	$(RM) $(BUILDDIR)/deps

clean-tests:
	$(RM) $(BUILDTESTDIR)

.NO_PARALLEL: clean clean-except-deps clean-deps clean-tests

sanity:
ifdef JDK8HOME
	@echo "JDK8HOME            = $(JDK8HOME)"
endif
ifdef JDK9HOME
	@echo "JDK9HOME            = $(JDK9HOME)"
endif
ifdef JDK10HOME
	@echo "JDK10HOME           = $(JDK10HOME)"
endif
ifdef JDK14HOME
	@echo "JDK14HOME           = $(JDK14HOME)"
endif
ifdef JDK18HOME
	@echo "JDK18HOME           = $(JDK18HOME)"
endif
	@echo "JDKHOME             = $(JDKHOME)"
	@echo "JAVATEST_HOME       = $(JAVATEST_HOME)"
ifneq ($(JTHARNESS_HOME), $(JAVATEST_HOME))
	@echo "JTHARNESS_HOME      = $(JTHARNESS_HOME)"
endif
	@echo "JTHARNESS_NOTICES   = $(JTHARNESS_NOTICES)"
	@echo "ASMTOOLS_JAR        = $(ASMTOOLS_JAR)"
	@echo "ASMTOOLS_NOTICES    = $(ASMTOOLS_NOTICES)"
	@echo "JUNIT_JARS          = $(JUNIT_JARS)"
	@echo "JUNIT_NOTICES       = $(JUNIT_NOTICES)"
	@echo "TESTNG_JARS         = $(TESTNG_JARS)"
	@echo "TESTNG_NOTICES      = $(TESTNG_NOTICES)"
	@echo "BUILD_VERSION       = $(BUILD_VERSION)"
	@echo "BUILD_MILESTONE     = $(BUILD_MILESTONE)"
	@echo "BUILD_NUMBER        = $(BUILD_NUMBER)"

#----------------------------------------------------------------------

$(BUILDTESTDIR):
	$(MKDIR) -p $@

#----------------------------------------------------------------------

checkJavaOSVersion:
ifeq ($(OS_NAME), macosx)
	$(JDKJAVA) CheckJavaOSVersion.java $(OS_VERSION)
endif

#----------------------------------------------------------------------


.PHONY: default all build test images new-images clean sanity

