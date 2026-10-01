package com.researchspace.webapp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class WebConfigCorsTest {
  @Test
  void permitsConditionalApiUpdatesWithoutOpeningSessionTokenIssuance() {
    WebConfig config = new WebConfig();
    ReflectionTestUtils.setField(config, "permissiveCorsEnabled", "true");
    var registry =
        new CorsRegistry() {
          void verify() {
            var api = getCorsConfigurations().get("/api/v2/**");
            assertNotNull(api.checkHttpMethod(HttpMethod.PUT));
            assertNotNull(api.checkHttpMethod(HttpMethod.PATCH));
            assertEquals(
                List.of("Authorization", "Content-Type", "If-Match"),
                api.checkHeaders(List.of("Authorization", "Content-Type", "If-Match")));
            assertEquals(List.of("ETag"), api.getExposedHeaders());
            assertNull(
                getCorsConfigurations()
                    .get("/api/v2/oauth/tokens")
                    .checkOrigin("https://client.example"));
          }
        };
    config.addCorsMappings(registry);
    registry.verify();
  }
}
