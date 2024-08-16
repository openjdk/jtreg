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

JCOV_SRC_TAG="${JCOV_SRC_TAG:-${DEFAULT_JCOV_SRC_TAG}}"
JCOV_SRC_ARCHIVE_CHECKSUM="${JCOV_SRC_ARCHIVE_CHECKSUM:-${DEFAULT_JCOV_SRC_ARCHIVE_CHECKSUM}}"

ANT_VERSION="${ANT_VERSION:-${DEFAULT_ANT_VERSION}}"
ANT_ARCHIVE_CHECKSUM="${ANT_ARCHIVE_CHECKSUM:-${DEFAULT_ANT_ARCHIVE_CHECKSUM}}"

ASM_VERSION="${ASM_VERSION:-${DEFAULT_ASM_VERSION}}"
ASM_URL_BASE="${ASM_URL_BASE:-${MAVEN_REPO_URL_BASE}}"
ASM_JAR_CHECKSUM="${ASM_JAR_CHECKSUM:-${DEFAULT_ASM_JAR_CHECKSUM}}"
ASM_TREE_JAR_CHECKSUM="${ASM_TREE_JAR_CHECKSUM:-${DEFAULT_ASM_TREE_JAR_CHECKSUM}}"
ASM_UTIL_JAR_CHECKSUM="${ASM_UTIL_JAR_CHECKSUM:-${DEFAULT_ASM_UTIL_JAR_CHECKSUM}}"

JTHARNESS_SRC_TAG="${JTHARNESS_SRC_TAG:-${DEFAULT_JTHARNESS_SRC_TAG}}"
JTHARNESS_SRC_ARCHIVE_CHECKSUM="${JTHARNESS_SRC_ARCHIVE_CHECKSUM:-${DEFAULT_JTHARNESS_SRC_ARCHIVE_CHECKSUM}}"

setup_ant
info "ANT: ${ANT}"

setup_jcov_src() {
    check_arguments "${FUNCNAME}" 1 $#

    local dir="$1"

    # Build jcov
    local src_archive_dir="$(builtin  cd ${dir}/..; pwd)"
    local JCOV_LOCAL_SRC_ARCHIVE="${src_archive_dir}/source.zip"
    if [ "${JCOV_SRC_TAG}" = "tip" -o "${JCOV_SRC_TAG}" = "master" ]; then
        local BRANCH="master"
        get_archive_no_checksum "${CODE_TOOLS_URL_BASE}/jcov/archive/${BRANCH}.zip" "${JCOV_LOCAL_SRC_ARCHIVE}" "${dir}"
        JCOV_SRC_DIR="${dir}/jcov-${BRANCH}"
    else
        get_archive "${CODE_TOOLS_URL_BASE}/jcov/archive/${JCOV_SRC_TAG}.zip" "${JCOV_LOCAL_SRC_ARCHIVE}" "${dir}" "${JCOV_SRC_ARCHIVE_CHECKSUM}"
        JCOV_SRC_DIR="${dir}/jcov-${JCOV_SRC_TAG}"
    fi
}

setup_asm() {
    check_arguments "${FUNCNAME}" 0 $#

    local ASM_DEPS_DIR="${DEPS_DIR}/asm"

    ASM_JAR="${ASM_DEPS_DIR}/asm-${ASM_VERSION}.jar"
    download_and_checksum "${ASM_URL_BASE}/org/ow2/asm/asm/${ASM_VERSION}/asm-${ASM_VERSION}.jar" "${ASM_JAR}" "${ASM_JAR_CHECKSUM}"

    ASM_TREE_JAR="${ASM_DEPS_DIR}/asm-tree-${ASM_VERSION}.jar"
    download_and_checksum "${ASM_URL_BASE}/org/ow2/asm/asm-tree/${ASM_VERSION}/asm-tree-${ASM_VERSION}.jar" "${ASM_TREE_JAR}" "${ASM_TREE_JAR_CHECKSUM}"

    ASM_UTIL_JAR="${ASM_DEPS_DIR}/asm-utils-${ASM_VERSION}.jar"
    download_and_checksum "${ASM_URL_BASE}/org/ow2/asm/asm-util/${ASM_VERSION}/asm-util-${ASM_VERSION}.jar" "${ASM_UTIL_JAR}" "${ASM_UTIL_JAR_CHECKSUM}"
}

