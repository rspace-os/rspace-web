package com.researchspace.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.service.ClientReadable;
import com.researchspace.service.ClientReadableSecret;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

class DeploymentPropertiesControllerTest {

  static class Properties {
    @ClientReadable
    @Value("${service.url}")
    String url = "https://service.example";

    @ClientReadableSecret("test")
    @Value("${service.browser.key:}")
    String browserKey = "browser-key";

    @Value("${service.client.secret}")
    String clientSecret = "client-secret";

    @ClientReadable String notAProperty = "x";

    @ClientReadable
    @Value("literal-expression-should-not-log")
    String literalReadable = "literal-value-should-not-log";

    @ClientReadableSecret("test")
    @Value("#{'spel-expression-should-not-log'}")
    String spelSecret = "spel-value-should-not-log";

    @ClientReadable
    @Value("${}")
    String emptyReadableName = "empty-readable-value-should-not-log";

    @ClientReadableSecret("test")
    @Value("${   }")
    String whitespaceSecretName = "whitespace-secret-value-should-not-log";
  }

  @Test
  void onlyMarkedPropertiesAreClientReadable() {
    assertEquals(
        Map.of("service.url", "https://service.example", "service.browser.key", "browser-key"),
        DeploymentPropertiesController.clientReadableFields(new Properties(), Properties.class));
  }

  @Test
  void warnsAboutInvalidClientReadableValuesWithoutLoggingValues() {
    Logger logger = (Logger) LogManager.getLogger(DeploymentPropertiesController.class);
    StringAppenderForTestLogging capture = new StringAppenderForTestLogging();
    capture.start();
    logger.addAppender(capture);
    try {
      DeploymentPropertiesController.clientReadableFields(new Properties(), Properties.class);
    } finally {
      logger.removeAppender(capture);
      capture.stop();
    }

    assertThat(capture.logContents)
        .contains(
            "Skipping client-readable field Properties.literalReadable: @Value has no "
                + "property name",
            "Skipping client-readable field Properties.spelSecret: @Value has no property name")
        .doesNotContain("should-not-log");
  }
}
