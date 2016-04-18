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

package org.anhonesteffort.chnlzr;

import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import org.anhonesteffort.chnlzr.samples.RfChannelSink;
import org.anhonesteffort.chnlzr.samples.SamplesSourceController;
import org.anhonesteffort.dsp.ChannelSpec;
import org.anhonesteffort.dsp.ComplexNumber;
import org.anhonesteffort.dsp.sample.Samples;
import org.anhonesteffort.dsp.sample.SamplesSourceException;
import org.anhonesteffort.dsp.sample.TunableSamplesSource;
import org.anhonesteffort.dsp.sample.TunableSamplesSourceFactory;
import org.anhonesteffort.dsp.sample.TunableSamplesSourceProvider;
import org.junit.Test;

import java.util.Optional;

import static org.anhonesteffort.chnlzr.capnp.Proto.Error;

public class SamplesSourceControllerTest {

  public static class DumbTunableSamplesSourceProvider implements TunableSamplesSourceProvider {
    @Override
    public Optional<TunableSamplesSource> get(Disruptor<Samples> disruptor, int affinity, int concurrency) {
      return Optional.of(new DumbTunableSamplesSource(disruptor, affinity, concurrency));
    }

    @Override
    public Samples newInstance() {
      return new Samples(new ComplexNumber[50]);
    }
  }

  public static class DumbTunableSamplesSource extends TunableSamplesSource {
    public static final Long   MAX_SAMPLE_RATE =   400_000l;
    public static final Double MIN_FREQ        =   100_000d;
    public static final Double MAX_FREQ        = 1_000_000d;

    public DumbTunableSamplesSource(Disruptor<Samples> disruptor, int affinity, int concurrency) {
      super(MAX_SAMPLE_RATE, MIN_FREQ, MAX_FREQ, disruptor, affinity, concurrency);
    }

    @Override
    protected Long setSampleRate(Long targetRate) throws SamplesSourceException {
      if (targetRate > MAX_SAMPLE_RATE)
        throw new SamplesSourceException("don't");

      this.sampleRate = targetRate;
      return targetRate;
    }

    @Override
    protected Double setFrequency(Double targetFreq) throws SamplesSourceException {
      if (targetFreq < MIN_FREQ || targetFreq > MAX_FREQ)
        throw new SamplesSourceException("don't");

      this.frequency = targetFreq;
      return targetFreq;
    }

    @Override
    protected void fillBuffer(Samples samples) throws SamplesSourceException { }
  }

  private static class DumbChannelSink implements RfChannelSink {
    private final ChannelSpec spec;

    public DumbChannelSink(ChannelSpec spec) {
      this.spec = spec;
    }

    @Override
    public ChannelSpec getChannelSpec() {
      return spec;
    }

    @Override
    public void onSourceStateChange(Long sampleRate, Double frequency) { }

    @Override
    public void consume(Samples samples) { }
  }

  private static RfChannelSink sinkFor(Double minFreq, Double maxFreq) {
    return new DumbChannelSink(ChannelSpec.fromMinMax(minFreq, maxFreq));
  }

