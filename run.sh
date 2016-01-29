#!/bin/bash
./activator assembly
java -jar ./target/scala-2.11/rt-emailer-assembly-1.0-SNAPSHOT.jar -c ./conf/rt-emailer.conf
