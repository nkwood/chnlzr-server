package org.anhonesteffort.chnlzr;

import org.anhonesteffort.chnlzr.samples.SamplesSourceController;

import java.util.concurrent.ExecutorService;

public class ServerHandlerFactory {

  private final ChnlzrServerConfig      config;
  private final ExecutorService         executor;
  private final SamplesSourceController sourceController;

  public ServerHandlerFactory(ChnlzrServerConfig      config,
                              ExecutorService         executor,
                              SamplesSourceController sourceController)
  {
    this.config           = config;
    this.executor         = executor;
    this.sourceController = sourceController;
  }

  public ServerHandler create() {
    return new ServerHandler(config, executor, sourceController);
  }

}
