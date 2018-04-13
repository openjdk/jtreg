#!/bin/bash

#
# Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

set -e
set -u

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 /path/to/jdk/1.8/image" >&2
    exit 1
fi

if [ ! -d $1 ]; then
    echo "Error: '$1' is not a directory" >&2
    exit 1
fi

if [ ! -x $1/bin/java ]; then
    echo "Error: Could not find an executable binary at '$1/bin/java'" >&2
    exit 1
fi

JAVA_VERSION=$($1/bin/java -version 2>&1 | grep "^.* version \".*\"$" | sed 's/.*\"\(.*\)\".*/\1/')

if case ${JAVA_VERSION} in 1.8*) false ;; *) true; esac; then
    echo "Error: Expected a path to JDK with version 1.8, got version ${JAVA_VERSION}" >&2
    exit 1
fi

export JAVA_HOME=$1
export PATH="$JAVA_HOME:$PATH"

if [ -n `which sha1sum` ]; then
    SHASUM=sha1sum;
elif [ -n `which shasum` ]; then
    SHASUM="shasum -a 1"
else
    echo "Error: can't find shasum or sha1sum" >&2
    exit 1
fi

UNZIP=unzip
UNZIP_OPTS=${UNZIP_OPTS:--q}
WGET=wget
WGET_OPTS=${WGET_OPTS:--q}

ROOT=$(hg root)
BUILD_DIR=${BUILD_DIR:-${ROOT}/build}

if [ -d ${BUILD_DIR} ]; then
    echo "Error: Build directory '${BUILD_DIR}' already exists" >&2
    exit 1
fi

mkdir ${BUILD_DIR}


# DEPENDENCIES
##############

APACHE_ANT_URL=https://archive.apache.org/dist/ant/binaries
MAVEN_REPO_URL=https://repo1.maven.org/maven2

CODE_TOOLS_URL=http://hg.openjdk.java.net/code-tools
# The following are Mercurial tags for the corresponding OpenJDK Code Tools repo
ASMTOOLS_VERSION=${ASMTOOLS_VERSION:-7.0-b04} # early access for 7.0
JTHARNESS_VERSION=${JTHARNESS_VERSION:-jt5.0-b01} # jt5.0 + build fixes
JCOV_VERSION=${JCOV_VERSION:-jcov3.0-b04} # jcov3.0 + html5 reports

# ANT
#####

ANT_DIR=${BUILD_DIR}/ant
mkdir ${ANT_DIR}

ANT_VERSION=${ANT_VERSION:-apache-ant-1.9.4}
ANT_ZIP=${ANT_DIR}/${ANT_VERSION}.zip
${WGET} ${WGET_OPTS} ${APACHE_ANT_URL}/${ANT_VERSION}-bin.zip -O ${ANT_ZIP}
echo "ec57a35eb869a307abdfef8712f3688fff70887f  ${ANT_ZIP}" | ${SHASUM} --check -
${UNZIP} ${UNZIP_OPTS} -d ${ANT_DIR} ${ANT_ZIP}

ANT_JAR=${ANT_DIR}/${ANT_VERSION}/lib/ant.jar
ANT=${ANT_DIR}/${ANT_VERSION}/bin/ant

# ASMTOOLS
##########

ASMTOOLS_BUILD_DIR=${BUILD_DIR}/asmtools
mkdir ${ASMTOOLS_BUILD_DIR}

# Build asmtools
ASMTOOLS_SRC_ZIP=${ASMTOOLS_BUILD_DIR}/source.zip
${WGET} ${WGET_OPTS} ${CODE_TOOLS_URL}/asmtools/archive/${ASMTOOLS_VERSION}.zip -O ${ASMTOOLS_SRC_ZIP}
${UNZIP} ${UNZIP_OPTS} -d ${ASMTOOLS_BUILD_DIR} ${ASMTOOLS_SRC_ZIP}

if [ "${ASMTOOLS_VERSION}" = "tip" ]; then
    ASMTOOLS_VERSION=`cd ${ASMTOOLS_BUILD_DIR} ; ls -d asmtools-* | sed -e 's/asmtools-//'`
fi

ASMTOOLS_SRC=${ASMTOOLS_BUILD_DIR}/asmtools-${ASMTOOLS_VERSION}
ASMTOOLS_DIST=${ASMTOOLS_BUILD_DIR}/build
${ANT} -DBUILD_DIR=${ASMTOOLS_DIST} -f ${ASMTOOLS_SRC}/build/build.xml
ASMTOOLS_JAR=${ASMTOOLS_DIST}/binaries/lib/asmtools.jar
ASMTOOLS_LICENSE=${ASMTOOLS_SRC}/LICENSE

