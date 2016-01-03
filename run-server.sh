#!/bin/bash
unzip -o ./import/* "*.so" -d ./native
java -cp "target/chnlzr-server-0.1.1.jar:import/*" -Djava.library.path=./native -Dorg.slf4j.simpleLogger.defaultLogLevel=debug org.anhonesteffort.chnlzr.ChnlzrServer "$@"
