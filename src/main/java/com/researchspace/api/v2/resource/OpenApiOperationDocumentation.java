package com.researchspace.api.v2.resource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Optional developer-supplied OpenAPI refinements for one generated resource operation. */
public record OpenApiOperationDocumentation(
    String summary,
    String description,
    List<String> tags,
    boolean deprecated,
    Object requestExample,
    Object responseExample,
    Map<Integer, Response> responses,
    Map<String, Object> extensions) {

  /** Documentation for one response. Error responses also carry their stable problem codes. */
  public record Response(String description, Map<String, String> errors) {
    public Response(String description, String errorCode) {
      this(description, errorCode == null ? Map.of() : Map.of(errorCode, description));
    }

    public Response {
      if (description == null || description.isBlank()) {
        throw new IllegalArgumentException("OpenAPI response description must not be blank");
      }
      errors = Map.copyOf(errors);
      if (errors.entrySet().stream()
          .anyMatch(
              error ->
                  error.getKey() == null
                      || error.getKey().isBlank()
                      || error.getValue() == null
                      || error.getValue().isBlank())) {
        throw new IllegalArgumentException(
            "OpenAPI error responses require a code and description");
      }
    }

    /** Returns the only error code, or {@code null} when the response has zero or many codes. */
    public String errorCode() {
      return errors.size() == 1 ? errors.keySet().iterator().next() : null;
    }

    Response merge(Response other) {
      Map<String, String> combinedErrors = new LinkedHashMap<>(errors);
      other.errors.forEach(
          (code, errorDescription) -> {
            String existing = combinedErrors.putIfAbsent(code, errorDescription);
            if (existing != null && !existing.equals(errorDescription)) {
              throw new IllegalArgumentException("Conflicting OpenAPI descriptions for " + code);
            }
          });
      String combinedDescription =
          description.equals(other.description)
              ? description
              : description + " " + other.description;
      return new Response(combinedDescription, combinedErrors);
    }
  }

  public static final OpenApiOperationDocumentation EMPTY =
      new OpenApiOperationDocumentation(
          null, null, List.of(), false, null, null, Map.of(), Map.of());

  public OpenApiOperationDocumentation {
    summary = blankToNull(summary);
    description = blankToNull(description);
    tags = List.copyOf(tags);
    responses = Map.copyOf(responses);
    extensions = Map.copyOf(extensions);
    if (tags.stream().anyMatch(tag -> tag == null || tag.isBlank())) {
      throw new IllegalArgumentException("OpenAPI operation tags must not be blank");
    }
    responses.forEach(
        (status, response) -> {
          if (status < 100 || status > 599 || response == null) {
            throw new IllegalArgumentException("OpenAPI responses require a valid HTTP status");
          }
          if (!response.errors().isEmpty() && status < 400) {
            throw new IllegalArgumentException("OpenAPI error response requires an error status");
          }
        });
    if (extensions.keySet().stream().anyMatch(name -> !name.startsWith("x-"))) {
      throw new IllegalArgumentException("OpenAPI operation extensions must start with x-");
    }
  }

  OpenApiOperationDocumentation withResponses(Map<Integer, Response> additionalResponses) {
    if (additionalResponses.isEmpty()) {
      return this;
    }
    Map<Integer, Response> combined = new LinkedHashMap<>(responses);
    additionalResponses.forEach(
        (status, response) -> {
          Response existing = combined.putIfAbsent(status, response);
          if (existing != null && !existing.equals(response)) {
            throw new IllegalArgumentException(
                "Conflicting REST API v2 response definitions for status " + status);
          }
        });
    return new OpenApiOperationDocumentation(
        summary,
        description,
        tags,
        deprecated,
        requestExample,
        responseExample,
        combined,
        extensions);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String summary;
    private String description;
    private final List<String> tags = new ArrayList<>();
    private boolean deprecated;
    private Object requestExample;
    private Object responseExample;
    private final Map<Integer, Response> responses = new LinkedHashMap<>();
    private final Map<String, Object> extensions = new LinkedHashMap<>();

    public Builder summary(String value) {
      summary = value;
      return this;
    }

    public Builder description(String value) {
      description = value;
      return this;
    }

    public Builder tag(String value) {
      tags.add(value);
      return this;
    }

    public Builder deprecated() {
      deprecated = true;
      return this;
    }

    public Builder requestExample(Object value) {
      requestExample = value;
      return this;
    }

    public Builder responseExample(Object value) {
      responseExample = value;
      return this;
    }

    public Builder responseDescription(int status, String value) {
      Response current = responses.get(status);
      responses.put(status, new Response(value, current == null ? Map.of() : current.errors()));
      return this;
    }

    public Builder errorResponse(int status, String code, String description) {
      responses.put(status, new Response(description, code));
      return this;
    }

    public Builder extension(String name, Object value) {
      extensions.put(name, value);
      return this;
    }

    public OpenApiOperationDocumentation build() {
      return new OpenApiOperationDocumentation(
          summary,
          description,
          tags,
          deprecated,
          requestExample,
          responseExample,
          responses,
          extensions);
    }
  }
}
