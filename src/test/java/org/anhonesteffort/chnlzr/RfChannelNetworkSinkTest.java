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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.anhonesteffort.dsp.sample.Samples;
import org.capnproto.MessageBuilder;
import org.junit.Test;
import org.mockito.Mockito;

import java.nio.FloatBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.anhonesteffort.chnlzr.Proto.ChannelRequest;

public class RfChannelNetworkSinkTest {

  private ChnlzrServerConfig config() {
    final ChnlzrServerConfig CONFIG = Mockito.mock(ChnlzrServerConfig.class);

    Mockito.when(CONFIG.samplesPerMessage()).thenReturn(10000);
    Mockito.when(CONFIG.clientWriteQueueSize()).thenReturn(16);
    Mockito.when(CONFIG.samplesQueueSize()).thenReturn(16);
    Mockito.when(CONFIG.latitude()).thenReturn(22.208335d);
    Mockito.when(CONFIG.longitude()).thenReturn(-159.507002d);
    Mockito.when(CONFIG.polarization()).thenReturn(1);
    Mockito.when(CONFIG.dcOffset()).thenReturn(0.0d);

    return CONFIG;
  }

  private static ChannelRequest.Reader request(long sampleRate) {
    return CapnpUtil.channelRequest(
        0d, 0d, 0d, 1, 9001d, 1337d, sampleRate, 150
    );
  }

  @Test
  public void testRateChange() throws Exception {
    final ChnlzrServerConfig    CONFIG       = config();
    final long                  SOURCE_RATE  = 2000l;
    final long                  CHANNEL_RATE = 1000l;
    final ChannelRequest.Reader REQUEST      = request(CHANNEL_RATE);
    final Samples               SAMPLES      = new Samples(FloatBuffer.wrap(new float[CONFIG.samplesPerMessage() * 2]));

    final ChannelHandlerContext CONTEXT  = Mockito.mock(ChannelHandlerContext.class);
    final Channel               CHANNEL  = Mockito.mock(Channel.class);
    final ChannelFuture         FUTURE   = Mockito.mock(ChannelFuture.class);
    final ExecutorService       EXECUTOR = Executors.newSingleThreadExecutor();

    Mockito.when(CHANNEL.closeFuture()).thenReturn(FUTURE);
    Mockito.when(CHANNEL.isWritable()).thenReturn(true);
    Mockito.when(CONTEXT.channel()).thenReturn(CHANNEL);

    final WriteQueuingContext  QUEUE = new WriteQueuingContext(CONTEXT, 16);
    final RfChannelNetworkSink SINK  = new RfChannelNetworkSink(CONFIG, QUEUE, REQUEST);

    EXECUTOR.submit(SINK);
    SINK.onSourceStateChange(SOURCE_RATE, 9001d);
    SINK.consume(SAMPLES);
    Thread.sleep(100l);

    Mockito.verify(CONTEXT, Mockito.times(1)).writeAndFlush(Mockito.any());

    final int SAMPLES_TO_FEED    = 16;
    final int DECIMATION         = (int) (SOURCE_RATE / CHANNEL_RATE);
    final int SAMPLES_TO_CONSUME = SAMPLES_TO_FEED / DECIMATION;

    IntStream.range(0, SAMPLES_TO_FEED - 1).forEach(i -> {
      SINK.consume(SAMPLES);
      try { Thread.sleep(100l); } catch (InterruptedException e) { assert false; }
    });

    Mockito.verify(CONTEXT, Mockito.times(SAMPLES_TO_CONSUME + 1)).writeAndFlush(Mockito.any());
    EXECUTOR.shutdownNow();
  }

  @Test
  public void testSampleQueueOverflow() {
    final ChnlzrServerConfig CONFIG  = config();
    final Samples            SAMPLES = new Samples(FloatBuffer.wrap(new float[100]));

    final long                  CHANNEL_RATE = 1000;
    final ChannelRequest.Reader REQUEST      = request(CHANNEL_RATE);
    final ChannelHandlerContext CONTEXT      = Mockito.mock(ChannelHandlerContext.class);
    final Channel               CHANNEL      = Mockito.mock(Channel.class);
    final ChannelFuture         FUTURE       = Mockito.mock(ChannelFuture.class);

    Mockito.when(CONTEXT.writeAndFlush(Mockito.any(MessageBuilder.class))).thenReturn(FUTURE);
    Mockito.when(CHANNEL.closeFuture()).thenReturn(FUTURE);
    Mockito.when(CHANNEL.isWritable()).thenReturn(true);
    Mockito.when(CONTEXT.channel()).thenReturn(CHANNEL);

    final WriteQueuingContext  QUEUE = new WriteQueuingContext(CONTEXT, 16);
    final RfChannelNetworkSink SINK  = new RfChannelNetworkSink(CONFIG, QUEUE, REQUEST);

    IntStream.range(0, CONFIG.samplesQueueSize())
             .forEach(i -> SINK.consume(SAMPLES));

    Mockito.verify(FUTURE, Mockito.never()).addListener(Mockito.eq(ChannelFutureListener.CLOSE));
    SINK.consume(SAMPLES);
    Mockito.verify(FUTURE, Mockito.times(1)).addListener(Mockito.eq(ChannelFutureListener.CLOSE));
  }

}
