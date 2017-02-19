/*
 * Copyright (C) 2017 An Honest Effort LLC.
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
import org.anhonesteffort.chnlzr.output.SampleSink;
import org.anhonesteffort.dsp.filter.ComplexNumberFrequencyTranslatingFilter;
import org.anhonesteffort.dsp.filter.Filter;
import org.anhonesteffort.dsp.filter.FilterFactory;
import org.anhonesteffort.dsp.filter.rate.RateChangeFilter;
import org.anhonesteffort.dsp.sample.Samples;
import org.anhonesteffort.dsp.util.ChannelSpec;
import org.anhonesteffort.dsp.util.ComplexNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.anhonesteffort.chnlzr.capnp.Proto.ChannelRequest;

public class ResamplingSamplesSink implements SamplesSink {

  private static final Logger log = LoggerFactory.getLogger(ResamplingSamplesSink.class);

  private final SampleSink nextSink;
  private final ChannelSpec spec;
  private final long maxRateDiff;

  private Filter<ComplexNumber> freqTranslation;

  public ResamplingSamplesSink(ChannelRequest.Reader request, SampleSink nextSink) {
    this.nextSink = nextSink;
    spec          = new ProtoFactory().spec(request);
    maxRateDiff   = request.getMaxRateDiff();
  }

  @Override
  public ChannelSpec getSpec() {
    return spec;
  }

  @Override
  public void onStateChange(long sampleRate, double frequency) {
    freqTranslation = new ComplexNumberFrequencyTranslatingFilter(
        sampleRate, frequency, spec.getCenterFrequency()
    );
    RateChangeFilter<ComplexNumber> resampling = FilterFactory.getCicResampler(
        sampleRate, spec.getSampleRate(), maxRateDiff
    );

    freqTranslation.addSink(resampling);
    resampling.addSink(nextSink);

    long channelRate = (long) (sampleRate * resampling.getRateChange());
    nextSink.onStateChange(channelRate, 0d);

    log.info(spec + " source rate " + sampleRate + ", desired rate " + spec.getSampleRate() + ", channel rate " + channelRate);
    log.info(spec + " interpolation " + resampling.getInterpolation() + ", decimation " + resampling.getDecimation());
  }

  @Override
  public void consume(Samples samples) {
    ComplexNumber[] samps = samples.getSamples();
    for (int i = 0; i < samps.length; i++) {
      freqTranslation.consume(samps[i]);
    }
  }

}
