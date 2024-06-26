#!/bin/sh
#
# Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
#    jtreg ...args....
#       Run the application via the regression test-suite front end
#       with the given arguments.
#       The Java runtime used to run jtreg is found as follows:
#       -   $JTREG_JAVA is used, if it is set
#       -   Otherwise, $JAVA_HOME/bin/java is used if $JAVA_HOME is set
#           (that is, similar to JDK.)
#       -   Otherwise, the value of the -jdk option is used if found
#       -   Otherwise, "java" is used
#
# jtreg requires a version of Java equivalent to JDK 1.8.0 or higher.

# $JTREG_HOME can be used to specify the jtreg installation directory
#   (e.g. /usr/local/jtreg/5.0)
#
# $JTREG_JAVA is used to specify the version of java to use when running jtreg
#   (e.g. /usr/local/java/jdk1.8.0/bin/java)
#
# You can also run the jar file directly, as in
#   java -jar <path>/lib/jtreg.jar ...args...
#
# jtreg also provides Ant tasks; see the documentation for details.

# Implementation notes for Windows:
# Cygwin:
#   Detected with `uname -s` (CYGWIN*)
#   Windows drives are mounted with /cygdrive/LETTER
# Windows Subsystem for Linux (WSL):
#   Detected with `uname -s` (Linux) and /proc/version contains "Microsoft"
#   Windows drives are mounted with /mnt/LETTER
#   Windows binaries need an explicit .exe suffix.
#
# Values are evaluated according to whether they are used in the context of the
# shell, or in the context of the JDK under test.
# JTREG_JAVA is evaluated for use in the shell, to run java
# JTREG_HOME is evaluated as a JDK arg, for use in -classpath or -jar args
# Other command line are updated to be JDK args for jtreg.

case "`uname -s`" in
    CYGWIN* ) cygwin=1 ;;
    Linux ) if test -f /proc/version && grep -qi Microsoft /proc/version ; then wsl=1 ; fi ;;
esac

# Determine jtreg installation directory
JTREG_HOME=${JTREG_HOME:-$JT_HOME}      # allow for old version of name
if [ -n "$JTREG_HOME" ]; then
    if [ ! -r $JTREG_HOME/lib/jtreg.jar ];then
        echo "Invalid JTREG_HOME=$JTREG_HOME. Cannot find or read $JTREG_HOME/lib/jtreg.jar"
       exit 1;
    fi
else
    # Deduce where script is installed
    # - should work on most derivatives of Bourne shell, like ash, bash, ksh,
    #   sh, zsh, etc, including on Windows, MKS (ksh), Cygwin (ash or bash)
    #   and Windows Subsystem for Linux (WSL)
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
        if [ -r "$p"/lib/jtreg.jar ]; then JTREG_HOME="$p" ; break; fi
        p=`dirname "$p"`
    done
    if [ -z "$JTREG_HOME" ]; then
        echo "Cannot determine JTREG_HOME; please set it explicitly"; exit 1
    fi
fi


# Look for -jdk option as possible default to run jtreg
# Unset IFS and use newline as arg separator to preserve spaces in args
DUALCASE=1  # for MKS: make case statement case-sensitive (6709498)
saveIFS="$IFS"
nl='
'
for i in "$@" ; do
    IFS=
    case $i in
    -jdk:* )    jdk="`echo $i | sed -e 's/^-jdk://'`" ;;
    esac
    IFS="$saveIFS"
done
unset DUALCASE

# Determine java for jtreg, from JTREG_JAVA, JAVA_HOME, -jdk, java
JTREG_JAVA=${JTREG_JAVA:-$JT_JAVA}      # allow for old version of name
if [ -n "$JTREG_JAVA" ]; then
    if [ -d "$JTREG_JAVA" ]; then
        JTREG_JAVA="$JTREG_JAVA/bin/java"
    fi
elif [ -n "$JAVA_HOME" ]; then
    JTREG_JAVA="$JAVA_HOME/bin/java"
elif [ -n "$jdk" ]; then
    JTREG_JAVA="$jdk/bin/java"
else
    JTREG_JAVA=java
fi

# Fixup JTREG_JAVA, JTREG_HOME as needed, if using Cygwin or WSL
if [ -n "$cygwin" ]; then
    JTREG_HOME=`cygpath -a -m "$JTREG_HOME"`
    driveDir=cygdrive
elif [ -n "$wsl" -a -x "$JTREG_JAVA".exe ]; then
    JTREG_JAVA="$JTREG_JAVA".exe
    JTREG_HOME=`wslpath -a -m "$JTREG_HOME"`
    driveDir=mnt
fi

if [ ! -e "$JTREG_JAVA" ]; then
    echo "No java executable at $JTREG_JAVA"
    exit 1;
fi

# Verify java version 11 or newer used to run jtreg
version=`"$JTREG_JAVA" -classpath "${JTREG_HOME}/lib/jtreg.jar" com.sun.javatest.regtest.agent.GetSystemProperty java.version 2>&1 |
        grep 'java.version=' | sed -e 's/^.*=//' -e 's/^1\.//' -e 's/\([1-9][0-9]*\).*/\1/'`

if [ -z "$version" ]; then
    echo "Cannot determine version of java to run jtreg"
    exit 1;
elif [ "$version" -lt 11 ]; then
    echo "java version 11 or later is required to run jtreg"
    exit 1;
fi

# Separate out -J* options for the JVM
# Unset IFS and use newline as arg separator to preserve spaces in arg
DUALCASE=1  # for MKS: make case statement case-sensitive (6709498)
saveIFS="$IFS"
nl='
'
for i in "$@" ; do
    IFS=
    if [ -n "$driveDir" ]; then i=`echo $i | sed -e 's|/'$driveDir'/\([A-Za-z]\)/|\1:/|'` ; fi
    case $i in
    -J* )       javaOpts=$javaOpts$nl`echo $i | sed -e 's/^-J//'` ;;
    *   )       jtregOpts=$jtregOpts$nl$i ;;
    esac
    IFS="$saveIFS"
done
unset DUALCASE

# And finally ...

IFS=$nl

"${JTREG_JAVA}" \
    $javaOpts \
    -Dprogram=`basename "$0"` \
    -jar "${JTREG_HOME}/lib/jtreg.jar" \
    $jtregOpts
