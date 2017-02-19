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

import com.lmax.disruptor.SleepingWaitStrategy;
import org.anhonesteffort.chnlzr.CriticalCallback;
import org.anhonesteffort.chnlzr.resample.SamplesSink;
import org.anhonesteffort.dsp.sample.Samples;
import org.anhonesteffort.dsp.sample.SamplesEvent;
import org.anhonesteffort.dsp.sample.SdrDriver;
import org.anhonesteffort.dsp.sample.SdrDriverProvider;
import org.anhonesteffort.dsp.sample.SdrSamplesSource;
import org.anhonesteffort.dsp.sample.SdrSamplesSourceProvider;
import org.anhonesteffort.dsp.util.ChannelSpec;
import org.anhonesteffort.dsp.util.ComplexNumber;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.anhonesteffort.chnlzr.capnp.Proto.Error;

public class SamplesSourceControllerTest {

  public static class DumbSdrDriverProvider implements SdrDriverProvider {
    @Override
    public Optional<SdrDriver> getDriver() {
      return Optional.of(new DumbSdrDriver());
    }

    @Override
    public SamplesEvent newInstance() {
      return new SamplesEvent(-1l, -1d, new Samples(new ComplexNumber[50]));
    }
  }

  public static class DumbSdrDriver extends SdrDriver {
    public static final long   MAX_SAMPLE_RATE =   400_000l;
    public static final double MIN_FREQ        =   100_000d;
    public static final double MAX_FREQ        = 1_000_000d;

    public DumbSdrDriver() {
      super(MAX_SAMPLE_RATE, MIN_FREQ, MAX_FREQ);
    }

    @Override
    protected ChannelSpec onStart() {
      return new ChannelSpec(MIN_FREQ, MAX_SAMPLE_RATE, MAX_SAMPLE_RATE);
    }

    @Override
    protected long setSampleRate(long targetRate) {
      if (targetRate > MAX_SAMPLE_RATE) {
        throw new IllegalArgumentException("don't");
      } else {
        return targetRate;
      }
    }

    @Override
    protected double setFrequency(double targetFreq) {
      if (targetFreq < MIN_FREQ || targetFreq > MAX_FREQ) {
        throw new IllegalArgumentException("don't");
      } else {
        return targetFreq;
      }
    }

    @Override
    protected void fillBuffer(Samples samples) { }
  }

  private static class DumbChannelSink implements SamplesSink {
    private final ChannelSpec spec;

    public DumbChannelSink(ChannelSpec spec) {
      this.spec = spec;
    }

    @Override
    public ChannelSpec getSpec() { return spec; }

    @Override
    public void onStateChange(long sampleRate, double frequency) { }

    @Override
    public void consume(Samples samples) { }
  }

  private static SdrSamplesSource sourceFor(int concurrency) {
    return new SdrSamplesSourceProvider(
        new SleepingWaitStrategy(),
        128, concurrency,0,
        new CriticalCallback()
    ).getSource().get();
  }

  private static SamplesSink sinkFor(Double minFreq, Double maxFreq) {
    return new DumbChannelSink(ChannelSpec.fromMinMax(minFreq, maxFreq));
  }

  private ExecutorService POOL;

  @Before
  public void setup() {
    POOL = Executors.newFixedThreadPool(1);
  }

  @After
  public void tearDown() {
    POOL.shutdownNow();
  }

  @Test
  public void testWithSingleSink() throws Exception {
    final SdrSamplesSource        SOURCE     = sourceFor(1);
    final SamplesSourceController CONTROLLER = new SamplesSourceController(SOURCE, 1, 0d);

    POOL.submit(SOURCE);
    Thread.sleep(500l);

    final SamplesSink SINK0 = sinkFor(500_000d, 600_000d);
    assert CONTROLLER.configureSourceForSink(SINK0) == 0x00;
    CONTROLLER.releaseSink(SINK0);

    final SamplesSink SINK1 = sinkFor(100_000d, 200_000d);
    assert CONTROLLER.configureSourceForSink(SINK1) == 0x00;
    CONTROLLER.releaseSink(SINK1);

    final SamplesSink SINK2 = sinkFor(900_000d, 1_000_000d);
    assert CONTROLLER.configureSourceForSink(SINK2) == 0x00;
    CONTROLLER.releaseSink(SINK2);

    final SamplesSink SINK3 = sinkFor(100_000d, 300_000d);
    assert CONTROLLER.configureSourceForSink(SINK3) == 0x00;
    CONTROLLER.releaseSink(SINK3);

    final SamplesSink SINK4 = sinkFor(99_999d,  199_999d);
    assert CONTROLLER.configureSourceForSink(SINK4) == Error.ERROR_BANDWIDTH_UNAVAILABLE;
    CONTROLLER.releaseSink(SINK4);

    final SamplesSink SINK5 = sinkFor(900_001d, 1_000_001d);
    assert CONTROLLER.configureSourceForSink(SINK5) == Error.ERROR_BANDWIDTH_UNAVAILABLE;
    CONTROLLER.releaseSink(SINK5);

    final SamplesSink SINK6 = sinkFor(100_000d, 300_001d);
    assert CONTROLLER.configureSourceForSink(SINK6) == Error.ERROR_BANDWIDTH_UNAVAILABLE;
    CONTROLLER.releaseSink(SINK6);
  }

  @Test
  public void testWithMultipleSinks() throws Exception {
    final SdrSamplesSource        SOURCE     = sourceFor(5);
    final SamplesSourceController CONTROLLER = new SamplesSourceController(SOURCE, 5, 0d);
    final SamplesSink             SINK0      = sinkFor(500_000d, 600_000d);
    final SamplesSink             SINK1      = sinkFor(600_000d, 700_000d);
    final SamplesSink             SINK2      = sinkFor(700_000d, 800_000d);
    final SamplesSink             SINK3      = sinkFor(800_000d, 900_000d);

    POOL.submit(SOURCE);
    Thread.sleep(500l);

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
    final SdrSamplesSource        SOURCE     = sourceFor(3);
    final SamplesSourceController CONTROLLER = new SamplesSourceController(SOURCE, 3, 0d);
    final SamplesSink             SINK0      = sinkFor(500_000d, 600_000d);
    final SamplesSink             SINK1      = sinkFor(600_000d, 700_000d);
    final SamplesSink             SINK2      = sinkFor(700_000d, 800_000d);
    final SamplesSink             SINK3      = sinkFor(800_000d, 900_000d);

    POOL.submit(SOURCE);
    Thread.sleep(500l);

    assert CONTROLLER.configureSourceForSink(SINK0) == 0x00;
    assert CONTROLLER.configureSourceForSink(SINK1) == 0x00;
    assert CONTROLLER.configureSourceForSink(SINK2) == 0x00;
    assert CONTROLLER.configureSourceForSink(SINK3) == Error.ERROR_PROCESSING_UNAVAILABLE;
  }

}
