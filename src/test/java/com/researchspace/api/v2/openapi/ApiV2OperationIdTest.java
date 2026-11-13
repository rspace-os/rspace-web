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
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.Sort;
import java.util.List;
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
        ApiV2ResourceSpec.builder(description, operations, Long::valueOf)
            .mutationErrors("create-error", "update-error")
            .expose(Set.of(ResourceOperation.LIST, ResourceOperation.READ))
            .build();
    var document =
        new ApiV2OpenApiGenerator(
                new ApiV2ResourceCatalog(List.of(spec)),
                ApiV2OpenApiTestText.english(),
                "API",
                "2.0.0")
            .generate();
    var withoutSingular =
        new CollectionDescription<>(
            "people",
            User.class,
            List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), User::getId)),
            "id",
            List.of(new Sort("id", true)));
    var generator =
        new ApiV2OpenApiGenerator(
            new ApiV2ResourceCatalog(
                List.of(
                    ApiV2ResourceSpec.readOnly(withoutSingular, operations, Long::valueOf)
                        .build())),
            ApiV2OpenApiTestText.english(),
            "API",
            "2.0.0");
    assertThrows(IllegalArgumentException.class, generator::generate);
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
