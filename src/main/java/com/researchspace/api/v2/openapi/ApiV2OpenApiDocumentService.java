package com.researchspace.api.v2.openapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.util.StringUtils;
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
    List<Tag> controllerTags = new ArrayList<>();
    try (Stream<RequestMappingHandlerMapping> mappings = handlerMappings.get()) {
      mappings.forEach(
          mapping -> {
            mapping.getHandlerMethods().values().stream()
                .map(handler -> handler.getBeanType())
                .distinct()
                .forEach(
                    type ->
                        controllerTags.addAll(Arrays.asList(type.getAnnotationsByType(Tag.class))));
            ApiV2OpenApiAnnotationMerger.merge(
                document, mapping.getHandlerMethods(), endpoints, generator.text());
          });
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
    requireUniqueOperationIds(document);
    buildTags(document, controllerTags);
    return document;
  }

  /** Generated names can coincide (an uncountable singular equals its plural), so check here. */
  private static void requireUniqueOperationIds(Map<String, Object> document) {
    Map<String, String> seen = new HashMap<>();
    ApiV2OpenApiMaps.mutableObjectMap(document.get("paths"))
        .forEach(
            (path, item) ->
                ApiV2OpenApiMaps.mutableObjectMap(item)
                    .forEach(
                        (method, operation) -> {
                          if (operation instanceof Map<?, ?> map
                              && map.get("operationId") instanceof String id) {
                            String previous = seen.putIfAbsent(id, method + " " + path);
                            if (previous != null) {
                              throw new IllegalStateException(
                                  "Duplicate OpenAPI operationId "
                                      + id
                                      + ": "
                                      + previous
                                      + " and "
                                      + method
                                      + " "
                                      + path);
                            }
                          }
                        }));
  }

  private record TagMetadata(String description, String group) {}

  private static void buildTags(Map<String, Object> document, List<Tag> controllerTags) {
    Map<String, TagMetadata> metadata = new TreeMap<>();
    for (Tag tag : controllerTags) {
      String group =
          Arrays.stream(tag.extensions())
              .flatMap(extension -> Arrays.stream(extension.properties()))
              .filter(
                  property ->
                      OpenApiOperationDocumentation.TAG_GROUP_EXTENSION.equals(property.name()))
              .map(property -> property.value())
              .findFirst()
              .orElse(null);
      addTagMetadata(metadata, tag.name(), tag.description(), group);
    }
    Set<String> used = new TreeSet<>();
    var paths = ApiV2OpenApiMaps.mutableObjectMap(document.get("paths"));
    paths.forEach(
        (path, item) ->
            ApiV2OpenApiMaps.mutableObjectMap(item)
                .forEach(
                    (method, value) -> {
                      if (!Set.of(
                              "get", "post", "put", "patch", "delete", "head", "options", "trace")
                          .contains(method)) return;
                      var operation = ApiV2OpenApiMaps.mutableObjectMap(value);
                      String relative =
                          path.startsWith("/api/v2/")
                              ? path.substring("/api/v2/".length())
                              : path.replaceFirst("^/+", "");
                      String tag = StringUtils.stripFilenameExtension(relative.split("/")[0]);
                      operation.put("tags", List.of(tag));
                      used.add(tag);
                      Object description =
                          operation.remove(OpenApiOperationDocumentation.TAG_DESCRIPTION_EXTENSION);
                      Object group =
                          operation.remove(OpenApiOperationDocumentation.TAG_GROUP_EXTENSION);
                      if (description != null || group != null)
                        addTagMetadata(metadata, tag, text(description), text(group));
                    }));
    List<Map<String, Object>> tags = new ArrayList<>();
    Map<String, Set<String>> groups = new TreeMap<>();
    for (String name : used) {
      TagMetadata tag = metadata.get(name);
      if (tag == null || text(tag.description()) == null || text(tag.group()) == null) {
        throw new IllegalStateException("OpenAPI tag requires a description and group: " + name);
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("name", name);
      entry.put("description", tag.description());
      tags.add(entry);
      groups.computeIfAbsent(tag.group(), ignored -> new TreeSet<>()).add(name);
    }
    // OpenAPI 3.2: replace x-tagGroups with Tag.parent and a kind: "nav" parent tag per group
    // (https://learn.openapis.org/specification/tags). Blocked on swagger-api/swagger-core#5326.
    List<Map<String, Object>> tagGroups = new ArrayList<>();
    groups.forEach(
        (name, names) -> {
          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("name", name);
          entry.put("tags", List.copyOf(names));
          tagGroups.add(entry);
        });
    document.put("tags", tags);
    document.put("x-tagGroups", tagGroups);
  }

  private static String text(Object value) {
    return value instanceof String string && !string.isBlank() ? string : null;
  }

  private static void addTagMetadata(
      Map<String, TagMetadata> metadata, String name, String description, String group) {
    if (name == null || name.isBlank()) return;
    TagMetadata existing = metadata.get(name);
    if (existing != null
        && text(existing.group()) != null
        && text(group) != null
        && !existing.group().equals(group)) {
      throw new IllegalStateException("Conflicting OpenAPI tag groups for " + name);
    }
    metadata.put(
        name,
        new TagMetadata(
            existing != null && text(existing.description()) != null
                ? existing.description()
                : description,
            existing != null && text(existing.group()) != null ? existing.group() : group));
  }

  private static void normalize(Object value) {
    if (value instanceof Map<?, ?> raw) {
      var map = ApiV2OpenApiMaps.mutableObjectMap(raw);
      if (map.get("oneOf") instanceof List<?> branches
          && !branches.isEmpty()
          && branches.stream()
              .allMatch(
                  branch -> branch instanceof Map<?, ?> schema && schema.containsKey("type"))) {
        map.remove("type");
      }
      if ("query".equals(map.get("in"))
          && map.get("schema") instanceof Map<?, ?> schema
          && "array".equals(schema.get("type"))) map.put("explode", true);
      if (map.get("type") instanceof List<?> types
          && types.contains("null")
          && map.get("enum") instanceof List<?> choices
          && !choices.contains(null)) {
        var nullableChoices = new ArrayList<Object>(choices);
        nullableChoices.add(null);
        map.put("enum", nullableChoices);
      }
      if (map.containsKey("$ref") && map.size() > 1) {
        Object reference = map.remove("$ref");
        var branches = new ArrayList<Object>();
        branches.add(Map.of("$ref", reference));
        if (map.get("allOf") instanceof List<?> existing) branches.addAll(existing);
        map.put("allOf", branches);
      }
      map.values().forEach(ApiV2OpenApiDocumentService::normalize);
    } else if (value instanceof List<?> list) list.forEach(ApiV2OpenApiDocumentService::normalize);
  }

  private static Map<String, Object> modelMap(OpenAPI document) {
    return Json31.mapper().convertValue(document, OBJECT_MAP);
  }
}
