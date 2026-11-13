package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.core.util.Json31;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Moves access policy objects into {@code components.x-rspace-access-policies} and replaces each
 * use with a reference. A policy is named by a digest of its content, so identical policies share
 * one entry and a name changes only when its policy does.
 */
final class ApiV2OpenApiAccessPolicies {

  static final String SECTION = "x-rspace-access-policies";
  private static final String ACCESS = "x-rspace-access";
  private static final String FIELDS = "x-rspace-fields";
  private static final Set<String> FIELD_ACCESS =
      Set.of("readAccess", "createAccess", "updateAccess");
  private static final TypeReference<List<Object>> FIELD_LIST = new TypeReference<>() {};
  private static final String DENIAL_REASON_CODES = "denialReasonCodes";
  private static final ObjectMapper CANONICAL =
      Json31.mapper().copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

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

  private static Map<String, Object> policy(Map<?, ?> raw, ApiV2OpenApiComponents components) {
    Map<String, Object> policy = new LinkedHashMap<>(ApiV2OpenApiMaps.objectMap(raw));
    if (policy.get(DENIAL_REASON_CODES) instanceof Collection<?> codes) {
      // The codes come from a set, so sort them to name equal policies alike.
      policy.put(DENIAL_REASON_CODES, codes.stream().map(String::valueOf).sorted().toList());
    }
    return components.reference(SECTION, name(policy), policy);
  }

  private static String name(Map<String, Object> policy) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(CANONICAL.writeValueAsString(policy).getBytes(StandardCharsets.UTF_8));
      return "AccessPolicy" + HexFormat.of().formatHex(digest, 0, 4);
    } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Cannot name OpenAPI access policy", ex);
    }
  }
}
