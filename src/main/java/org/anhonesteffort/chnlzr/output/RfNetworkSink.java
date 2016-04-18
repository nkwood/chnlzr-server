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

package org.anhonesteffort.chnlzr.output;

import org.anhonesteffort.chnlzr.capnp.ProtoFactory;
import org.anhonesteffort.chnlzr.netty.WriteQueuingContext;
import org.anhonesteffort.dsp.ComplexNumber;
import org.capnproto.MessageBuilder;

import java.nio.ByteBuffer;

import static org.anhonesteffort.chnlzr.capnp.Proto.BaseMessage;

public class RfNetworkSink implements NetworkSink {

  private final ProtoFactory        proto = new ProtoFactory();
  private final WriteQueuingContext context;
  private final int                 samplesPerMessage;

  private MessageBuilder nextMessage;
  private ByteBuffer     nextSamples;

  public RfNetworkSink(WriteQueuingContext context, int samplesPerMessage) {
    this.context           = context;
    this.samplesPerMessage = samplesPerMessage;

    initNextMessage();
  }

  private void initNextMessage() {
    nextMessage = proto.samples(samplesPerMessage);
    nextSamples = nextMessage.getRoot(BaseMessage.factory).getSamples().getSamples().asByteBuffer();
  }

  @Override
  public void onSourceStateChange(Long sampleRate, Double frequency) {
    context.writeOrQueue(proto.state(sampleRate, 0d));
  }

  @Override
  public void consume(ComplexNumber sample) {
    nextSamples.putFloat(sample.getInPhase());
    nextSamples.putFloat(sample.getQuadrature());

    if (nextSamples.remaining() <= 0) {
      context.writeOrQueue(nextMessage);
      initNextMessage();
    }
  }

}