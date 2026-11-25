package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;

import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FieldSchema;
import com.researchspace.model.collection.ResourceFieldSchema;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Publishes the object of each top-level declared object or array field on a read schema as a
 * component, so generated clients get one named model per row type. Fields that declare the same
 * row type identically share {@code Declared<Type>}. When any of them differ, each gets {@code
 * <Component><Field>} instead, so registering another collection never renames an existing model.
 */
final class ApiV2OpenApiDeclaredRows {

  private static final Set<String> OBJECT_KEYWORDS =
      Set.of("type", "additionalProperties", "properties", "required");

  private record Declared(
      Class<?> type,
      String component,
      String field,
      boolean array,
      Map<String, Object> properties,
      Map<String, Object> row) {}

  private ApiV2OpenApiDeclaredRows() {}

  static void share(
      Map<String, Object> schemas,
      Collection<CollectionDescription<?>> resources,
      Map<String, String> componentNames) {
    Map<String, List<Declared>> byTypeName = new LinkedHashMap<>();
    for (CollectionDescription<?> resource : resources) {
      String component = componentNames.get(resource.resourceName());
      Map<String, Object> properties =
          mutableObjectMap(mutableObjectMap(schemas.get(component + "Read")).get("properties"));
      for (ResourceFieldSchema field : resource.schema().documentFields()) {
        if (field instanceof FieldSchema declared
            && !declared.fields().isEmpty()
            && declared.type().objectType() != null
            && properties.get(field.name()) instanceof Map<?, ?> property) {
          boolean array = "array".equals(declared.type().jsonType());
          Map<String, Object> row =
              array
                  ? mutableObjectMap(mutableObjectMap(property).get("items"))
                  : objectKeywords(mutableObjectMap(property));
          Class<?> type = declared.type().objectType();
          requireLikeComponent(row, type, declared.fields());
          byTypeName
              .computeIfAbsent(ApiV2OpenApiNames.typeName(type), name -> new ArrayList<>())
              .add(new Declared(type, component, field.name(), array, properties, row));
        }
      }
    }
    byTypeName.forEach(
        (typeName, declarations) -> {
          Declared first = declarations.get(0);
          boolean shared =
              declarations.stream()
                  .allMatch(
                      declared ->
                          declared.type() == first.type() && declared.row().equals(first.row()));
          for (Declared declared : declarations) {
            String name =
                shared
                    ? "Declared" + typeName
                    : declared.component() + ApiV2OpenApiNames.pascalCase(declared.field());
            if (!shared || declared == first) {
              if (schemas.putIfAbsent(name, declared.row()) != null) {
                throw new IllegalStateException(
                    "OpenAPI component name collision for declared rows: " + name);
              }
            }
            declared.properties().put(declared.field(), withRowReference(declared, name));
          }
        });
  }

  /**
   * Copies {@code required} from the response component of the row's Java type when the row has the
   * same properties with the same nullability, keeping the row's own schema. A property is copied
   * only when the embedded renderer always writes it: it has no read restriction of its own, and it
   * is nullable (written as null) or required on create (never missing).
   */
  private static void requireLikeComponent(
      Map<String, Object> row, Class<?> type, List<FieldSchema> fields) {
    if (!(row.get("properties") instanceof Map<?, ?> rowProperties)) return;
    Map<String, Object> scratch = new LinkedHashMap<>();
    Map<String, Object> component =
        resolve(ApiV2OpenApiSchemas.responseSchemaFor(type, scratch), scratch);
    if (!(component.get("properties") instanceof Map<?, ?> componentProperties)
        || !rowProperties.keySet().equals(componentProperties.keySet())
        || !(component.get("required") instanceof List<?> componentRequired)) {
      return;
    }
    for (Object name : rowProperties.keySet()) {
      if (nullable(rowProperties.get(name)) != nullable(componentProperties.get(name))) {
        return;
      }
    }
    List<String> required = new ArrayList<>();
    for (Object name : rowProperties.keySet()) {
      fields.stream()
          .filter(field -> field.name().equals(name))
          .filter(field -> field.readInherited() && (field.nullable() || field.requiredOnCreate()))
          .filter(field -> componentRequired.contains(field.name()))
          .forEach(field -> required.add(field.name()));
    }
    if (!required.isEmpty()) row.put("required", required);
  }

  private static Map<String, Object> resolve(Object schema, Map<String, Object> components) {
    Map<String, Object> map = mutableObjectMap(schema);
    return map.get("$ref") instanceof String ref
        ? mutableObjectMap(components.get(ref.substring(ref.lastIndexOf('/') + 1)))
        : map;
  }

  private static boolean nullable(Object schema) {
    if (!(schema instanceof Map<?, ?> map)) return false;
    if ("null".equals(map.get("type"))
        || (map.get("type") instanceof List<?> types && types.contains("null"))) {
      return true;
    }
    for (String keyword : List.of("anyOf", "oneOf")) {
      if (map.get(keyword) instanceof List<?> branches
          && branches.stream().anyMatch(ApiV2OpenApiDeclaredRows::nullable)) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, Object> objectKeywords(Map<String, Object> property) {
    Map<String, Object> row = ordered("type", "object");
    property.forEach(
        (key, value) -> {
          if (OBJECT_KEYWORDS.contains(key) && !"type".equals(key)) {
            row.put(key, value);
          }
        });
    return row;
  }

  private static Map<String, Object> withRowReference(Declared declared, String name) {
    Map<String, Object> property = mutableObjectMap(declared.properties().get(declared.field()));
    Map<String, Object> result = new LinkedHashMap<>();
    property.forEach(
        (key, value) -> {
          if (declared.array()) {
            result.put(key, "items".equals(key) ? ref(name) : value);
          } else if ("type".equals(key)) {
            // The reference takes the object keywords' place, keeping a nullable field nullable.
            result.putAll(
                value instanceof List<?> types && types.contains("null")
                    ? ordered("anyOf", List.of(ref(name), ordered("type", "null")))
                    : ref(name));
          } else if (!OBJECT_KEYWORDS.contains(key)) {
            result.put(key, value);
          }
        });
    return result;
  }
}
