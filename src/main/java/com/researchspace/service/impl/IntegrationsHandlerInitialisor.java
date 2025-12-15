package com.researchspace.service.impl;

import com.researchspace.service.IntegrationsHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * initialises {@link IntegrationsHandler} property lookup.
 *
 * <p>This can't be run as a regular @PostInit method as it depends on the Liquibase updates having
 * been run before. The success of this would depend on the bean initialisation order, which is
 * variable. <br>
 * This method is run <em>after</em> the application context is refreshed and completed.
 */
public class IntegrationsHandlerInitialisor extends AbstractAppInitializor {

  @Autowired IntegrationsHandler handler;

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {
    log.info("running IntegrationsHandlerInitialisor...");
    handler.init();
    log.info("IntegrationsHandlerInitialisor onAppStartup complete");
  }
}
