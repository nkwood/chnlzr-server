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
import org.anhonesteffort.dsp.sample.Samples;
import org.anhonesteffort.dsp.util.ComplexNumber;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.stream.IntStream;

import static org.anhonesteffort.chnlzr.capnp.Proto.ChannelRequest;

public class ResamplingSamplesSinkTest {

  private static final ProtoFactory PROTO = new ProtoFactory();

  private static ChannelRequest.Reader request(long sampleRate) {
    return PROTO.channelRequest(
        9001d, 1337d, sampleRate, 150l
    );
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testRateChange() throws Exception {
    final long                  SOURCE_RATE  = 2000l;
    final long                  CHANNEL_RATE = 1000l;
    final ChannelRequest.Reader REQUEST      = request(CHANNEL_RATE);
    final Samples               SAMPLES      = new Samples(new ComplexNumber[200]);

    IntStream.range(0, SAMPLES.getSamples().length).forEach(i ->
        SAMPLES.getSamples()[i] = new ComplexNumber(0f, 0f)
    );

    final SampleSink            NEXT_SINK = Mockito.mock(SampleSink.class);
    final ResamplingSamplesSink SINK      = new ResamplingSamplesSink(REQUEST, NEXT_SINK);

    Mockito.verify(NEXT_SINK, Mockito.never()).onStateChange(Mockito.any(Long.class), Mockito.any(Double.class));

    SINK.onStateChange(SOURCE_RATE, 9001d);
    SINK.consume(SAMPLES);

    Mockito.verify(NEXT_SINK, Mockito.times(1)).onStateChange(Mockito.any(Long.class), Mockito.any(Double.class));

    final int SAMPLES_TO_FEED    = 16;
    final int DECIMATION         = (int) (SOURCE_RATE / CHANNEL_RATE);
    final int SAMPLES_TO_CONSUME = (SAMPLES_TO_FEED * SAMPLES.getSamples().length) / DECIMATION;

    IntStream.range(0, SAMPLES_TO_FEED - 1).forEach(i -> SINK.consume(SAMPLES));

    Mockito.verify(NEXT_SINK, Mockito.times(SAMPLES_TO_CONSUME)).consume(Mockito.any());
  }

}
