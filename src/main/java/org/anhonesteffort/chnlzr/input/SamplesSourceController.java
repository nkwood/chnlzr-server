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

package org.anhonesteffort.chnlzr.input;

import org.anhonesteffort.chnlzr.resample.SamplesSink;
import org.anhonesteffort.dsp.sample.SdrSamplesSource;
import org.anhonesteffort.dsp.util.ChannelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.anhonesteffort.chnlzr.capnp.Proto.Error;

public class SamplesSourceController {

  private static final Logger log = LoggerFactory.getLogger(SamplesSourceController.class);

  private final Object txnLock = new Object();
  private final AtomicReference<ChannelSpec> tunedChannel = new AtomicReference<>();
  private final Queue<SamplesSink> sinks = new ConcurrentLinkedQueue<>();

  private final SdrSamplesSource source;
  private final int maxSinks;
  private final double dcOffsetHz;

  public SamplesSourceController(SdrSamplesSource source, int maxSinks, double dcOffsetHz) {
    this.source     = source;
    this.maxSinks   = maxSinks;
    this.dcOffsetHz = dcOffsetHz;
  }

  private Optional<Double> getMinChannelFrequency() {
    if (sinks.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(sinks.stream()
          .mapToDouble(sink -> sink.getSpec().getMinFreq())
          .min()
          .getAsDouble());
    }
  }

  private Optional<Double> getMaxChannelFrequency() {
    if (sinks.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(sinks.stream()
          .mapToDouble(sink -> sink.getSpec().getMaxFreq())
          .max()
          .getAsDouble());
    }
  }

  private Optional<Long> getMaxChannelSampleRate() {
    if (sinks.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(sinks.stream()
          .mapToLong(sink -> sink.getSpec().getSampleRate())
          .max()
          .getAsLong());
    }
  }

  private ChannelSpec fitAllChannels(ChannelSpec newChannel) {
    double minRequiredFreq   = Math.min(getMinChannelFrequency().get(), newChannel.getMinFreq());
    double maxRequiredFreq   = Math.max(getMaxChannelFrequency().get(), newChannel.getMaxFreq());
    double requiredBandwidth = maxRequiredFreq - minRequiredFreq;

    long maxChannelSampleRate = Math.max(getMaxChannelSampleRate().get(), newChannel.getSampleRate());
    long requiredSampleRate   = Math.max(maxChannelSampleRate, (long) requiredBandwidth);

    return ChannelSpec.fromMinMax(minRequiredFreq, maxRequiredFreq, requiredSampleRate);
  }

  private ChannelSpec fitDcOffset(ChannelSpec spec) {
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
    if (sinks.isEmpty()) {
      return source.getCapabilities().contains(fitDcOffset(spec));
    } else {
      return source.getCapabilities().contains(fitAllChannels(spec));
    }
  }

  private ChannelSpec tryTune(ChannelSpec newChannel) {
    if (sinks.isEmpty()) {
      return source.tryTune(fitDcOffset(newChannel));
    } else {
      return source.tryTune(fitAllChannels(newChannel));
    }
  }

  public ChannelSpec getCapabilities() {
    return source.getCapabilities();
  }

  public int configureSourceForSink(SamplesSink sink) {
    synchronized (txnLock) {

      if (sinks.size() >= maxSinks) {
        return Error.ERROR_PROCESSING_UNAVAILABLE;
      } else if (!isTunable(sink.getSpec())) {
        return Error.ERROR_BANDWIDTH_UNAVAILABLE;
      } else {
        ChannelSpec tuned = tunedChannel.get();
        if (tuned != null && tuned.contains(sink.getSpec())) {
          if (source.addSink(sink)) {
            sinks.add(sink);
            return 0x00;
          } else {
            log.error("Source.addSink() returned false");
            return Error.ERROR_UNKNOWN;
          }
        } else {
          tunedChannel.set(tryTune(sink.getSpec()));
          if (tunedChannel.get().contains(sink.getSpec()) && source.addSink(sink)) {
            sinks.add(sink);
            return 0x00;
          } else {
            log.error("failed to configure source for consumer channel " + sink.getSpec().toString());
            return Error.ERROR_UNKNOWN;
          }
        }
      }

    }
  }

  public void releaseSink(SamplesSink sink) {
    synchronized (txnLock) {
      source.removeSink(sink);
      sinks.remove(sink);
    }
  }

}
