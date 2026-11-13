package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ApiV2OpenApiMapsTest {

  @ParameterizedTest
  @CsvSource({
    "headers, ETag, #/components/headers/ETag",
    "headers, #/components/headers/ETag, #/components/headers/ETag",
    "responses, #/components/responses/NotFound, #/components/responses/NotFound",
    "requestBodies, shared.yaml#/Body, shared.yaml#/Body"
  })
  void annotationRefsPlaceBareNamesAndKeepFullReferences(
      String section, String value, String expected) {
    assertEquals(Map.of("$ref", expected), ApiV2OpenApiMaps.annotationRef(section, value));
  }
}
