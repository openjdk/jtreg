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

log_message() {
    local level="$1"
    shift
    echo "[${log_module}][${level}] $@"
}

error() {
    log_message "ERROR" "$@"
}

info() {
    log_message "INFO" "$@"
}

##
# Helper used to ensure the correct number of arguments is passed to bash functions
check_arguments() {
    local name="$1"
    local expected="$2"
    local actual="$3"

    if [ ! "${expected}" = "${actual}" ]; then
        error "Incorrect number of arguments to function '${name}' (expecting ${expected} but got ${actual})"
        exit 1
    fi
}

##
# Print an absolute path
abspath() {
    check_arguments "${FUNCNAME}" 1 $#

    local path="$1"

    if [[ ${path} = /* ]]; then
        echo "${path}"
    else
        echo "$PWD/${path}"
    fi
}

##
# Set up the checksum tool to use
#
setup_shasum() {
    if [ -n "${SHASUM:-}" ]; then
        return
    fi

    if [ -n "$(which sha1sum)" ]; then
        SHASUM="sha1sum"
        SHASUM_OPTIONS=""
    elif [ -n "$(which shasum)" ]; then
        SHASUM="shasum"
        SHASUM_OPTIONS="-a 1"
    else
        error "Can't find shasum or sha1sum"
        exit 1
    fi
}

native_path() {
    check_arguments "${FUNCNAME}" 1 $#

    if [ $CYGWIN_OR_MSYS -eq 1 ]; then echo $(cygpath -w $1); else echo "$1"; fi
}

mixed_path() {
    check_arguments "${FUNCNAME}" 1 $#

    if [ $CYGWIN_OR_MSYS -eq 1 ]; then echo $(cygpath -m $1); else echo "$1"; fi
}

##
# Download a file using wget
#
# wget options can be provided through the WGET_OPTIONS environment
# variable
#
download_using_wget() {
    check_arguments "${FUNCNAME}" 2 $#

    local url="$1"
    local destfile="$2"

    set +e
    "${WGET}" ${WGET_OPTIONS} "${url}" -O "${destfile}"
    ret=$?
    if [ ! ${ret} = 0 ]; then
        error "wget exited with exit code ${ret}"
        exit 1
    fi
    set -e
}

##
# Download a file using curl
#
# curl options can be provided through the CURL_OPTIONS environment
# variable
#
download_using_curl() {
    check_arguments "${FUNCNAME}" 2 $#

    local url="$1"
    local destfile="$2"

    set +e
    "${CURL}" ${CURL_OPTIONS} "${url}" -o "${destfile}"
    ret=$?
     if [ ! ${ret} = 0 ]; then
        error "curl exited with exit code ${ret}"
        exit 1
    fi
    set -e
}

##
# Download a file
#
# Will attempt to skip the download if the SKIP_DOWNLOAD environment
# variable is set and the destination file already exists
#
download() {
    check_arguments "${FUNCNAME}" 2 $#

    local url="$1"
    local destfile="$2"

    if [ "${SKIP_DOWNLOAD:-}" != "" -a -r "${destfile}" ]; then
        info "Skipping download of ${url}..."
        return
    fi

    info "Downloading ${url} to ${destfile}"
    mkdir -p "$(dirname "${destfile}")"
    if [ -n "${WGET}" ]; then
        download_using_wget "${url}" "${destfile}"
    elif [ -n "${CURL}" ]; then
        download_using_curl "${url}" "${destfile}"
    else
        error "Cannot find a suitable tool for downloading fils (tried 'wget' and 'curl')"
        exit 1
    fi
}

##
# Checksum a file
#
checksum() {
    check_arguments "${FUNCNAME}" 2 $#

    local file="$1"
    local expected="$2"

    if [ -n "${SKIP_CHECKSUM_CHECK:-}" ]; then
        return
    fi

    if [ x"${expected}" = x"" ]; then
        error "Expected checksum unexpectedly empty.."
        exit 1
    fi

    local actual="$("${SHASUM}" ${SHASUM_OPTIONS} "${dest}" | awk '{ print $1; }')"
    if [ ! x"${actual}" = x"${expected}" ]; then
        error "Checksum mismatch for ${dest}:"
        error "Expected: ${expected}"
        error "Actual  : ${actual}"
        exit 1
    fi
}

##
# Download and checksum a file
#
download_and_checksum() {
    check_arguments "${FUNCNAME}" 3 $#

    local url="$1"
    local dest="$2"
    local shasum="$3"

    download "${url}" "${dest}"
    checksum "${dest}" "${shasum}"
}

##
# Unpack an archive
#
unpack() {
    check_arguments "${FUNCNAME}" 2 $#

    local file="$1"
    local unpackdir="$2"

    info "Unpacking $file in $unpackdir"

    (
        mkdir -p "${unpackdir}"
        case ${file} in
            *.tar.gz)
                "${TAR_CMD}" -xzf "$1" -C "${unpackdir}"
                ;;
            *.zip)
                "${UNZIP_CMD}" -q "$1" -d "${unpackdir}"
                ;;
            *)
                error "Unknown archive type for file '${file}'"
                exit 1
        esac
    )
}

##
# Download and unpack an archive without performing a checksum check
#
get_archive_no_checksum() {
    check_arguments "${FUNCNAME}" 3 $#

    local url="$1"
    local destfile="$2"
    local unpackdir="$3"

    rm -rf "${unpackdir}"/*
    download "${url}" "${destfile}"
    unpack "${destfile}" "${unpackdir}"
}

##
# Download, checksum, and unpack an archive
#
get_archive() {
    check_arguments "${FUNCNAME}" 4 $#

    local url="$1"
    local destfile="$2"
    local unpackdir="$3"
    local shasum="$4"

    rm -rf "${unpackdir}"/*
    download_and_checksum "${url}" "${destfile}" "${shasum}"
    unpack "${destfile}" "${unpackdir}"
}


##
# Set up the ANT (and possibly ANT_JAR) environment variable(s)
#
setup_ant() {
    check_arguments "${FUNCNAME}" 0 $#

    if [ -n "${ANT:-}" ]; then
        return
    fi

    if [ -z "${ANT_ARCHIVE_URL:-}" ]; then
        if [ -n "${ANT_ARCHIVE_URL_BASE:-}" ]; then
            ANT_ARCHIVE_URL="${ANT_ARCHIVE_URL_BASE}/apache-ant-${ANT_VERSION}-bin.zip"
        fi
    fi

    local ANT_DEPS_DIR="${DEPS_DIR}/ant"

    if [ -n "${ANT_ARCHIVE_URL:-}" ]; then
        local ANT_LOCAL_ARCHIVE_FILE="${DEPS_DIR}/$(basename "${ANT_ARCHIVE_URL}")"
        get_archive "${ANT_ARCHIVE_URL}" "${ANT_LOCAL_ARCHIVE_FILE}" "${ANT_DEPS_DIR}" "${ANT_ARCHIVE_CHECKSUM}"
        ANT="$(find "${ANT_DEPS_DIR}" -path '*/bin/ant')"
        ANT_JAR="$(dirname "${ANT}")/../lib/ant.jar"
        return
    fi

    error "Neither ANT_ARCHIVE_URL or ANT_ARCHIVE_URL_BASE is set"
    exit 1
}


set -e
set -u

if [ -z "${mydir:-}" ]; then
    error "mydir not set in caller (line/file): $(caller)"
    exit 1
fi
if [ -z "${log_module:-}" ]; then
    error "log_module not set in caller (line/file): $(caller)"
    exit 1
fi
DEFAULT_ROOT="$(builtin cd ${mydir}/..; pwd)"
ROOT="$(abspath ${ROOT:-${DEFAULT_ROOT}})"
BUILD_DIR="$(abspath "${BUILD_DIR:-${ROOT}/build}")"
DEPS_DIR="${BUILD_DIR}/deps"

export TAR_CMD="${TAR_CMD:-tar}"
export TAR_OPTIONS="${TAR_OPTIONS:-}"
export UNZIP_CMD="${UNZIP_CMD:-unzip}"
export UNZIP_OPTIONS="${UNZIP_OPTIONS:--q} -u"
export WGET="${WGET:-$(which wget)}"
export WGET_OPTIONS="${WGET_OPTIONS:--q}"
export CURL="${CURL:-$(which curl)}"
export CURL_OPTIONS="${CURL_OPTIONS:--s -f -L}"

export MAVEN_REPO_URL_BASE="${MAVEN_REPO_URL_BASE:-https://repo1.maven.org/maven2}"
export CODE_TOOLS_URL_BASE="${CODE_TOOLS_URL_BASE:-https://git.openjdk.org}"
export ANT_ARCHIVE_URL_BASE="${ANT_ARCHIVE_URL_BASE:-https://archive.apache.org/dist/ant/binaries}"

setup_shasum

##
# Support for Cygwin and MSYS2 (which may identify as MSYS, MINGW32 or MINGW64 (the default))
#
case $(uname) in CYGWIN*|MSYS*|MINGW*) CYGWIN_OR_MSYS=1 ;; *) CYGWIN_OR_MSYS=0 ;; esac
info "CYGWIN_OR_MSYS=$CYGWIN_OR_MSYS"
