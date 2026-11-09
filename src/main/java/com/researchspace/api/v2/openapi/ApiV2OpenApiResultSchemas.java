package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;

import java.util.List;
import java.util.Map;

/** Schemas for collection result envelopes. */
final class ApiV2OpenApiResultSchemas {
  private ApiV2OpenApiResultSchemas() {}

  static Map<String, Object> listResult(Map<String, Object> item) {
    return ordered(
        "type",
        "object",
        "required",
        List.of("docs", "limit", "page"),
        "properties",
        ordered(
            "docs",
            ordered("type", "array", "items", item),
            "totalDocs",
            ordered(
                "type",
                "integer",
                "format",
                "int64",
                "description",
                "Present only when totals=true."),
            "totalIsLowerBound",
            ordered("type", "boolean", "description", "True when the requested total exceeds max."),
            "limit",
            ordered("type", "integer"),
            "page",
            ordered("type", "integer"),
            "hasNextPage",
            ordered("type", "boolean")));
  }

  static Map<String, Object> bulkResult(Map<String, Object> item) {
    return ordered(
        "type",
        "object",
        "required",
        List.of("docs"),
        "properties",
        ordered("docs", ordered("type", "array", "items", item)));
  }
}
