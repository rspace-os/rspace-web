package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.objectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.jackson.TypeNameResolver;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.RefUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/** Converts Java types to OpenAPI 3.1 component schemas. */
final class ApiV2OpenApiSchemas {

  // Swagger's Json31 mapper uses NON_NULL for its own models, unlike Spring's wire mapper.
  private static final ObjectMapper RESPONSE_MAPPER = Jackson2ObjectMapperBuilder.json().build();

  private static final ModelConverters CONVERTERS = new ModelConverters(true);

  static {
    CONVERTERS.addConverter(
        new ModelResolver(Json31.mapper(), new NestedTypeNameResolver()).openapi31(true));
  }

  private ApiV2OpenApiSchemas() {}

  /**
   * Names a nested type after its enclosing types, so {@code ApiV2Config.Branding} becomes {@code
   * ApiV2ConfigBranding} and features cannot collide on short names such as {@code Entry}. An
   * explicit {@code @Schema(name)} still wins. Components are keyed by name alone, so two types
   * with one derived name would silently replace each other's schema; naming the second one fails
   * instead. Swagger applies an explicit {@code @Schema(name)} without this resolver, so such a
   * name is the author's responsibility.
   */
  private static final class NestedTypeNameResolver extends TypeNameResolver {
    private final Map<String, Class<?>> named = new ConcurrentHashMap<>();

    @Override
    protected String nameForClass(Class<?> cls, Set<Options> options) {
      String simple = super.nameForClass(cls, options);
      Class<?> enclosing = cls.getEnclosingClass();
      String name =
          enclosing != null && simple.equals(cls.getSimpleName())
              ? nameForClass(enclosing, options) + simple
              : simple;
      Class<?> previous = named.putIfAbsent(name, cls);
      if (previous != null && previous != cls) {
        throw new IllegalStateException(
            "OpenAPI schema name "
                + name
                + " names both "
                + previous.getName()
                + " and "
                + cls.getName());
      }
      return name;
    }
  }

