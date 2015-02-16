#!/bin/sh
#
# Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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

# Usage:
#    jtdiff ...args....
#
# jtdiff requires a version of Java equivalent to JDK 1.5.0 or higher.

# $JT_HOME can be used to specify the jtdiff installation directory
#   (e.g. /usr/local/java/jct-tools/3.2.2)
#
# $JT_JAVA is used to specify the version of java to use when running JavaTest
#   (e.g. /usr/local/java/jdk1.5.0/solaris-sparc/bin/java)
#
# jtdiff also provides an Ant task for direct invocation from Ant.

# Determine jtdiff/JavaTest installation directory
if [ -n "$JT_HOME" ]; then
    if [ ! -r $JT_HOME/lib/jtreg.jar ];then
        echo "Invalid JT_HOME=$JT_HOME. Cannot find or read $JT_HOME/lib/jtreg.jar"
       exit 1;
    fi
else
    # Deduce where script is installed
    # - should work on most derivatives of Bourne shell, like ash, bash, ksh,
    #   sh, zsh, etc, including on Windows, MKS (ksh) and Cygwin (ash or bash)
    if type -p type 1>/dev/null 2>&1 && test -z "`type -p type`" ; then
        myname=`type -p "$0"`
    elif type type 1>/dev/null 2>&1 ; then
        myname=`type "$0" | sed -e 's/^.* is a tracked alias for //' -e 's/^.* is //'`
    elif whence whence 1>/dev/null 2>&1 ; then
        myname=`whence "$0"`
    fi
    mydir=`dirname "$myname"`
    p=`cd "$mydir" ; pwd`
    while [ -n "$p" -a "$p" != "/" ]; do
        if [ -r "$p"/lib/jtreg.jar ]; then JT_HOME="$p" ; break; fi
        p=`dirname "$p"`
    done
    if [ -z "$JT_HOME" ]; then
        echo "Cannot determine JT_HOME; please set it explicitly"; exit 1
    fi
fi

# Normalize JT_HOME if using Cygwin
case "`uname -s`" in
    CYGWIN* ) cygwin=1 ; JT_HOME=`cygpath -a -m "$JT_HOME"` ;;
esac


# Separate out -J* options for the JVM
# Unset IFS and use newline as arg separator to preserve spaces in args
DUALCASE=1  # for MKS: make case statement case-sensitive (6709498)
saveIFS="$IFS"
nl='
'
for i in "$@" ; do
    IFS=
    if [ -n "$cygwin" ]; then i=`echo $i | sed -e 's|/cygdrive/\([A-Za-z]\)/|\1:/|'` ; fi
    case $i in
    -J* )       javaOpts=$javaOpts$nl`echo $i | sed -e 's/^-J//'` ;;
    *   )       jtdiffOpts=$jtdiffOpts$nl$i ;;
    esac
    IFS="$saveIFS"
done
unset DUALCASE

# Determine java for jtdiff, from JT_JAVA, JAVA_HOME, java
if [ -n "$JT_JAVA" ]; then
    if [ -d "$JT_JAVA" ]; then
        JT_JAVA="$JT_JAVA/bin/java"
    fi
elif [ -n "$JAVA_HOME" ]; then
    JT_JAVA="$JAVA_HOME/bin/java"
else
    JT_JAVA=java
fi

# Verify java version (1.)5 or newer used to run jtdiff
version=`"$JT_JAVA" -classpath "${JT_HOME}/lib/jtreg.jar" com.sun.javatest.regtest.agent.GetSystemProperty java.version 2>&1 |
        grep 'java.version=' | sed -e 's/^.*=//' -e 's/^1\.//' -e 's/\([1-9][0-9]*\).*/\1/'`

if [ -z "$version" ]; then
    echo "Cannot determine version of java to run jtdiff"
    exit 1;
elif [ "$version" -lt 5 ]; then
    echo "java version 5 or later is required to run jtdiff"
    exit 1;
fi

# And finally ...

IFS=$nl

"${JT_JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}" \
    $javaOpts \
    -Dprogram=`basename "$0"` \
    -cp "${JT_HOME}/lib/jtreg.jar" \
    com.sun.javatest.diff.Main \
    $jtdiffOpts
