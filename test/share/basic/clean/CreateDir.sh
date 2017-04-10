OS=`uname -s`
case "$OS" in
  SunOS )
    FILESEP="/" ;;
  Windows_95 | Windows_NT )
    FILESEP="\\" ;;
  * )
    echo "Unrecognized system!"
esac

CURDIR="."

mkdir ${CURDIR}${FILESEP}testDir

date > "${CURDIR}${FILESEP}elk"
date > "${CURDIR}${FILESEP}testDir${FILESEP}cow"
date > "${CURDIR}${FILESEP}testDir${FILESEP}moose"

echo "Directory structure created"
