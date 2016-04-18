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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.anhonesteffort.chnlzr.capnp.ProtoFactory;
import org.anhonesteffort.chnlzr.samples.SamplesSourceController;
import org.anhonesteffort.dsp.ChannelSpec;
import org.capnproto.MessageBuilder;
import org.junit.Test;
import org.mockito.Mockito;

import static org.anhonesteffort.chnlzr.capnp.Proto.BaseMessage;
import static org.anhonesteffort.chnlzr.capnp.Proto.BaseMessage.Type;

public class ServerHandlerTest {

  private static final ProtoFactory PROTO = new ProtoFactory();

  private static ChnlzrServerConfig config() {
    final ChnlzrServerConfig CONFIG = Mockito.mock(ChnlzrServerConfig.class);

    Mockito.when(CONFIG.latitude()).thenReturn(37.807143d);
    Mockito.when(CONFIG.longitude()).thenReturn(-122.261150d);
    Mockito.when(CONFIG.polarization()).thenReturn(1);
    Mockito.when(CONFIG.clientWriteQueueSize()).thenReturn(8);
    Mockito.when(CONFIG.samplesPerMessage()).thenReturn(1000);

    return CONFIG;
  }

  private static MessageBuilder request() {
    return PROTO.channelRequest(PROTO.channelRequest(
        1337, 9001, 48000, 150
    ));
  }

  @Test
  public void testCapabilitiesSentFirst() {
    final ChnlzrServerConfig      CONFIG            = config();
    final SamplesSourceController SOURCE_CONTROLLER = Mockito.mock(SamplesSourceController.class);
    final ChannelSpec             SPEC              = ChannelSpec.fromMinMax(1337d, 9001d);

    Mockito.when(SOURCE_CONTROLLER.getCapabilities()).thenReturn(SPEC);

    final ChannelHandler  HANDLER      = new ServerHandler(CONFIG, SOURCE_CONTROLLER);
    final EmbeddedChannel CHANNEL      = new EmbeddedChannel(HANDLER);
    final MessageBuilder  RECEIVED_MSG = CHANNEL.readOutbound();

    assert RECEIVED_MSG.getRoot(BaseMessage.factory).getType() == Type.CAPABILITIES;
  }

  @Test
  public void testRequestResourcesReleasedOnClose() throws Exception {
    final ChnlzrServerConfig      CONFIG            = config();
    final SamplesSourceController SOURCE_CONTROLLER = Mockito.mock(SamplesSourceController.class);
    final ChannelSpec             SPEC              = ChannelSpec.fromMinMax(1337d, 9001d);

    Mockito.when(SOURCE_CONTROLLER.getCapabilities()).thenReturn(SPEC);
    Mockito.when(SOURCE_CONTROLLER.configureSourceForSink(Mockito.any())).thenReturn(0x00);

    final ChannelHandler  HANDLER = new ServerHandler(CONFIG, SOURCE_CONTROLLER);
    final EmbeddedChannel CHANNEL = new EmbeddedChannel(HANDLER);

    assert CHANNEL.readOutbound() != null;

    CHANNEL.writeInbound(request().getRoot(BaseMessage.factory).asReader());

    Mockito.verify(SOURCE_CONTROLLER, Mockito.times(1)).configureSourceForSink(Mockito.any());
    Mockito.verify(SOURCE_CONTROLLER, Mockito.never()).releaseSink(Mockito.any());

    HANDLER.channelInactive(Mockito.mock(ChannelHandlerContext.class));

    Mockito.verify(SOURCE_CONTROLLER, Mockito.times(1)).releaseSink(Mockito.any());
  }

  @Test
  public void testContextClosedOnChannelRequestAfterChannelAllocation() throws Exception {
    final ChnlzrServerConfig      CONFIG            = config();
    final SamplesSourceController SOURCE_CONTROLLER = Mockito.mock(SamplesSourceController.class);
    final ChannelSpec             SPEC              = ChannelSpec.fromMinMax(1337d, 9001d);

    Mockito.when(SOURCE_CONTROLLER.getCapabilities()).thenReturn(SPEC);
    Mockito.when(SOURCE_CONTROLLER.configureSourceForSink(Mockito.any())).thenReturn(0x00);

    final ChannelHandler  HANDLER = new ServerHandler(CONFIG, SOURCE_CONTROLLER);
    final EmbeddedChannel CHANNEL = new EmbeddedChannel(HANDLER);

    assert CHANNEL.readOutbound() != null;

    CHANNEL.writeInbound(request().getRoot(BaseMessage.factory).asReader());

    Mockito.verify(SOURCE_CONTROLLER, Mockito.times(1)).configureSourceForSink(Mockito.any());

    final ChannelHandlerContext CONTEXT = Mockito.mock(ChannelHandlerContext.class);
    HANDLER.channelRead(CONTEXT, request().getRoot(BaseMessage.factory).asReader());

    Mockito.verify(CONTEXT, Mockito.times(1)).close();
  }

}
