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

import org.anhonesteffort.chnlzr.netty.WriteQueuingContext;
import org.anhonesteffort.chnlzr.output.RfNetworkSink;
import org.anhonesteffort.chnlzr.resample.ResamplingSink;
import org.anhonesteffort.chnlzr.resample.RfResamplingSink;

import static org.anhonesteffort.chnlzr.capnp.Proto.ChannelRequest;

public class ResamplingNetworkSinkFactory {

  private final int samplesPerMessage;

  public ResamplingNetworkSinkFactory(ChnlzrServerConfig config) {
    this.samplesPerMessage = config.samplesPerMessage();
  }

  public ResamplingSink create(WriteQueuingContext context, ChannelRequest.Reader request) {
    return new RfResamplingSink(request, new RfNetworkSink(context, samplesPerMessage));
  }

}
