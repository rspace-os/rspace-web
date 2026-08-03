package com.researchspace.api.v2.openapi;

import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApiV2OpenApiValidator {

  private static final Set<String> HTTP_METHODS =
      Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
  private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([^}/]+)}");

  private ApiV2OpenApiValidator() {}

  public static void validate(Map<String, Object> document) {
    if (!"3.1.0".equals(document.get("openapi"))) {
      throw new IllegalArgumentException("REST API v2 must generate OpenAPI 3.1.0");
    }
    Map<String, Object> paths = objectMap(document.get("paths"), "OpenAPI paths");
    Map<String, Object> components = objectMap(document.get("components"), "OpenAPI components");
    Map<String, Object> schemas = objectMap(components.get("schemas"), "OpenAPI schemas");
    Map<String, Object> responses = objectMap(components.get("responses"), "OpenAPI responses");
    Map<String, Object> headers = objectMap(components.get("headers"), "OpenAPI headers");
    Map<String, Object> securitySchemes =
        objectMap(components.get("securitySchemes"), "OpenAPI security schemes");
    Set<String> operationIds = new HashSet<>();
    paths.forEach(
        (path, rawPathItem) -> {
          if (!path.startsWith("/")) {
            throw new IllegalArgumentException("OpenAPI paths must start with '/': " + path);
          }
          Map<String, Object> pathItem = objectMap(rawPathItem, "OpenAPI path item");
          pathItem.forEach(
              (method, rawOperation) -> {
                if (!HTTP_METHODS.contains(method)) {
                  return;
                }
                Map<String, Object> operation = objectMap(rawOperation, "OpenAPI operation");
                validateOperation(path, operation, operationIds, securitySchemes.keySet());
              });
        });
    validateReferences(document, schemas.keySet(), responses.keySet(), headers.keySet());
    validateWithSwaggerCore(document);
  }

  private static void validateOperation(
      String path,
      Map<String, Object> operation,
      Set<String> operationIds,
      Set<String> securitySchemes) {
    Object id = operation.get("operationId");
    if (!(id instanceof String operationId)
        || operationId.isBlank()
        || !operationIds.add(operationId)) {
      throw new IllegalArgumentException("OpenAPI operation IDs must be present and unique: " + id);
    }
    Map<String, Object> responses =
        objectMap(operation.get("responses"), "OpenAPI operation responses");
    if (responses.isEmpty()
        || responses.keySet().stream()
            .anyMatch(code -> !code.equals("default") && !code.matches("[1-5](?:\\d\\d|XX)"))) {
      throw new IllegalArgumentException(
          "OpenAPI operation responses must use valid status codes: " + operationId);
    }
    List<?> parameters = operation.get("parameters") instanceof List<?> values ? values : List.of();
    Set<String> declared = new HashSet<>();
    for (Object rawParameter : parameters) {
      Map<String, Object> parameter = objectMap(rawParameter, "OpenAPI parameter");
      String name = String.valueOf(parameter.get("name"));
      String location = String.valueOf(parameter.get("in"));
      if (!declared.add(location + ":" + name)) {
        throw new IllegalArgumentException(
            "Duplicate OpenAPI parameter " + location + ":" + name + " on " + operationId);
      }
      if (location.equals("path") && !Boolean.TRUE.equals(parameter.get("required"))) {
        throw new IllegalArgumentException("OpenAPI path parameters must be required: " + name);
      }
    }
    Matcher matcher = PATH_PARAMETER.matcher(path);
    while (matcher.find()) {
      if (!declared.contains("path:" + matcher.group(1))) {
        throw new IllegalArgumentException(
            "OpenAPI path parameter is not declared: " + matcher.group(1));
      }
    }
    if (operation.get("security") instanceof List<?> requirements) {
      requirements.stream()
          .map(requirement -> objectMap(requirement, "OpenAPI security requirement"))
          .flatMap(requirement -> requirement.keySet().stream())
          .filter(name -> !securitySchemes.contains(name))
          .findFirst()
          .ifPresent(
              name -> {
                throw new IllegalArgumentException(
                    "OpenAPI operation references an unknown security scheme: " + name);
              });
    }
  }

  private static void validateReferences(
      Object value, Set<String> schemas, Set<String> responses, Set<String> headers) {
    if (value instanceof Map<?, ?> map) {
      Object reference = map.get("$ref");
      if (reference instanceof String ref) {
        validateReference(ref, schemas, responses, headers);
      }
      map.values().forEach(child -> validateReferences(child, schemas, responses, headers));
    } else if (value instanceof List<?> list) {
      list.forEach(child -> validateReferences(child, schemas, responses, headers));
    }
  }

  private static void validateReference(
      String reference, Set<String> schemas, Set<String> responses, Set<String> headers) {
    String schemaPrefix = "#/components/schemas/";
    String responsePrefix = "#/components/responses/";
    String headerPrefix = "#/components/headers/";
    boolean valid =
        (reference.startsWith(schemaPrefix)
                && schemas.contains(reference.substring(schemaPrefix.length())))
            || (reference.startsWith(responsePrefix)
                && responses.contains(reference.substring(responsePrefix.length())))
            || (reference.startsWith(headerPrefix)
                && headers.contains(reference.substring(headerPrefix.length())));
    if (!valid) {
      throw new IllegalArgumentException("Unresolved OpenAPI reference " + reference);
    }
  }

  private static void validateWithSwaggerCore(Map<String, Object> document) {
    try {
      OpenAPI parsed =
          Json31.mapper().readValue(Json31.mapper().writeValueAsBytes(document), OpenAPI.class);
      if (parsed.getInfo() == null || parsed.getPaths() == null || parsed.getComponents() == null) {
        throw new IllegalArgumentException("Generated OpenAPI document is incomplete");
      }
    } catch (IOException | IllegalArgumentException ex) {
      throw new IllegalArgumentException("Generated document is not valid OpenAPI 3.1", ex);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> objectMap(Object value, String label) {
    if (!(value instanceof Map<?, ?>)) {
      throw new IllegalArgumentException(label + " must be an object");
    }
    return (Map<String, Object>) value;
  }
}
