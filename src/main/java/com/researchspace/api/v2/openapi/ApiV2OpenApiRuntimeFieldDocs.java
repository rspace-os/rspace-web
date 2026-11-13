package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiGenerator.AUTHENTICATED_SECURITY;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.content;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.parameter;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.putStandardResponses;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Documents the caller-specific runtime field catalog routes. */
final class ApiV2OpenApiRuntimeFieldDocs {

  private ApiV2OpenApiRuntimeFieldDocs() {}

  static void addRuntimeFieldsPath(
      Map<String, Object> paths,
      ApiV2ResourceRegistration<?, ?> resource,
      List<RuntimeCollectionFields<?>> namespaces) {
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
}
