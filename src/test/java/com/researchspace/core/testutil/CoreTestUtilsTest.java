package com.researchspace.core.testutil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.Test;

class CoreTestUtilsTest {

  @Test
  void stringLoggerCapturesWithoutRemovingRootAppenders() {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Set<String> rootAppenders =
        Set.copyOf(ctx.getConfiguration().getRootLogger().getAppenders().keySet());
    assertThat(rootAppenders).isNotEmpty();
    Logger log = LogManager.getLogger(CoreTestUtilsTest.class);

    StringAppenderForTestLogging first = CoreTestUtils.configureStringLogger(log);
    StringAppenderForTestLogging second = CoreTestUtils.configureStringLogger(log);
    log.info("captured");

    assertEquals("", first.logContents);
    assertEquals("captured", second.logContents);
    assertThat(ctx.getConfiguration().getRootLogger().getAppenders().keySet())
        .containsExactlyInAnyOrderElementsOf(rootAppenders);
  }
}
