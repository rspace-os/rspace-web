package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourceMember;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldBinding;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.collection.Sort;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiV2RuntimeProjectionOpenApiTest {

  private static final String OWN_SELECTOR = "customFields.SF1";
  private static final String HOPPED_SELECTOR = "target.customFields.TF1";

  @Test
  void generatedReadSchemaStructurallyCoversActualRuntimeProjectionDocuments() {
    ProjectionFixture fixture = fixture();
    JsonNode openApi = Json31.mapper().valueToTree(fixture.document());
    JsonNode readSchema = openApi.at("/components/schemas/SourcesRead");
    JsonNode properties = readSchema.path("properties");

    for (Map<String, Object> document : fixture.documents()) {
      for (String key : document.keySet()) {
        assertTrue(properties.has(key), "Read schema does not declare returned key " + key);
      }
    }
    assertEquals(
        Map.of("SF1", Map.of("nested", List.of("source"))),
        fixture.documents().get(0).get("customFields"));
    assertEquals(Map.of(), fixture.documents().get(1).get("customFields"));
    assertEquals(
        Map.of("TF1", List.of(Map.of("nested", true))),
        fixture.documents().get(0).get("target.customFields"));
    assertEquals(Map.of(), fixture.documents().get(1).get("target.customFields"));

    for (String namespace : List.of("customFields", "target.customFields")) {
      JsonNode namespaceSchema = properties.path(namespace);
      assertEquals("object", namespaceSchema.path("type").asText());
      assertTrue(namespaceSchema.path("additionalProperties").asBoolean());
      assertFalse(isRequired(readSchema, namespace));
    }
    assertFalse(properties.has("target.privateFields"));
    assertFalse(openApi.at("/components/schemas/SourcesCreate/properties").has("customFields"));
    assertFalse(
        openApi.at("/components/schemas/SourcesCreate/properties").has("target.customFields"));
    assertFalse(openApi.at("/components/schemas/SourcesUpdate/properties").has("customFields"));
    assertFalse(
        openApi.at("/components/schemas/SourcesUpdate/properties").has("target.customFields"));
    assertFalse(readSchema.path("additionalProperties").asBoolean());
    assertFalse(properties.has("unexpectedRootField"));
    assertFalse(readSchema.toString().contains("SF1"));
    assertFalse(readSchema.toString().contains("TF1"));
  }

  private static ProjectionFixture fixture() {
    User actor = new User("reader");
    NamespaceTarget target = new NamespaceTarget(10L);
    List<NamespaceSource> sources =
        List.of(new NamespaceSource(1L, target), new NamespaceSource(2L, null));

    @SuppressWarnings("unchecked")
    ResourceOperations<NamespaceSource, Long> sourceOperations = mock(ResourceOperations.class);
    when(sourceOperations.find(any(), eq(actor)))
        .thenReturn(new ResourcePage<>(sources, sources.size()));
    RuntimeCollectionFields<NamespaceSource> sourceFields =
        mock(RuntimeCollectionFields.class, CALLS_REAL_METHODS);
    when(sourceFields.namespace()).thenReturn("customFields");
    when(sourceFields.values(sources, Set.of("SF1"), actor))
        .thenReturn(Map.of(1L, Map.of("SF1", Map.of("nested", List.of("source")))));

    @SuppressWarnings("unchecked")
    ResourceOperations<NamespaceTarget, Long> targetOperations = mock(ResourceOperations.class);
    when(targetOperations.find(any(), eq(actor)))
        .thenReturn(new ResourcePage<>(List.of(target), 1));
    RuntimeCollectionFields<NamespaceTarget> targetFields =
        mock(RuntimeCollectionFields.class, CALLS_REAL_METHODS);
    when(targetFields.namespace()).thenReturn("customFields");
    when(targetFields.projectsThroughRelationship()).thenReturn(true);
    when(targetFields.valuesForIds(Set.of(10L), Set.of("TF1"), actor))
        .thenReturn(Map.of(10L, Map.of("TF1", List.of(Map.of("nested", true)))));
    RuntimeCollectionFields<NamespaceTarget> privateFields =
        mock(RuntimeCollectionFields.class, CALLS_REAL_METHODS);
    when(privateFields.namespace()).thenReturn("privateFields");

    CollectionDescription<NamespaceTarget> targetDescription =
        new CollectionDescription<>(
            "targets",
            NamespaceTarget.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), NamespaceTarget::id)),
            "id",
            List.of(new Sort("id", true)));
    CollectionDescription<NamespaceSource> sourceDescription =
        new CollectionDescription<>(
            "sources",
            NamespaceSource.class,
            List.<ResourceMember<NamespaceSource>>of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), NamespaceSource::id),
                Relationship.referenceToOne(
                    "target",
                    "targets",
                    CollectionFieldTypes.longNumber(),
                    NamespaceTarget.class,
                    NamespaceSource::target,
                    NamespaceTarget::id,
                    "id")),
            "id",
            List.of(new Sort("id", true)));
    ApiV2ResourceSpec<NamespaceSource, Long> sourceSpec =
        resourceSpec(
            sourceDescription,
            sourceOperations,
            Set.of(
                ResourceOperation.LIST,
                ResourceOperation.READ,
                ResourceOperation.CREATE,
                ResourceOperation.UPDATE),
            List.of(sourceFields));
    ApiV2ResourceSpec<NamespaceTarget, Long> targetSpec =
        resourceSpec(
            targetDescription,
            targetOperations,
            Set.of(ResourceOperation.LIST, ResourceOperation.READ),
            List.of(targetFields, privateFields));
    ApiV2ResourceCatalog catalog = new ApiV2ResourceCatalog(List.of(sourceSpec, targetSpec));

    ResolvedRuntimeField own = resolved(OWN_SELECTOR, "SF1", NamespaceSource.class);
    ResolvedRuntimeField hopped = resolved(HOPPED_SELECTOR, "TF1", NamespaceTarget.class);
    RuntimeFieldSelection selection =
        new RuntimeFieldSelection(
            Map.of(OWN_SELECTOR, own, HOPPED_SELECTOR, hopped),
            Set.of(OWN_SELECTOR, HOPPED_SELECTOR),
            Map.of(HOPPED_SELECTOR, "target"));
    ResourceRequest request =
        new ResourceRequest(
            null,
            List.of(),
            new ResourceRequest.Page(1, 2),
            ResourceFieldSelections.root(FieldSelection.all()),
            IncludeTree.empty(),
            selection);
    ApiV2ResourceRegistration<?, ?> registration = catalog.find("sources").orElseThrow();
    List<Map<String, Object>> documents = registration.list(request, actor).docs();
    OpenAPI document = new ApiV2OpenApiGenerator(catalog, "API", "2.0.0").generate();
    return new ProjectionFixture(document, documents);
  }

  private static ResolvedRuntimeField resolved(String selector, String id, Class<?> resourceType) {
    return new ResolvedRuntimeField(
        new RuntimeFieldDefinition(
            id, selector, "Custom field", RuntimeFieldValueType.TEXT, "", "", List.of()),
        new RuntimeFieldBinding(resourceType, "id", "name", Map.of()));
  }

  private static <T> ApiV2ResourceSpec<T, Long> resourceSpec(
      CollectionDescription<T> description,
      ResourceOperations<T, Long> operations,
      Set<ResourceOperation> exposedOperations,
      List<RuntimeCollectionFields<T>> runtimeFields) {
    return new ApiV2ResourceSpec<>(
        description,
        operations,
        Long::valueOf,
        "errors.api.v2.invalidRequest",
        "errors.api.v2.invalidRequest",
        exposedOperations,
        Map.of(),
        Map.of(),
        CollectionMutationLimits.DEFAULT,
        runtimeFields);
  }

  private static boolean isRequired(JsonNode schema, String property) {
    for (JsonNode required : schema.path("required")) {
      if (property.equals(required.asText())) {
        return true;
      }
    }
    return false;
  }

  private record ProjectionFixture(OpenAPI document, List<Map<String, Object>> documents) {}

  private record NamespaceSource(Long id, NamespaceTarget target) {}

  private record NamespaceTarget(Long id) {}
}
