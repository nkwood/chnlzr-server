package org.anhonesteffort.chnlzr;

import org.anhonesteffort.chnlzr.samples.SamplesSourceController;

public class ServerHandlerFactory {

  private final ChnlzrServerConfig      config;
  private final SamplesSourceController sourceController;

  public ServerHandlerFactory(ChnlzrServerConfig config, SamplesSourceController sourceController) {
    this.config           = config;
    this.sourceController = sourceController;
  }

  public ServerHandler create() {
    return new ServerHandler(config, sourceController);
  }

}
