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

package org.anhonesteffort.chnlzr.input;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.ExceptionHandler;
import org.anhonesteffort.chnlzr.ChnlzrServerConfig;
import org.anhonesteffort.dsp.sample.SdrSamplesSource;
import org.anhonesteffort.dsp.sample.SdrSamplesSourceProvider;

import java.util.Optional;

public class InputFactory {

  private final Optional<SdrSamplesSource> source;
  private final Optional<SamplesSourceController> sourceController;

  public InputFactory(ChnlzrServerConfig config, ExceptionHandler disruptorCallback) {
    SdrSamplesSourceProvider sourceProvider = new SdrSamplesSourceProvider(
        new BlockingWaitStrategy(), config.ringBufferSize(),
        config.cicPoolSize(), config.sourceCpuAffinity(), disruptorCallback
    );

    source = sourceProvider.getSource();
    if (source.isPresent()) {
      this.sourceController = Optional.of(new SamplesSourceController(
          source.get(), config.cicPoolSize(), config.dcOffset()
      ));
    } else {
      this.sourceController = Optional.empty();
    }
  }

  public Optional<SdrSamplesSource> getSource() {
    return source;
  }

  public Optional<SamplesSourceController> getSourceController() {
    return sourceController;
  }

}