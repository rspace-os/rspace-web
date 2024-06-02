package com.researchspace.dao.customliquibaseupdates;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AsyncDBUpdateLauncher implements ApplicationListener<ContextRefreshedEvent> {

  private @Autowired AsyncLiveLiquibaseUpdater asyncDb;
  private boolean contextRefreshEventHandled = false;

  @Override
  public void onApplicationEvent(ContextRefreshedEvent appEvent) {

    if (contextRefreshEventHandled) {
      return; // only need to handle event once
    }
    try {
      asyncDb.executeAsync();
    } catch (Exception e) {
      log.error("Couldn't perform live post-start up data migration, {}", e.getMessage());
    } finally {
      contextRefreshEventHandled = true;
    }
  }
}
