package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The message codes the external metadata update asks for must exist in the catalogue.
 *
 * <p>Worth its own test because a missing key does not fail loudly: {@code
 * applicationContext-resources.xml} sets {@code useCodeAsDefaultMessage=true}, so a typo returns
 * the bare code, and the user's reason - and the audit entry - would read
 * "errors.inventory.identifier.externalUpdateFailed". The service's own unit tests mock the message
 * source and assert on the code that was requested, which pins the call site but cannot see whether
 * the catalogue answers it.
 *
 * <p>Reads the catalogue file directly rather than through Spring, so it stays a fast unit test.
 * {@code JsonMessageSource} flattens these files with dots and gives {@code server.*} an empty
 * prefix, so a code is the flattened JSON path exactly as asserted here.
 */
class ExternalUpdateMessageKeysTest {

  private static final File CATALOGUE =
      new File("src/main/webapp/ui/src/modules/common/i18n/locales/en-US/server.inventory.json");

  @ParameterizedTest
  @ValueSource(
      strings = {
        "inventory.identifier.externalUpdated",
        "errors.inventory.identifier.externalUpdateFailed",
        "errors.inventory.identifier.externalUpdateNotPossibleB2inst",
        "errors.inventory.identifier.externalUpdateNotPossibleDataCite"
      })
  void everyCodeTheServiceAsksForResolves(String code) throws IOException {
    JsonNode node = new ObjectMapper().readTree(CATALOGUE);
    for (String segment : code.split("\\.")) {
      node = node.path(segment);
    }
    assertTrue(
        node.isTextual() && !node.asText().isBlank(), code + " is missing from " + CATALOGUE);
  }

  /** The provider name is passed as {0}, so every one of these must have somewhere to put it. */
  @Test
  void everyProviderMessageInterpolatesTheProviderName() throws IOException {
    JsonNode errors =
        new ObjectMapper().readTree(CATALOGUE).path("errors").path("inventory").path("identifier");
    for (String key :
        new String[] {
          "externalUpdateFailed",
          "externalUpdateNotPossibleB2inst",
          "externalUpdateNotPossibleDataCite"
        }) {
      assertTrue(errors.path(key).asText().contains("{0}"), key + " must interpolate {0}");
    }
  }
}
