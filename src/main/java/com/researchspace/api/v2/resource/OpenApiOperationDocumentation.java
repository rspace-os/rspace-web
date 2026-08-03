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
    Map<Integer, String> responseDescriptions,
    Map<String, Object> extensions) {

  public static final OpenApiOperationDocumentation EMPTY =
      new OpenApiOperationDocumentation(
          null, null, List.of(), false, null, null, Map.of(), Map.of());

  public OpenApiOperationDocumentation {
    summary = blankToNull(summary);
    description = blankToNull(description);
    tags = List.copyOf(tags);
    responseDescriptions = Map.copyOf(responseDescriptions);
    extensions = Map.copyOf(extensions);
    if (tags.stream().anyMatch(tag -> tag == null || tag.isBlank())) {
      throw new IllegalArgumentException("OpenAPI operation tags must not be blank");
    }
    responseDescriptions.forEach(
        (status, text) -> {
          if (status < 100 || status > 599 || text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                "OpenAPI response descriptions require a valid HTTP status and text");
          }
        });
    if (extensions.keySet().stream().anyMatch(name -> !name.startsWith("x-"))) {
      throw new IllegalArgumentException("OpenAPI operation extensions must start with x-");
    }
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
    private final Map<Integer, String> responseDescriptions = new LinkedHashMap<>();
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
      responseDescriptions.put(status, value);
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
          responseDescriptions,
          extensions);
    }
  }
}
