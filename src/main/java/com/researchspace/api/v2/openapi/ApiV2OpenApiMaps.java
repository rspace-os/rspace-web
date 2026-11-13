package com.researchspace.api.v2.openapi;

import io.swagger.v3.core.util.RefUtils;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small ordered-map helpers shared by the generated OpenAPI builders. */
final class ApiV2OpenApiMaps {

  private ApiV2OpenApiMaps() {}

  static Map<String, Object> ordered(Object... entries) {
    if (entries.length % 2 != 0) {
      throw new IllegalArgumentException("Ordered map entries must be key/value pairs");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index < entries.length; index += 2) {
      result.put(String.class.cast(entries[index]), entries[index + 1]);
    }
    return result;
  }

  @SuppressWarnings("unchecked") // Key types are checked immediately before the invariant cast.
  static Map<String, Object> mutableObjectMap(Object value) {
    if (!(value instanceof Map<?, ?> map)
        || map.keySet().stream().anyMatch(key -> !(key instanceof String))) {
      throw new IllegalArgumentException("Expected an object with string keys");
    }
    return (Map<String, Object>) map;
  }

  static Map<String, Object> objectMap(Map<?, ?> source) {
    Map<String, Object> result = new LinkedHashMap<>();
    source.forEach((key, value) -> result.put(String.class.cast(key), value));
    return result;
  }

  /** A content map with one media type whose only member is {@code schema}. */
  static Map<String, Object> content(String mediaType, Map<String, Object> schema) {
    return Map.of(mediaType, ordered("schema", schema));
  }

  static Map<String, Object> ref(String name) {
    return Map.of("$ref", RefUtils.constructRef(name));
  }

  static void putIfNotNull(Map<String, Object> target, String name, Object value) {
    if (value != null) {
      target.put(name, value);
    }
  }
}
