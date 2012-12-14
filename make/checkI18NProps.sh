#! /bin/sh
#
# Copyright (c) 2000, 2007, Oracle and/or its affiliates. All rights reserved.
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

#
# shell script to extract the set of i18n message keys from a package
# and to compare them against the keys in the i18n.properties file.
# The message keys are extracted by a combination of static and dynamic
# analysis.

srcDir=$1
dynProps=$2

base=`basename $0`.$$
requiredProps=$base.reqd.tmp
definedProps=$base.defd.tmp
diffs=$base.diff.tmp
reqdNotDefd=$base.rqnd.tmp
defdNotReqd=$base.dfnr.tmp

# this is the command to extract the keys from the source files
# customize as required

#egrep  '(Message.get|Fault|BadArgs|BadValue|println|error|showMessage|popupError)\(msgs, "|(printMessage|showCount)\("' ${srcDir}/*.java | sed -e 's/.*"\([^"]*\)".*/\1/'      | sort -u > $requiredProps

# .log for WorkDirectory.log(...)
# temp allow showError 5/28/03
# temp allow WaitDialog lots of strings, standard ones would be better
# createList() checking not possible because of some dynamically generated
#   resource keys, use dynamic analysis in I18NExecTest.java
( egrep  '(Message.get|Fault|BadArgs|BadValue|println|printErrorMessage|printMessage|[eE]rror|showMessage|popupError|write|JavaTestError|\.log|super)\((msgs|i18n), "[^"]*"(,|\))|(printMessage|getI18NString|writeI18N|i18n.getString|formatI18N|setI18NTitle)\("[^"]*"(,|\))' ${srcDir}/*.java | sed -e 's/[^"]*"\([^"]*\)".*/\1/' ;
  egrep  'uif.createMessageArea\("' ${srcDir}/*.java | sed -e 's/.*"\([^"]*\)".*/\1.txt/' ;
  egrep  'uif.showError\("' ${srcDir}/*.java | sed -e 's/.*"\([^"]*\)".*/\1.err/' ;
  egrep  'showError\("' ${srcDir}/*.java | sed -e 's/.*"\([^"]*\)".*/\1.err/' ;
  egrep  'uif.show(YesNo|YesNoCancel|OKCancel|Information|CustomInfo)Dialog\("' ${srcDir}/*.java | sed -e 's/.*"\([^"]*\)".*/\1.txt\
\1.title/' ;
  egrep  'uif.createWaitDialog\("' ${srcDir}/*.java | sed -e 's/.*"\([^"]*\)".*/\1.txt\
\1.title\
\1.desc\
\1.name/' ;
  grep 'new FileType' ${srcDir}/*.java | sed -e 's/.*new FileType("\([^"]*\)");.*/filetype\1/' -e 's/.*new FileType();.*/filetype.allFiles/'
  if [ ! -z "$dynProps" ]; then grep '^i18n:' $dynProps | awk '{print $2}' ; fi
) | sort -u > $requiredProps

# end

sed -e '/^#/d' -e '/^[  ]/d' -e '/^[^=]*$/d' -e 's/^\([A-Za-z0-9/_.-]*\).*/\1/' ${srcDir}/i18n.properties  | sort -u > $definedProps

diff $requiredProps $definedProps > $diffs

grep '^<' $diffs | awk '{print $2}' > $reqdNotDefd
if [ -s $reqdNotDefd ]; then
  echo "messages required but not defined:"
  cat $reqdNotDefd
  echo
  exitCode=1
fi

grep '^>' $diffs | awk '{print $2}' > $defdNotReqd
if [ -s $defdNotReqd ]; then
  echo "messages defined but not required:"
  cat $defdNotReqd
  echo
  exitCode=1
fi

rm -f $requiredProps $definedProps $diffs $reqdNotDefd $defdNotReqd
exit ${exitCode:-0}
