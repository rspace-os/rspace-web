package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v2.resource.ApiV2ResourceException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class ApiV2ConditionalRequestTest {

  private static final String REQUIRED = "errors.api.v2.test.ifMatchRequired";

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "\"0\"|0",
        "\"0-gzip\"|0",
        "\"12-br\"|12",
        "\"12-deflate\"|12",
        "W/\"7\"|7",
        "W/\"7-gzip\"|7"
      })
  void versionToleratesProxySuffixAndWeakPrefix(String ifMatch, long expected) {
    assertEquals(expected, ApiV2ConditionalRequest.parseVersion(ifMatch, REQUIRED));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "7",
        "\"\"",
        "\"-gzip\"",
        "W/\"-gzip\"",
        "\"7\"-gzip",
        "\"7-gzipp\"",
        "\"7-gzip-gzip\"",
        "\"-1\"",
        "\"7",
        "w/\"7\"",
        "W/7"
      })
  void versionStillRejectsMalformedTags(String ifMatch) {
    assertThrows(
        ApiV2BadRequestException.class,
        () -> ApiV2ConditionalRequest.parseVersion(ifMatch, REQUIRED));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "\"subscription-1\"|\"subscription-1\"",
        "\"subscription-1-gzip\"|\"subscription-1\"",
        "\"subscription-1-br\"|\"subscription-1\"",
        "W/\"subscription-1\"|\"subscription-1\"",
        "W/\"subscription-1-deflate\"|\"subscription-1\"",
        "\"ab12cd-gzip\"|\"ab12cd\""
      })
  void strongEtagIsReturnedAsIssued(String ifMatch, String expected) {
    assertEquals(expected, ApiV2ConditionalRequest.parseStrongEtag(ifMatch, REQUIRED));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "subscription-1",
        "\"\"",
        "\"-gzip\"",
        "\"subscription-1\"-gzip",
        "\"a\"b\"",
        "\"a b\"",
        "\"a\tb-gzip\"",
        "W/subscription-1"
      })
  void strongEtagStillRejectsMalformedTags(String ifMatch) {
    assertThrows(
        ApiV2BadRequestException.class,
        () -> ApiV2ConditionalRequest.parseStrongEtag(ifMatch, REQUIRED));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void missingIfMatchIsAPreconditionRequired(boolean strong) {
    ApiV2ResourceException failure =
        assertThrows(
            ApiV2ResourceException.class,
            () -> {
              if (strong) {
                ApiV2ConditionalRequest.parseStrongEtag(null, REQUIRED);
              } else {
                ApiV2ConditionalRequest.parseVersion(null, REQUIRED);
              }
            });
    assertEquals(HttpStatus.PRECONDITION_REQUIRED, failure.status());
    assertEquals(REQUIRED, failure.errorCode());
  }
}
