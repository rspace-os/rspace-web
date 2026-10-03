package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.service.ClientReadable;
import com.researchspace.service.ClientReadableSecret;
import java.util.Map;
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
  }

  @Test
  void onlyMarkedPropertiesAreClientReadable() {
    assertEquals(
        Map.of("service.url", "https://service.example", "service.browser.key", "browser-key"),
        DeploymentPropertiesController.clientReadableFields(new Properties(), Properties.class));
  }
}
