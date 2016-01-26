#!/bin/bash
unzip -o ./import/* "*.so" -d ./native
java -cp "target/chnlzr-server-0.2.0.jar:import/*" -Djava.library.path=./native -Dorg.slf4j.simpleLogger.defaultLogLevel=debug org.anhonesteffort.chnlzr.ChnlzrServer "$@"
