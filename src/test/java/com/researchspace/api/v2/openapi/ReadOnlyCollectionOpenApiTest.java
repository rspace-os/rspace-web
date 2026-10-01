package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.user.UserResourceOperations;
import com.researchspace.model.User;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.service.UserManager;
import io.swagger.v3.oas.models.PathItem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReadOnlyCollectionOpenApiTest {

  @Test
  void publishesOnlyDeclaredReadMethodsForReadOnlyCollections() {
    ApiV2ResourceSpec<User, Long> spec =
        new ApiV2ResourceSpec<>(
            ApiV2UserResource.DESCRIPTION,
            new UserResourceOperations(mock(UserManager.class)),
            Long::valueOf,
            "errors.api.v2.invalidRequest",
            "errors.api.v2.invalidRequest",
            Set.of(ResourceOperation.LIST, ResourceOperation.COUNT, ResourceOperation.READ),
            Map.of());
    var document =
        new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of(spec)), "Read API", "2.0.0")
            .generate();

    for (String path : List.of("/api/v2/users", "/api/v2/users/count", "/api/v2/users/{id}")) {
      assertEquals(
          Set.of(PathItem.HttpMethod.GET),
          document.getPaths().get(path).readOperationsMap().keySet());
    }
    assertNull(document.getPaths().get("/api/v2/users/bulk"));
  }
}
