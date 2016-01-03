/*
 * Copyright (C) 2015 An Honest Effort LLC, coping.
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

package org.anhonesteffort.chnlzr;

import org.anhonesteffort.dsp.ChannelSpec;
import org.anhonesteffort.dsp.sample.SamplesSourceException;
import org.anhonesteffort.dsp.sample.TunableSamplesSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.anhonesteffort.chnlzr.Proto.Error;

public class SamplesSourceController {

  private static final Logger log = LoggerFactory.getLogger(SamplesSourceController.class);

  private final Queue<RfChannelSink> sinks   = new ConcurrentLinkedQueue<>();
  private final Object               txnLock = new Object();
  private final TunableSamplesSource source;
  private final Double               dcOffsetHz;

  public SamplesSourceController(TunableSamplesSource source, Double dcOffsetHz) {
    this.source     = source;
    this.dcOffsetHz = dcOffsetHz;
  }

  private Optional<Double> getMinChannelFrequency() {
    if (sinks.isEmpty())
      return Optional.empty();

    return Optional.of(sinks.stream()
                            .mapToDouble(sink -> sink.getChannelSpec().getMinFreq())
                            .min()
                            .getAsDouble());
  }

  private Optional<Double> getMaxChannelFrequency() {
    if (sinks.isEmpty())
      return Optional.empty();

    return Optional.of(sinks.stream()
                            .mapToDouble(sink -> sink.getChannelSpec().getMaxFreq())
                            .max()
                            .getAsDouble());
  }

  private Optional<Long> getMaxChannelSampleRate() {
    if (sinks.isEmpty())
      return Optional.empty();

    return Optional.of(sinks.stream()
                            .mapToLong(sink -> sink.getChannelSpec().getSampleRate())
                            .max()
                            .getAsLong());
  }

  private ChannelSpec getIdealChannelSpec(ChannelSpec newChannel) {
    double minRequiredFreq   = Math.min(getMinChannelFrequency().get(), newChannel.getMinFreq());
    double maxRequiredFreq   = Math.max(getMaxChannelFrequency().get(), newChannel.getMaxFreq());
    double requiredBandwidth = maxRequiredFreq - minRequiredFreq;

    long maxChannelSampleRate = Math.max(getMaxChannelSampleRate().get(), newChannel.getSampleRate());
    long requiredSampleRate   = Math.max(maxChannelSampleRate, (long) requiredBandwidth);

    return ChannelSpec.fromMinMax(minRequiredFreq, maxRequiredFreq, requiredSampleRate);
  }

  private ChannelSpec accommodateDcOffset(ChannelSpec spec) {
    double      offsetCenterFreq = spec.getCenterFrequency() + dcOffsetHz;
    double      offsetBandwidth  = spec.getBandwidth() + (Math.abs(dcOffsetHz) * 2d);
    ChannelSpec offsetSpec       = new ChannelSpec(offsetCenterFreq, offsetBandwidth, spec.getSampleRate());

    if ((offsetSpec.getSampleRate() / 2d) < offsetSpec.getBandwidth()) {
      double aliasedBw        = offsetSpec.getBandwidth() - (offsetSpec.getSampleRate() / 2d);
      long   offsetSampleRate = (long) Math.ceil(offsetSpec.getSampleRate() + (aliasedBw * 2d));
             offsetSpec       = new ChannelSpec(offsetCenterFreq, offsetBandwidth, offsetSampleRate);
    }

    return offsetSpec;
  }

  private boolean isTunable(ChannelSpec spec) {
    if (sinks.isEmpty())
      return source.isTunable(accommodateDcOffset(spec));

    return source.isTunable(getIdealChannelSpec(spec));
  }

  private void handleTuneToFitNewChannel(ChannelSpec newChannel) throws SamplesSourceException {
    if (sinks.isEmpty())
      source.tune(accommodateDcOffset(newChannel));
    else
      source.tune(getIdealChannelSpec(newChannel));
  }

  public ChannelSpec getCapabilities() {
    return ChannelSpec.fromMinMax(source.getMinTunableFreq(),
                                  source.getMaxTunableFreq(),
                                  source.getMaxSampleRate());
  }

  public int configureSourceForSink(RfChannelSink sink) {
    synchronized (txnLock) {
      ChannelSpec requestedChannel = sink.getChannelSpec();
      ChannelSpec tunedChannel     = source.getTunedChannel();

      if (!source.isTunable(accommodateDcOffset(requestedChannel)))
        return Error.ERROR_INCAPABLE;

      if (tunedChannel.containsChannel(requestedChannel)) {
        source.addSink(sink);
        sinks.add(sink);
        return 0x00;
      } else if (isTunable(requestedChannel)) {
        try {

          handleTuneToFitNewChannel(requestedChannel);
          source.addSink(sink);
          sinks.add(sink);
          return 0x00;

        } catch (SamplesSourceException e) {
          log.error("failed to configure source for consumer channel " + requestedChannel, e);
          return Error.ERROR_UNKNOWN;
        }
      }
    }

    return Error.ERROR_BANDWIDTH_UNAVAILABLE;
  }

  public void releaseSink(RfChannelSink sink) {
    synchronized (txnLock) {
      source.removeSink(sink);
      sinks.remove(sink);
    }
  }

}
