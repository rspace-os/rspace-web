package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.ResourceMember;
import com.researchspace.model.collection.RuntimeCollectionFields;
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
  void publishesEnumsEverywhereAndWriteConstraintsOnlyOnBodies() {
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
    JsonNode body = json.at("/components/schemas/GadgetsRead/properties");
    JsonNode filters = whereSelectors(json.at("/paths/~1api~1v2~1gadgets/get/parameters"));

    assertEquals("[\"OPEN\",\"CLOSED\"]", body.at("/phase/enum").toString());
    assertEquals("[\"OPEN\",\"CLOSED\"]", filters.at("/phase/schema/enum").toString());
    assertEquals("[\"OPEN\",\"CLOSED\",null]", body.at("/previous/enum").toString());
    assertEquals("[\"OPEN\",\"CLOSED\",null]", filters.at("/previous/schema/enum").toString());
    assertEquals(2, body.at("/code/minLength").asInt());
    assertEquals("^[A-Z]", body.at("/code/pattern").asText());
    assertTrue(filters.at("/code/schema/minLength").isMissingNode());
    assertTrue(filters.at("/code/schema/pattern").isMissingNode());
  }

  @Test
  void relationshipRuntimeNamespaceUsesUnqualifiedTargetCatalogUrl() {
    CollectionDescription<NamespaceTarget> targetDescription =
        new CollectionDescription<>(
            "targets",
            NamespaceTarget.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), NamespaceTarget::id)),
            "id",
            List.of(new Sort("id", true)));
    @SuppressWarnings("unchecked")
    RuntimeCollectionFields<NamespaceTarget> runtimeFields = mock(RuntimeCollectionFields.class);
    when(runtimeFields.namespace()).thenReturn("customFields");
    ApiV2ResourceSpec<NamespaceTarget, Long> targetSpec =
        resourceSpec(targetDescription, List.of(runtimeFields));

    Relationship<NamespaceSource> targetRelationship =
        Relationship.referenceToOne(
            "target",
            "targets",
            CollectionFieldTypes.longNumber(),
            NamespaceTarget.class,
            NamespaceSource::target,
            NamespaceTarget::id,
            "id");
    CollectionDescription<NamespaceSource> sourceDescription =
        new CollectionDescription<>(
            "sources",
            NamespaceSource.class,
            List.<ResourceMember<NamespaceSource>>of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), NamespaceSource::id),
                targetRelationship),
            "id",
            List.of(new Sort("id", true)));
    ApiV2ResourceSpec<NamespaceSource, Long> sourceSpec =
        resourceSpec(sourceDescription, List.of());

    JsonNode document =
        Json31.mapper()
            .valueToTree(
                new ApiV2OpenApiGenerator(
                        new ApiV2ResourceCatalog(List.of(sourceSpec, targetSpec)), "API", "2.0.0")
                    .generate());
    JsonNode sourceWhere =
        parameter(document.at("/paths/~1api~1v2~1sources/get/parameters"), "where");
    JsonNode targetWhere =
        parameter(document.at("/paths/~1api~1v2~1targets/get/parameters"), "where");

    JsonNode relationshipNamespace = sourceWhere.at("/x-rspace-runtime-fields/0");
    assertEquals("target.customFields", relationshipNamespace.path("namespace").asText());
    assertEquals(
        "/api/v2/targets/fields/customFields", relationshipNamespace.path("catalog").asText());
    JsonNode ownNamespace = targetWhere.at("/x-rspace-runtime-fields/0");
    assertEquals("customFields", ownNamespace.path("namespace").asText());
    assertEquals("/api/v2/targets/fields/customFields", ownNamespace.path("catalog").asText());
  }

  private static JsonNode whereSelectors(JsonNode parameters) {
    return parameter(parameters, "where").at("/x-rspace-filter/selectors");
  }

  private static JsonNode parameter(JsonNode parameters, String name) {
    for (JsonNode parameter : parameters) {
      if (name.equals(parameter.path("name").asText())) {
        return parameter;
      }
    }
    throw new AssertionError("No " + name + " parameter");
  }

  private static <T> ApiV2ResourceSpec<T, Long> resourceSpec(
      CollectionDescription<T> description, List<RuntimeCollectionFields<T>> runtimeFields) {
    @SuppressWarnings("unchecked")
    ResourceOperations<T, Long> operations = mock(ResourceOperations.class);
    return new ApiV2ResourceSpec<>(
        description,
        operations,
        Long::valueOf,
        "errors.api.v2.invalidRequest",
        "errors.api.v2.invalidRequest",
        Set.of(ResourceOperation.LIST),
        Map.of(),
        Map.of(),
        CollectionMutationLimits.DEFAULT,
        runtimeFields);
  }

  private enum Phase {
    OPEN,
    CLOSED
  }

  private record Gadget(Long id, Phase phase, Phase previous, String code) {}

  private record NamespaceTarget(Long id) {}

  private record NamespaceSource(Long id, NamespaceTarget target) {}
}
