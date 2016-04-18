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

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import org.anhonesteffort.chnlzr.capnp.ProtoFactory;
import org.anhonesteffort.chnlzr.netty.WriteQueuingContext;
import org.anhonesteffort.chnlzr.resample.ResamplingNetworkSink;
import org.anhonesteffort.chnlzr.input.SamplesSourceController;
import org.capnproto.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.anhonesteffort.chnlzr.capnp.Proto.BaseMessage;
import static org.anhonesteffort.chnlzr.capnp.Proto.ChannelRequest;

public class ServerHandler extends ChannelHandlerAdapter {

  private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

  private final ProtoFactory            proto = new ProtoFactory();
  private final ChnlzrServerConfig      config;
  private final SamplesSourceController sourceController;
  private final MessageBuilder          capabilities;

  private Optional<ChannelAllocationRef> allocation = Optional.empty();

  public ServerHandler(ChnlzrServerConfig config, SamplesSourceController sourceController) {
    this.config           = config;
    this.sourceController = sourceController;
    capabilities          = proto.capabilities(
        config.latitude(),     config.longitude(),
        config.polarization(), sourceController.getCapabilities().getMinFreq(),
        sourceController.getCapabilities().getMaxFreq(),
        sourceController.getCapabilities().getSampleRate()
    );
  }

  @Override
  public void channelActive(ChannelHandlerContext context) {
    context.writeAndFlush(capabilities);
  }

  private void handleChannelRequest(ChannelHandlerContext context, ChannelRequest.Reader request) {
    if (allocation.isPresent()) {
      log.warn("received channel request after channel allocation, closing");
      context.close();
      return;
    }

    WriteQueuingContext   channelQueue = new WriteQueuingContext(context, config.clientWriteQueueSize());
    ResamplingNetworkSink channelSink  = new ResamplingNetworkSink(config, channelQueue, request);
    int                   error        = sourceController.configureSourceForSink(channelSink);

    if (error == 0x00) {
      allocation = Optional.of(new ChannelAllocationRef(channelQueue, channelSink));
      log.info(proto.spec(request) + " channel sink started");
    } else {
      context.writeAndFlush(proto.error(error));
    }
  }

  @Override
  public void channelRead(ChannelHandlerContext context, Object request) {
    BaseMessage.Reader message = (BaseMessage.Reader) request;

    switch (message.getType()) {
      case CHANNEL_REQUEST:
        handleChannelRequest(context, message.getChannelRequest());
        break;

      default:
        log.warn("received unknown message type " + message.getType() + ", closing");
        context.close();
    }
  }

  @Override
  public void channelWritabilityChanged(ChannelHandlerContext context) {
    if (allocation.isPresent()) {
      allocation.get().getChannelQueue().onWritabilityChanged();
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
    if (allocation.isPresent()) {
      log.error(allocation.get().getChannelSink().getChannelSpec() + " caught unexpected exception, closing", cause);
    } else {
      log.error("caught unexpected exception, closing", cause);
    }
    context.close();
  }

  @Override
  public void channelInactive(ChannelHandlerContext context) {
    if (allocation.isPresent()) {
      sourceController.releaseSink(allocation.get().getChannelSink());
      log.info(allocation.get().getChannelSink().getChannelSpec() + " channel sink stopped");
      allocation = Optional.empty();
    }
  }

  private static class ChannelAllocationRef {
    private final WriteQueuingContext  channelQueue;
    private final ResamplingNetworkSink channelSink;

    public ChannelAllocationRef(WriteQueuingContext channelQueue, ResamplingNetworkSink channelSink) {
      this.channelQueue = channelQueue;
      this.channelSink  = channelSink;
    }

    public WriteQueuingContext getChannelQueue() {
      return channelQueue;
    }

    public ResamplingNetworkSink getChannelSink() {
      return channelSink;
    }
  }

}