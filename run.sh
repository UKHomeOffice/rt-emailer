#!/bin/bash
./activator clean assembly
java -Dkube=true -jar ./target/scala-2.11/rt-emailer-assembly-1.4.4-SNAPSHOT.jar -c ./conf/rt-emailer.conf