setup_jtharness_javatest_jar() {
    check_arguments "${FUNCNAME}" 0 $#

    if [ -n "${JTHARNESS_JAVATEST_JAR:-}" ]; then
        return
    fi

    JTHARNESS_JAVATEST_JAR="$(find "${DEPS_DIR}" -name jtharness.jar)"
    if [ -n "${JTHARNESS_JAVATEST_JAR}" ]; then
        return
    fi

    if [ -z "${JTHARNESS_ARCHIVE_URL:-}" ]; then
        if [ -n "${JTHARNESS_ARCHIVE_URL_BASE:-}" ]; then
            JTHARNESS_ARCHIVE_URL="${JTHARNESS_ARCHIVE_URL_BASE}/${JTHARNESS_VERSION}/${JTHARNESS_BUILD_NUMBER}/${JTHARNESS_FILE}"
        fi
    fi
    
    if [ -n "${JTHARNESS_ARCHIVE_URL:-}" ]; then
        local JTHARNESS_DEPS_DIR="${DEPS_DIR}/jtharness"
        local JTHARNESS_LOCAL_ARCHIVE_FILE="${DEPS_DIR}/$(basename "${JTHARNESS_ARCHIVE_URL}")"
        get_archive "${JTHARNESS_ARCHIVE_URL}" "${JTHARNESS_LOCAL_ARCHIVE_FILE}" "${JTHARNESS_DEPS_DIR}" "${JTHARNESS_ARCHIVE_CHECKSUM}"
        JTHARNESS_JAVATEST_JAR="${JTHARNESS_DEPS_DIR}/${JTHARNESS_ARCHIVE_DIR_NAME}/lib/javatest.jar"
        JTHARNESS_LICENSE="$(dirname "${JTHARNESS_JAVATEST_JAR}")/../legal/license.txt"
        JTHARNESS_COPYRIGHT="$(dirname "${JTHARNESS_JAVATEST_JAR}")/../legal/copyright.txt"
        return
    fi

    info "Neither JTHARNESS_ARCHIVE_URL or JTHARNESS_ARCHIVE_URL_BASE is set, building from source"
    export JTHARNESS_BUILD_RESULTS_FILE="${BUILD_DIR}/deps/jtharness.results"
    (
        export BUILD_DIR="${BUILD_DIR}/deps/jtharness"
        export BUILD_RESULTS_FILE="${JTHARNESS_BUILD_RESULTS_FILE}"
        export JTHARNESS_SRC_TAG="${JTHARNESS_SRC_TAG}"
        export JTHARNESS_SRC_ARCHIVE_CHECKSUM="${JTHARNESS_SRC_ARCHIVE_CHECKSUM}"
        export ANT="${ANT}"
        bash "${mydir}/../jtharness/build.sh"
    )
    ret=$?
    if [ ! $ret = 0 ]; then
        exit ${ret}
    fi
    . "${JTHARNESS_BUILD_RESULTS_FILE}"
}

build_jcov() {
    check_arguments "${FUNCNAME}" 0 $#

    setup_asm
    setup_jtharness_javatest_jar

    local JCOV_SRC_DIR_BASE="${BUILD_DIR}/src"
    setup_jcov_src "${JCOV_SRC_DIR_BASE}"

    local JCOV_DIST="${BUILD_DIR}/build"
    (
        cd "${JCOV_SRC_DIR}/build"
        "${ANT}" -Dresult.dir="$(native_path "${JCOV_DIST}")"               \
                 -Dasm.jar="$(native_path "${ASM_JAR}")"                    \
                 -Dasm.checksum="${ASM_JAR_CHECKSUM}"                       \
                 -Dasm.tree.jar="$(native_path "${ASM_TREE_JAR}")"          \
                 -Dasm.tree.checksum="${ASM_TREE_JAR_CHECKSUM}"             \
                 -Dasm.util.jar="$(native_path "${ASM_UTIL_JAR}")"          \
                 -Dasm.util.checksum="${ASM_UTIL_JAR_CHECKSUM}"             \
                 -Djavatestjar="$(native_path "${JTHARNESS_JAVATEST_JAR}")" \
                 -Dverify.strict=                                           \
                 -f "$(native_path "${JCOV_SRC_DIR}/build/build.xml")"
    )

    local JCOV_DIST_JCOV_DIR="$(ls -d "${JCOV_DIST}/jcov"*)"
    JCOV_JAR="${JCOV_DIST_JCOV_DIR}/jcov.jar"
    JCOV_NETWORK_SAVER_JAR="${JCOV_DIST_JCOV_DIR}/jcov_network_saver.jar"
    JCOV_LICENSE="${JCOV_SRC_DIR}/LICENSE"
}
build_jcov

if [ ! x"$BUILD_RESULTS_FILE" = x"" ]; then
    mkdir -p "$(dirname "${BUILD_RESULTS_FILE}")"
    cat > "${BUILD_RESULTS_FILE}" << EOF
JCOV_JAR="${JCOV_JAR}"
JCOV_NETWORK_SAVER_JAR="${JCOV_NETWORK_SAVER_JAR}"
JCOV_LICENSE="${JCOV_LICENSE}"
EOF
fi
