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
  void usesTheDefinitionSingularOrARoundTrippingInflection() {
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
    assertEquals("listPeople", document.getPaths().get("/api/v2/people").getGet().getOperationId());
    assertEquals(
        "getPerson", document.getPaths().get("/api/v2/people/{id}").getGet().getOperationId());
    assertEquals(
        "getPerson",
        generatorWithoutSingular("people")
            .generate()
            .getPaths()
            .get("/api/v2/people/{id}")
            .getGet()
            .getOperationId());
    // pluralize gives singular("mangoes") = "mango" but plural("mango") = "mangos".
    assertThrows(IllegalArgumentException.class, generatorWithoutSingular("mangoes")::generate);
  }

  private ApiV2OpenApiGenerator generatorWithoutSingular(String name) {
    var withoutSingular =
        new CollectionDescription<>(
            name,
            User.class,
            List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), User::getId)),
            "id",
            List.of(new Sort("id", true)));
    return new ApiV2OpenApiGenerator(
        new ApiV2ResourceCatalog(
            List.of(
                ApiV2ResourceSpec.readOnly(withoutSingular, operations, Long::valueOf).build())),
        ApiV2OpenApiTestText.english(),
        "API",
        "2.0.0");
  }

  @ApiV2ResourceDefinition(
      name = "people",
      singular = "person",
      entity = User.class,
      id = "id",
      auditFields = false)
  private record People(@ApiV2ResourceField Long id) {}
}
