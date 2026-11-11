package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.PARAMETERS;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.idParameter;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiQueryParameters.parameter;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.RESPONSES;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.problemResponse;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.putStandardResponses;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiResponses.rateLimitHeaders;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.researchspace.api.v2.openapi.ApiV2OpenApiText.Key;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.AccessPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Documents the audit list of resources with an audit policy. Every resource shares the same
 * parameters and responses, so the operation references them as components.
 */
final class ApiV2OpenApiAuditDocs {

  private ApiV2OpenApiAuditDocs() {}

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

  static void addAuditPaths(
      Map<String, Object> paths,
      ApiV2ResourceRegistration<?, ?> resource,
      String component,
      ApiV2OpenApiText text,
      ApiV2OpenApiComponents components) {
    if (resource.audit().isEmpty()) {
      return;
    }
    String itemAuditPath = "/api/v2/" + resource.resourceName() + "/{id}/audit";
    paths.put(itemAuditPath, ordered("get", auditOperation(resource, component, text, components)));
  }

  private static Map<String, Object> auditOperation(
      ApiV2ResourceRegistration<?, ?> resource,
      String component,
      ApiV2OpenApiText text,
      ApiV2OpenApiComponents components) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("operationId", "list" + ApiV2OpenApiNames.singularName(resource) + "AuditEvents");
    result.put("summary", text.get(Key.AUDIT_SUMMARY, resource.resourceName()));
    result.put("description", text.get(Key.AUDIT_DESCRIPTION));
    result.put("tags", List.of(resource.resourceName()));
    result.put("security", ApiV2OpenApiGenerator.AUTHENTICATED_SECURITY);
    result.put(
        "x-rspace-access",
        ApiV2OpenApiAccessPolicies.extension(
            new AccessDocumentation(
                "AuditRead",
                text.get(Key.AUDIT_ACCESS),
                Set.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN))));
    result.put("parameters", auditParameters(resource, component, text, components));
    result.put("responses", auditResponses(text, components));
    result.put("x-rspace-operation", "AUDIT_LIST");
    return result;
  }

  private static List<Map<String, Object>> auditParameters(
      ApiV2ResourceRegistration<?, ?> resource,
      String component,
      ApiV2OpenApiText text,
      ApiV2OpenApiComponents components) {
    List<Map<String, Object>> parameters = new ArrayList<>();
    parameters.add(
        components.reference(
            PARAMETERS, component + "Id", idParameter(resource.description().schema(), text)));
    parameters.add(
        components.reference(
            PARAMETERS,
            "AuditStart",
            parameter(
                "start",
                "query",
                false,
                ordered("type", "string", "format", "date-time"),
                text.get(Key.AUDIT_START))));
    parameters.add(
        components.reference(
            PARAMETERS,
            "AuditEnd",
            parameter(
                "end",
                "query",
                false,
                ordered("type", "string", "format", "date-time"),
                text.get(Key.AUDIT_END))));
    parameters.add(
        components.reference(
            PARAMETERS,
            "AuditSearch",
            parameter(
                "q",
                "query",
                false,
                ordered("type", "string", "maxLength", 255),
                text.get(Key.AUDIT_SEARCH))));
    Map<String, Object> actions =
        parameter(
            "actions",
            "query",
            false,
            ordered(
                "type",
                "array",
                "items",
                ordered(
                    "type",
                    "string",
                    "enum",
                    Arrays.stream(AuditAction.values()).map(Enum::name).toList())),
            text.get(Key.AUDIT_ACTIONS));
    actions.put("style", "form");
    actions.put("explode", true);
    parameters.add(components.reference(PARAMETERS, "AuditActions", actions));
    return parameters;
  }

  private static Map<String, Object> auditResponses(
      ApiV2OpenApiText text, ApiV2OpenApiComponents components) {
    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put(
        "200",
        components.reference(
            RESPONSES,
            "AuditEvents",
            ordered(
                "description",
                text.get(Key.AUDIT_SUCCESS),
                "headers",
                rateLimitHeaders(),
                "content",
                Map.of(
                    APPLICATION_JSON_VALUE,
                    ordered("schema", auditPage(ref("ApiV2AuditEvent")))))));
    responses.put(
        "400",
        components.reference(
            RESPONSES,
            "AuditInvalidRequest",
            problemResponse(text.get(Key.AUDIT_INVALID), 400, "errors.api.v2.invalidRequest")));
    putStandardResponses(responses, "401", "403", "404", "406", "429", "500");
    responses.put(
        "503",
        components.reference(
            RESPONSES,
            "AuditUnavailable",
            problemResponse(
                text.get(Key.AUDIT_UNAVAILABLE), 503, "errors.api.v2.audit.unavailable")));
    return responses;
  }
}
