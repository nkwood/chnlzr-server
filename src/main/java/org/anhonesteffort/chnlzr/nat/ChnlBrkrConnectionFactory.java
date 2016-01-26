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

package org.anhonesteffort.chnlzr.nat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.anhonesteffort.chnlzr.CapnpUtil;
import org.anhonesteffort.chnlzr.ChnlzrServerConfig;
import org.anhonesteffort.chnlzr.pipeline.BaseMessageDecoder;
import org.anhonesteffort.chnlzr.pipeline.BaseMessageEncoder;
import org.anhonesteffort.chnlzr.pipeline.IdleStateHeartbeatWriter;
import org.capnproto.MessageBuilder;

import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

import static org.anhonesteffort.chnlzr.Proto.HostId;

public class ChnlBrkrConnectionFactory {

  private final ChnlzrServerConfig       config;
  private final Class<? extends Channel> channel;
  private final EventLoopGroup           workerGroup;
  private final MessageBuilder           chnlzrHello;

  public ChnlBrkrConnectionFactory(ChnlzrServerConfig       config,
                                   Class<? extends Channel> channel,
                                   EventLoopGroup           workerGroup)
  {
    this.config      = config;
    this.channel     = channel;
    this.workerGroup = workerGroup;
    chnlzrHello      = CapnpUtil.chnlzrHello(config.chnlzrId());
  }

  public ListenableFuture<ChnlBrkrGreetingHandler> create(HostId.Reader brkrHost) {
    SettableFuture<ChnlBrkrGreetingHandler> future     = SettableFuture.create();
    ChnlBrkrGreetingHandler                 connection = new ChnlBrkrGreetingHandler(future, chnlzrHello);
    Bootstrap                               bootstrap  = new Bootstrap();

    bootstrap.group(workerGroup)
             .channel(channel)
             .option(ChannelOption.SO_KEEPALIVE, true)
             .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.connectionTimeoutMs())
             .option(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, config.bufferHighWaterMark())
             .option(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, config.bufferLowWaterMark())
             .handler(new ChannelInitializer<SocketChannel>() {
               @Override
               public void initChannel(SocketChannel ch) {
                 ch.pipeline().addLast("idle state", new IdleStateHandler(0, 0, config.idleStateThresholdMs(), TimeUnit.MILLISECONDS));
                 ch.pipeline().addLast("heartbeat",  IdleStateHeartbeatWriter.INSTANCE);
                 ch.pipeline().addLast("encoder",    BaseMessageEncoder.INSTANCE);
                 ch.pipeline().addLast("decoder",    new BaseMessageDecoder());
                 ch.pipeline().addLast("connector",  connection);
               }
             });

    bootstrap.connect(brkrHost.getHostname().toString(), brkrHost.getPort())
             .addListener(connect -> {
               if (!connect.isSuccess())
                 future.setException(new ConnectException("failed to connect to chnlbrkr"));
             });

    return future;
  }

}
