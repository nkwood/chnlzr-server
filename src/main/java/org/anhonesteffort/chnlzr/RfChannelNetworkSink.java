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
import org.anhonesteffort.dsp.ComplexNumber;
import org.anhonesteffort.dsp.Sink;
import org.anhonesteffort.dsp.StreamInterruptedException;
import org.anhonesteffort.dsp.filter.ComplexNumberFrequencyTranslatingFilter;
import org.anhonesteffort.dsp.filter.Filter;
import org.anhonesteffort.dsp.filter.FilterFactory;
import org.anhonesteffort.dsp.filter.rate.RateChangeFilter;
import org.anhonesteffort.dsp.sample.Samples;
import org.capnproto.MessageBuilder;
import org.capnproto.PrimitiveList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
  private final Object                     processChainLock = new Object();

  private final WriteQueuingContext writeQueue;
  private final ChannelSpec         spec;
  private final long                maxRateDiff;
  private final int                 samplesPerMessage;

  private Filter<ComplexNumber>       freqTranslation;
  private MessageBuilder              nextMessage;
  private PrimitiveList.Float.Builder nextSamples;

  private int floatIndex = -1;

  public RfChannelNetworkSink(ChnlzrServerConfig    config,
                              WriteQueuingContext   writeQueue,
                              ChannelRequest.Reader request)
  {
    this.writeQueue   = writeQueue;
    samplesQueue      = new LinkedBlockingQueue<>(config.samplesQueueSize());
    spec              = CapnpUtil.spec(request);
    maxRateDiff       = request.getMaxRateDiff();
    samplesPerMessage = config.samplesPerMessage();

    initNextMessage();
  }

  @Override
  public ChannelSpec getChannelSpec() {
    return spec;
  }

  private void initNextMessage() {
    nextMessage = CapnpUtil.samples(samplesPerMessage);
    nextSamples = nextMessage.getRoot(BaseMessage.factory).getSamples().getSamples();
    floatIndex  = 0;
  }

  private class SamplesWritingQueue implements Sink<ComplexNumber> {
    @Override
    public void consume(ComplexNumber sample) {
      nextSamples.set(floatIndex++, sample.getInPhase());
      nextSamples.set(floatIndex++, sample.getQuadrature());

      if (floatIndex >= nextSamples.size()) {
        writeQueue.writeOrQueue(nextMessage);
        initNextMessage();
      }
    }
  }

  @Override
  public void onSourceStateChange(Long sampleRate, Double frequency) {
    synchronized (processChainLock) {
      freqTranslation = new ComplexNumberFrequencyTranslatingFilter(
          sampleRate, frequency, spec.getCenterFrequency()
      );

      RateChangeFilter<ComplexNumber> resampling = FilterFactory.getCicResampler(
          sampleRate, spec.getSampleRate(), maxRateDiff
      );

      freqTranslation.addSink(resampling);
      resampling.addSink(new SamplesWritingQueue());

      long           channelRate  = (long) (sampleRate * resampling.getRateChange());
      MessageBuilder channelState = CapnpUtil.state(channelRate, 0d);

      writeQueue.writeOrQueue(channelState);

      log.info(spec + " source rate " + sampleRate + ", desired rate " + spec.getSampleRate() + ", channel rate " + channelRate);
      log.info(spec + " interpolation " + resampling.getInterpolation() + ", decimation " + resampling.getDecimation());
    }
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
        if (Thread.currentThread().isInterrupted())
          throw new StreamInterruptedException("interrupted while reading from ComplexNumber stream");

        synchronized (processChainLock) { samples.forEach(freqTranslation::consume); }
      });

    } catch (StreamInterruptedException e) {
      log.debug(spec + " interrupted, assuming execution was canceled");
    } finally {
      samplesQueue.clear();
      freqTranslation = null;
      nextMessage     = null;
      nextSamples     = null;
      floatIndex      = -1;
    }
  }

}
