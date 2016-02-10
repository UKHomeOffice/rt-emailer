#!/bin/sh
echo "[build.sh] Building with Activator!"


# Read app version from root level version.properties
rtEmailerVersion=$(grep -i ^version ./version.properties | cut -d'=' -f 2)


# Write app version and build number to conf/version.conf
export build_number=${BUILD_NUMBER:-1}
echo "rt-emailer-version=\""$rtEmailerVersion"-"$BUILD_NUMBER\" > src/main/resources/version.conf


# Ensure working directory is local to this script
cd "$(dirname "$0")"

./activator clean update test assembly publish

if [ $? -ne 0 ]; then
  echo "[build.sh] failure"
  exit 1
else
  ./activator coverage test coverageReport
    if [ $? -ne 0 ]; then
      echo "[build.sh] failure"
      exit 1
    else
      echo "[build.sh] done"
    fi
fi