#!/bin/sh

#
# Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

# display.sh [-kill]
#
# A wrapper around optional use of vncserver.
#
# If available and enabled, a vnc server will be started and its display
# name returned. Otherwise $DISPLAY or a default will be returned.
# If the -kill option is given the vnc server on $DISPLAY will be killed.
#
# Set environment variable VNC to false|off|no|0 to disable VNC;
# this is useful if you need to debug GUI targets

#echo debug: VNC=$VNC 1>&2
#echo debug: DISPLAY=$DISPLAY 1>&2

# setup VNC if enabled
case "${VNC:-1}" in
    false|off|no|0 )
        VNC=0
        ;;
    * ) if [ -f $HOME/.vnc/passwd ]; then
            # put VNC_HOME on front of path, if set
            # otherwise, try and find vncserver if not on PATH
            if [ -n "$VNC_HOME" ]; then
                PATH=$VNC_HOME:$PATH
                export PATH
            elif [ -z "`which vncserver`" ]; then
                if [ -f /usr/dist/exe/vncserver ]; then
                    PATH=/usr/dist/exe:$PATH
                    export PATH
                fi
            fi
            # final check
            if [ -n "`which vncserver`" ]; then
                VNC=1
            else
                VNC=0
            fi
        else
            # user does not have VNC password set
            VNC=0
        fi
        ;;
esac

# use VNC if enabled and available
if [ $VNC = 1 ]; then
    if [ "$1" = "-kill" ]; then
        vncserver -kill $DISPLAY
    else
        # echo debug: starting VNC 1>&2
        VNC_SERVERLOG=${BUILDDIR:-../build}/vncserver.log
        vncserver 2>&1 | tee $VNC_SERVERLOG 1>&2
        grep 'New .* desktop is' $VNC_SERVERLOG | \
            sed -e 's/^.*desktop is \(.*\)/\1/'
     fi
else
    if [ "$1" != "-kill" ]; then
        echo ${DISPLAY:-`uname -n`:0.0}
    fi
fi

