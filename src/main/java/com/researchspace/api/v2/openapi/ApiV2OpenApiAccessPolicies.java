package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;

import com.fasterxml.jackson.core.type.TypeReference;
import com.researchspace.model.collection.AccessDocumentation;
import io.swagger.v3.core.util.Json31;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Moves access policy objects into {@code components.x-rspace-access-policies} and replaces each
 * use with a reference. Each policy is keyed by the name its {@link AccessDocumentation} declares;
 * two different policies that declare one name fail generation.
 */
final class ApiV2OpenApiAccessPolicies {

  static final String SECTION = "x-rspace-access-policies";
  private static final String ACCESS = "x-rspace-access";
  private static final String FIELDS = "x-rspace-fields";
  private static final Set<String> FIELD_ACCESS =
      Set.of("readAccess", "createAccess", "updateAccess");
  private static final TypeReference<List<Object>> FIELD_LIST = new TypeReference<>() {};
  private static final String NAME = "name";
  private static final String DESCRIPTION = "description";
  private static final String AUTHENTICATION = "authentication";
  private static final String DENIAL_REASON_CODES = "denialReasonCodes";
  private static final Set<String> KEYS =
      Set.of(NAME, DESCRIPTION, AUTHENTICATION, DENIAL_REASON_CODES);

  private ApiV2OpenApiAccessPolicies() {}

  /**
   * Replaces every {@code x-rspace-access} value, and the access policies of {@code
   * x-rspace-fields} entries, under {@code node} with references.
   */
  static void share(Object node, ApiV2OpenApiComponents components) {
    if (node instanceof Map<?, ?> raw) {
      if (raw.containsKey(ACCESS) || raw.containsKey(FIELDS)) {
        Map<String, Object> map = mutableObjectMap(raw);
        if (map.get(ACCESS) instanceof Map<?, ?> policy) {
          map.put(ACCESS, policy(policy, components));
        }
        if (map.containsKey(FIELDS)) {
          map.put(FIELDS, fields(map.get(FIELDS), components));
        }
      }
      raw.values().forEach(value -> share(value, components));
    } else if (node instanceof List<?> list) {
      list.forEach(value -> share(value, components));
    }
  }

  private static Object fields(Object value, ApiV2OpenApiComponents components) {
    if (!(value instanceof Collection<?>)) {
      return value;
    }
    List<Object> fields = Json31.mapper().convertValue(value, FIELD_LIST);
    fields.forEach(field -> fieldAccess(field, components));
    return fields;
  }

  private static void fieldAccess(Object field, ApiV2OpenApiComponents components) {
    if (!(field instanceof Map<?, ?> raw)) {
      return;
    }
    Map<String, Object> map = mutableObjectMap(raw);
    map.replaceAll(
        (key, value) ->
            FIELD_ACCESS.contains(key) && value instanceof Map<?, ?> policy
                ? policy(policy, components)
                : value);
    if (map.get("fields") instanceof List<?> children) {
      children.forEach(child -> fieldAccess(child, components));
    }
  }

  /** The {@code x-rspace-access} object for {@code access}, before {@link #share} names it. */
  static Map<String, Object> extension(AccessDocumentation access) {
    return ordered(
        NAME,
        access.name(),
        DESCRIPTION,
        access.description(),
        AUTHENTICATION,
        access.authenticationRequirement().name(),
        DENIAL_REASON_CODES,
        access.denialReasonCodes());
  }

  private static Map<String, Object> policy(Map<?, ?> raw, ApiV2OpenApiComponents components) {
    Map<String, Object> policy = ApiV2OpenApiMaps.objectMap(raw);
    if (!KEYS.equals(policy.keySet()) || !(policy.get(NAME) instanceof String name)) {
      throw new IllegalStateException(
          "OpenAPI access policy must have exactly " + KEYS + ": " + raw);
    }
    // The codes come from a set, so sort them to keep equal policies equal.
    Collection<?> codes = (Collection<?>) policy.get(DENIAL_REASON_CODES);
    return components.reference(
        SECTION,
        name,
        ordered(
            DESCRIPTION,
            policy.get(DESCRIPTION),
            AUTHENTICATION,
            policy.get(AUTHENTICATION),
            DENIAL_REASON_CODES,
            codes.stream().map(String::valueOf).sorted().toList()));
  }
}
