package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.DeletePolicy;
import com.researchspace.api.v2.resource.IdempotencyPolicy;
import com.researchspace.api.v2.resource.MutationPolicy;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.api.v2.resource.VersionPolicy;
import com.researchspace.api.v2.user.UserResourceOperations;
import com.researchspace.inventory.model.ApiV2ContainerResource;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionFieldTypes.Constraints;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResourceMember;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.collection.SplitReferenceBinding;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.UserManager;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.PathItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ReadOnlyCollectionOpenApiTest {

  @Test
  void publishesOnlyDeclaredReadMethodsForReadOnlyCollections() {
    ApiV2ResourceSpec<User, Long> spec =
        ApiV2ResourceSpec.builder(
                ApiV2UserResource.DESCRIPTION,
                new UserResourceOperations(mock(UserManager.class)),
                Long::valueOf)
            .mutationErrors("errors.api.v2.invalidRequest", "errors.api.v2.invalidRequest")
            .expose(Set.of(ResourceOperation.LIST, ResourceOperation.COUNT, ResourceOperation.READ))
            .build();
    var document =
        new ApiV2OpenApiGenerator(
                new ApiV2ResourceCatalog(List.of(spec)),
                ApiV2OpenApiTestText.english(),
                "Read API",
                "2.0.0")
            .generate();

    for (String path : List.of("/api/v2/users", "/api/v2/users/count", "/api/v2/users/{id}")) {
      assertEquals(
          Set.of(PathItem.HttpMethod.GET),
          document.getPaths().get(path).readOperationsMap().keySet());
    }
    assertNull(document.getPaths().get("/api/v2/users/bulk"));
  }

  @Test
  void publishesOptionalAndCappedTotalsForEveryCollection() {
    JsonNode document =
        document(
            resourceSpec(ApiV2ContainerResource.DESCRIPTION, List.of()),
            resourceSpec(ApiV2InstrumentResource.DESCRIPTION, List.of()));
    for (String resource : List.of("containers", "instruments")) {
      JsonNode list = document.at("/paths/~1api~1v2~1" + resource + "/get");
      JsonNode totals = parameter(list.path("parameters"), "totals");
      assertEquals("boolean", totals.at("/schema/type").asText());
      assertFalse(totals.at("/schema/default").asBoolean(true));
      assertFalse(totals.path("required").asBoolean());
      for (String suffix : List.of("", "~1count")) {
        JsonNode parameters =
            document.at("/paths/~1api~1v2~1" + resource + suffix + "/get/parameters");
        JsonNode max = parameter(parameters, "max");
        assertEquals("integer", max.at("/schema/type").asText());
        assertEquals(1, max.at("/schema/minimum").asInt());
        assertEquals(10000, max.at("/schema/maximum").asInt());
        assertFalse(max.path("required").asBoolean());
      }
      JsonNode schema = list.at("/responses/200/content/application~1json/schema");
      assertFalse(schema.path("required").toString().contains("totalDocs"));
      assertFalse(schema.path("required").toString().contains("totalPages"));
      assertEquals("boolean", schema.at("/properties/totalIsLowerBound/type").asText());
      assertTrue(schema.at("/properties/totalDocs").isObject());
      assertTrue(schema.at("/properties/totalPages").isMissingNode());
    }
    assertEquals(
        "boolean",
        document
            .at("/components/schemas/ApiV2CountResult/properties/totalIsLowerBound/type")
            .asText());
  }

  @Test
  void collectionOperationsShareParameterAndResponseComponents() {
    JsonNode document =
        referencingDocument(
            resourceSpec(ApiV2ContainerResource.DESCRIPTION, List.of()),
            resourceSpec(ApiV2InstrumentResource.DESCRIPTION, List.of()));
    for (String resource : List.of("containers", "instruments")) {
      String component = Character.toUpperCase(resource.charAt(0)) + resource.substring(1);
      String where = "#/components/parameters/" + component + "Where";
      JsonNode list = document.at("/paths/~1api~1v2~1" + resource + "/get");
      JsonNode count = document.at("/paths/~1api~1v2~1" + resource + "~1count/get");
      assertEquals(where, list.at("/parameters/0/$ref").asText());
      assertTrue(count.path("parameters").toString().contains(where));
      assertTrue(list.path("parameters").toString().contains("#/components/parameters/Page"));
      assertEquals("#/components/responses/CountResult", count.at("/responses/200/$ref").asText());
    }
    assertEquals(
        "ApiV2CountResult",
        document
            .at("/components/responses/CountResult/content/application~1json/schema/$ref")
            .asText()
            .replace("#/components/schemas/", ""));
    assertTrue(document.at("/components/parameters/ContainersId").isMissingNode());
    assertEquals(
        "#/components/x-rspace-filters/Containers",
        document.at("/components/parameters/ContainersWhere/x-rspace-filter/$ref").asText());
    assertTrue(document.at("/components/x-rspace-filters/Containers/selectors").isObject());
    String policy = document.at("/components/schemas/ContainersRead/x-rspace-access/$ref").asText();
    assertEquals("#/components/x-rspace-access-policies/Never", policy);
    assertEquals(
        policy, document.at("/paths/~1api~1v2~1containers/get/x-rspace-access/$ref").asText());
    assertTrue(document.at(policy.substring(1) + "/description").isTextual());
  }

  @Test
  void documentsPerIntervalRateLimitHeadersOnce() {
    JsonNode document =
        referencingDocument(resourceSpec(ApiV2ContainerResource.DESCRIPTION, List.of()));
    List<String> base =
        List.of("X-Rate-Limit-WaitTimeMillis", "X-Rate-Limit-MinWaitIntervalMillis");
    List<String> listed = new ArrayList<>();
    document
        .at("/paths/~1api~1v2~1containers/get/responses/200/headers")
        .fieldNames()
        .forEachRemaining(listed::add);
    assertEquals(base, listed);
    JsonNode limits = document.path("x-rspace-rate-limits");
    assertEquals("[\"quarter_min\",\"hour\",\"day\"]", limits.path("intervals").toString());
    assertEquals(
        "#/components/headers/RateLimitWait",
        limits
            .at("/perIntervalHeaders/X-Rate-Limit-WaitTimeTillNextRequestMillis-{interval}/$ref")
            .asText());
    assertEquals(
        base.size() + 9, document.at("/components/responses/TooManyRequests/headers").size());
  }

  @Test
  void publishesMissingIfMatchResponseOnlyForVersionedWrites() {
    String requiredCode = "errors.api.v2.resourceAccess.ifMatchRequired";
    for (boolean versioned : List.of(false, true)) {
      @SuppressWarnings("unchecked")
      ResourceOperations<User, Long> operations = mock(ResourceOperations.class);
      var spec =
          ApiV2ResourceSpec.builder(ApiV2UserResource.DESCRIPTION, operations, Long::valueOf)
              .mutationPolicy(
                  new MutationPolicy(
                      versioned
                          ? VersionPolicy.required(
                              "version",
                              requiredCode,
                              ResourceOperation.UPDATE,
                              ResourceOperation.DELETE)
                          : VersionPolicy.NONE,
                      DeletePolicy.HARD,
                      IdempotencyPolicy.NONE))
              .mutationErrors("errors.api.v2.invalidRequest", "errors.api.v2.invalidRequest")
              .expose(
                  Set.of(
                      ResourceOperation.READ, ResourceOperation.UPDATE, ResourceOperation.DELETE))
              .build();
      JsonNode document = document(spec);
      JsonNode path = document.at("/paths/~1api~1v2~1users~1{id}");
      assertFalse(path.at("/get/responses").has("428"));
      for (String method : List.of("patch", "delete")) {
        JsonNode operation = path.path(method);
        assertEquals(versioned, operation.path("responses").has("428"));
        if (versioned) {
          assertTrue(
              parameter(operation.path("parameters"), "If-Match").path("required").asBoolean());
          JsonNode example =
              operation.at("/responses/428/content/application~1problem+json/example");
          assertEquals(428, example.path("status").asInt());
          assertEquals(requiredCode, example.path("code").asText());
        }
      }
    }
  }

  @Test
  void publishesContainersAndInstrumentParentRelationship() {
    JsonNode document =
        document(
            resourceSpec(ApiV2ContainerResource.DESCRIPTION, List.of()),
            resourceSpec(ApiV2InstrumentResource.DESCRIPTION, List.of()));
    assertTrue(document.at("/paths/~1api~1v2~1containers/get").isObject());
    JsonNode properties = document.at("/components/schemas/InstrumentsRead/properties");
    String relationship = properties.path("parentContainer").toString();
    assertTrue(relationship.contains("\"const\":\"containers\""), relationship);
    assertTrue(relationship.contains("#/components/schemas/ContainersRead"), relationship);
    assertEquals(
        "#/components/schemas/ContainersRead",
        properties.at("/parentContainer/x-rspace-relationship/targets/containers/$ref").asText());
  }

  @Test
  void publishesRecursiveAndPolymorphicDisplayEdgesWithoutAddingNestedFilters() {
    var nodes = graphDescription("nodes", List.of("nodes", "branches"));
    var branches = graphDescription("branches", List.of("nodes"));
    JsonNode document = document(resourceSpec(nodes, List.of()), resourceSpec(branches, List.of()));
    JsonNode schemas = document.at("/components/schemas");
    JsonNode nodeSchema = schemas.path("NodesRead");
    JsonNode targets = nodeSchema.at("/properties/related/x-rspace-relationship/targets");
    assertEquals(2, targets.size());
    String self = targets.at("/nodes/$ref").asText();
    assertEquals("#/components/schemas/NodesRead", self);
    assertEquals(nodeSchema, document.at(self.substring(1)));
    String branch = targets.at("/branches/$ref").asText();
    assertEquals("#/components/schemas/BranchesRead", branch);
    assertEquals(
        self,
        document
            .at(branch.substring(1))
            .at("/properties/related/x-rspace-relationship/targets/nodes/$ref")
            .asText());
    assertEquals(
        1, targets.path("nodes").size(), "An edge contains a reference, not an inline graph");
    assertEquals("null", nodeSchema.at("/properties/related/anyOf/1/type").asText());
    assertFalse(nodeSchema.at("/properties/id").has("x-rspace-relationship"));
    for (String variant : List.of("Create", "Update")) {
      assertFalse(
          schemas.path("Nodes" + variant).at("/properties/related").has("x-rspace-relationship"));
    }
    JsonNode selectors = whereSelectors(document.at("/paths/~1api~1v2~1nodes/get/parameters"));
    assertTrue(selectors.has("related.id"));
    assertFalse(selectors.has("related.related.id"));
  }

  private static CollectionDescription<GraphNode> graphDescription(
      String name, List<String> targets) {
    return new CollectionDescription<>(
        name,
        GraphNode.class,
        List.of(
            Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), GraphNode::id),
            Relationship.polymorphicToOne(
                    "related",
                    CollectionFieldTypes.longNumber(),
                    targets.stream()
                        .map(target -> new RelationshipTarget<>(target, target, GraphNode.class))
                        .toList(),
                    new SplitReferenceBinding<>(GraphNode::related, "relatedKind", "relatedId"))
                .allowNull()
                .allowSelfReference()),
        "id",
        List.of(new Sort("id", true)));
  }

  @Test
  void publishesFieldsetsForNestedRelationshipTargets() {
    CollectionDescription<InstrumentSource> description =
        new CollectionDescription<>(
            "sources",
            InstrumentSource.class,
            List.<ResourceMember<InstrumentSource>>of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), InstrumentSource::id),
                Relationship.referenceToOne(
                    "instrument",
                    "instruments",
                    CollectionFieldTypes.longNumber(),
                    Instrument.class,
                    InstrumentSource::instrument,
                    Instrument::getId,
                    "id")),
            "id",
            List.of(new Sort("id", true)));
    JsonNode document =
        document(
            resourceSpec(description, List.of()),
            resourceSpec(ApiV2InstrumentResource.DESCRIPTION, List.of()),
            resourceSpec(ApiV2ContainerResource.DESCRIPTION, List.of()));
    for (String name : List.of("fields", "exclude")) {
      JsonNode fieldset = parameter(document.at("/paths/~1api~1v2~1sources/get/parameters"), name);
      assertTrue(fieldset.at("/schema/properties/containers").isObject());
      assertTrue(fieldset.path("x-rspace-allowed-fields").has("containers"));
    }
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
        ApiV2ResourceSpec.builder(description, operations, Long::valueOf)
            .mutationErrors("errors.api.v2.invalidRequest", "errors.api.v2.invalidRequest")
            .expose(Set.of(ResourceOperation.LIST, ResourceOperation.READ))
            .build();
    JsonNode json = document(spec);
    JsonNode filters = whereSelectors(json.at("/paths/~1api~1v2~1gadgets/get/parameters"));

    assertEquals("[\"OPEN\",\"CLOSED\"]", filters.at("/phase/schema/enum").toString());
    assertEquals("[\"OPEN\",\"CLOSED\",null]", filters.at("/previous/schema/enum").toString());
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

    JsonNode document = document(sourceSpec, targetSpec);
    JsonNode sourceWhere =
        parameter(document.at("/paths/~1api~1v2~1sources/get/parameters"), "where");
    JsonNode targetWhere =
        parameter(document.at("/paths/~1api~1v2~1targets/get/parameters"), "where");

    JsonNode relationshipNamespace = sourceWhere.at("/x-rspace-runtime-fields/0");
    assertEquals("target.customFields", relationshipNamespace.path("namespace").asText());
    assertEquals(
        "/api/v2/targets/fields/customFields", relationshipNamespace.path("catalog").asText());
    assertEquals("q", relationshipNamespace.path("catalogSearchParameter").asText());
    JsonNode ownNamespace = targetWhere.at("/x-rspace-runtime-fields/0");
    assertEquals("q", ownNamespace.path("catalogSearchParameter").asText());
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

  private static JsonNode document(ApiV2ResourceSpec<?, ?>... specs) {
    return ApiV2OpenApiTestRefs.dereferenced(referencingDocument(specs));
  }

  private static JsonNode referencingDocument(ApiV2ResourceSpec<?, ?>... specs) {
    return Json31.mapper()
        .valueToTree(
            new ApiV2OpenApiGenerator(
                    new ApiV2ResourceCatalog(List.of(specs)),
                    ApiV2OpenApiTestText.english(),
                    "API",
                    "2.0.0")
                .generate());
  }

  private static <T> ApiV2ResourceSpec<T, Long> resourceSpec(
      CollectionDescription<T> description, List<RuntimeCollectionFields<T>> runtimeFields) {
    @SuppressWarnings("unchecked")
    ResourceOperations<T, Long> operations = mock(ResourceOperations.class);
    return ApiV2ResourceSpec.builder(description, operations, Long::valueOf)
        .mutationErrors("errors.api.v2.invalidRequest", "errors.api.v2.invalidRequest")
        .expose(Set.of(ResourceOperation.LIST, ResourceOperation.COUNT))
        .runtimeFields(runtimeFields)
        .build();
  }

  private enum Phase {
    OPEN,
    CLOSED
  }

  private record Gadget(Long id, Phase phase, Phase previous, String code) {}

  private record InstrumentSource(Long id, Instrument instrument) {}

  private record GraphNode(Long id, ResourceReference<String, Long> related) {}

  private record NamespaceTarget(Long id) {}

  private record NamespaceSource(Long id, NamespaceTarget target) {}
}
