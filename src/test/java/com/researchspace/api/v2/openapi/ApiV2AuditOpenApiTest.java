package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceAuditPolicy;
import com.researchspace.api.v2.user.UserResourceOperations;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.service.UserManager;
import io.swagger.v3.core.util.Json31;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ApiV2AuditOpenApiTest {

  @Test
  void documentsAuditListWithSearchAndSnapshot() {
    ApiV2ResourceSpec<User, Long> spec =
        ApiV2ResourceSpec.builder(
                ApiV2UserResource.DESCRIPTION,
                new UserResourceOperations(Mockito.mock(UserManager.class)),
                Long::valueOf)
            .mutationErrors("errors.api.v2.invalidRequest", "errors.api.v2.invalidRequest")
            .audit(
                ResourceAuditPolicy.standard(
                    new UserResourceOperations(Mockito.mock(UserManager.class))))
            .build();
    JsonNode referencing =
        Json31.mapper()
            .valueToTree(
                new ApiV2OpenApiGenerator(
                        new ApiV2ResourceCatalog(List.of(spec)),
                        ApiV2OpenApiTestText.english(),
                        "Test API",
                        "2.0.0")
                    .generate());
    JsonNode document = ApiV2OpenApiTestRefs.dereferenced(referencing);

    JsonNode operation = document.at("/paths/~1api~1v2~1users~1{id}~1audit/get");
    JsonNode search = parameter(operation, "q");
    assertEquals("query", search.path("in").asText());
    assertEquals("string", search.at("/schema/type").asText());
    assertEquals(255, search.at("/schema/maxLength").asInt());
    assertEquals("[{\"apiKey\":[]},{\"bearerAuth\":[]}]", operation.path("security").toString());
    assertEquals(
        "Authenticated callers who can read the resource.",
        operation.at("/x-rspace-access/description").asText());
    List<String> denials = new ArrayList<>();
    operation.at("/x-rspace-access/denialReasonCodes").forEach(code -> denials.add(code.asText()));
    assertEquals(List.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN), denials);
    List<String> names = new ArrayList<>();
    operation.path("parameters").forEach(parameter -> names.add(parameter.path("name").asText()));
    assertEquals(List.of("id", "start", "end", "q", "actions"), names);
    assertTrue(document.at("/paths/~1api~1v2~1users~1{id}~1audit~1count").isMissingNode());

    JsonNode referencingOperation = referencing.at("/paths/~1api~1v2~1users~1{id}~1audit/get");
    assertEquals(
        "#/components/parameters/AuditSearch",
        referencingOperation.at("/parameters/3/$ref").asText());
    assertEquals(
        "#/components/responses/AuditEvents",
        referencingOperation.at("/responses/200/$ref").asText());

    JsonNode documentLimit = parameter(document.at("/paths/~1api~1v2~1users/get"), "limit");
    assertEquals("Maximum documents per page.", documentLimit.path("description").asText());
  }

  private static JsonNode parameter(JsonNode operation, String name) {
    for (JsonNode parameter : operation.path("parameters")) {
      if (name.equals(parameter.path("name").asText())) {
        return parameter;
      }
    }
    throw new AssertionError("Missing parameter " + name);
  }
}
