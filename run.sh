#!/bin/bash
./activator clean assembly
java -jar ./target/scala-2.11/rt-emailer-assembly-1.4.0.jar -c ./conf/rt-emailer.conf
