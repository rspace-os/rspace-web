package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.model.User;
import com.researchspace.model.collection.ApiV2ResourceDefinition;
import com.researchspace.model.collection.ApiV2ResourceField;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.Sort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiV2OperationIdTest {
  @Mock private ResourceOperations<User, Long> operations;

  @Test
  void requiresAndUsesTheDefinitionSingularForAnIrregularResource() {
    var description =
        CollectionDescription.fromApiV2Resource(
            People.class, User.class, List.of(new Sort("id", true)));
    var spec =
        new ApiV2ResourceSpec<>(
            description,
            operations,
            Long::valueOf,
            "create-error",
            "update-error",
            Set.of(ResourceOperation.LIST, ResourceOperation.READ),
            Map.of());
    var withoutSingular =
        new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of(spec)), "API", "2.0.0");
    assertThrows(IllegalArgumentException.class, withoutSingular::generate);
    var document =
        new ApiV2OpenApiGenerator(
                new ApiV2ResourceCatalog(List.of(spec.withDefinition(People.class))),
                "API",
                "2.0.0")
            .generate();
    assertEquals("listPeople", document.getPaths().get("/api/v2/people").getGet().getOperationId());
    assertEquals(
        "getPerson", document.getPaths().get("/api/v2/people/{id}").getGet().getOperationId());
  }

  @ApiV2ResourceDefinition(
      name = "people",
      singular = "person",
      entity = User.class,
      id = "id",
      auditFields = false)
  private record People(@ApiV2ResourceField Long id) {}
}
