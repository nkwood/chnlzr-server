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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.anhonesteffort.chnlzr.pipeline.BaseMessageDecoder;
import org.anhonesteffort.chnlzr.pipeline.BaseMessageEncoder;
import org.anhonesteffort.chnlzr.pipeline.IdleStateHeartbeatWriter;
import org.anhonesteffort.dsp.ConcurrentSource;
import org.anhonesteffort.dsp.sample.SamplesSourceException;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChnlzrServer {

  private final ScheduledExecutorService greetingExecutor = Executors.newSingleThreadScheduledExecutor();
  private final ExecutorService          dspExecutor;
  private final SamplesSourceController  sourceController;
  private final ChnlzrServerConfig       config;

  private final String            hostname;
  private final int               listenPort;
  private final Optional<String>  brokerHostname;
  private final Optional<Integer> brokerPort;

  public ChnlzrServer(ChnlzrServerConfig config,
                      String             hostname,
                      int                listenPort,
                      Optional<String>   brokerHostname,
                      Optional<Integer>  brokerPort)
      throws SamplesSourceException
  {
    dspExecutor         = Executors.newFixedThreadPool(config.dspExecutorPoolSize());
    sourceController    = new SamplesSourceController(config.dcOffset());
    this.config         = config;
    this.hostname       = hostname;
    this.listenPort     = listenPort;
    this.brokerHostname = brokerHostname;
    this.brokerPort     = brokerPort;
  }

  public void run() throws InterruptedException {
    EventLoopGroup  bossGroup   = new NioEventLoopGroup();
    EventLoopGroup  workerGroup = new NioEventLoopGroup();
    ServerBootstrap bootstrap   = new ServerBootstrap();

    try {

      bootstrap.group(bossGroup, workerGroup)
               .channel(NioServerSocketChannel.class)
               .option(ChannelOption.SO_BACKLOG, 128)
               .childOption(ChannelOption.SO_KEEPALIVE, true)
               .childOption(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, config.avgByteRate())
               .childOption(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK,  config.avgByteRate() / 4)
               .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) {
                   ch.pipeline().addLast("idle state", new IdleStateHandler(0, 0, config.idleStateThresholdMs(), TimeUnit.MILLISECONDS));
                   ch.pipeline().addLast("heartbeat",  IdleStateHeartbeatWriter.INSTANCE);
                   ch.pipeline().addLast("encoder",    BaseMessageEncoder.INSTANCE);
                   ch.pipeline().addLast("decoder",    new BaseMessageDecoder());
                   ch.pipeline().addLast("handler",    new ServerHandler(config, dspExecutor, sourceController));
                 }
               });

      ChannelFuture channelFuture = bootstrap.bind(listenPort).sync();

      if (brokerHostname.isPresent() && brokerPort.isPresent()) {
        greetingExecutor.scheduleAtFixedRate(new ChnlBrkrGreeter(
            config, workerGroup, hostname, listenPort, brokerHostname.get(), brokerPort.get()
        ), 0l, config.brokerGreetingIntervalMs(), TimeUnit.MILLISECONDS);
      }

      channelFuture.channel().closeFuture().sync();

    } finally {
      greetingExecutor.shutdownNow();
      dspExecutor.shutdownNow();
      ConcurrentSource.shutdownSources();
      workerGroup.shutdownGracefully();
      bossGroup.shutdownGracefully();
    }
  }

  public static void main(String[] args) throws Exception {
    new ChnlzrServer(
        new ChnlzrServerConfig(),
        (args.length > 0) ? args[0]                                : "localhost",
        (args.length > 1) ? Integer.parseInt(args[1])              : 8080,
        (args.length > 2) ? Optional.of(args[2])                   : Optional.<String>empty(),
        (args.length > 3) ? Optional.of(Integer.parseInt(args[3])) : Optional.<Integer>empty()
    ).run();
  }

}