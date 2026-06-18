package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SystemPropertyNameTest {

  @ParameterizedTest
  @CsvSource({
    "pidinst.datacite.enabled, PIDINST_DATACITE_ENABLED",
    "pidinst.datacite.server.url, PIDINST_DATACITE_SERVER_URL",
    "pidinst.datacite.username, PIDINST_DATACITE_USERNAME",
    "pidinst.datacite.password, PIDINST_DATACITE_PASSWORD",
    "pidinst.datacite.repositoryPrefix, PIDINST_DATACITE_REPOSITORY_PREFIX"
  })
  void valueOfPropertyNameResolvesPidinstProperties(String propertyName, String expectedEnumName) {
    SystemPropertyName resolved = SystemPropertyName.valueOfPropertyName(propertyName);
    assertNotNull(resolved, "no SystemPropertyName for " + propertyName);
    assertEquals(expectedEnumName, resolved.name());
    assertEquals(propertyName, resolved.getPropertyName());
  }
}
