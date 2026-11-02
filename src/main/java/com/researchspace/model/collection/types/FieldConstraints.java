package com.researchspace.model.collection.types;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/** Write-only scalar constraints published on request-body properties. */
public record FieldConstraints(
    BigDecimal minimum,
    BigDecimal maximum,
    Integer minLength,
    Pattern pattern,
    List<String> allowed) {

  public FieldConstraints {
    if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
      throw new IllegalArgumentException("Minimum must not exceed maximum");
    }
    if (minLength != null && minLength < 0) {
      throw new IllegalArgumentException("Minimum length must not be negative");
    }
    if (pattern != null && pattern.flags() != 0) {
      throw new IllegalArgumentException("Pattern flags cannot be published");
    }
    if (allowed != null) {
      allowed = List.copyOf(allowed);
      if (allowed.isEmpty() || allowed.stream().anyMatch(String::isBlank)) {
        throw new IllegalArgumentException("Allowed values must not be empty or blank");
      }
    }
  }

  public boolean isEmpty() {
    return minimum == null
        && maximum == null
        && minLength == null
        && pattern == null
        && allowed == null;
  }
}
