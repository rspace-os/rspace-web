package com.researchspace.testutils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

class ApiV2DatabaseCleanerExtensionTest {

  @Test
  void nestedTestsRegisterTheOuterClassAndCleanOnlyAfterAllSiblingsFinish() {
    ExtensionContext outer = mock(ExtensionContext.class);
    ExtensionContext nested = mock(ExtensionContext.class);
    ApplicationContext application = mock(ApplicationContext.class);
    DataSource dataSource = mock(DataSource.class);
    when(outer.getTestClass()).thenReturn(Optional.of(getClass()));
    when(outer.getRequiredTestClass()).thenReturn((Class) getClass());
    when(nested.getParent()).thenReturn(Optional.of(outer));
    when(nested.getRequiredTestClass()).thenReturn((Class) NestedTests.class);
    when(application.getBean(DataSource.class)).thenReturn(dataSource);

    try (var spring = mockStatic(SpringExtension.class);
        var cleaner = mockStatic(DatabaseCleaner.class);
        var runner = mockStatic(TestRunnerController.class)) {
      spring.when(() -> SpringExtension.getApplicationContext(nested)).thenReturn(application);
      runner.when(TestRunnerController::isFastRun).thenReturn(false);
      ApiV2DatabaseCleanerExtension extension = new ApiV2DatabaseCleanerExtension();
      extension.beforeEach(nested);
      cleaner.verify(() -> DatabaseCleaner.register(getClass(), dataSource));
      extension.afterAll(nested);
      cleaner.verifyNoMoreInteractions();
      extension.afterAll(outer);
      cleaner.verify(() -> DatabaseCleaner.cleanUp(getClass()));
      cleaner.verifyNoMoreInteractions();
    }
  }

  private class NestedTests {}
}
