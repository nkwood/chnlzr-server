/*
 * Copyright (C) 2016 An Honest Effort LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.anhonesteffort.chnlzr.resample;

import org.anhonesteffort.chnlzr.capnp.ProtoFactory;
import org.anhonesteffort.dsp.ChannelSpec;
import org.anhonesteffort.dsp.ComplexNumber;
import org.anhonesteffort.dsp.DynamicSink;
import org.anhonesteffort.dsp.filter.ComplexNumberFrequencyTranslatingFilter;
import org.anhonesteffort.dsp.filter.Filter;
import org.anhonesteffort.dsp.filter.FilterFactory;
import org.anhonesteffort.dsp.filter.rate.RateChangeFilter;
import org.anhonesteffort.dsp.sample.Samples;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

import static org.anhonesteffort.chnlzr.capnp.Proto.ChannelRequest;

public class RfResamplingSink implements ResamplingSink {

  private static final Logger log = LoggerFactory.getLogger(RfResamplingSink.class);

  private final AtomicReference<StateChange> stateChange = new AtomicReference<>(null);
  private final DynamicSink<ComplexNumber>   nextSink;
  private final ChannelSpec                  spec;
  private final long                         maxRateDiff;

  private Filter<ComplexNumber> freqTranslation;

  public RfResamplingSink(ChannelRequest.Reader request, DynamicSink<ComplexNumber> nextSink) {
    this.nextSink = nextSink;
    spec          = new ProtoFactory().spec(request);
    maxRateDiff   = request.getMaxRateDiff();
  }

  @Override
  public ChannelSpec getChannelSpec() {
    return spec;
  }

  @Override
  public void onSourceStateChange(Long sampleRate, Double frequency) {
    stateChange.set(new StateChange(sampleRate, frequency));
  }

  private void onSourceStateChange(StateChange stateChange) {
    freqTranslation = new ComplexNumberFrequencyTranslatingFilter(
        stateChange.sampleRate, stateChange.frequency, spec.getCenterFrequency()
    );
    RateChangeFilter<ComplexNumber> resampling = FilterFactory.getCicResampler(
        stateChange.sampleRate, spec.getSampleRate(), maxRateDiff
    );

    long channelRate = (long) (stateChange.sampleRate * resampling.getRateChange());

    freqTranslation.addSink(resampling);
    resampling.addSink(nextSink);
    nextSink.onSourceStateChange(channelRate, 0d);

    log.info(spec + " source rate " + stateChange.sampleRate + ", desired rate " + spec.getSampleRate() + ", channel rate " + channelRate);
    log.info(spec + " interpolation " + resampling.getInterpolation() + ", decimation " + resampling.getDecimation());
  }

  @Override
  public void consume(Samples samples) {
    StateChange change = stateChange.get();

    if (change != null) {
      // todo: could lazySet() happen after set()?
      stateChange.lazySet(null);
      onSourceStateChange(change);
    }

    for (int i = 0; i < samples.getSamples().length; i++) {
      freqTranslation.consume(samples.getSamples()[i]);
    }
  }

  private static class StateChange {
    private final Long   sampleRate;
    private final Double frequency;

    public StateChange(Long sampleRate, Double frequency) {
      this.sampleRate = sampleRate;
      this.frequency  = frequency;
    }
  }

}
