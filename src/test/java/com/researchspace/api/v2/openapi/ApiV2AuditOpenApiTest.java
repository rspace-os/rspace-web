package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.user.UserResourceOperations;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.service.UserManager;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ApiV2AuditOpenApiTest {

  @Test
  void documentsSearchOnPagedAndCountAuditRoutes() {
    ApiV2ResourceSpec<User, Long> spec =
        new ApiV2ResourceSpec<>(
            ApiV2UserResource.DESCRIPTION,
            new UserResourceOperations(Mockito.mock(UserManager.class)),
            Long::valueOf,
            "errors.api.v2.invalidRequest",
            "errors.api.v2.invalidRequest");
    var document =
        new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of(spec)), "Test API", "2.0.0")
            .generate();

    for (String path : List.of("/api/v2/users/{id}/audit", "/api/v2/users/{id}/audit/count")) {
      Operation operation = document.getPaths().get(path).getGet();
      Parameter search =
          operation.getParameters().stream()
              .filter(parameter -> "search".equals(parameter.getName()))
              .findFirst()
              .orElse(null);

      assertNotNull(search);
      assertEquals("query", search.getIn());
      assertEquals(Set.of("string"), search.getSchema().getTypes());
      assertEquals(Integer.valueOf(255), search.getSchema().getMaxLength());
      assertEquals(
          List.of(Set.of("apiKey"), Set.of("bearerAuth")),
          operation.getSecurity().stream().map(Map::keySet).toList());
      assertEquals(
          Map.of(
              "description",
              "Authenticated callers who can read the resource.",
              "denialReasonCodes",
              List.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN)),
          operation.getExtensions().get("x-rspace-access"));
      assertEquals(
          path.endsWith("/count")
              ? List.of("id", "dateFrom", "dateTo", "search", "actions")
              : List.of(
                  "id",
                  "dateFrom",
                  "dateTo",
                  "search",
                  "actions",
                  "snapshotDate",
                  "snapshotFingerprint",
                  "page",
                  "limit"),
          operation.getParameters().stream().map(Parameter::getName).toList());
      if (!path.endsWith("/count")) {
        Parameter limit =
            operation.getParameters().stream()
                .filter(parameter -> "limit".equals(parameter.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals("Maximum events per page.", limit.getDescription());
        assertEquals(1, ((Number) limit.getSchema().getMinimum()).intValue());
        assertEquals(
            CollectionQueryLimits.MAX_PAGE_SIZE,
            ((Number) limit.getSchema().getMaximum()).intValue());
        assertEquals(20, ((Number) limit.getSchema().getDefault()).intValue());
      }
    }

    Parameter documentLimit =
        document.getPaths().get("/api/v2/users").getGet().getParameters().stream()
            .filter(parameter -> "limit".equals(parameter.getName()))
            .findFirst()
            .orElseThrow();
    assertEquals("Maximum documents per page.", documentLimit.getDescription());
  }
}
