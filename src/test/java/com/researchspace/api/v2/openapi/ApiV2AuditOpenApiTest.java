package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.user.UserResourceOperations;
import com.researchspace.model.User;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.service.UserManager;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
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
      Parameter search =
          document.getPaths().get(path).getGet().getParameters().stream()
              .filter(parameter -> "search".equals(parameter.getName()))
              .findFirst()
              .orElse(null);

      assertNotNull(search);
      assertEquals("query", search.getIn());
      assertEquals(Set.of("string"), search.getSchema().getTypes());
      assertEquals(Integer.valueOf(255), search.getSchema().getMaxLength());
    }
  }
}
