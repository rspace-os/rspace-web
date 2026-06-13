package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SystemPropertyNameTest {

  @ParameterizedTest
  @CsvSource({
    "pdinst.provider, PDINST_PROVIDER",
    "pdinst.enabled, PDINST_ENABLED",
    "pdinst.server.url, PDINST_SERVER_URL",
    "pdinst.username, PDINST_USERNAME",
    "pdinst.password, PDINST_PASSWORD",
    "pdinst.repositoryPrefix, PDINST_REPOSITORY_PREFIX"
  })
  void valueOfPropertyNameResolvesPdinstProperties(String propertyName, String expectedEnumName) {
    SystemPropertyName resolved = SystemPropertyName.valueOfPropertyName(propertyName);
    assertNotNull(resolved, "no SystemPropertyName for " + propertyName);
    assertEquals(expectedEnumName, resolved.name());
    assertEquals(propertyName, resolved.getPropertyName());
  }
}
