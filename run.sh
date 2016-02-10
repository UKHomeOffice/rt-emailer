#!/bin/bash
./activator clean assembly
java -jar ./target/scala-2.11/rt-emailer-assembly-1.2.0-SNAPSHOT.jar -c ./conf/rt-emailer.conf
