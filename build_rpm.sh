#!/bin/sh
version="1.0.0"
cd fpm
cp ../target/scala-2.11/rt-emailer*.jar  build/usr/share/rt-emailer/rt-emailer.jar
fpm -x .git* --config-files etc/rt-emailer/rt-emailer.conf --before-install scripts/preinstall.sh --after-install scripts/postinstall.sh --before-remove scripts/preuninstall.sh --after-remove scripts/postuninstall.sh -C build -t rpm -s dir -d java -n rt-emailer -v "$version-$BUILD_NUMBER" -a all .
