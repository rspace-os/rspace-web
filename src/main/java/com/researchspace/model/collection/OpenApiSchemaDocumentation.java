package com.researchspace.model.collection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Optional public schema refinements that cannot be inferred from collection execution rules. */
public record OpenApiSchemaDocumentation(
    String title,
    String description,
    String example,
    String pattern,
    String minimum,
    String maximum,
    List<String> enumValues,
    boolean deprecated,
    String format,
    String defaultValue,
    Integer minLength,
    List<String> additionalExamples,
    Map<String, Object> extensions) {

  public static final OpenApiSchemaDocumentation EMPTY =
      new OpenApiSchemaDocumentation(
          null, null, null, null, null, null, List.of(), false, null, null, null, List.of(),
          Map.of());

  public OpenApiSchemaDocumentation(
      String title,
      String description,
      String example,
      String pattern,
      String minimum,
      String maximum,
      List<String> enumValues,
      boolean deprecated) {
    this(
        title,
        description,
        example,
        pattern,
        minimum,
        maximum,
        enumValues,
        deprecated,
        null,
        null,
        null,
        List.of(),
        Map.of());
  }

  public OpenApiSchemaDocumentation {
    title = blankToNull(title);
    description = blankToNull(description);
    example = blankToNull(example);
    pattern = blankToNull(pattern);
    minimum = blankToNull(minimum);
    maximum = blankToNull(maximum);
    format = blankToNull(format);
    defaultValue = blankToNull(defaultValue);
    enumValues = List.copyOf(Objects.requireNonNull(enumValues, "OpenAPI enum values"));
    additionalExamples =
        List.copyOf(Objects.requireNonNull(additionalExamples, "OpenAPI additional examples"));
    extensions = Map.copyOf(Objects.requireNonNull(extensions, "OpenAPI schema extensions"));
    if (pattern != null) {
      Pattern.compile(pattern);
    }
    if (minimum != null) {
      new BigDecimal(minimum);
    }
    if (maximum != null) {
      new BigDecimal(maximum);
    }
    if (enumValues.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException("OpenAPI enum values must not be blank");
    }
    if (additionalExamples.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException("OpenAPI examples must not be blank");
    }
    if (minLength != null && minLength < 0) {
      throw new IllegalArgumentException("OpenAPI minimum length must not be negative");
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

  public static final class Builder {
    private String title;
    private String description;
    private String example;
    private String pattern;
    private String minimum;
    private String maximum;
    private final List<String> enumValues = new ArrayList<>();
    private boolean deprecated;
    private String format;
    private String defaultValue;
    private Integer minLength;
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

    public Builder pattern(String value) {
      pattern = value;
      return this;
    }

    public Builder minimum(String value) {
      minimum = value;
      return this;
    }

    public Builder maximum(String value) {
      maximum = value;
      return this;
    }

    public Builder enumValue(String value) {
      enumValues.add(value);
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

    public Builder defaultValue(String value) {
      defaultValue = value;
      return this;
    }

    public Builder minLength(int value) {
      minLength = value;
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
          title,
          description,
          example,
          pattern,
          minimum,
          maximum,
          enumValues,
          deprecated,
          format,
          defaultValue,
          minLength,
          additionalExamples,
          extensions);
    }
  }
}
