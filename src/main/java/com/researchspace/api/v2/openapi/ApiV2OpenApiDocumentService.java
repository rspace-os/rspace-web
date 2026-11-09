package com.researchspace.api.v2.openapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

public final class ApiV2OpenApiDocumentService {

  private static final TypeReference<LinkedHashMap<String, Object>> OBJECT_MAP =
      new TypeReference<>() {};

  private final ApiV2OpenApiGenerator generator;
  private final ApiV2EndpointCatalog endpoints;
  private final Supplier<Stream<RequestMappingHandlerMapping>> handlerMappings;

  public ApiV2OpenApiDocumentService(
      ApiV2OpenApiGenerator generator,
      ApiV2EndpointCatalog endpoints,
      Supplier<Stream<RequestMappingHandlerMapping>> handlerMappings) {
    this.generator = Objects.requireNonNull(generator, "OpenAPI generator");
    this.endpoints = Objects.requireNonNull(endpoints, "Endpoint catalog");
    this.handlerMappings = Objects.requireNonNull(handlerMappings, "Handler mappings");
  }

  public Map<String, Object> generate() {
    Map<String, Object> document = modelMap(generator.generate());
    try (Stream<RequestMappingHandlerMapping> mappings = handlerMappings.get()) {
      mappings.forEach(
          mapping ->
              ApiV2OpenApiAnnotationMerger.merge(document, mapping.getHandlerMethods(), endpoints));
    }
    normalize(document);
    var schemas =
        ApiV2OpenApiMaps.mutableObjectMap(
            ApiV2OpenApiMaps.mutableObjectMap(document.get("components")).get("schemas"));
    schemas.forEach(
        (name, schema) -> {
          if (schema instanceof Map<?, ?> map && map.isEmpty())
            throw new IllegalStateException("Unconstrained OpenAPI component: " + name);
        });
    var paths = ApiV2OpenApiMaps.mutableObjectMap(document.get("paths"));
    paths.forEach(
        (path, item) ->
            ApiV2OpenApiMaps.mutableObjectMap(item)
                .forEach(
                    (method, value) -> {
                      if (!java.util.Set.of(
                              "get", "post", "put", "patch", "delete", "head", "options")
                          .contains(method)) return;
                      var operation = ApiV2OpenApiMaps.mutableObjectMap(value);
                      operation.put(
                          "tags",
                          java.util.List.of(path.substring("/api/v2/".length()).split("/")[0]));
                    }));
    return document;
  }

  private static void normalize(Object value) {
    if (value instanceof Map<?, ?> raw) {
      var map = ApiV2OpenApiMaps.mutableObjectMap(raw);
      if (map.get("oneOf") instanceof java.util.List<?> branches
          && !branches.isEmpty()
          && branches.stream()
              .allMatch(
                  branch -> branch instanceof Map<?, ?> schema && schema.containsKey("type"))) {
        map.remove("type");
      }
      if ("query".equals(map.get("in"))
          && map.get("schema") instanceof Map<?, ?> schema
          && "array".equals(schema.get("type"))) map.put("explode", true);
      if (map.get("type") instanceof java.util.List<?> types
          && types.contains("null")
          && map.get("enum") instanceof java.util.List<?> choices
          && !choices.contains(null)) {
        var nullableChoices = new java.util.ArrayList<Object>(choices);
        nullableChoices.add(null);
        map.put("enum", nullableChoices);
      }
      if (map.containsKey("$ref") && map.size() > 1) {
        Object reference = map.remove("$ref");
        var branches = new java.util.ArrayList<Object>();
        branches.add(Map.of("$ref", reference));
        if (map.get("allOf") instanceof java.util.List<?> existing) branches.addAll(existing);
        map.put("allOf", branches);
      }
      map.values().forEach(ApiV2OpenApiDocumentService::normalize);
    } else if (value instanceof java.util.List<?> list)
      list.forEach(ApiV2OpenApiDocumentService::normalize);
  }

  private static Map<String, Object> modelMap(OpenAPI document) {
    return Json31.mapper().convertValue(document, OBJECT_MAP);
  }
}
