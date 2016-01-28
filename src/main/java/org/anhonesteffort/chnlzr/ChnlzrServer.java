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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
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
import org.anhonesteffort.chnlzr.samples.SamplesSourceController;
import org.anhonesteffort.dsp.sample.SamplesSourceException;
import org.anhonesteffort.dsp.sample.TunableSamplesSource;
import org.anhonesteffort.dsp.sample.TunableSamplesSourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ChnlzrServer {

  private final ChnlzrServerConfig   config;
  private final ExecutorService      dspExecutor;
  private final TunableSamplesSource source;
  private final ServerHandlerFactory handlers;

  public ChnlzrServer(ChnlzrServerConfig config) throws SamplesSourceException {
    this.config = config;
    dspExecutor = Executors.newFixedThreadPool(config.dspExecutorPoolSize());

    TunableSamplesSourceFactory sourceFactory = new TunableSamplesSourceFactory();
    List<TunableSamplesSource>  sources       = sourceFactory.get();

    if (!sources.isEmpty()) {
      SamplesSourceController sourceController = new SamplesSourceController(
          sources.get(0), (config.dspExecutorPoolSize() - 1), config.dcOffset()
      );

      this.source = sources.get(0);
      handlers    = new ServerHandlerFactory(config, dspExecutor, sourceController);
    } else {
      throw new SamplesSourceException("no samples sources available");
    }
  }

  public void run() throws InterruptedException {
    ListenableFuture sourceFuture = MoreExecutors.listeningDecorator(dspExecutor).submit(source);
    EventLoopGroup   bossGroup    = new NioEventLoopGroup();
    EventLoopGroup   workerGroup  = new NioEventLoopGroup();
    ServerBootstrap  bootstrap    = new ServerBootstrap();

    try {

      bootstrap.group(bossGroup, workerGroup)
               .channel(NioServerSocketChannel.class)
               .option(ChannelOption.SO_BACKLOG, 128)
               .childOption(ChannelOption.SO_KEEPALIVE, true)
               .childOption(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, config.bufferHighWaterMark())
               .childOption(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, config.bufferLowWaterMark())
               .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) {
                   ch.pipeline().addLast("idle state", new IdleStateHandler(0, 0, config.idleStateThresholdMs(), TimeUnit.MILLISECONDS));
                   ch.pipeline().addLast("heartbeat",  IdleStateHeartbeatWriter.INSTANCE);
                   ch.pipeline().addLast("encoder",    BaseMessageEncoder.INSTANCE);
                   ch.pipeline().addLast("decoder",    new BaseMessageDecoder());
                   ch.pipeline().addLast("handler",    handlers.create());
                 }
               });

      ChannelFuture channelFuture = bootstrap.bind(config.serverPort()).sync();
      Futures.addCallback(sourceFuture, new SourceStoppedCallback(channelFuture.channel()));
      channelFuture.channel().closeFuture().sync();

    } finally {
      dspExecutor.shutdownNow();
      workerGroup.shutdownGracefully();
      bossGroup.shutdownGracefully();
      sourceFuture.cancel(true);
    }

    System.exit(1);
  }

  private static class SourceStoppedCallback implements FutureCallback<Void> {
    private static final Logger log = LoggerFactory.getLogger(SourceStoppedCallback.class);
    private final Channel boundChannel;

    public SourceStoppedCallback(Channel boundChannel) {
      this.boundChannel = boundChannel;
    }

    @Override
    public void onSuccess(Void nothing) {
      log.error("samples source stopped unexpectedly");
      boundChannel.close();
    }

    @Override
    public void onFailure(Throwable throwable) {
      log.error("samples source stopped unexpectedly", throwable);
      boundChannel.close();
    }
  }

  public static void main(String[] args) throws Exception {
    new ChnlzrServer(new ChnlzrServerConfig()).run();
  }

}