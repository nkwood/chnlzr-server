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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import org.anhonesteffort.chnlzr.ChnlzrServerConfig;
import org.anhonesteffort.chnlzr.capnp.ProtoFactory;
import org.anhonesteffort.chnlzr.netty.WriteQueuingContext;
import org.anhonesteffort.dsp.ComplexNumber;
import org.anhonesteffort.dsp.sample.Samples;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.stream.IntStream;

import static org.anhonesteffort.chnlzr.capnp.Proto.ChannelRequest;

public class ResamplingNetworkSinkTest {

  private static final ProtoFactory PROTO = new ProtoFactory();

  private ChnlzrServerConfig config() {
    final ChnlzrServerConfig CONFIG = Mockito.mock(ChnlzrServerConfig.class);
    Mockito.when(CONFIG.samplesPerMessage()).thenReturn(10000);
    return CONFIG;
  }

  private static ChannelRequest.Reader request(long sampleRate) {
    return PROTO.channelRequest(
        9001d, 1337d, sampleRate, 150
    );
  }

  @Test
  public void testRateChange() throws Exception {
    final ChnlzrServerConfig    CONFIG       = config();
    final long                  SOURCE_RATE  = 2000l;
    final long                  CHANNEL_RATE = 1000l;
    final ChannelRequest.Reader REQUEST      = request(CHANNEL_RATE);
    final Samples               SAMPLES      = new Samples(new ComplexNumber[CONFIG.samplesPerMessage()]);

    IntStream.range(0, SAMPLES.getSamples().length).forEach(i ->
        SAMPLES.getSamples()[i] = new ComplexNumber(0f, 0f)
    );

    final ChannelHandlerContext CONTEXT = Mockito.mock(ChannelHandlerContext.class);
    final Channel               CHANNEL = Mockito.mock(Channel.class);
    final ChannelFuture         FUTURE  = Mockito.mock(ChannelFuture.class);

    Mockito.when(CHANNEL.closeFuture()).thenReturn(FUTURE);
    Mockito.when(CHANNEL.isWritable()).thenReturn(true);
    Mockito.when(CONTEXT.channel()).thenReturn(CHANNEL);

    final WriteQueuingContext   QUEUE = new WriteQueuingContext(CONTEXT, 16);
    final ResamplingNetworkSink SINK  = new ResamplingNetworkSink(CONFIG, QUEUE, REQUEST);

    SINK.onSourceStateChange(SOURCE_RATE, 9001d);
    SINK.consume(SAMPLES);

    Mockito.verify(CONTEXT, Mockito.times(1)).writeAndFlush(Mockito.any());

    final int SAMPLES_TO_FEED    = 16;
    final int DECIMATION         = (int) (SOURCE_RATE / CHANNEL_RATE);
    final int SAMPLES_TO_CONSUME = SAMPLES_TO_FEED / DECIMATION;

    IntStream.range(0, SAMPLES_TO_FEED - 1).forEach(i -> SINK.consume(SAMPLES));

    Mockito.verify(CONTEXT, Mockito.times(SAMPLES_TO_CONSUME + 1)).writeAndFlush(Mockito.any());
  }

}