# JAVATEST
##########

JTHARNESS_BUILD_DIR=${BUILD_DIR}/jtharness
mkdir ${JTHARNESS_BUILD_DIR}

# Build jtharness
JTHARNESS_SRC_ZIP=${JTHARNESS_BUILD_DIR}/source.zip
${WGET} ${WGET_OPTS} ${CODE_TOOLS_URL}/jtharness/archive/${JTHARNESS_VERSION}.zip -O ${JTHARNESS_SRC_ZIP}
${UNZIP} ${UNZIP_OPTS} -d ${JTHARNESS_BUILD_DIR} ${JTHARNESS_SRC_ZIP}

if [ "${JTHARNESS_VERSION}" = "tip" ]; then
    JTHARNESS_VERSION=`cd ${JTHARNESS_BUILD_DIR} ; ls -d jtharness-* | sed -e 's/jtharness-//'`
fi

JTHARNESS_SRC=${JTHARNESS_BUILD_DIR}/jtharness-${JTHARNESS_VERSION}
JTHARNESS_DIST=${JTHARNESS_BUILD_DIR}/build
${ANT} -DBUILD_DIR=${JTHARNESS_DIST} -f ${JTHARNESS_SRC}/build/build.xml

JAVATEST_JAR=${JTHARNESS_DIST}/binaries/lib/javatest.jar
JTHARNESS_LICENSE=${JTHARNESS_SRC}/legal/license.txt
JTHARNESS_COPYRIGHT=${JTHARNESS_SRC}/legal/copyright.txt

# JCOV
######

JCOV_BUILD_DIR=${BUILD_DIR}/jcov
mkdir ${JCOV_BUILD_DIR}

# Get jcov dependencies
JCOV_DEPS_DIR=${JCOV_BUILD_DIR}/deps
mkdir ${JCOV_DEPS_DIR}

ASM_JAR=${JCOV_DEPS_DIR}/asm-6.0.jar
${WGET} ${WGET_OPTS} ${MAVEN_REPO_URL}/org/ow2/asm/asm/6.0/asm-6.0.jar -O ${ASM_JAR}
echo "bc6fa6b19424bb9592fe43bbc20178f92d403105  ${ASM_JAR}" | ${SHASUM} --check -

ASM_TREE_JAR=${JCOV_DEPS_DIR}/asm-tree-6.0.jar
${WGET} ${WGET_OPTS} ${MAVEN_REPO_URL}/org/ow2/asm/asm-tree/6.0/asm-tree-6.0.jar -O ${ASM_TREE_JAR}
echo "a624f1a6e4e428dcd680a01bab2d4c56b35b18f0  ${ASM_TREE_JAR}" | ${SHASUM} --check -

ASM_UTIL_JAR=${JCOV_DEPS_DIR}/asm-utils-6.0.jar
${WGET} ${WGET_OPTS} ${MAVEN_REPO_URL}/org/ow2/asm/asm-util/6.0/asm-util-6.0.jar -O ${ASM_UTIL_JAR}
echo "430b2fc839b5de1f3643b528853d5cf26096c1de  ${ASM_UTIL_JAR}" | ${SHASUM} --check -

# Build jcov
JCOV_SRC_ZIP=${JCOV_BUILD_DIR}/source.zip
${WGET} ${WGET_OPTS} ${CODE_TOOLS_URL}/jcov/archive/${JCOV_VERSION}.zip -O ${JCOV_SRC_ZIP}
${UNZIP} ${UNZIP_OPTS} -d ${JCOV_BUILD_DIR} ${JCOV_SRC_ZIP}

if [ "${JCOV_VERSION}" = "tip" ]; then
    JCOV_VERSION=`cd ${JCOV_BUILD_DIR} ; ls -d jcov-* | sed -e 's/jcov-//'`
fi

JCOV_SRC=${JCOV_BUILD_DIR}/jcov-${JCOV_VERSION}
JCOV_DIST=${JCOV_BUILD_DIR}/build
( cd ${JCOV_SRC}/build
${ANT} -Dresult.dir=${JCOV_DIST}      \
    -Dasm.jar=${ASM_JAR}           \
    -Dasm.tree.jar=${ASM_TREE_JAR} \
    -Dasm.util.jar=${ASM_UTIL_JAR} \
    -Djavatestjar=${JAVATEST_JAR}  \
    -Dverify.strict=               \
    -f ${JCOV_SRC}/build/build.xml
)

