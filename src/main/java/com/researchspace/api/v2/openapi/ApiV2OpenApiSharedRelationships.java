package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;

import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.RelationshipSchema;
import com.researchspace.model.collection.ResourceFieldSchema;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Publishes a relationship schema that several resource properties declare identically as one
 * component, so generated clients get one model per relationship. Read schemas name it {@code
 * <Targets>Relationship} and write schemas {@code <Targets>RelationshipInput}, after the target
 * components. A schema that one property alone declares stays inline, so registering another
 * resource never renames an existing model.
 */
final class ApiV2OpenApiSharedRelationships {

  private static final List<String> SCHEMA_SUFFIXES = List.of("Read", "Create", "Update");

  /** Keywords that document one property rather than describe the relationship itself. */
  private static final Set<String> PROPERTY_KEYWORDS =
      Set.of("title", "description", "example", "examples", "default", "readOnly", "deprecated");

  private record Use(Map<String, Object> properties, String field) {}

  private ApiV2OpenApiSharedRelationships() {}

  static void share(
      Map<String, Object> schemas,
      Collection<CollectionDescription<?>> resources,
      Map<String, String> componentNames) {
    Map<String, Map<Map<String, Object>, List<Use>>> byName = new LinkedHashMap<>();
    for (CollectionDescription<?> resource : resources) {
      String component = componentNames.get(resource.resourceName());
      for (ResourceFieldSchema field : resource.schema().documentFields()) {
        if (!(field instanceof RelationshipSchema relationship)) {
          continue;
        }
        String targets =
            relationship.targetResources().stream()
                .map(componentNames::get)
                .collect(Collectors.joining());
        for (String suffix : SCHEMA_SUFFIXES) {
          if (!(schemas.get(component + suffix) instanceof Map<?, ?> schema)
              || !(schema.get("properties") instanceof Map<?, ?> properties)
              || !(properties.get(field.name()) instanceof Map<?, ?> property)) {
            continue;
          }
          String name = targets + ("Read".equals(suffix) ? "Relationship" : "RelationshipInput");
          byName
              .computeIfAbsent(name, ignored -> new LinkedHashMap<>())
              .computeIfAbsent(
                  relationship(mutableObjectMap(property)), ignored -> new ArrayList<>())
              .add(new Use(mutableObjectMap(properties), field.name()));
        }
      }
    }
    byName.forEach(
        (name, declarations) -> {
          List<Map.Entry<Map<String, Object>, List<Use>>> shared =
              declarations.entrySet().stream()
                  .filter(entry -> entry.getValue().size() > 1)
                  .toList();
          if (shared.size() > 1) {
            throw new IllegalStateException(
                "OpenAPI relationships named " + name + " declare different schemas");
          }
          for (Map.Entry<Map<String, Object>, List<Use>> entry : shared) {
            if (schemas.putIfAbsent(name, entry.getKey()) != null) {
              throw new IllegalStateException(
                  "OpenAPI component name collision for relationships: " + name);
            }
            for (Use use : entry.getValue()) {
              use.properties()
                  .put(
                      use.field(),
                      withReference(mutableObjectMap(use.properties().get(use.field())), name));
            }
          }
        });
  }

  /** The relationship schema itself, without a nullable wrapper or property documentation. */
  private static Map<String, Object> relationship(Map<String, Object> property) {
    Map<String, Object> schema = new LinkedHashMap<>();
    property.forEach(
        (key, value) -> {
          if (!isPropertyKeyword(key)) {
            schema.put(key, value);
          }
        });
    return nonNull(schema);
  }

  private static Map<String, Object> nonNull(Map<String, Object> schema) {
    if (schema.size() == 1
        && schema.get("anyOf") instanceof List<?> alternatives
        && alternatives.size() == 2
        && alternatives.get(1) instanceof Map<?, ?> last
        && last.equals(Map.of("type", "null"))
        && alternatives.get(0) instanceof Map<?, ?> first) {
      return mutableObjectMap(first);
    }
    return schema;
  }

  private static Map<String, Object> withReference(Map<String, Object> property, String name) {
    Map<String, Object> schema = new LinkedHashMap<>();
    Map<String, Object> result = new LinkedHashMap<>();
    property.forEach(
        (key, value) -> {
          if (isPropertyKeyword(key)) {
            result.put(key, value);
          } else {
            schema.put(key, value);
          }
        });
    result.putAll(
        nonNull(schema) == schema
            ? ref(name)
            : ordered("anyOf", List.of(ref(name), ordered("type", "null"))));
    return result;
  }

  private static boolean isPropertyKeyword(String key) {
    return PROPERTY_KEYWORDS.contains(key) || key.startsWith("x-");
  }
}
