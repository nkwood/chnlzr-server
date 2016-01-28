# chnlzr

Resample RF spectrum and stream it over network.

## Chose a sample source
chnlzr uses the Java SPI pattern to allow for modular software defined
radio support. To add support for your SDR extend `org.anhonesteffort.dsp.sample.TunableSamplesSource`
and implement `org.anhonesteffort.dsp.sample.TunableSamplesSourceProvider`
then compile to a .jar and copy it to `import/`.

Currently the following drivers are available:
  + Mock Sample Source - [dsp-mock-source](https://github.com/rhodey/dsp-mock-source)
  + Ettus USRP SDRs - [dsp-usrp-source](https://github.com/rhodey/dsp-usrp-source)

## Create chnlzr.properties
Copy `example-chnlzr.properties` to `chnlzr.properties` and modify as you see fit.

## Build
```
$ mvn package
```

## Run
```
$ ./run-server.sh
```

## License

Copyright 2016 An Honest Effort LLC

Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html