  static Map<String, Object> schemaFor(Type type, Map<String, Object> components) {
    if (type == byte[].class) {
      return ordered("type", "string", "format", "binary");
    }
    ResolvedSchema resolved =
        CONVERTERS.resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true));
    resolved.referencedSchemas.forEach(
        (name, schema) -> components.put(name, Json31.jsonSchemaAsMap(schema)));
    return resolved.schema == null
        ? ordered("type", "object")
        : Json31.jsonSchemaAsMap(resolved.schema);
  }

  static Map<String, Object> responseSchemaFor(Type type, Map<String, Object> components) {
    Map<String, Object> schema = schemaFor(type, components);
    normalizeResponse(RESPONSE_MAPPER.constructType(type), schema, components, new HashSet<>());
    return schema;
  }

  private static void normalizeResponse(
      JavaType type,
      Map<String, Object> schema,
      Map<String, Object> components,
      Set<String> visited) {
    if (type.hasRawClass(Optional.class)) {
      addNull(schema);
      type = type.containedTypeOrUnknown(0);
    }
    Map<String, Object> object = nonNullSchema(schema, components);
    if (type.isCollectionLikeType() || type.isArrayType()) {
      if (object.get("items") instanceof Map<?, ?> items) {
        normalizeResponse(type.getContentType(), mutableObjectMap(items), components, visited);
      }
      return;
    }
    if (!(object.get("properties") instanceof Map<?, ?> properties)
        || !visited.add(type.toCanonical())) {
      return;
    }
    var config = RESPONSE_MAPPER.getSerializationConfig();
    var bean = config.introspect(type);
    var inclusion = bean.findPropertyInclusion(config.getDefaultPropertyInclusion());
    for (var property : bean.findProperties()) {
      if (!property.couldSerialize()
          || !(properties.get(property.getName()) instanceof Map<?, ?> value)) {
        continue;
      }
      Map<String, Object> shape = mutableObjectMap(value);
      JavaType propertyType = property.getPrimaryType();
      var annotation = property.getPrimaryMember().getAnnotation(Schema.class);
      if (annotation != null
          && (annotation.nullable() || List.of(annotation.types()).contains("null"))) {
        // Swagger can emit a bare "null" type alongside a non-null allOf reference in 3.1.
        if ((shape.containsKey("allOf") || shape.containsKey("$ref")) && hasNull(shape))
          shape.remove("type");
        addNull(shape);
      }
      boolean optional = propertyType.hasRawClass(Optional.class);
      var include = inclusion.withOverrides(property.findInclusion()).getValueInclusion();
      // NON_NULL does not omit an empty Optional with Jackson's default Jdk8Module.
      boolean omitsNull =
          include == JsonInclude.Include.NON_ABSENT
              || include == JsonInclude.Include.NON_EMPTY
              || include == JsonInclude.Include.NON_DEFAULT
              || (include == JsonInclude.Include.NON_NULL && !optional);
      if (omitsNull) {
        if (hasNull(shape) || optional) {
          removeNull(shape);
          if (object.get("required") instanceof List<?> required) {
            var remaining = new ArrayList<>(required);
            remaining.remove(property.getName());
            if (remaining.isEmpty()) object.remove("required");
            else object.put("required", remaining);
          }
        }
        if (optional) propertyType = propertyType.containedTypeOrUnknown(0);
      }
      normalizeResponse(propertyType, shape, components, visited);
    }
  }

  private static boolean hasNull(Map<String, Object> schema) {
    if ("null".equals(schema.get("type"))) return true;
    if (schema.get("type") instanceof List<?> types && types.contains("null")) return true;
    for (String keyword : List.of("anyOf", "oneOf")) {
      if (schema.get(keyword) instanceof List<?> alternatives
          && alternatives.stream()
              .anyMatch(
                  value ->
                      value instanceof Map<?, ?> branch && hasNull(mutableObjectMap(branch)))) {
        return true;
      }
    }
    return false;
  }

  private static void addNull(Map<String, Object> schema) {
    if (hasNull(schema)) return;
    if (schema.get("type") instanceof String type) {
      schema.put("type", List.of(type, "null"));
    } else if (schema.get("type") instanceof List<?> types) {
      var nullable = new ArrayList<Object>(types);
      nullable.add("null");
      schema.put("type", nullable);
    } else {
      var nonNull = new LinkedHashMap<>(schema);
      schema.clear();
      schema.put("anyOf", List.of(nonNull, ordered("type", "null")));
    }
  }

  private static void removeNull(Map<String, Object> schema) {
    if (schema.get("type") instanceof List<?> types) {
      var nonNull = types.stream().filter(value -> !"null".equals(value)).toList();
      schema.put("type", nonNull.size() == 1 ? nonNull.get(0) : nonNull);
    }
    for (String keyword : List.of("anyOf", "oneOf")) {
      if (schema.get(keyword) instanceof List<?> alternatives) {
        var nonNull =
            alternatives.stream()
                .filter(
                    value ->
                        !(value instanceof Map<?, ?> branch && "null".equals(branch.get("type"))))
                .toList();
        if (nonNull.size() == 1 && nonNull.get(0) instanceof Map<?, ?> branch) {
          schema.remove(keyword);
          schema.putAll(mutableObjectMap(branch));
        } else schema.put(keyword, nonNull);
      }
    }
  }

  private static Map<String, Object> nonNullSchema(
      Map<String, Object> schema, Map<String, Object> components) {
    if (schema.get("$ref") instanceof String ref) {
      return mutableObjectMap(components.get(RefUtils.extractSimpleName(ref).getLeft()));
    }
    for (String keyword : List.of("anyOf", "oneOf", "allOf")) {
      if (schema.get(keyword) instanceof List<?> alternatives) {
        for (Object value : alternatives) {
          if (value instanceof Map<?, ?> branch && !"null".equals(branch.get("type"))) {
            return nonNullSchema(mutableObjectMap(branch), components);
          }
        }
      }
    }
    return schema;
  }

  /**
   * The swagger converter renders a nullable reference property as {@code {"type": "null", "$ref":
   * ...}}, which no value satisfies in OpenAPI 3.1. Rewrites each such property as {@code anyOf}
   * the reference and {@code null}, keeping its other keywords such as the description.
   */
  static Object withNullableReferenceProperties(Object schema) {
    if (schema instanceof Map<?, ?> composite && composite.get("allOf") instanceof List<?> parts) {
      Map<String, Object> copy = new LinkedHashMap<>();
      composite.forEach((key, value) -> copy.put((String) key, value));
      copy.put(
          "allOf",
          parts.stream().map(ApiV2OpenApiSchemas::withNullableReferenceProperties).toList());
      schema = copy;
    }
    if (!(schema instanceof Map<?, ?> component)
        || !(component.get("properties") instanceof Map<?, ?> properties)
        || properties.values().stream().noneMatch(ApiV2OpenApiSchemas::isNullableReference)) {
      return schema;
    }
    Map<String, Object> rewritten = new LinkedHashMap<>();
    properties.forEach(
        (name, property) ->
            rewritten.put(
                (String) name,
                isNullableReference(property)
                    ? nullableReference((Map<?, ?>) property)
                    : property));
    Map<String, Object> result = objectMap(component);
    result.put("properties", rewritten);
    return result;
  }

  private static boolean isNullableReference(Object property) {
    return property instanceof Map<?, ?> map
        && map.get("$ref") instanceof String
        && ("null".equals(map.get("type"))
            || map.get("type") instanceof List<?> types && types.contains("null"));
  }

  private static Map<String, Object> nullableReference(Map<?, ?> property) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("anyOf", List.of(ordered("$ref", property.get("$ref")), ordered("type", "null")));
    property.forEach(
        (key, value) -> {
          if (!"$ref".equals(key) && !"type".equals(key)) {
            result.put((String) key, value);
          }
        });
    return result;
  }
}
