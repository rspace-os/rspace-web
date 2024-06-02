package com.researchspace.dao.customliquibaseupdates;

import org.springframework.scheduling.annotation.Async;

public interface AsyncLiveLiquibaseUpdater {
  // we don't need shiro permissions for these  updates

  @Async("asyncDbMigrationExecutor")
  void executeAsync() throws Exception;
}
