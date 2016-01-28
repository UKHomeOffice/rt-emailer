#!/bin/sh

# Read app version from root level version.properties
caseworkerVersion=$(grep -i ^version ../version.properties | cut -d'=' -f 2)

# Write app version and build number to conf/version.conf
export build_number=${BUILD_NUMBER:-1}
echo "buildNumber=\""$caseworkerVersion"-"$BUILD_NUMBER\" > src/main/resources/version.conf

echo "[build.sh] building with Activator!"
./activator clean compile test publish rpm:package-bin

if [ $? -ne 0 ]; then
  echo "[build.sh] failure"
  exit 1
else
  echo "[build.sh] done"
fi
