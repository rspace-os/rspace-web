package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.componentRef;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Components that the builders of one document reference instead of repeating. A component exists
 * only once something references it, and each section lists its components by name.
 */
final class ApiV2OpenApiComponents {

  private final Map<String, Map<String, Object>> sections = new TreeMap<>();

  /**
   * Registers {@code value} as {@code #/components/<section>/<name>} and returns a reference to it.
   * Every use of a name must register the same value.
   */
  Map<String, Object> reference(String section, String name, Object value) {
    Objects.requireNonNull(value, name);
    Object existing = sections.computeIfAbsent(section, ignored -> new TreeMap<>()).get(name);
    if (existing != null && !existing.equals(value)) {
      throw new IllegalArgumentException("Conflicting OpenAPI component " + section + "/" + name);
    }
    sections.get(section).put(name, value);
    return componentRef(section, name);
  }

  /**
   * A reference to {@code value} under {@code name}, or the value itself when the name already
   * holds a different value. Use it where callers outside the builders choose the content.
   */
  Object referenceOrInline(String section, String name, Object value) {
    Object existing = sections.getOrDefault(section, Map.of()).get(name);
    return existing == null || existing.equals(value) ? reference(section, name, value) : value;
  }

  /** Adds the registered components to {@code components}, beside any it already holds. */
  void addTo(Map<String, Object> components) {
    sections.forEach(
        (section, entries) -> {
          Map<String, Object> merged =
              new LinkedHashMap<>(
                  mutableObjectMap(components.getOrDefault(section, new LinkedHashMap<>())));
          entries.forEach(
              (name, value) -> {
                if (merged.putIfAbsent(name, value) != null) {
                  throw new IllegalArgumentException(
                      "Duplicate OpenAPI component " + section + "/" + name);
                }
              });
          components.put(section, merged);
        });
  }
}
