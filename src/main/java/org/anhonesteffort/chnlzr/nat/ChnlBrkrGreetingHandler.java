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

import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import org.capnproto.MessageBuilder;

import java.net.ConnectException;

public class ChnlBrkrGreetingHandler extends ChannelHandlerAdapter {

  private final SettableFuture<ChnlBrkrGreetingHandler> future;
  private final MessageBuilder hello;
  private ChannelHandlerContext context;

  public ChnlBrkrGreetingHandler(SettableFuture<ChnlBrkrGreetingHandler> future, MessageBuilder hello) {
    this.future = future;
    this.hello  = hello;
  }

  public ChannelHandlerContext getContext() {
    return context;
  }

  @Override
  public void channelActive(ChannelHandlerContext context) {
    this.context = context;
    context.writeAndFlush(hello);
    future.set(this);
  }

  @Override
  public void channelInactive(ChannelHandlerContext context) {
    future.setException(new ConnectException("failed to connect to chnlbrkr"));
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
    context.close();
    if (!future.setException(cause)) {
      super.exceptionCaught(context, cause);
    }
  }

}
