#
# Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

# This is a Makefile for JPRT testing


# Get OS/ARCH specifics
OSNAME = $(shell uname -s)
ifeq ($(OSNAME), SunOS)
  PLATFORM = solaris
  JT_PLATFORM = solaris
  ARCH = $(shell uname -p)
  ifeq ($(ARCH), i386)
    ARCH=i586
  endif
endif
ifeq ($(OSNAME), Linux)
  PLATFORM = linux
  JT_PLATFORM = linux
  ARCH = $(shell uname -m)
  ifeq ($(ARCH), i386)
    ARCH=i586
  endif
endif
ifeq ($(OSNAME), Windows_NT)
  PLATFORM = windows
  JT_PLATFORM = win32
  ifeq ($(word 1, $(PROCESSOR_IDENTIFIER)),ia64)
    ARCH=ia64
  else
    ifeq ($(word 1, $(PROCESSOR_IDENTIFIER)),AMD64)
      ARCH=x64
    else
      ifeq ($(word 1, $(PROCESSOR_IDENTIFIER)),EM64T)
        ARCH=x64
      else
        ARCH=i586
      endif
    endif
  endif
endif


# Root of this test area (important to use full paths in some places)
TEST_ROOT := $(shell pwd)

# Default bundle of all test results (passed or not) (JPRT only)
ifdef JPRT_JOB_ID
  JPRT_CLEAN = clean
  JPRT_ARCHIVE_BUNDLE = $(TEST_ROOT)/JPRT_ARCHIVE_BUNDLE.zip
endif

ifeq ($(PLATFORM), windows)
  SLASH_JAVA = J:
else
  SLASH_JAVA = /java
endif

# Default JTREG to run
ifdef JPRT_JTREG_HOME
  JTREG_HOME = $(JPRT_JTREG_HOME)
else
  JTREG_HOME = $(SLASH_JAVA)/re/jtreg/4.0/promoted/latest/binaries/jtreg
endif
JTREG = $(JTREG_HOME)/$(JT_PLATFORM)/bin/jtreg


# Default JDK for JTREG and JCK
#
# JT_JAVA is the version of java used to run jtreg/JCK. Since it is now
# standard to execute tests in sameVM mode, it should normally be set the
# same as TESTJAVA (although not necessarily so.)
#
ifdef JPRT_JAVA_HOME
  JT_JAVA = $(JPRT_JAVA_HOME)
else
  JT_JAVA = $(SLASH_JAVA)/re/jdk/1.6.0/archive/fcs/binaries/$(PLATFORM)-$(ARCH)
endif

# Default JDK to test
ifdef JPRT_IMPORT_PRODUCT_HOME
  TESTJAVA = $(JPRT_IMPORT_PRODUCT_HOME)
else
  TESTJAVA = $(SLASH_JAVA)/re/jdk/1.7.0/promoted/latest/binaries/$(PLATFORM)-$(ARCH)
endif


# Exit codes:
# jtreg, jck:   0: OK, 1: tests failed, 2: tests error; 3+: SERIOUS
FATAL_JTREG_EXIT = 3
#
# Exit -- used for final "normal" exit from "make". Redefine to "true" to avoid
# having make exit with non-zero return code.
EXIT = exit
# Function to exit shell if exit code of preceding command is greater than or equal 
# to a given level. Redefine function or preceding FATAL_*_EXIT codes as needed.
EXIT_IF_FATAL = status=$$?; if [ $$status -ge $(1) ]; then exit $$status ; fi

# Root of all test results
TEST_OUTPUT_DIR = $(TEST_ROOT)/../build/$(PLATFORM)-$(ARCH)/jtreg
ABS_TEST_OUTPUT_DIR := \
	$(shell mkdir -p $(TEST_OUTPUT_DIR); cd $(TEST_OUTPUT_DIR); pwd )

all:    init run report summary

init:
	rm -f $(TEST_OUTPUT_DIR)/summary

run:	init
	for mode in agentvm samevm othervm ; do \
	    for test in *Test.java ; do \
		echo ; echo "*** Test $$mode $$test"; echo ; \
		$(MAKE) MODE=$$mode TEST=$$test SUMMARY=$(TEST_OUTPUT_DIR)/summary jtreg-test ; \
	    done ; \
	done

report: run $(JPRT_ARCHIVE_BUNDLE)

summary: report
	if grep FAILED $(TEST_OUTPUT_DIR)/summary ; then \
	    exit 1 ; \
	fi
	
	 

JTREG_OUTPUT_DIR = $(ABS_TEST_OUTPUT_DIR)/$(MODE)/$(subst .java,,$(TEST))

JTREG_OPTIONS += -timeoutFactor:0.1

CHECK_PASS = \
    if grep '$(1).*Passed' $(JTREG_OUTPUT_DIR)/report/text/summary.txt ; then \
	echo $(1) OK ; \
    else \
	echo $(1) FAILED ; \
    fi

# Run jtreg test
#
# JTREG_HOME
#	Installed location of jtreg
# JT_JAVA
#	Version of java used to run jtreg.  Should normally be the same as TESTJAVA
# TESTJAVA
# 	Version of java to be tested.  
# JTREG_OPTIONS
#	Additional options for jtreg
# JTREG_TESTDIRS
#	Directories of tests to be run
# JTREG_OUTPUT_DIR
#	Where to write the results
# JTREG_REFERENCE
#	(Optional) reference results (e.g. work, report or summary.txt)
#
jtreg-test: check-jtreg FRC
	@rm -f -r $(JTREG_OUTPUT_DIR)/work $(JTREG_OUTPUT_DIR)/report \
	    $(JTREG_OUTPUT_DIR)/diff.html $(JTREG_OUTPUT_DIR)/status.txt
	@mkdir -p $(JTREG_OUTPUT_DIR)
	JT_JAVA=$(JT_JAVA) $(JTREG) \
	  -$(MODE) \
	  -v:fail,error \
          -r:$(JTREG_OUTPUT_DIR)/report \
          -w:$(JTREG_OUTPUT_DIR)/work \
          -jdk:$(TESTJAVA) \
          $(JAVA_ARGS:%=-vmoption:%) \
	  $(JTREG_OPTIONS) \
          AAA_Pass.java $(TEST) ZZZ_Pass.java \
	|| ( $(call EXIT_IF_FATAL,$(FATAL_JTREG_EXIT)) ; \
	    echo $$status > $(JTREG_OUTPUT_DIR)/status.txt \
	)
	if ( \
	    grep AAA_Pass.*Passed $(JTREG_OUTPUT_DIR)/report/text/summary.txt && \
	    grep ZZZ_Pass.*Passed $(JTREG_OUTPUT_DIR)/report/text/summary.txt ) \
	then \
	    echo "$(MODE) $(TEST) OK" >> $(SUMMARY) ; \
	else \
	    echo "$(MODE) $(TEST) FAILED" >> $(SUMMARY) ; \
	fi


# Check to make sure these directories exist
check-jtreg: $(JT_HOME) $(JTREG)

# Bundle up the results
$(JPRT_ARCHIVE_BUNDLE): FRC
	@rm -f $@
	@mkdir -p $(@D)
	( cd $(TEST_OUTPUT_DIR) && zip -q -r $@ . )

# Cleanup
clean:
	rm -f $(JPRT_ARCHIVE_BUNDLE)

# Used to force a target rules to run
FRC:

# Phony targets (e.g. these are not filenames)
.PHONY: all init run report summary clean jtreg-test FRC

# No use of suffix rules
.SUFFIXES:

