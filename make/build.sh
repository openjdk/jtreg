#!/bin/bash

#
# Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

# This script will download/build the dependencies for jtreg and then
# build jtreg. Downloaded files are verified against known/specified
# checksums.

# The default version to use when building jtreg can be found in the
# make/version-numbers file, where the default versions and
# corresponding known checksums for the dependencies are also
# specified. Almost all of the defaults can be overridden by setting
# the respective environment variables.

# For each of the dependency the following steps are applied and the
# first successful one is used:
#
# 1. Check if the dependency is available locally
# 2. Download a prebuilt version of the dependency
# 3. Build the dependency from source, downloading the source archive
#    first
#
# In particular, when not found locally the dependencies will be
# handled as follows:
#
# * JUnit, TestNG, JCommander, and Ant jar are by default
#   downloaded from Maven central.
# * JT Harness and AsmTools are downloaded or built from source.
# * The JDK dependency is downloaded. No default URL is set.
#

# Some noteworthy control variables:
#
# MAVEN_REPO_URL_BASE (e.g. "https://repo1.maven.org/maven2")
#     The base URL for the maven central repository.
#
# CODE_TOOLS_URL_BASE (e.g. "https://git.openjdk.org")
#     The base URL for the code tools source repositories.
#
# ANT_ARCHIVE_URL_BASE (e.g. "https://archive.apache.org/dist/ant/binaries")
#     The base URL for Ant dist binaries.
#
# JTREG_VERSION         (e.g. "5.2")
# JTREG_VERSION_STRING  (e.g. "jtreg-5.2+8"
# JTREG_BUILD_NUMBER    (e.g. "8")
# JTREG_BUILD_MILESTONE (e.g. "dev")
#     The version information to use for when building jtreg.
#
# MAKE_ARGS (e.g. "-j4 all")
#     Additional arguments to pass to make when building jtreg.
#
# WGET
#     The wget-like executable to use when downloading files.
#
# WGET_OPTIONS (e.g. "-v")
#     Additional arguments to pass to WGET when downloading files.
#
# CURL (e.g. "/path/to/my/wget")
#     The curl-like executable to use when downloading files.
#     Note: If available, wget will be preferred.
#
# CURL_OPTIONS (e.g. "-v")
#     Additional arguments to pass to CURL when downloading files.
#
# SKIP_DOWNLOAD
#     Skip the downloads if the file is already present locally.
#
# SKIP_CHECKSUM_CHECK
#     Skip the checksum verification for downloaded files.

# The control variables for dependencies are on the following general
# form (not all of them are relevant for all dependencies):
#
# <dependency>_URL (e.g. JTHARNESS_ARCHIVE_URL)
#     The full URL for the dependency.
#
# <dependency>_URL_BASE (e.g. JTHARNESS_ARCHIVE_URL_BASE)
#     The base URL for the dependency. Requires additional dependency
#     specific variables to be specified.
#
# <dependency>_CHECKSUM (e.g. JTHARNESS_ARCHIVE_CHECKSUM)
#     The expected checksum of the download file.
#
# <dependency>_SRC_TAG (e.g. JTHARNESS_SRC_TAG)
#     The SCM tag to use when building from source. The special value
#     "tip" can be used to get the most recent version.
#
# <dependency>_SRC_ARCHIVE_CHECKSUM (e.g. JTHARNESS_SRC_ARCHIVE_CHECKSUM)
#     The checksum of the source archive.
#

# The below outlines the details of how the dependencies are
# handled. For each dependency the steps are tried in order and the
# first successful one will be used.
#
# Ant (required to build AsmTools and JT Harness)
#     Checksum variables:
#         ANT_ARCHIVE_CHECKSUM: checksum of binary archive
#
#     1. ANT
#         The path to the ant executable.
#     2a. ANT_ARCHIVE_URL
#         The full URL for the archive.
#     2b. ANT_ARCHIVE_URL_BASE + ANT_VERSION
#         The individual URL components used to construct the full URL.
#
# AsmTools
#     Checksum variables:
#         ASMTOOLS_ARCHIVE_CHECKSUM: checksum of binary archive
#         ASMTOOLS_SRC_ARCHIVE_CHECKSUM: checksum of source archive
#
#     1. ASMTOOLS_JAR + ASMTOOLS_LICENSE
#         The path to asmtools.jar and LICENSE respectively.
#     2a. ASMTOOLS_ARCHIVE_URL
#         The full URL for the archive.
#     2b. ASMTOOLS_ARCHIVE_URL_BASE + ASMTOOLS_VERSION + ASMTOOLS_BUILD_NUMBER + ASMTOOLS_FILE
#         The individual URL components used to construct the full URL.
#     3. ASMTOOLS_SRC_TAG
#         The SCM repository tag to use when building from source.
#
# Google Guice (required by TestNG)
#     Checksum variables:
#         GOOGLE_GUICE_JAR_CHECKSUM: checksum of jar
#
#     1. GOOGLE_GUICE_JAR
#         The path to guice.jar.
#     2a. GOOGLE_GUICE_JAR_URL
#         The full URL for the jar.
#     2b. GOOGLE_GUICE_JAR_URL_BASE + GOOGLE_GUICE_VERSION
#         The individual URL components used to construct the full URL.
#
# JCommander (required by TestNG)
#     Checksum variables:
#         JCOMMANDER_JAR_CHECKSUM: checksum of jar
#
#     1. JCOMMANDER_JAR
#         The path to jcommander.jar.
#     2a. JCOMMANDER_JAR_URL
#         The full URL for the jar.
#     2b. JCOMMANDER_JAR_URL_BASE + JCOMMANDER_VERSION
#         The individual URL components used to construct the full URL.
#
# JDK
#     Checksum variables:
#         JDK_ARCHIVE_CHECKSUM: checksum of binary archive
#
#     1. JAVA_HOME
#         The path to the JDK.
#     2a. JDK_ARCHIVE_URL
#         The full URL for the archive.
#     2b. JDK_ARCHIVE_URL_BASE + JDK_VERSION + JDK_BUILD_NUMBER + JDK_FILE
#         The individual URL components used to construct the full URL.
#
# JT Harness
#     Checksum variables:
#         JTHARNESS_ARCHIVE_CHECKSUM: checksum of binary archive
#         JTHARNESS_SRC_ARCHIVE_CHECKSUM: checksum of source archive
#
#     1. JTHARNESS_JAVATEST_JAR + JTHARNESS_LICENSE + JTHARNESS_COPYRIGHT
#         The path to javatest.jar, LICENSE, and copyright.txt respectively.
#     2a. JTHARNESS_ARCHIVE_URL
#         The full URL for the archive.
#     2b. JTHARNESS_ARCHIVE_URL_BASE + JTHARNESS_VERSION + JTHARNESS_BUILD_NUMBER + JTHARNESS_FILE
#         The individual URL components used to construct the full URL.
#     3. JTHARNESS_SRC_TAG
#         The SCM repository tag to use when building from source.
#
# JUnit (includes HamCrest)
#     Checksum variables:
#         JUNIT_JAR_CHECKSUM: checksum of binary archive
#
#     1. JUNIT_JAR + JUNIT_LICENSE
#         The path to junit.jar and LICENSE respectively.
#     2a. JUNIT_JAR_URL
#         The full URL for the jar.
#     2b. JUNIT_JAR_URL_BASE + JUNIT_VERSION + JUNIT_FILE
#         The individual URL components used to construct the full URL.
#
# TestNG (requires JCommander, Google Guice)
#     Checksum variables:
#         TESTNG_JAR_CHECKSUM: checksum of binary archive
#         TESTNG_LICENSE_CHECKSUM: checksum of LICENSE file
#
#     1. TESTNG_JAR + TESTNG_LICENSE
#         The path to testng.jar and LICENSE.txt respectively.
#     2a. TESTNG_JAR_URL
#         The full URL for the jar.
#     2b. TESTNG_JAR_URL_BASE + TESTNG_VERSION + TESTNG_FILE
#         The individual URL components used to construct the full URL.
#

mydir="$(dirname ${BASH_SOURCE[0]})"
log_module="$(basename "${BASH_SOURCE[0]}")"
. "${mydir}/build-support/build-common.sh"

usage() {
    echo "Usage: $0 <options> [ [--] <make-options-and-targets> ]"
    echo "--help"
    echo "      Show this message"
    echo "--jdk /path/to/jdk"
    echo "      Path to JDK; must be JDK 11 or higher"
    echo "--quiet | -q"
    echo "      Reduce the logging output."
    echo "--show-default-versions"
    echo "      Show default versions of external components"
    echo "--show-config-details"
    echo "      Show configuration details"
    echo "--skip-checksum-check"
    echo "      Skip the checksum check for downloaded files."
    echo "--skip-download"
    echo "      Skip downloading files if file already available"
    echo "--skip-make"
    echo "      Skip running 'make' (just download dependencies if needed)"
    echo "--version-numbers file"
    echo "      Provide an alternate file containing dependency version information"
    echo "--"
    echo "      Subsequent arguments are for 'make'"
}

ensure_arg() {
    check_arguments "${FUNCNAME}" 2 $#
    local option="$1"
    local arg_count="$2"
    if [ "$2" -lt "2" ]; then
        echo "The $option option requires an argument"
        exit
    fi
}

process_args() {
    while [ "$#" -gt 0 ]; do
        case "$1" in
            --help|-h )             HELP=1 ;                                        shift ;;
            --jdk )                 ensure_arg "$1" $# ; JAVA_HOME="$2" ;           shift ; shift ;;
            --quiet|-q )            export QUIET=1 ;                                shift ;;
            --show-config-details ) SHOW_CONFIG_DETAILS=1 ;                         shift ;;
            --show-default-versions ) SHOW_DEFAULT_VERSIONS=1 ;                     shift ;;
            --skip-checksum-check ) export SKIP_CHECKSUM_CHECK=1 ;                  shift ;;
            --skip-download )       export SKIP_DOWNLOAD=1 ;                        shift ;;
            --skip-make )           SKIP_MAKE=1 ;                                   shift ;;
            --version-numbers )     ensure_arg "$1" $# ; VERSION_NUMBERS="$2" ;     shift ; shift ;;
            -- )                    shift ; MAKE_ARGS="$@" ;                        break ;;
            -* )                    error "unknown option: '$1'" ;                  exit 1 ;;
            * )                     MAKE_ARGS="$@" ;                                break ;;
        esac
    done
}

process_args "$@"

if [ -n "${HELP:-}" ]; then
    usage
    exit
fi

. "${VERSION_NUMBERS:-${mydir}/build-support/version-numbers}"


JTREG_VERSION="${JTREG_VERSION:-}"

ANT_VERSION="${ANT_VERSION:-${DEFAULT_ANT_VERSION}}"
ANT_ARCHIVE_CHECKSUM="${ANT_ARCHIVE_CHECKSUM:-${DEFAULT_ANT_ARCHIVE_CHECKSUM}}"

# Not available in Maven
ASMTOOLS_SRC_TAG="${ASMTOOLS_SRC_TAG:-${DEFAULT_ASMTOOLS_SRC_TAG}}"
ASMTOOLS_SRC_ARCHIVE_CHECKSUM="${ASMTOOLS_SRC_ARCHIVE_CHECKSUM:-${DEFAULT_ASMTOOLS_SRC_ARCHIVE_CHECKSUM}}"

GOOGLE_GUICE_VERSION="${GOOGLE_GUICE_VERSION:-${DEFAULT_GOOGLE_GUICE_VERSION}}"
GOOGLE_GUICE_JAR_URL_BASE="${GOOGLE_GUICE_JAR_URL_BASE:-${MAVEN_REPO_URL_BASE}}"
GOOGLE_GUICE_JAR_CHECKSUM="${GOOGLE_GUICE_JAR_CHECKSUM:-${DEFAULT_GOOGLE_GUICE_JAR_CHECKSUM}}"

JCOMMANDER_VERSION="${JCOMMANDER_VERSION:-${DEFAULT_JCOMMANDER_VERSION}}"
JCOMMANDER_JAR_URL_BASE="${JCOMMANDER_JAR_URL_BASE:-${MAVEN_REPO_URL_BASE}}"
JCOMMANDER_JAR_CHECKSUM="${JCOMMANDER_JAR_CHECKSUM:-${DEFAULT_JCOMMANDER_JAR_CHECKSUM}}"

# Not available in Maven
JTHARNESS_SRC_TAG="${JTHARNESS_SRC_TAG:-${DEFAULT_JTHARNESS_SRC_TAG}}"
JTHARNESS_SRC_ARCHIVE_CHECKSUM="${JTHARNESS_SRC_ARCHIVE_CHECKSUM:-${DEFAULT_JTHARNESS_SRC_ARCHIVE_CHECKSUM}}"

JUNIT_VERSION="${JUNIT_VERSION:-${DEFAULT_JUNIT_VERSION}}"
JUNIT_JAR_URL_BASE="${JUNIT_JAR_URL_BASE:-${MAVEN_REPO_URL_BASE}}"
JUNIT_JAR_CHECKSUM="${JUNIT_JAR_CHECKSUM:-${DEFAULT_JUNIT_JAR_CHECKSUM}}"
JUNIT_LICENSE_FILE="${JUNIT_LICENSE_FILE:-${DEFAULT_JUNIT_LICENSE_FILE}}"

TESTNG_VERSION="${TESTNG_VERSION:-${DEFAULT_TESTNG_VERSION}}"
TESTNG_JAR_URL_BASE="${TESTNG_JAR_URL_BASE:-${MAVEN_REPO_URL_BASE}}"
TESTNG_JAR_CHECKSUM="${TESTNG_JAR_CHECKSUM:-${DEFAULT_TESTNG_JAR_CHECKSUM}}"
TESTNG_LICENSE_VERSION="${TESTNG_LICENSE_VERSION:-${DEFAULT_TESTNG_LICENSE_VERSION:-${TESTNG_VERSION}}}"
TESTNG_LICENSE_CHECKSUM="${TESTNG_LICENSE_CHECKSUM:-${DEFAULT_TESTNG_LICENSE_CHECKSUM}}"

if [ "${SHOW_DEFAULT_VERSIONS:-}" != "" ]; then
    find ${mydir} -name version-numbers | \
        xargs cat | \
        grep -v '^#' | \
        grep -E 'DEFAULT.*(_VERSION|_SRC_TAG)' | \
        sort -u
    exit
fi

if [ "${SHOW_CONFIG_DETAILS:-}" != "" ]; then
    ( set -o posix ; set ) | \
        grep -E '^(ANT|ASM|ASMTOOLS|GOOGLE_GUICE|JCOMMANDER|JTHARNESS|JUNIT|TESTNG)_[A-Z_]*=' | \
        sort -u
    exit
fi

setup_java_home() {
    check_arguments "${FUNCNAME}" 0 $#

    if [ -n "${JAVA_HOME:-}" ]; then
        return
    fi

    if [ -z "${JDK_ARCHIVE_URL:-}" ]; then
        if [ -n "${JDK_ARCHIVE_URL_BASE:-}" ]; then
            if [ -z "${JDK_VERSION:-}" ]; then
                error "JDK_VERSION not set"
                exit 1
            fi
            if [ -z "${JDK_BUILD_NUMBER:-}" ]; then
                error "JDK_BUILD_NUMBER not set"
                exit 1
            fi
            if [ -z "${JDK_FILE:-}" ]; then
                error "JDK_FILE not set"
                exit 1
            fi
            JDK_ARCHIVE_URL="${JDK_ARCHIVE_URL_BASE}/${JDK_VERSION}/${JDK_BUILD_NUMBER}/${JDK_FILE}"
        fi
    fi

    local JDK_DEPS_DIR="${DEPS_DIR}"

    if [ -n "${JDK_ARCHIVE_URL:-}" ]; then
        local JDK_LOCAL_ARCHIVE_FILE="${DEPS_DIR}/$(basename "${JDK_ARCHIVE_URL}")"
        if [ -n "${JDK_ARCHIVE_CHECKSUM:-}" ]; then
            get_archive "${JDK_ARCHIVE_URL}" "${JDK_LOCAL_ARCHIVE_FILE}" "${JDK_DEPS_DIR}" "${JDK_ARCHIVE_CHECKSUM}"
        else
            get_archive_no_checksum "${JDK_ARCHIVE_URL}" "${JDK_LOCAL_ARCHIVE_FILE}" "${JDK_DEPS_DIR}"
        fi
        local JDK_JAVAC="$(find "${JDK_DEPS_DIR}" -path '*/bin/javac')"
        JAVA_HOME="$(dirname $(dirname "${JDK_JAVAC}"))"
        return
    fi

    error "None of JAVA_HOME, JDK_ARCHIVE_URL or JDK_ARCHIVE_URL_BASE are set"
    exit 1
}

sanity_check_java_home() {
    if [ -z "${JAVA_HOME:-}" ]; then
        error "No JAVA_HOME set"
        exit 1
    fi

    if [ ! -d "${JAVA_HOME}" ]; then
        error "'${JAVA_HOME}' is not a directory"
        exit 1
    fi

    if [ ! -x "${JAVA_HOME}/bin/java" ]; then
        error "Could not find an executable binary at '${JAVA_HOME}/bin/java'"
        exit 1
    fi

    local version=$(${JAVA_HOME}/bin/java -version 2>&1)
    local vnum=$(echo "${version}" | \
        grep -e ^java -e ^openjdk |
        head -n 1 | \
        sed -e 's/^[^0-9]*\(1\.\)*\([1-9][0-9]*\).*/\2/' )
    if [ "${vnum:-0}" -lt "11" ]; then
        error "JDK 11 or newer is required to build jtreg"
        exit 1
    fi
    JAVA_SPECIFICATION_VERSION=${vnum}
}

checkJavaOSVersion() {
  # This checks that the value in the Java "os.version" system property
  # is as expected.  While it is OK to *build* jtreg with a JDK with this bug,
  # some of the `jtreg` self-tests will fail: notably, test/problemList.
  # See https://bugs.openjdk.org/browse/JDK-8253702
  case `uname` in
    Darwin )
      OS_VERSION=`defaults read loginwindow SystemVersionStampAsString`
      ${JAVA_HOME}/bin/java ${mydir}/CheckJavaOSVersion.java ${OS_VERSION}
  esac
}

setup_java_home
sanity_check_java_home
#checkJavaOSVersion   #temp: check for presence of the JDK os.version bug (JDK-8253702)
export JAVA_HOME
info "JAVA_HOME: ${JAVA_HOME}"

#----- Ant -----
setup_ant
info "ANT: ${ANT}"

#----- JT Harness -----
setup_jtharness_javatest_jar() {
    check_arguments "${FUNCNAME}" 0 $#

    if [ -n "${JTHARNESS_JAVATEST_JAR:-}" ]; then
        return
    fi

    if [ -z "${JTHARNESS_ARCHIVE_URL:-}" ]; then
        if [ -n "${JTHARNESS_ARCHIVE_URL_BASE:-}" ]; then
            JTHARNESS_ARCHIVE_URL="${JTHARNESS_ARCHIVE_URL_BASE}/${JTHARNESS_VERSION}/${JTHARNESS_BUILD_NUMBER}/${JTHARNESS_FILE}"
        fi
    fi

    local JTHARNESS_DEPS_DIR="${DEPS_DIR}/jtharness"

    if [ -n "${JTHARNESS_ARCHIVE_URL:-}" ]; then
        local JTHARNESS_LOCAL_ARCHIVE_FILE="${DEPS_DIR}/$(basename "${JTHARNESS_ARCHIVE_URL}")"
        get_archive "${JTHARNESS_ARCHIVE_URL}" "${JTHARNESS_LOCAL_ARCHIVE_FILE}" "${JTHARNESS_DEPS_DIR}" "${JTHARNESS_ARCHIVE_CHECKSUM}"
        JTHARNESS_JAVATEST_JAR="$(find "${JTHARNESS_DEPS_DIR}" -path '*/lib/javatest.jar')"
        JTHARNESS_LICENSE="$(dirname "${JTHARNESS_JAVATEST_JAR}")/../LICENSE"
        JTHARNESS_COPYRIGHT="$(dirname "${JTHARNESS_JAVATEST_JAR}")/../legal/copyright.txt"
        return
    fi

    info "None of JTHARNESS_JAVATEST_JAR, JTHARNESS_ARCHIVE_URL or JTHARNESS_ARCHIVE_URL_BASE are set; building from source"
    export JTHARNESS_BUILD_RESULTS_FILE="${DEPS_DIR}/jtharness.results"
    (
        export BUILD_DIR="${JTHARNESS_DEPS_DIR}"
        export BUILD_RESULTS_FILE="${JTHARNESS_BUILD_RESULTS_FILE}"
        export JTHARNESS_SRC_TAG="${JTHARNESS_SRC_TAG}"
        export JTHARNESS_SRC_ARCHIVE_CHECKSUM="${JTHARNESS_SRC_ARCHIVE_CHECKSUM}"
        export ANT="${ANT}"
        bash "${mydir}/build-support/jtharness/build.sh"
    )
    ret=$?
    if [ ! $ret = 0 ]; then
        exit ${ret}
    fi
    . "${JTHARNESS_BUILD_RESULTS_FILE}"
}
setup_jtharness_javatest_jar
info "JTHARNESS_JAVATEST_JAR: ${JTHARNESS_JAVATEST_JAR}"

#----- JT Harness License and Copyright -----
setup_jtharness_license_and_copyright() {
    check_arguments "${FUNCNAME}" 0 $#

    if [ -n "${JTHARNESS_LICENSE:-}" -a -n "${JTHARNESS_COPYRIGHT:-}" ]; then
        return
    fi

    if [ -z "${JTHARNESS_SRC:-}" ]; then
        local JTHARNESS_SRC_DEPS_DIR="${DEPS_DIR}/jtharness-src"
        local JTHARNESS_LOCAL_SRC_ARCHIVE="${JTHARNESS_SRC_DEPS_DIR}/source.zip"
        get_archive "${CODE_TOOLS_URL_BASE}/jtharness/archive/${JTHARNESS_SRC_VERSION}.zip" "${JTHARNESS_LOCAL_SRC_ARCHIVE}" "${JTHARNESS_SRC_DEPS_DIR}" "${JTHARNESS_SRC_ARCHIVE_CHECKSUM}"
        JTHARNESS_SRC="${JTHARNESS_SRC_DEPS_DIR}/jtharness-${JTHARNESS_SRC_VERSION}"
    fi
    JTHARNESS_LICENSE="${JTHARNESS_SRC}/LICENSE"
    JTHARNESS_COPYRIGHT="${JTHARNESS_SRC}/legal/copyright.txt"
}
setup_jtharness_license_and_copyright
info "JTHARNESS_LICENSE: ${JTHARNESS_LICENSE}"
info "JTHARNESS_COPYRIGHT: ${JTHARNESS_COPYRIGHT}"

#----- AsmTools -----
setup_asmtools() {
    check_arguments "${FUNCNAME}" 0 $#

    if [ -n "${ASMTOOLS_JAR:-}" -a -n "${ASMTOOLS_LICENSE:-}" ]; then
        return
    fi

    if [ -z "${ASMTOOLS_ARCHIVE_URL:-}" ]; then
        if [ -n "${ASMTOOLS_ARCHIVE_URL_BASE:-}" ]; then
            if [ -z "${ASMTOOLS_VERSION:-}" ]; then
                error "ASMTOOLS_VERSION not set"
                exit 1
            fi
            if [ -z "${ASMTOOLS_BUILD_NUMBER:-}" ]; then
                error "ASMTOOLS_BUILD_NUMBER not set"
                exit 1
            fi
            if [ -z "${ASMTOOLS_FILE:-}" ]; then
                error "ASMTOOLS_FILE not set"
                exit 1
            fi
            ASMTOOLS_ARCHIVE_URL="${ASMTOOLS_ARCHIVE_URL_BASE}/${ASMTOOLS_VERSION}/${ASMTOOLS_BUILD_NUMBER}/${ASMTOOLS_FILE}"
        fi
    fi

    local ASMTOOLS_DEPS_DIR="${DEPS_DIR}/asmtools"

    if [ -n "${ASMTOOLS_ARCHIVE_URL:-}" ]; then
        local ASMTOOLS_LOCAL_ARCHIVE_FILE="${DEPS_DIR}/$(basename "${ASMTOOLS_ARCHIVE_URL}")"
        get_archive "${ASMTOOLS_ARCHIVE_URL}" "${ASMTOOLS_LOCAL_ARCHIVE_FILE}" "${ASMTOOLS_DEPS_DIR}" "${ASMTOOLS_ARCHIVE_CHECKSUM}"
        ASMTOOLS_JAR="$(find "${ASMTOOLS_DEPS_DIR}" -name asmtools.jar)"
        ASMTOOLS_LICENSE="$(dirname "${ASMTOOLS_JAR}")/../LICENSE"
        return
    fi

    info "None of ASMTOOLS_JAR, ASMTOOLS_ARCHIVE_URL or ASMTOOLS_ARCHIVE_URL_BASE are set; building from source"
    export ASMTOOLS_BUILD_RESULTS_FILE="${DEPS_DIR}/asmtools.results"
    (
        export BUILD_DIR="${ASMTOOLS_DEPS_DIR}"
        export BUILD_RESULTS_FILE="${ASMTOOLS_BUILD_RESULTS_FILE}"
        export ANT="${ANT}"
        export ASMTOOLS_SRC_TAG="${ASMTOOLS_SRC_TAG}"
        bash "${mydir}/build-support/asmtools/build.sh"
    )
    ret=$?
    if [ ! $ret = 0 ]; then
        exit ${ret}
    fi
    . "${ASMTOOLS_BUILD_RESULTS_FILE}"
}
setup_asmtools
info "ASMTOOLS_JAR: ${ASMTOOLS_JAR}"
info "ASMTOOLS_LICENSE: ${ASMTOOLS_LICENSE}"

#----- JUnit -----
setup_junit() {
    check_arguments "${FUNCNAME}" 0 $#

    if [ -n "${JUNIT_JAR:-}" ]; then
        return
    fi

    if [ -z "${JUNIT_JAR_URL:-}" ]; then
        if [ -n "${JUNIT_JAR_URL_BASE:-}" ]; then
            JUNIT_JAR_URL="${JUNIT_JAR_URL_BASE}/org/junit/platform/junit-platform-console-standalone/${JUNIT_VERSION}/junit-platform-console-standalone-${JUNIT_VERSION}.jar"
        fi
    fi

    local JUNIT_DEPS_DIR="${DEPS_DIR}/junit"

    if [ -n "${JUNIT_JAR_URL:-}" ]; then
        JUNIT_JAR="${JUNIT_DEPS_DIR}/$(basename ${JUNIT_JAR_URL})"
        download_and_checksum "${JUNIT_JAR_URL}" "${JUNIT_JAR}" "${JUNIT_JAR_CHECKSUM}"
        return
    fi

    error "None of JUNIT_JAR, JUNIT_JAR_URL or JUNIT_JAR_URL_BASE is set"
    exit 1
}
setup_junit
info "JUNIT_JAR ${JUNIT_JAR}"

#----- JUnit license -----
setup_junit_license() {
    check_arguments "${FUNCNAME}" 0 $#

    if [ -n "${JUNIT_LICENSE:-}" ]; then
        return
    fi

    local JUNIT_LICENSE_DEPS_DIR="${DEPS_DIR}/junit-license"
    "${UNZIP_CMD}" ${UNZIP_OPTIONS} "${JUNIT_JAR}" ${JUNIT_LICENSE_FILE} -d "${JUNIT_LICENSE_DEPS_DIR}"
    JUNIT_LICENSE="${JUNIT_LICENSE_DEPS_DIR}/${JUNIT_LICENSE_FILE}"
}
setup_junit_license
info "JUNIT_LICENSE: ${JUNIT_LICENSE}"

#----- TestNG -----
setup_testng() {
    check_arguments "${FUNCNAME}" 0 $#

    if [ -n "${TESTNG_JAR:-}" ]; then
        return
    fi

    if [ -z "${TESTNG_JAR_URL:-}" ]; then
        if [ -n "${TESTNG_JAR_URL_BASE:-}" ]; then
            TESTNG_JAR_URL="${TESTNG_JAR_URL_BASE}/org/testng/testng/${TESTNG_VERSION}/testng-${TESTNG_VERSION}.jar"
        fi
    fi

    local TESTNG_DEPS_DIR="${DEPS_DIR}/testng"

    if [ -n "${TESTNG_JAR_URL:-}" ]; then
        TESTNG_JAR="${TESTNG_DEPS_DIR}/$(basename "${TESTNG_JAR_URL}")"
        download_and_checksum "${TESTNG_JAR_URL}" "${TESTNG_JAR}" "${TESTNG_JAR_CHECKSUM}"
        return
    fi

    error "None of TESTNG_JAR, TESTNG_JAR_URL or TESTNG_JAR_URL_BASE are set"
    exit 1
}
setup_testng
info "TESTNG_JAR: ${TESTNG_JAR}"

#----- TestNG License -----
setup_testng_license() {
    check_arguments "${FUNCNAME}" 0 $#

    if [ -n "${TESTNG_LICENSE:-}" ]; then
        return
    fi

    local TESTNG_LICENSE_DEPS_DIR="${DEPS_DIR}/testng-license"
    TESTNG_LICENSE="${TESTNG_LICENSE_DEPS_DIR}/LICENSE.txt"
    download_and_checksum "https://raw.githubusercontent.com/cbeust/testng/${TESTNG_LICENSE_VERSION}/LICENSE.txt" "${TESTNG_LICENSE}" "${TESTNG_LICENSE_CHECKSUM}"
}
setup_testng_license
info "TESTNG_LICENSE: ${TESTNG_LICENSE}"

#----- JCommander (required by TestNG) -----
setup_jcommander() {
    check_arguments "${FUNCNAME}" 0 $#

    if [ -n "${JCOMMANDER_JAR:-}" ]; then
        return
    fi

    if [ -z "${JCOMMANDER_JAR_URL:-}" ]; then
        if [ -n "${JCOMMANDER_JAR_URL_BASE:-}" ]; then
            JCOMMANDER_JAR_URL="${JCOMMANDER_JAR_URL_BASE}/com/beust/jcommander/${JCOMMANDER_VERSION}/jcommander-${JCOMMANDER_VERSION}.jar"
        fi
    fi

    local JCOMMANDER_DEPS_DIR="${DEPS_DIR}/jcommander"

    if [ -n "${JCOMMANDER_JAR_URL:-}" ]; then
        JCOMMANDER_JAR="${JCOMMANDER_DEPS_DIR}/$(basename "${JCOMMANDER_JAR_URL}")"
        download_and_checksum "${JCOMMANDER_JAR_URL}" "${JCOMMANDER_JAR}" "${JCOMMANDER_JAR_CHECKSUM}"
        return
    fi

    error "None of JCOMMANDER_JAR, JCOMMANDER_JAR_URL or JCOMMANDER_JAR_URL_BASE are set"
    exit 1
}
setup_jcommander
info "JCOMMANDER_JAR: ${JCOMMANDER_JAR}"

#----- Google Guice (required by TestNG) -----
setup_google_guice() {
    check_arguments "${FUNCNAME}" 0 $#

    if [ -n "${GOOGLE_GUICE_JAR:-}" ]; then
        return
    fi

    if [ -z "${GOOGLE_GUICE_JAR_URL:-}" ]; then
        if [ -n "${GOOGLE_GUICE_JAR_URL_BASE:-}" ]; then
            GOOGLE_GUICE_JAR_URL="${GOOGLE_GUICE_JAR_URL_BASE}/com/google/inject/guice/${GOOGLE_GUICE_VERSION}/guice-${GOOGLE_GUICE_VERSION}.jar"
        fi
    fi

    local GOOGLE_GUICE_DEPS_DIR="${DEPS_DIR}/guice"

    if [ -n "${GOOGLE_GUICE_JAR_URL:-}" ]; then
        GOOGLE_GUICE_JAR="${GOOGLE_GUICE_DEPS_DIR}/$(basename "${GOOGLE_GUICE_JAR_URL}")"
        download_and_checksum "${GOOGLE_GUICE_JAR_URL}" "${GOOGLE_GUICE_JAR}" "${GOOGLE_GUICE_JAR_CHECKSUM}"
        return
    fi

    error "None of GOOGLE_GUICE_JAR, GOOGLE_GUICE_JAR_URL or GOOGLE_GUICE_JAR_URL_BASE are set"
    exit 1
}
setup_google_guice
info "GOOGLE_GUICE_JAR: ${GOOGLE_GUICE_JAR}"

#-----
# Create aggregate settings

ASMTOOLS_NOTICES="$(mixed_path "${ASMTOOLS_LICENSE}")"
info "ASMTOOLS_NOTICES: ${ASMTOOLS_NOTICES}"

JTHARNESS_NOTICES="$(mixed_path "${JTHARNESS_COPYRIGHT}") $(mixed_path "${JTHARNESS_LICENSE}")"
info "JTHARNESS_NOTICES: ${JTHARNESS_NOTICES}"