JCOV_JAR=${JCOV_DIST}/jcov_3.0/jcov.jar
JCOV_NETWORK_SAVER_JAR=${JCOV_DIST}/jcov_3.0/jcov_network_saver.jar
JCOV_LICENSE=${JCOV_SRC}/LICENSE

# JTREG
#######

# Get jtreg dependencies
JTREG_DEPS_DIR=${BUILD_DIR}/deps
mkdir ${JTREG_DEPS_DIR}

## JUnit
JUNIT_DEPS_DIR=${JTREG_DEPS_DIR}/junit
mkdir ${JUNIT_DEPS_DIR}

JUNIT_JAR=${JUNIT_DEPS_DIR}/junit-4.10.jar
${WGET} ${WGET_OPTS} ${MAVEN_REPO_URL}/junit/junit/4.10/junit-4.10.jar -O ${JUNIT_JAR}
echo "e4f1766ce7404a08f45d859fb9c226fc9e41a861  ${JUNIT_JAR}" | ${SHASUM} --check -

${UNZIP} ${UNZIP_OPTS} ${JUNIT_JAR} LICENSE.txt -d ${JUNIT_DEPS_DIR}
JUNIT_LICENSE=${JUNIT_DEPS_DIR}/LICENSE.txt

## TestNG
TESTNG_DEPS_DIR=${JTREG_DEPS_DIR}/testng
mkdir ${TESTNG_DEPS_DIR}

TESTNG_JAR=${TESTNG_DEPS_DIR}/testng-6.9.5.jar
${WGET} ${WGET_OPTS} ${MAVEN_REPO_URL}/org/testng/testng/6.9.5/testng-6.9.5.jar -O ${TESTNG_JAR}
echo "5d12ea207fc47c3f341a3f8ecc88a3eac396a777  ${TESTNG_JAR}" | ${SHASUM} --check -

TESTNG_LICENSE=${TESTNG_DEPS_DIR}/LICENSE.txt
${WGET} ${WGET_OPTS} https://raw.githubusercontent.com/cbeust/testng/testng-6.9.5/LICENSE.txt -O ${TESTNG_LICENSE}

JCOMMANDER_JAR=${TESTNG_DEPS_DIR}/jcommander-1.72.jar
${WGET} ${WGET_OPTS} ${MAVEN_REPO_URL}/com/beust/jcommander/1.72/jcommander-1.72.jar -O ${JCOMMANDER_JAR}
echo "6375e521c1e11d6563d4f25a07ce124ccf8cd171  ${JCOMMANDER_JAR}" | ${SHASUM} --check -


## Set version and build numbers to the latest tagged version by default
if [ -z ${BUILD_NUMBER:-} ]; then
    BUILD_NUMBER=`hg tags | grep jtreg | head -1 | sed 's/jtreg\([0-9]\.[0-9]\)-\(b[0-9]*\).*/\2/'`
fi
if [ -z ${BUILD_VERSION:-} ]; then
    BUILD_VERSION=`hg tags | grep jtreg | head -1 | sed 's/jtreg\([0-9]\.[0-9]\)-\(b[0-9]*\).*/\1/'`
fi

# Build jtreg
cd ${ROOT}/make
make JUNIT_JAR=${JUNIT_JAR}                           \
     JUNIT_LICENSE=${JUNIT_LICENSE}                   \
     TESTNG_JAR=${TESTNG_JAR}                         \
     TESTNG_LICENSE=${TESTNG_LICENSE}                 \
     JCOMMANDER_JAR=${JCOMMANDER_JAR}                 \
     ANT=${ANT}                                       \
     ANT_JAR=${ANT_JAR}                               \
     JCOV_JAR=${JCOV_JAR}                             \
     JCOV_LICENSE=${JCOV_LICENSE}                     \
     JCOV_NETWORK_SAVER_JAR=${JCOV_NETWORK_SAVER_JAR} \
     JAVATEST_JAR=${JAVATEST_JAR}                     \
     JTHARNESS_LICENSE=${JTHARNESS_LICENSE}           \
     JTHARNESS_COPYRIGHT=${JTHARNESS_COPYRIGHT}       \
     ASMTOOLS_JAR=${ASMTOOLS_JAR}                     \
     ASMTOOLS_LICENSE=${ASMTOOLS_LICENSE}             \
     BUILD_VERSION=${BUILD_VERSION}                   \
     BUILD_MILESTONE=${BUILD_MILESTONE:=dev}          \
     BUILD_NUMBER=${BUILD_NUMBER}                     \
     JDKHOME=$JAVA_HOME                               \
     ${MAKE_ARGS:-}

