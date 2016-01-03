package org.anhonesteffort.chnlzr;

import org.anhonesteffort.dsp.sample.TunableSamplesSource;
import org.anhonesteffort.dsp.sample.TunableSamplesSourceProvider;

import java.util.Optional;
import java.util.ServiceLoader;

public class SamplesSourceFactory {

  private TunableSamplesSource source;

  public SamplesSourceFactory() {
    ServiceLoader.load(TunableSamplesSourceProvider.class).forEach(provider -> {
      if (source == null) {
        Optional<TunableSamplesSource> source = provider.get();
        if (source.isPresent())
          this.source = source.get();
      }
    });
  }

  public Optional<TunableSamplesSource> get() {
    return Optional.ofNullable(source);
  }

}