TESTNG_JARS="$(mixed_path "${TESTNG_JAR}") $(mixed_path "${GOOGLE_GUICE_JAR}") $(mixed_path "${JCOMMANDER_JAR}")"
info "TESTNG_JARS: ${TESTNG_JARS}"
TESTNG_NOTICES="$(mixed_path "${TESTNG_LICENSE}")"
info "TESTNG_NOTICES: ${TESTNG_NOTICES}"

JUNIT_JARS="$(mixed_path "${JUNIT_JAR}")"
info "JUNIT_JARS: ${JUNIT_JARS}"
JUNIT_NOTICES="$(mixed_path "${JUNIT_LICENSE}")"
info "JUNIT_NOTICES: ${JUNIT_NOTICES}"

##
# The build version typically comes from the version-numbers file;
# It is expected that the build number will typically come from an external CI system.
#
setup_build_info() {
    check_arguments "${FUNCNAME}" 0 $#

    JTREG_BUILD_MILESTONE="${JTREG_BUILD_MILESTONE:-dev}"
    JTREG_BUILD_NUMBER="${JTREG_BUILD_NUMBER:-0}"

    if [ -z "${JTREG_VERSION_STRING:-}" ]; then
        MILESTONE=""
        if [ -n "${JTREG_BUILD_MILESTONE}" ]; then
            MILESTONE="-${JTREG_BUILD_MILESTONE}"
        fi
        JTREG_VERSION_STRING="${JTREG_VERSION}${MILESTONE}+${JTREG_BUILD_NUMBER}"
    fi
}
setup_build_info
info "JTREG_VERSION: ${JTREG_VERSION}"
info "JTREG_BUILD_NUMBER: ${JTREG_BUILD_NUMBER}"
info "JTREG_BUILD_MILESTONE: ${JTREG_BUILD_MILESTONE}"

check_files() {
    for i in "$@" ; do
        check_file "$i"
    done
}

check_file() {
    check_arguments "${FUNCNAME}" 1 $#

    info "Checking $1"
    if [ ! -f "$1" ]; then
        error "Missing: $1"
        exit 1
    fi
}

check_dir() {
    check_arguments "${FUNCNAME}" 1 $#

    info "Checking $1"
    if [ ! -d "$1" ]; then
        error "Missing: $1"
        exit 1
    fi
}

check_file  "${ANT}"
check_file  "${ASMTOOLS_JAR}"
check_files  ${ASMTOOLS_NOTICES}
check_dir   "${JAVA_HOME}"
check_file  "${JTHARNESS_JAVATEST_JAR}"
check_files  ${JTHARNESS_NOTICES}
check_files  ${JUNIT_JARS}
check_files  ${JUNIT_NOTICES}
check_files  ${TESTNG_JARS}
check_files  ${TESTNG_NOTICES}

if [ -n "${SKIP_MAKE:-}" ]; then
    exit
fi

MAKE=$(which make)
setup_make() {
    case `uname` in
      FreeBSD )
          MAKE=/usr/local/bin/gmake ;;
    esac
}
setup_make
check_file "${MAKE}"

# save make command for possible later reuse, bypassing this script
mkdir -p ${BUILD_DIR}
cat > ${BUILD_DIR}/make.sh << EOF
#!/bin/sh

cd "${ROOT}/make"
${MAKE} ASMTOOLS_JAR="${ASMTOOLS_JAR}"                           \\
     ASMTOOLS_NOTICES="${ASMTOOLS_NOTICES}"                   \\
     BUILDDIR="${BUILD_DIR}"                                  \\
     BUILD_MILESTONE="${JTREG_BUILD_MILESTONE}"               \\
     BUILD_NUMBER="${JTREG_BUILD_NUMBER}"                     \\
     BUILD_VERSION="${JTREG_VERSION}"                         \\
     BUILD_VERSION_STRING="${JTREG_VERSION_STRING}"           \\
     JAVATEST_JAR="$(mixed_path "${JTHARNESS_JAVATEST_JAR}")" \\
     JAVA_SPECIFICATION_VERSION="${JAVA_SPECIFICATION_VERSION}" \\
     JDKHOME="$(mixed_path ${JAVA_HOME})"                     \\
     JTHARNESS_NOTICES="${JTHARNESS_NOTICES}"                 \\
     JTREG_HOME=""                                            \\
     JT_HOME=""                                               \\
     JUNIT_JARS="${JUNIT_JARS}"                               \\
     JUNIT_NOTICES="${JUNIT_NOTICES}"                         \\
     TESTNG_JARS="${TESTNG_JARS}"                             \\
     TESTNG_NOTICES="${TESTNG_NOTICES}"                       \\
   "\$@"
EOF

sh ${BUILD_DIR}/make.sh ${MAKE_ARGS:-}
