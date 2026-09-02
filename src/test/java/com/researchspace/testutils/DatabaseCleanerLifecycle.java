package com.researchspace.testutils;

import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

/** Registers a test class for database cleanup after its final test. */
public interface DatabaseCleanerLifecycle {

  DataSource getDataSourceForCleanup();

  @BeforeEach
  default void registerDatabaseCleaner() {
    DatabaseCleaner.register(getClass(), getDataSourceForCleanup());
  }

  @AfterAll
  static void cleanDatabase(TestInfo testInfo) {
    if (!TestRunnerController.isFastRun()) {
      DatabaseCleaner.cleanUp(testInfo.getTestClass().orElseThrow());
    }
  }
}
