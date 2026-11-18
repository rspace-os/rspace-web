package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.objectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    // Added last, so it runs first and sees every model the resolver defines.
    CONVERTERS.addConverter(new ComponentNameGuard());
  }

  /**
   * Marks the properties that response normalisation made required, so the document can check that
   * no request body reaches them; {@link #requireResponseOnlyRequired} removes it.
   */
  static final String RESPONSE_REQUIRED = "x-rspace-response-required";

  private ApiV2OpenApiSchemas() {}

  /**
   * Names a nested type after its enclosing types, so {@code ApiV2Config.Branding} becomes {@code
   * ApiV2ConfigBranding} and features cannot collide on short names such as {@code Entry}. An
   * explicit {@code @Schema(name)} still wins. Components are keyed by name alone, so two types
   * with one derived name would silently replace each other's schema; naming the second one fails
   * instead. Swagger applies an explicit {@code @Schema(name)} without this resolver, so {@link
   * ComponentNameGuard} checks explicit names too.
   *
   * <p>A type nested in a controller or manager needs an explicit {@code @Schema(name)}, so the
   * public model name does not carry an implementation class name.
   */
  private static final class NestedTypeNameResolver extends TypeNameResolver {
    private final Map<String, Class<?>> named = new ConcurrentHashMap<>();

    @Override
    protected String nameForClass(Class<?> cls, Set<Options> options) {
      String simple = super.nameForClass(cls, options);
      Class<?> enclosing = cls.getEnclosingClass();
      boolean derived = enclosing != null && simple.equals(cls.getSimpleName());
      if (derived
          && (enclosing.getSimpleName().endsWith("Controller")
              || enclosing.getSimpleName().endsWith("Manager"))) {
        throw new IllegalStateException(
            "OpenAPI schema "
                + cls.getName()
                + " is nested in "
                + enclosing.getSimpleName()
                + "; give it an explicit @Schema(name = ...)");
      }
      String name = derived ? nameForClass(enclosing, options) + simple : simple;
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

  /**
   * Records the Java type behind every component the resolver defines, whether its name is explicit
   * or derived, and fails when one name would describe two types. Components are keyed by name
   * alone, so the second type would otherwise silently share the first one's schema.
   */
  private static final class ComponentNameGuard implements ModelConverter {
    private final Map<String, Class<?>> named = new ConcurrentHashMap<>();

    @Override
    public io.swagger.v3.oas.models.media.Schema<?> resolve(
        AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
      return chain.hasNext() ? chain.next().resolve(type, new Recording(context), chain) : null;
    }

    @Override
    public boolean isOpenapi31() {
      return true;
    }

    private void record(String name, Type type) {
      if (name == null || type == null) return;
      Class<?> raw = Json31.mapper().constructType(type).getRawClass();
      Class<?> previous = named.putIfAbsent(name, raw);
      if (previous != null && previous != raw) {
        throw new IllegalStateException(
            "OpenAPI component name "
                + name
                + " names both "
                + previous.getName()
                + " and "
                + raw.getName());
      }
    }

    private final class Recording implements ModelConverterContext {
      private final ModelConverterContext delegate;

      Recording(ModelConverterContext delegate) {
        this.delegate = delegate;
      }

      @Override
      public void defineModel(String name, io.swagger.v3.oas.models.media.Schema model) {
        delegate.defineModel(name, model);
      }

      @Override
      public void defineModel(
          String name,
          io.swagger.v3.oas.models.media.Schema model,
          AnnotatedType type,
          String prevName) {
        record(name, type == null ? null : type.getType());
        delegate.defineModel(name, model, type, prevName);
      }

      @Override
      public void defineModel(
          String name, io.swagger.v3.oas.models.media.Schema model, Type type, String prevName) {
        record(name, type);
        delegate.defineModel(name, model, type, prevName);
      }

      @Override
      public io.swagger.v3.oas.models.media.Schema resolve(AnnotatedType type) {
        return delegate.resolve(type);
      }

      @Override
      public Map<String, io.swagger.v3.oas.models.media.Schema> getDefinedModels() {
        return delegate.getDefinedModels();
      }

      @Override
      public Iterator<ModelConverter> getConverters() {
        return delegate.getConverters();
      }
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
    boolean typeOmitsPerCaller =
        bean.getClassAnnotations().has(JsonView.class)
            || bean.getClassAnnotations().has(JsonFilter.class);
    for (var property : bean.findProperties()) {
      if (!property.couldSerialize()
          || !(properties.get(property.getName()) instanceof Map<?, ?> value)) {
        continue;
      }
      Map<String, Object> shape = mutableObjectMap(value);
      JavaType propertyType = property.getPrimaryType();
      var annotation = property.getPrimaryMember().getAnnotation(Schema.class);
      boolean notRequired =
          annotation != null && annotation.requiredMode() == Schema.RequiredMode.NOT_REQUIRED;
      boolean omittedPerCaller =
          typeOmitsPerCaller
              || property.findViews() != null
              || property.getPrimaryMember().hasAnnotation(JsonView.class)
              || property.getPrimaryMember().hasAnnotation(JsonFilter.class);
      if (omittedPerCaller && !notRequired) {
        throw new IllegalStateException(
            "OpenAPI response property "
                + type.getRawClass().getName()
                + "."
                + property.getName()
                + " may be omitted for some callers by @JsonView or @JsonFilter. Use separate types"
                + " per audience, mark it @Schema(requiredMode = NOT_REQUIRED), or send null in a"
                + " property declared nullable.");
      }
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
      // The serialiser always writes the key, possibly as null; a primitive is never null.
      boolean alwaysWritten =
          !omitsNull || (include == JsonInclude.Include.NON_NULL && propertyType.isPrimitive());
      if (alwaysWritten && !notRequired && !omittedPerCaller) {
        requireProperty(object, property.getName());
      }
      normalizeResponse(propertyType, shape, components, visited);
    }
  }

  private static void requireProperty(Map<String, Object> object, String name) {
    var required =
        object.get("required") instanceof List<?> existing
            ? new ArrayList<Object>(existing)
            : new ArrayList<Object>();
    if (required.contains(name)) return;
    required.add(name);
    object.put("required", required);
    var marked =
        object.get(RESPONSE_REQUIRED) instanceof List<?> existing
            ? new ArrayList<Object>(existing)
            : new ArrayList<Object>();
    marked.add(name);
    object.put(RESPONSE_REQUIRED, marked);
  }

  /**
   * Response normalisation writes into shared components, so a component that a request body also
   * reaches would make those properties required in requests too. Fails when that happens, then
   * removes the markers.
   */
  static void requireResponseOnlyRequired(
      Map<String, Object> paths, Map<String, Object> components) {
    Set<String> reachable = new LinkedHashSet<>();
    paths
        .values()
        .forEach(
            item -> {
              if (item instanceof Map<?, ?> operations) {
                operations
                    .values()
                    .forEach(
                        operation -> {
                          if (operation instanceof Map<?, ?> map) {
                            collectReferences(map.get("requestBody"), components, reachable);
                          }
                        });
              }
            });
    for (String name : reachable) {
      List<String> marked = new ArrayList<>();
      collectMarked(components.get(name), marked);
      if (!marked.isEmpty()) {
        throw new IllegalStateException(
            "OpenAPI component "
                + name
                + " is used by a request body, but response normalisation requires "
                + marked
                + ". Use a separate response type or mark the properties"
                + " @Schema(requiredMode = REQUIRED) explicitly.");
      }
    }
    removeMarkers(paths);
    removeMarkers(components);
  }

  private static void collectReferences(
      Object value, Map<String, Object> components, Set<String> reachable) {
    if (value instanceof Map<?, ?> map) {
      if (map.get("$ref") instanceof String ref && ref.startsWith("#/components/schemas/")) {
        String name = ref.substring("#/components/schemas/".length());
        if (reachable.add(name)) collectReferences(components.get(name), components, reachable);
      }
      map.values().forEach(child -> collectReferences(child, components, reachable));
    } else if (value instanceof List<?> list) {
      list.forEach(child -> collectReferences(child, components, reachable));
    }
  }

  private static void collectMarked(Object value, List<String> marked) {
    if (value instanceof Map<?, ?> map) {
      if (map.get(RESPONSE_REQUIRED) instanceof List<?> names) {
        names.forEach(name -> marked.add(String.valueOf(name)));
      }
      map.values().forEach(child -> collectMarked(child, marked));
    } else if (value instanceof List<?> list) {
      list.forEach(child -> collectMarked(child, marked));
    }
  }

  private static void removeMarkers(Object value) {
    if (value instanceof Map<?, ?> map) {
      if (map.containsKey(RESPONSE_REQUIRED)) mutableObjectMap(map).remove(RESPONSE_REQUIRED);
      map.values().forEach(ApiV2OpenApiSchemas::removeMarkers);
    } else if (value instanceof List<?> list) {
      list.forEach(ApiV2OpenApiSchemas::removeMarkers);
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
