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

JTHARNESS_SRC_TAG="${JTHARNESS_SRC_TAG:-${DEFAULT_JTHARNESS_SRC_TAG}}"
JTHARNESS_SRC_ARCHIVE_CHECKSUM="${JTHARNESS_SRC_ARCHIVE_CHECKSUM:-${DEFAULT_JTHARNESS_SRC_ARCHIVE_CHECKSUM}}"

ANT_VERSION="${ANT_VERSION:-${DEFAULT_ANT_VERSION}}"
ANT_ARCHIVE_CHECKSUM="${ANT_ARCHIVE_CHECKSUM:-${DEFAULT_ANT_ARCHIVE_CHECKSUM}}"

setup_ant
info "ANT: ${ANT}"

setup_jtharness_source() {
    check_arguments "${FUNCNAME}" 1 $#

    local dir="$1"

    # Build jtharness
    local src_archive_dir="$(builtin  cd ${dir}/..; pwd)"
    local JTHARNESS_LOCAL_SRC_ARCHIVE="${src_archive_dir}/source.zip"
    if [ "${JTHARNESS_SRC_TAG}" = "tip" -o "${JTHARNESS_SRC_TAG}" = "master" ]; then
        local BRANCH="master"
        get_archive_no_checksum "${CODE_TOOLS_URL_BASE}/jtharness/archive/${BRANCH}.zip" "${JTHARNESS_LOCAL_SRC_ARCHIVE}" "${dir}"
        JTHARNESS_SRC_DIR="${dir}/jtharness-${BRANCH}"
    else
        get_archive "${CODE_TOOLS_URL_BASE}/jtharness/archive/${JTHARNESS_SRC_TAG}.zip" "${JTHARNESS_LOCAL_SRC_ARCHIVE}" "${dir}" "${JTHARNESS_SRC_ARCHIVE_CHECKSUM}"
        JTHARNESS_SRC_DIR="${dir}/jtharness-${JTHARNESS_SRC_TAG}"
    fi
}

build_jtharness() {
    check_arguments "${FUNCNAME}" 0 $#

    local JTHARNESS_SRC_DIR_BASE="${BUILD_DIR}/src"
    mkdir -p "${JTHARNESS_SRC_DIR_BASE}"
    setup_jtharness_source "${JTHARNESS_SRC_DIR_BASE}"

    local JTHARNESS_DIST="${BUILD_DIR}/build"
    "${ANT}" -DBUILD_DIR="$(native_path "${JTHARNESS_DIST}")" -f "$(native_path "${JTHARNESS_SRC_DIR}/build/build.xml")"

    JTHARNESS_JAVATEST_JAR="${JTHARNESS_DIST}/binaries/lib/javatest.jar"
    JTHARNESS_LICENSE="${JTHARNESS_SRC_DIR}/LICENSE"
    JTHARNESS_COPYRIGHT="${JTHARNESS_SRC_DIR}/legal/copyright.txt"
}
build_jtharness

if [ ! x"$BUILD_RESULTS_FILE" = x"" ]; then
    mkdir -p "$(dirname "${BUILD_RESULTS_FILE}")"
    cat > "${BUILD_RESULTS_FILE}" << EOF
JTHARNESS_JAVATEST_JAR="${JTHARNESS_JAVATEST_JAR}"
JTHARNESS_LICENSE="${JTHARNESS_LICENSE}"
JTHARNESS_COPYRIGHT="${JTHARNESS_COPYRIGHT}"
EOF
fi
