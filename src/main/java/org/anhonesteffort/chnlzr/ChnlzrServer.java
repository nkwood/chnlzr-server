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

package org.anhonesteffort.chnlzr;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.anhonesteffort.chnlzr.capnp.BaseMessageDecoder;
import org.anhonesteffort.chnlzr.capnp.BaseMessageEncoder;
import org.anhonesteffort.chnlzr.input.InputFactory;
import org.anhonesteffort.chnlzr.input.SamplesSourceController;
import org.anhonesteffort.chnlzr.netty.IdleStateHeartbeatWriter;
import org.anhonesteffort.chnlzr.resample.SamplesSinkFactory;
import org.anhonesteffort.dsp.sample.SdrSamplesSource;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ChnlzrServer {

  private final CriticalCallback criticalCallback = new CriticalCallback();
  private final ListeningExecutorService sourcePool = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());

  private final ChnlzrServerConfig      config;
  private final SdrSamplesSource        source;
  private final SamplesSourceController sourceController;
  private final SamplesSinkFactory      resampling;

  public ChnlzrServer(ChnlzrServerConfig config) throws IllegalStateException {
    this.config = config;
    InputFactory inputFactory = new InputFactory(config, criticalCallback);

    if (inputFactory.getSource().isPresent()) {
      source           = inputFactory.getSource().get();
      sourceController = inputFactory.getSourceController().get();
      resampling       = new SamplesSinkFactory(config);
    } else {
      throw new IllegalStateException("no samples sources available");
    }
  }

  @SuppressWarnings("unchecked")
  private void run() throws InterruptedException {
    ListenableFuture sourceFuture = sourcePool.submit(source);
    Futures.addCallback(sourceFuture, criticalCallback);

    EventLoopGroup  bossGroup   = new NioEventLoopGroup();
    EventLoopGroup  workerGroup = new NioEventLoopGroup();
    ServerBootstrap bootstrap   = new ServerBootstrap();

    try {

      bootstrap.group(bossGroup, workerGroup)
               .channel(NioServerSocketChannel.class)
               .option(ChannelOption.SO_BACKLOG, 128)
               .childOption(ChannelOption.SO_KEEPALIVE, true)
               .childOption(ChannelOption.TCP_NODELAY, true)
               .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) {
                   ch.pipeline().addLast("idle state", new IdleStateHandler(0, 0, config.idleStateThresholdMs(), TimeUnit.MILLISECONDS));
                   ch.pipeline().addLast("heartbeat",  IdleStateHeartbeatWriter.INSTANCE);
                   ch.pipeline().addLast("encoder",    BaseMessageEncoder.INSTANCE);
                   ch.pipeline().addLast("decoder",    new BaseMessageDecoder());
                   ch.pipeline().addLast("handler",    new ServerHandler(config, resampling, sourceController));
                 }
               });

      ChannelFuture channelFuture = bootstrap.bind(config.serverPort()).sync();
      channelFuture.channel().closeFuture().sync();

    } finally {
      workerGroup.shutdownGracefully();
      bossGroup.shutdownGracefully();
      sourceFuture.cancel(true);
      sourcePool.shutdownNow();
    }

    System.exit(1);
  }

  public static void main(String[] args) throws Exception {
    new ChnlzrServer(new ChnlzrServerConfig()).run();
  }

}