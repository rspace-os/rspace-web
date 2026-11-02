package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.api.v2.controller.ApiV2ControllerAdvice;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.service.CollectionMutationException;
import com.researchspace.service.MessageSourceUtils;
import io.swagger.v3.core.util.Json31;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class ApiV2OpenApiErrorExamplesTest {

  @ParameterizedTest
  @CsvSource({
    "400, BadRequest, errors.api.v2.invalidRequest",
    "401, Unauthenticated, errors.api.v2.authenticationRequired",
    "403, Forbidden, errors.api.v2.forbidden",
    "404, NotFound, errors.api.v2.notFound",
    "406, NotAcceptable, errors.api.v2.notAcceptable",
    "415, UnsupportedMediaType, errors.api.v2.unsupportedMediaType",
    "422, BulkLimit, errors.api.v2.bulk.limit",
    "429, TooManyRequests, errors.api.v2.tooManyRequests",
    "500, UnexpectedError, errors.api.v2.unexpected"
  })
  void sharedExampleCodesMatchTheErrorsSentByTheAdvice(
      int status, String responseName, String expectedCode) {
    JsonNode document =
        Json31.mapper()
            .valueToTree(
                new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of()), "Test API", "2.0.0")
                    .generate());
    JsonNode example =
        document
            .path("components")
            .path("responses")
            .path(responseName)
            .path("content")
            .path("application/problem+json")
            .path("example");
    StaticMessageSource source = new StaticMessageSource();
    source.setUseCodeAsDefaultMessage(true);
    ApiV2ControllerAdvice advice = new ApiV2ControllerAdvice(new MessageSourceUtils(source));
    ResponseEntity<ApiV2Problem> response =
        status == 422
            ? advice.handleCollectionMutation(
                new CollectionMutationException(CollectionMutationException.Reason.BULK_LIMIT))
            : advice.handleResponseStatus(new ResponseStatusException(HttpStatus.valueOf(status)));

    assertNotNull(response.getBody());
    assertEquals(
        expectedCode, Json31.mapper().valueToTree(response.getBody()).path("code").asText());
    assertEquals(status, example.path("status").asInt());
    assertEquals(expectedCode, example.path("code").asText());
  }
}
