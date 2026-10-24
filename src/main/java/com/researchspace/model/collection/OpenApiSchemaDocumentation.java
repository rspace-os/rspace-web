package com.researchspace.model.collection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Optional public schema refinements that cannot be inferred from collection execution rules.
 *
 * <p>Documentation only: rules the server enforces, such as bounds, patterns, allowed values and
 * defaults, belong on the field and its type so that publishing them never outruns enforcement.
 */
public record OpenApiSchemaDocumentation(
    String title,
    String description,
    String example,
    boolean deprecated,
    String format,
    List<String> additionalExamples,
    Map<String, Object> extensions) {

  public static final OpenApiSchemaDocumentation EMPTY =
      new OpenApiSchemaDocumentation(null, null, null, false, null, List.of(), Map.of());

  public OpenApiSchemaDocumentation {
    title = blankToNull(title);
    description = blankToNull(description);
    example = blankToNull(example);
    format = blankToNull(format);
    additionalExamples =
        List.copyOf(Objects.requireNonNull(additionalExamples, "OpenAPI additional examples"));
    extensions = Map.copyOf(Objects.requireNonNull(extensions, "OpenAPI schema extensions"));
    if (additionalExamples.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException("OpenAPI examples must not be blank");
    }
    if (extensions.keySet().stream().anyMatch(name -> !name.startsWith("x-"))) {
      throw new IllegalArgumentException("OpenAPI schema extensions must start with x-");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The common case: a title, a description and one example. */
  public static OpenApiSchemaDocumentation of(String title, String description, String example) {
    return builder().title(title).description(description).example(example).build();
  }

  public static final class Builder {
    private String title;
    private String description;
    private String example;
    private boolean deprecated;
    private String format;
    private final List<String> additionalExamples = new ArrayList<>();
    private final Map<String, Object> extensions = new LinkedHashMap<>();

    public Builder title(String value) {
      title = value;
      return this;
    }

    public Builder description(String value) {
      description = value;
      return this;
    }

    public Builder example(String value) {
      example = value;
      return this;
    }

    public Builder deprecated() {
      deprecated = true;
      return this;
    }

    public Builder format(String value) {
      format = value;
      return this;
    }

    public Builder additionalExample(String value) {
      additionalExamples.add(value);
      return this;
    }

    public Builder extension(String name, Object value) {
      extensions.put(name, value);
      return this;
    }

    public OpenApiSchemaDocumentation build() {
      return new OpenApiSchemaDocumentation(
          title, description, example, deprecated, format, additionalExamples, extensions);
    }
  }
}
