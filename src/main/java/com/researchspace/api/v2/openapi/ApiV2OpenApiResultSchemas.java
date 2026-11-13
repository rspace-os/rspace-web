package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;

import com.researchspace.api.v2.openapi.ApiV2OpenApiText.Key;
import java.util.List;
import java.util.Map;

/** Schemas for collection result envelopes. */
final class ApiV2OpenApiResultSchemas {
  private ApiV2OpenApiResultSchemas() {}

  static Map<String, Object> listResult(Map<String, Object> item, ApiV2OpenApiText text) {
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
                "type", "integer", "format", "int64", "description", text.get(Key.LIST_TOTAL_DOCS)),
            "totalIsLowerBound",
            ordered("type", "boolean", "description", text.get(Key.LIST_TOTAL_IS_LOWER_BOUND)),
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