  @Test
  public void testWithSingleSink() throws Exception {
    final Double                  DC_OFFSET  = 0d;
    final TunableSamplesSource    SOURCE     = new TunableSamplesSourceFactory(new SleepingWaitStrategy(), 512, 0, 10).getSource().get();
    final SamplesSourceController CONTROLLER = new SamplesSourceController(SOURCE, 1, DC_OFFSET);

    final RfChannelSink SINK0 = sinkFor(500_000d, 600_000d);
    assert CONTROLLER.configureSourceForSink(SINK0) == 0x00;
    CONTROLLER.releaseSink(SINK0);

    final RfChannelSink SINK1 = sinkFor(100_000d, 200_000d);
    assert CONTROLLER.configureSourceForSink(SINK1) == 0x00;
    CONTROLLER.releaseSink(SINK1);

    final RfChannelSink SINK2 = sinkFor(900_000d, 1_000_000d);
    assert CONTROLLER.configureSourceForSink(SINK2) == 0x00;
    CONTROLLER.releaseSink(SINK2);

    final RfChannelSink SINK3 = sinkFor(100_000d, 300_000d);
    assert CONTROLLER.configureSourceForSink(SINK3) == 0x00;
    CONTROLLER.releaseSink(SINK3);

    final RfChannelSink SINK4 = sinkFor(99_999d,  199_999d);
    assert CONTROLLER.configureSourceForSink(SINK4) == Error.ERROR_INCAPABLE;
    CONTROLLER.releaseSink(SINK4);

    final RfChannelSink SINK5 = sinkFor(900_001d, 1_000_001d);
    assert CONTROLLER.configureSourceForSink(SINK5) == Error.ERROR_INCAPABLE;
    CONTROLLER.releaseSink(SINK5);

    final RfChannelSink SINK6 = sinkFor(100_000d, 300_001d);
    assert CONTROLLER.configureSourceForSink(SINK6) == Error.ERROR_INCAPABLE;
    CONTROLLER.releaseSink(SINK6);
  }

  @Test
  public void testWithMultipleSinks() throws Exception {
    final Double                  DC_OFFSET  = 0d;
    final TunableSamplesSource    SOURCE     = new TunableSamplesSourceFactory(new SleepingWaitStrategy(), 512, 0, 10).getSource().get();
    final SamplesSourceController CONTROLLER = new SamplesSourceController(SOURCE, 5, DC_OFFSET);
    final RfChannelSink           SINK0      = sinkFor(500_000d, 600_000d);
    final RfChannelSink           SINK1      = sinkFor(600_000d, 700_000d);
    final RfChannelSink           SINK2      = sinkFor(700_000d, 800_000d);
    final RfChannelSink           SINK3      = sinkFor(800_000d, 900_000d);

    assert CONTROLLER.configureSourceForSink(SINK0) == 0x00;
    assert CONTROLLER.configureSourceForSink(SINK1) == 0x00;
    assert CONTROLLER.configureSourceForSink(SINK2) == 0x00;
    assert CONTROLLER.configureSourceForSink(SINK3) == 0x00;

    assert CONTROLLER.configureSourceForSink(sinkFor(800_000d, 900_001d)) == Error.ERROR_BANDWIDTH_UNAVAILABLE;
    assert CONTROLLER.configureSourceForSink(sinkFor(499_999d, 600_000d)) == Error.ERROR_BANDWIDTH_UNAVAILABLE;

    CONTROLLER.releaseSink(SINK0);
    assert CONTROLLER.configureSourceForSink(sinkFor(900_000d, 950_000d)) == 0x00;
    assert CONTROLLER.configureSourceForSink(sinkFor(550_000d, 600_000d)) == 0x00;
  }

  @Test
  public void testMaxSinks() throws Exception {
    final Double                  DC_OFFSET  = 0d;
    final TunableSamplesSource    SOURCE     = new TunableSamplesSourceFactory(new SleepingWaitStrategy(), 512, 0, 10).getSource().get();
    final SamplesSourceController CONTROLLER = new SamplesSourceController(SOURCE, 3, DC_OFFSET);
    final RfChannelSink           SINK0      = sinkFor(500_000d, 600_000d);
    final RfChannelSink           SINK1      = sinkFor(600_000d, 700_000d);
    final RfChannelSink           SINK2      = sinkFor(700_000d, 800_000d);
    final RfChannelSink           SINK3      = sinkFor(800_000d, 900_000d);

    assert CONTROLLER.configureSourceForSink(SINK0) == 0x00;
    assert CONTROLLER.configureSourceForSink(SINK1) == 0x00;
    assert CONTROLLER.configureSourceForSink(SINK2) == 0x00;
    assert CONTROLLER.configureSourceForSink(SINK3) == Error.ERROR_PROCESSING_UNAVAILABLE;
  }

}
