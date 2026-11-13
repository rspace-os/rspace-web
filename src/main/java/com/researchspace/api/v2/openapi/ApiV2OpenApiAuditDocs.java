package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;

import java.util.List;
import java.util.Map;

final class ApiV2OpenApiAuditDocs {
  static Map<String, Object> auditPage(Map<String, Object> item) {
    return ordered(
        "type",
        "object",
        "required",
        List.of("docs", "limit", "truncated", "start", "endExclusive"),
        "properties",
        ordered(
            "docs",
            ordered("type", "array", "items", item),
            "limit",
            ordered("type", "integer", "minimum", 1),
            "truncated",
            ordered("type", "boolean"),
            "start",
            ordered("type", "string", "format", "date-time"),
            "endExclusive",
            ordered("type", "string", "format", "date-time")));
  }
}
