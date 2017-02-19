#!/bin/bash
unzip -o ./import/* "*.so" -d ./native
java -cp "target/chnlzr-1.0.jar:import/*" -Djava.library.path=./native -Djava.awt.headless=true -Dio.netty.leakDetection.level=advanced -Dorg.slf4j.simpleLogger.defaultLogLevel=debug org.anhonesteffort.chnlzr.ChnlzrServer
