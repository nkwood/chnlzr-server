#!/bin/bash
unzip -o ./import/* "*.so" -d ./native
java -cp "target/chnlzr-0.3.0.jar:import/*" -Djava.library.path=./native -Djava.awt.headless=true -Dorg.slf4j.simpleLogger.defaultLogLevel=debug org.anhonesteffort.chnlzr.ChnlzrServer
