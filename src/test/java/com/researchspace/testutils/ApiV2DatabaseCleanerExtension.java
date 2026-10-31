package com.researchspace.testutils;

import javax.sql.DataSource;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** Cleans committed fixtures after the outer class and all of its nested tests finish. */
public class ApiV2DatabaseCleanerExtension implements BeforeEachCallback, AfterAllCallback {

  @Override
  public void beforeEach(ExtensionContext context) {
    DatabaseCleaner.register(
        outerClassContext(context).getRequiredTestClass(),
        SpringExtension.getApplicationContext(context).getBean(DataSource.class));
  }

  @Override
  public void afterAll(ExtensionContext context) {
    if (!TestRunnerController.isFastRun()
        && context
            .getRequiredTestClass()
            .equals(outerClassContext(context).getRequiredTestClass())) {
      DatabaseCleaner.cleanUp(context.getRequiredTestClass());
    }
  }

  private ExtensionContext outerClassContext(ExtensionContext context) {
    while (context.getParent().isPresent()
        && context.getParent().orElseThrow().getTestClass().isPresent()) {
      context = context.getParent().orElseThrow();
    }
    return context;
  }
}
