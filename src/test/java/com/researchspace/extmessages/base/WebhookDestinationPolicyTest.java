package com.researchspace.extmessages.base;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookDestinationPolicyTest {

  private final WebhookDestinationPolicy policy =
      WebhookDestinationPolicy.parse(
          "test.allowed.origins",
          "https://hooks.slack.com, https://*.environment.api.powerplatform.com,"
              + "https://*.logic.azure.com,,http://localhost:9099");

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://127.0.0.1:9988/x",
        "https://user:pw@hooks.slack.com/x",
        "http://hooks.slack.com/x",
        "https://hooks.slack.com.evil.example/x",
        "https://evilhooks.slack.com/x",
        "https://hooks.slack.com:8443/x",
        "https://logic.azure.com/x",
        "/relative/path",
        "not a url"
      })
  void rejectsUrlsOutsideTheAllowedOrigins(String url) {
    assertThrows(WebhookUrlNotAllowedException.class, () -> policy.check(url));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://hooks.slack.com/services/T/B/x",
        "https://HOOKS.slack.com./x",
        "https://default1234.56.environment.api.powerplatform.com:443/powerautomate/x?sig=y",
        "http://localhost:9099/msteams/webhook"
      })
  void acceptsUrlsOnAllowedOrigins(String url) {
    assertDoesNotThrow(() -> policy.check(url));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "hooks.slack.com",
        "ftp://hooks.slack.com",
        "https://h.example/path",
        "https://user@h.example",
        "https://h.example?sig=x",
        "https://h.example#x",
        "https://h.*.example",
        "https://",
        "https://*.0.0.1",
        "https://*.[::1]",
        "https://*.azurewebsites.net"
      })
  void malformedEntryStopsStartup(String entry) {
    assertThrows(
        IllegalStateException.class, () -> WebhookDestinationPolicy.parse("test.origins", entry));
  }

  @Test
  void entryDnsLabelsMayHaveUpTo63Characters() {
    String longest = "https://" + "a".repeat(63) + ".example";
    String tooLong = "https://" + "a".repeat(64) + ".example";
    assertDoesNotThrow(() -> WebhookDestinationPolicy.parse("test.origins", longest));
    assertThrows(
        IllegalStateException.class, () -> WebhookDestinationPolicy.parse("test.origins", tooLong));
  }
}
