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

import com.google.common.util.concurrent.FutureCallback;
import com.lmax.disruptor.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CriticalCallback implements FutureCallback<Void>, ExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(CriticalCallback.class);

  @Override
  public void onSuccess(Void nothing) {
    log.error("samples source stopped unexpectedly");
    System.exit(1);
  }

  @Override
  public void onFailure(Throwable throwable) {
    log.error("samples source stopped unexpectedly", throwable);
    System.exit(1);
  }

  @Override
  public void handleEventException(Throwable throwable, long l, Object o) {
    log.error("disruptor error", throwable);
    System.exit(1);
  }

  @Override
  public void handleOnStartException(Throwable throwable) {
    log.error("disruptor error", throwable);
    System.exit(1);
  }

  @Override
  public void handleOnShutdownException(Throwable throwable) {
    log.error("disruptor error", throwable);
    System.exit(1);
  }

}