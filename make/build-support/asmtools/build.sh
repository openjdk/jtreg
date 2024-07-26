#!/bin/bash

#
# Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

mydir="$(dirname ${BASH_SOURCE[0]})"
log_module="$(basename "${BASH_SOURCE[0]}")"
. "${mydir}/../build-common.sh"
. "${mydir}/version-numbers"

ASMTOOLS_SRC_TAG="${ASMTOOLS_SRC_TAG:-${DEFAULT_ASMTOOLS_SRC_TAG}}"
ASMTOOLS_SRC_ARCHIVE_CHECKSUM="${ASMTOOLS_SRC_ARCHIVE_CHECKSUM:-${DEFAULT_ASMTOOLS_SRC_ARCHIVE_CHECKSUM}}"

ANT_VERSION="${ANT_VERSION:-${DEFAULT_ANT_VERSION}}"
ANT_ARCHIVE_CHECKSUM="${ANT_ARCHIVE_CHECKSUM:-${DEFAULT_ANT_ARCHIVE_CHECKSUM}}"

setup_ant

setup_asmtools_src() {
    check_arguments "${FUNCNAME}" 1 $#

    local dir="$1"
    local src_archive_dir="$(builtin  cd ${dir}/..; pwd)"
    local ASMTOOLS_LOCAL_SRC_ARCHIVE="${src_archive_dir}/source.zip"
    if [ "${ASMTOOLS_SRC_TAG}" = "tip" -o "${ASMTOOLS_SRC_TAG}" = "master" ]; then
        local BRANCH="master"
        get_archive_no_checksum "${CODE_TOOLS_URL_BASE}/asmtools/archive/${BRANCH}.zip" "${ASMTOOLS_LOCAL_SRC_ARCHIVE}" "${dir}"
        ASMTOOLS_SRC_DIR="${dir}/asmtools-${BRANCH}"
    else
        get_archive "${CODE_TOOLS_URL_BASE}/asmtools/archive/${ASMTOOLS_SRC_TAG}.zip" "${ASMTOOLS_LOCAL_SRC_ARCHIVE}" "${dir}" "${ASMTOOLS_SRC_ARCHIVE_CHECKSUM}"
        ASMTOOLS_SRC_DIR="${dir}/asmtools-${ASMTOOLS_SRC_TAG}"
    fi
}

build_asmtools() {
    check_arguments "${FUNCNAME}" 0 $#

    local ASMTOOLS_SRC_DIR_BASE="${BUILD_DIR}/src"
    mkdir -p "${ASMTOOLS_SRC_DIR_BASE}"
    setup_asmtools_src "${ASMTOOLS_SRC_DIR_BASE}"

    local ASMTOOLS_DIST="${BUILD_DIR}/build"
    "${ANT}" -DBUILD_DIR="$(native_path "${ASMTOOLS_DIST}")" -f "$(native_path "${ASMTOOLS_SRC_DIR}/build/build.xml")"
    ASMTOOLS_JAR="${ASMTOOLS_DIST}/binaries/lib/asmtools.jar"
    ASMTOOLS_LICENSE="${ASMTOOLS_SRC_DIR}/LICENSE"
}
build_asmtools

if [ ! x"$BUILD_RESULTS_FILE" = x"" ]; then
    mkdir -p "$(dirname "${BUILD_RESULTS_FILE}")"
    cat > "${BUILD_RESULTS_FILE}" << EOF
ASMTOOLS_JAR="${ASMTOOLS_JAR}"
ASMTOOLS_LICENSE="${ASMTOOLS_LICENSE}"
EOF
fi
