#
# Copyright 1999-2007 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

TOPDIR = ..
BUILD_DIR = $(TOPDIR)/build

include Defs.gmk

#----------------------------------------------------------------------

all: build test $(BUILDDIR)/lockCheck.ok

#----------------------------------------------------------------------

include buildFiles.gmk

#----------------------------------------------------------------------
#
# shared

include doc.gmk
include javatest.gmk
include javahelp.gmk
include junit.gmk

#----------------------------------------------------------------------
#
# jtdiff support

include classes_com_sun_javatest_diff.gmk
#include doc_jtreg.gmk
include bin_jtdiff.gmk

#----------------------------------------------------------------------
#
# jtreg support

include classes_com_sun_javatest_regtest.gmk
include doc_jtreg.gmk
include bin_jtreg.gmk
include jtreg.gmk

#----------------------------------------------------------------------

include export.gmk

#----------------------------------------------------------------------

CLEANFILES += work report testWork testReport jtWork jtReport JTwork JTreport
CLEANFILES += $(CLASSDIR) testClasses $(BUILDFILES) $(TESTS)

#----------------------------------------------------------------------

build: $(BUILDFILES)

test:  $(TESTS)

debug: 
	echo '$(BUILDFILES)'

#----------------------------------------------------------------------
#
# do an upfront check that an X Windows server is running,
# in case it is needed by any tests.
# WARNING: THIS HANGS IF THE WINDOW SYSTEM IS NOT AVAILABLE
#
#.INIT:
#	@display=$${DISPLAY:-`uname -n`:0.0} ; \
#	if /usr/openwin/bin/xwininfo -root -display $$display 1>/dev/null 2>&1 ; then \
#	    true ; \
#	else \
#	    echo "Cannot access display $$display" ; \
#	    echo "Please make sure the window system is running and accessible." ; \
#	    exit 1 ; \
#	fi

.DONE:
	if [ -f $(BUILD_DIR)/.vnc/display ]; then sh display.sh -kill ; fi

#----------------------------------------------------------------------

include Rules.gmk


