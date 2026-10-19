package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ApiV2OpenApiAuditDocs {

  static Map<String, Object> auditPage(Map<String, Object> item) {
    Map<String, Object> page = listResult(item);
    List<String> required =
        ((List<?>) page.get("required")).stream().map(String::valueOf).collect(Collectors.toList());
    required.add("snapshotDate");
    required.add("snapshotFingerprint");
    page.put("required", required);
    Map<String, Object> properties = mutableObjectMap(page.get("properties"));
    properties.put("snapshotDate", ordered("type", "string", "format", "date"));
    properties.put(
        "snapshotFingerprint",
        ordered("type", "string", "pattern", "^[0-9a-f]{64}$", "minLength", 64, "maxLength", 64));
    return page;
  }

  private static Map<String, Object> listResult(Map<String, Object> item) {
    return ApiV2OpenApiGenerator.listResult(item);
  }
}
