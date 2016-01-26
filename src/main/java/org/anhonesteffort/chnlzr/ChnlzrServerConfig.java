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

import java.io.IOException;

public class ChnlzrServerConfig extends ChnlzrConfig {

  private final int    serverPort;
  private final String chnlzrId;
  private final int    samplesPerMessage;
  private final int    samplesQueueSize;
  private final int    dspExecutorPoolSize;
  private final long   brokerGreetingIntervalMs;
  private final long   brokerGreetingTimeoutMs;
  private final double latitude;
  private final double longitude;
  private final int    polarization;
  private final double dcOffset;

  public ChnlzrServerConfig() throws IOException {
    super();

    serverPort               = Integer.parseInt(properties.getProperty("server_port"));
    chnlzrId                 = properties.getProperty("chnlzr_id");
    samplesPerMessage        = Integer.parseInt(properties.getProperty("samples_per_message"));
    samplesQueueSize         = Integer.parseInt(properties.getProperty("samples_queue_size"));
    dspExecutorPoolSize      = Integer.parseInt(properties.getProperty("dsp_executor_pool_size"));
    brokerGreetingIntervalMs = Long.parseLong(properties.getProperty("broker_greeting_interval_ms"));
    brokerGreetingTimeoutMs  = Long.parseLong(properties.getProperty("broker_greeting_timeout_ms"));
    latitude                 = Double.parseDouble(properties.getProperty("latitude"));
    longitude                = Double.parseDouble(properties.getProperty("longitude"));
    polarization             = Integer.parseInt(properties.getProperty("polarization"));
    dcOffset                 = Double.parseDouble(properties.getProperty("dc_offset"));
  }

  public int serverPort() {
    return serverPort;
  }

  public String chnlzrId() {
    return chnlzrId;
  }

  public int samplesPerMessage() {
    return samplesPerMessage;
  }

  public int samplesQueueSize() {
    return samplesQueueSize;
  }

  public int dspExecutorPoolSize() {
    return dspExecutorPoolSize;
  }

  public long brokerGreetingIntervalMs() {
    return brokerGreetingIntervalMs;
  }

  public long brokerGreetingTimeoutMs() {
    return brokerGreetingTimeoutMs;
  }

  public double latitude() {
    return latitude;
  }

  public double longitude() {
    return longitude;
  }

  public int polarization() {
    return polarization;
  }

  public double dcOffset() {
    return dcOffset;
  }

}
