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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.anhonesteffort.chnlzr.pipeline.BaseMessageDecoder;
import org.anhonesteffort.chnlzr.pipeline.BaseMessageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

import static org.anhonesteffort.chnlzr.Proto.BaseMessage;

public class ChnlBrkrGreeter implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(ChnlBrkrGreeter.class);
  private static final Timer timer = new Timer(true);

  private final ChnlzrServerConfig config;
  private final EventLoopGroup     workerGroup;
  private final String             chnlzrHostname;
  private final int                chnlzrPort;
  private final String             brokerHostname;
  private final int                brokerPort;

  private TimerTask timeoutTask;

  public ChnlBrkrGreeter(ChnlzrServerConfig config,
                         EventLoopGroup     workerGroup,
                         String             chnlzrHostname,
                         int                chnlzrPort,
                         String             brokerHostname,
                         int                brokerPort)
  {
    this.config         = config;
    this.workerGroup    = workerGroup;
    this.chnlzrHostname = chnlzrHostname;
    this.chnlzrPort     = chnlzrPort;
    this.brokerHostname = brokerHostname;
    this.brokerPort     = brokerPort;
  }

  @Override
  public void run() {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(workerGroup)
             .channel(NioSocketChannel.class)
             .option(ChannelOption.SO_KEEPALIVE, false)
             .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.connectionTimeoutMs())
             .handler(new ChannelInitializer<SocketChannel>() {
               @Override
               public void initChannel(SocketChannel ch) {
                 ch.pipeline().addLast("encoder", BaseMessageEncoder.INSTANCE);
                 ch.pipeline().addLast("decoder", new BaseMessageDecoder());
                 ch.pipeline().addLast("handler", new GreetingHandler());
               }
             });

    ChannelFuture connectFuture = bootstrap.connect(brokerHostname, brokerPort);
                  timeoutTask   = new TimerTask() {
      @Override
      public void run() {
        log.warn("timed out while waiting for BRKR_HELLO");
        connectFuture.channel().close();
      }
    };

    timer.schedule(timeoutTask, config.brokerGreetingTimeoutMs());
  }

  private class GreetingHandler extends ChannelHandlerAdapter {

    @Override
    public void channelActive(ChannelHandlerContext context) {
      context.writeAndFlush(CapnpUtil.chnlzrHello(chnlzrHostname, chnlzrPort));
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object response) {
      BaseMessage.Reader message = (BaseMessage.Reader) response;

      switch (message.getType()) {
        case BRKR_HELLO:
          timeoutTask.cancel();
          log.info("greeted the channel broker");
          context.close();
          break;

        case BRKR_STATE:
          break;

        default:
          log.warn("received unexpected message type from channel broker: " + message.getType());
      }
    }

  }
}
