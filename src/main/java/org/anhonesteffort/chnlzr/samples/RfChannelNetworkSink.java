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

package org.anhonesteffort.chnlzr.samples;

import org.anhonesteffort.chnlzr.CapnpUtil;
import org.anhonesteffort.chnlzr.ChnlzrServerConfig;
import org.anhonesteffort.chnlzr.WriteQueuingContext;
import org.anhonesteffort.dsp.ChannelSpec;
import org.anhonesteffort.dsp.ComplexNumber;
import org.anhonesteffort.dsp.StreamInterruptedException;
import org.anhonesteffort.dsp.filter.ComplexNumberFrequencyTranslatingFilter;
import org.anhonesteffort.dsp.filter.Filter;
import org.anhonesteffort.dsp.filter.FilterFactory;
import org.anhonesteffort.dsp.filter.rate.RateChangeFilter;
import org.anhonesteffort.dsp.sample.Samples;
import org.capnproto.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.anhonesteffort.chnlzr.Proto.BaseMessage;
import static org.anhonesteffort.chnlzr.Proto.Error;
import static org.anhonesteffort.chnlzr.Proto.ChannelRequest;

public class RfChannelNetworkSink implements RfChannelSink, Runnable, Supplier<List<ComplexNumber>> {

  private static final Logger log = LoggerFactory.getLogger(RfChannelNetworkSink.class);

  private final BlockingQueue<FloatBuffer> samplesQueue;
  private final WriteQueuingContext        writeQueue;
  private final ChannelSpec                spec;
  private final long                       maxRateDiff;
  private final int                        samplesPerMessage;

  private AtomicReference<StateChange> stateChange;
  private Filter<ComplexNumber>        freqTranslation;
  private MessageBuilder               nextMessage;
  private ByteBuffer                   nextSamples;

  public RfChannelNetworkSink(ChnlzrServerConfig    config,
                              WriteQueuingContext   writeQueue,
                              ChannelRequest.Reader request)
  {
    this.writeQueue   = writeQueue;
    samplesQueue      = new LinkedBlockingQueue<>(config.samplesQueueSize());
    spec              = CapnpUtil.spec(request);
    maxRateDiff       = request.getMaxRateDiff();
    samplesPerMessage = config.samplesPerMessage();
    stateChange       = new AtomicReference<>();

    initNextMessage();
  }

  private void initNextMessage() {
    nextMessage = CapnpUtil.samples(samplesPerMessage);
    nextSamples = nextMessage.getRoot(BaseMessage.factory).getSamples().getSamples().asByteBuffer();
  }

  private void writeOrQueue(ComplexNumber sample) {
    nextSamples.putFloat(sample.getInPhase());
    nextSamples.putFloat(sample.getQuadrature());

    if (nextSamples.remaining() <= 0) {
      writeQueue.writeOrQueue(nextMessage);
      initNextMessage();
    }
  }

  private void onSourceStateChange(StateChange stateChange) {
    freqTranslation = new ComplexNumberFrequencyTranslatingFilter(
        stateChange.sampleRate, stateChange.frequency, spec.getCenterFrequency()
    );
    RateChangeFilter<ComplexNumber> resampling  = FilterFactory.getCicResampler(
        stateChange.sampleRate, spec.getSampleRate(), maxRateDiff
    );

    freqTranslation.addSink(resampling);
    resampling.addSink(this::writeOrQueue);

    long channelRate = (long) (stateChange.sampleRate * resampling.getRateChange());
    writeQueue.writeOrQueue(CapnpUtil.state(channelRate, 0d));

    log.info(spec + " source rate " + stateChange.sampleRate + ", desired rate " + spec.getSampleRate() + ", channel rate " + channelRate);
    log.info(spec + " interpolation " + resampling.getInterpolation() + ", decimation " + resampling.getDecimation());
  }

  @Override
  public ChannelSpec getChannelSpec() {
    return spec;
  }

  @Override
  public void onSourceStateChange(Long sampleRate, Double frequency) {
    stateChange.set(new StateChange(sampleRate, frequency));
  }

  @Override
  public void consume(Samples samples) {
    if (!samplesQueue.offer(samples.getSamples())) {
      log.warn(spec + " sample queue has overflowed, closing connection");
      writeQueue.writeAndClose(CapnpUtil.error(Error.ERROR_PROCESSING_UNAVAILABLE));
      samplesQueue.clear();
    }
  }

  @Override
  public List<ComplexNumber> get() {
    try {

      FloatBuffer iqSamples = samplesQueue.take();
      return IntStream.range(0, iqSamples.limit())
                      .filter(i -> ((i & 1) == 0) && (i + 1) < iqSamples.limit())
                      .mapToObj(i -> new ComplexNumber(iqSamples.get(i), iqSamples.get(i + 1)))
                      .collect(Collectors.toList());

    } catch (InterruptedException e) {
      throw new StreamInterruptedException("interrupted while supplying ComplexNumber stream", e);
    }
  }

  @Override
  public void run() {
    try {

      Stream.generate(this).forEach(samples -> {
        StateChange change = stateChange.getAndSet(null);
        if (change != null) {
          onSourceStateChange(change);
        }

        samples.forEach(freqTranslation::consume);
      });

    } catch (StreamInterruptedException e) {
      log.debug(spec + " interrupted, assuming execution was canceled");
    } finally {
      samplesQueue.clear();
      stateChange     = null;
      freqTranslation = null;
      nextMessage     = null;
      nextSamples     = null;
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
