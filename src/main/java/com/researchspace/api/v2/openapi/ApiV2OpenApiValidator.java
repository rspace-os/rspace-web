package com.researchspace.api.v2.openapi;

import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApiV2OpenApiValidator {

  private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([^}/]+)}");

  private ApiV2OpenApiValidator() {}

  public static void validate(OpenAPI document) {
    if (!"3.1.0".equals(document.getOpenapi())) {
      throw new IllegalArgumentException("REST API v2 must generate OpenAPI 3.1.0");
    }
    Paths paths = require(document.getPaths(), "OpenAPI paths");
    Components components = require(document.getComponents(), "OpenAPI components");
    Map<String, ?> schemas = require(components.getSchemas(), "OpenAPI schemas");
    Map<String, ?> responses = require(components.getResponses(), "OpenAPI responses");
    Map<String, ?> headers = require(components.getHeaders(), "OpenAPI headers");
    Map<String, ?> securitySchemes =
        require(components.getSecuritySchemes(), "OpenAPI security schemes");
    Set<String> operationIds = new HashSet<>();
    paths.forEach(
        (path, pathItem) -> {
          if (!path.startsWith("/")) {
            throw new IllegalArgumentException("OpenAPI paths must start with '/': " + path);
          }
          require(pathItem, "OpenAPI path item")
              .readOperations()
              .forEach(
                  operation ->
                      validateOperation(path, operation, operationIds, securitySchemes.keySet()));
        });
    validateReferences(
        Json31.mapper().convertValue(document, Object.class),
        schemas.keySet(),
        responses.keySet(),
        headers.keySet());
    validateWithSwaggerCore(document);
  }

  private static void validateOperation(
      String path, Operation operation, Set<String> operationIds, Set<String> securitySchemes) {
    String operationId = operation.getOperationId();
    if (operationId == null || operationId.isBlank() || !operationIds.add(operationId)) {
      throw new IllegalArgumentException(
          "OpenAPI operation IDs must be present and unique: " + operationId);
    }
    Map<String, ?> responses = require(operation.getResponses(), "OpenAPI operation responses");
    if (responses.isEmpty()
        || responses.keySet().stream()
            .anyMatch(code -> !code.equals("default") && !code.matches("[1-5](?:\\d\\d|XX)"))) {
      throw new IllegalArgumentException(
          "OpenAPI operation responses must use valid status codes: " + operationId);
    }
    List<Parameter> parameters =
        operation.getParameters() == null ? List.of() : operation.getParameters();
    Set<String> declared = new HashSet<>();
    for (Parameter parameter : parameters) {
      String name = String.valueOf(parameter.getName());
      String location = String.valueOf(parameter.getIn());
      if (!declared.add(location + ":" + name)) {
        throw new IllegalArgumentException(
            "Duplicate OpenAPI parameter " + location + ":" + name + " on " + operationId);
      }
      if (location.equals("path") && !Boolean.TRUE.equals(parameter.getRequired())) {
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
    if (operation.getSecurity() != null) {
      operation.getSecurity().stream()
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

  private static void validateWithSwaggerCore(OpenAPI document) {
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

  private static <T> T require(T value, String label) {
    if (value == null) {
      throw new IllegalArgumentException(label + " must be present");
    }
    return value;
  }
}
