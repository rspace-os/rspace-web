package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiGenerator.AUTHENTICATED_SECURITY;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.content;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.parameter;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.putStandardResponses;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.collection.CollectionQueryLimits;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Documents runtime fields: the caller-specific catalog route, the {@code x-rspace-runtime-fields}
 * extension and the projected read properties.
 */
final class ApiV2OpenApiRuntimeFieldDocs {

  private final ApiV2ResourceCatalog catalog;

  ApiV2OpenApiRuntimeFieldDocs(ApiV2ResourceCatalog catalog) {
    this.catalog = catalog;
  }

  void addRuntimeFieldsPath(Map<String, Object> paths, ApiV2ResourceRegistration<?, ?> resource) {
    List<RuntimeCollectionFields<?>> namespaces = catalog.runtimeFieldsOf(resource.resourceName());
    if (namespaces.isEmpty()) {
      return;
    }
    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put(
        "200",
        ordered(
            "description",
            "Caller-specific runtime field catalog.",
            "headers",
            Map.of("Cache-Control", Map.of("schema", Map.of("type", "string"))),
            "content",
            content(APPLICATION_JSON_VALUE, ref("ApiV2RuntimeFieldCatalog"))));
    putStandardResponses(responses, "400", "401", "403", "404", "406", "429", "500");
    paths.put(
        "/api/v2/" + resource.resourceName() + "/fields/{namespace}",
        Map.of(
            "get",
            ordered(
                "operationId",
                "get" + ApiV2OpenApiNames.singularName(resource) + "RuntimeFields",
                "x-rspace-operation",
                ResourceOperation.RUNTIME_FIELDS.name(),
                "tags",
                List.of(resource.resourceName()),
                "security",
                AUTHENTICATED_SECURITY,
                "responses",
                responses,
                "parameters",
                List.of(
                    parameter(
                        "namespace",
                        "path",
                        true,
                        ordered(
                            "type",
                            "string",
                            "enum",
                            namespaces.stream().map(n -> n.namespace()).toList()),
                        "Runtime field namespace."),
                    parameter("q", "query", false, Map.of("type", "string"), "Search text."),
                    parameter(
                        "ids",
                        "query",
                        false,
                        Map.of("type", "string"),
                        "Comma-separated field identifiers."),
                    parameter(
                        "page",
                        "query",
                        false,
                        Map.of("type", "integer", "minimum", 1, "default", 1),
                        "Page number."),
                    parameter(
                        "limit",
                        "query",
                        false,
                        Map.of(
                            "type",
                            "integer",
                            "minimum",
                            1,
                            "maximum",
                            RuntimeFieldCatalogQuery.MAX_LIMIT,
                            "default",
                            RuntimeFieldCatalogQuery.DEFAULT_LIMIT),
                        "Page size.")))));
  }

  void applyRuntimeProjectionSchemas(
      Map<String, Object> schemas, ApiV2ResourceRegistration<?, ?> resource, String component) {
    Map<String, Object> read = mutableObjectMap(schemas.get(component + "Read"));
    Map<String, Object> properties = mutableObjectMap(read.get("properties"));
    for (Map<String, Object> runtimeField : runtimeFieldsExtension(resource)) {
      String responseField = String.class.cast(runtimeField.get("responseField"));
      if (!responseField.isEmpty()) {
        properties.put(responseField, ordered("type", "object", "additionalProperties", true));
      }
    }
  }

  List<Map<String, Object>> runtimeFieldsExtension(ApiV2ResourceRegistration<?, ?> resource) {
    List<Map<String, Object>> namespaces = new ArrayList<>(own(resource));
    for (Relationship<?> relationship : resource.description().relationships()) {
      if (relationship.targets().size() != 1) {
        continue;
      }
      String targetName = relationship.targets().get(0).resourceName();
      for (RuntimeCollectionFields<?> provider : catalog.runtimeFieldsOf(targetName)) {
        String targetNamespace = provider.namespace();
        String namespace = relationship.name() + "." + targetNamespace;
        boolean projectable = provider.projectsThroughRelationship();
        namespaces.add(
            runtimeNamespace(
                namespace,
                catalogUrl(targetName, targetNamespace),
                relationship.name(),
                targetName,
                projectable));
      }
    }
    return List.copyOf(namespaces);
  }

  private static String catalogUrl(String resourceName, String namespace) {
    return "/api/v2/" + resourceName + "/fields/" + namespace;
  }

  private static Map<String, Object> runtimeNamespace(
      String namespace, String catalog, String via, String viaResource, boolean projectable) {
    return ordered(
        "namespace",
        namespace,
        "catalog",
        catalog,
        "responseField",
        projectable ? namespace : "",
        "via",
        via,
        "viaResource",
        viaResource,
        "filterable",
        true,
        "columnSelectable",
        projectable,
        "sortable",
        false,
        "maximumProjections",
        projectable ? CollectionQueryLimits.MAX_RUNTIME_PROJECTIONS : 0,
        "catalogDefaultLimit",
        RuntimeFieldCatalogQuery.DEFAULT_LIMIT,
        "catalogMaximumLimit",
        RuntimeFieldCatalogQuery.MAX_LIMIT,
        "catalogMaximumIds",
        RuntimeFieldCatalogQuery.MAX_IDS,
        "catalogSearchParameter",
        "q",
        "catalogIdsParameter",
        "ids");
  }

  private static List<Map<String, Object>> own(ApiV2ResourceRegistration<?, ?> resource) {
    List<Map<String, Object>> published = new ArrayList<>();
    for (String namespace : resource.runtimeNamespaces()) {
      published.add(
          runtimeNamespace(
              namespace, catalogUrl(resource.resourceName(), namespace), "", "", true));
    }
    return List.copyOf(published);
  }
}
