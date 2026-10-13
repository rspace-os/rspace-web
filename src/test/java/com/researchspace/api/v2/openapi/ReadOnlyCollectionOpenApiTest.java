package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.api.v2.user.UserResourceOperations;
import com.researchspace.model.User;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionFieldTypes.Constraints;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.Sort;
import com.researchspace.service.UserManager;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.PathItem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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

  @Test
  void publishesFilterEnumsAndOmitsWriteConstraints() {
    CollectionDescription<Gadget> description =
        new CollectionDescription<>(
            "gadgets",
            Gadget.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Gadget::id),
                Field.readOnly(
                    "phase", "phase", CollectionFieldTypes.enumeration(Phase.class), Gadget::phase),
                Field.readOnly(
                        "previous",
                        "previous",
                        CollectionFieldTypes.enumeration(Phase.class),
                        Gadget::previous)
                    .allowNull(),
                Field.readOnly(
                    "code",
                    "code",
                    CollectionFieldTypes.constrained(
                        CollectionFieldTypes.text(),
                        new Constraints(null, null, 2, Pattern.compile("^[A-Z]"), null)),
                    Gadget::code)),
            "id",
            List.of(new Sort("id", true)));
    @SuppressWarnings("unchecked")
    ResourceOperations<Gadget, Long> operations = mock(ResourceOperations.class);
    ApiV2ResourceSpec<Gadget, Long> spec =
        new ApiV2ResourceSpec<>(
            description,
            operations,
            Long::valueOf,
            "errors.api.v2.invalidRequest",
            "errors.api.v2.invalidRequest",
            Set.of(ResourceOperation.LIST, ResourceOperation.READ),
            Map.of());
    JsonNode json =
        Json31.mapper()
            .valueToTree(
                new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of(spec)), "API", "2.0.0")
                    .generate());
    JsonNode filters = whereSelectors(json.at("/paths/~1api~1v2~1gadgets/get/parameters"));

    assertEquals("[\"OPEN\",\"CLOSED\"]", filters.at("/phase/schema/enum").toString());
    assertEquals("[\"OPEN\",\"CLOSED\",null]", filters.at("/previous/schema/enum").toString());
    assertTrue(filters.at("/code/schema/minLength").isMissingNode());
    assertTrue(filters.at("/code/schema/pattern").isMissingNode());
  }

  private static JsonNode whereSelectors(JsonNode parameters) {
    for (JsonNode parameter : parameters) {
      if ("where".equals(parameter.path("name").asText())) {
        return parameter.at("/x-rspace-filter/selectors");
      }
    }
    throw new AssertionError("No where parameter");
  }

  private enum Phase {
    OPEN,
    CLOSED
  }

  private record Gadget(Long id, Phase phase, Phase previous, String code) {}
}
