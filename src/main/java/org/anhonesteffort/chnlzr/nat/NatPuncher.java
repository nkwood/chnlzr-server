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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import io.netty.channel.ChannelPipeline;
import org.anhonesteffort.chnlzr.ServerHandler;
import org.anhonesteffort.chnlzr.ServerHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.anhonesteffort.chnlzr.Proto.HostId;

public class NatPuncher implements FutureCallback<ChnlBrkrGreetingHandler> {

  private static final Logger log = LoggerFactory.getLogger(NatPuncher.class);

  private final ChnlBrkrConnectionFactory connections;
  private final ServerHandlerFactory      handlers;
  private final HostId.Reader             brkrHost;

  public NatPuncher(ChnlBrkrConnectionFactory connections,
                    ServerHandlerFactory      handlers,
                    HostId.Reader             brkrHost)
  {
    this.connections = connections;
    this.handlers    = handlers;
    this.brkrHost    = brkrHost;
  }

  public void punch() {
    Futures.addCallback(connections.create(brkrHost), this);
  }

  @Override
  public void onSuccess(ChnlBrkrGreetingHandler connection) {
    ChannelPipeline pipeline = connection.getContext().pipeline();
    ServerHandler   handler  = handlers.create();

    handler.setNatPuncher(this);
    pipeline.replace(connection, "handler", handler);
  }

  @Override
  public void onFailure(Throwable error) {
    log.warn("failed to greet chnlbrkr", error);
  }

}
