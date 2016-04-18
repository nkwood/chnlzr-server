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

package org.anhonesteffort.chnlzr.input;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import org.anhonesteffort.chnlzr.ChnlzrServerConfig;
import org.anhonesteffort.dsp.sample.Samples;
import org.anhonesteffort.dsp.sample.TunableSamplesSource;
import org.anhonesteffort.dsp.sample.TunableSamplesSourceFactory;

import java.util.Optional;

public class InputFactory {

  private final Optional<TunableSamplesSource>    source;
  private final Optional<SamplesSourceController> sourceController;
  private final Optional<Disruptor<Samples>>      disruptor;

  public InputFactory(ChnlzrServerConfig config) {
    TunableSamplesSourceFactory sourceFactory = new TunableSamplesSourceFactory(
        new BlockingWaitStrategy(), config.ringBufferSize(),
        config.sourceCpuAffinity(), config.cicPoolSize()
    );

    source    = sourceFactory.getSource();
    disruptor = Optional.ofNullable(sourceFactory.getDisruptor());

    if (source.isPresent()) {
      this.sourceController = Optional.of(new SamplesSourceController(
          sourceFactory.getSource().get(), config.cicPoolSize(), config.dcOffset()
      ));
    } else {
      this.sourceController = Optional.empty();
    }
  }

  public Optional<TunableSamplesSource> getSource() {
    return source;
  }

  public Optional<SamplesSourceController> getSourceController() {
    return sourceController;
  }

  public Optional<Disruptor<Samples>> getDisruptor() {
    return disruptor;
  }

}