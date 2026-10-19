package com.researchspace.model.collection;

/** Storage comparison identity; adapters supply SQL without coupling model types to a DAO. */
public record FieldComparisonStrategy(String id) {
  public static final FieldComparisonStrategy SCALAR = new FieldComparisonStrategy("scalar");
  public static final FieldComparisonStrategy NUMERIC_TEXT =
      new FieldComparisonStrategy("numeric-text");
  public static final FieldComparisonStrategy CHOICE_TEXT =
      new FieldComparisonStrategy("choice-text");
  public static final FieldComparisonStrategy NONE = new FieldComparisonStrategy("none");

  public FieldComparisonStrategy {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Comparison strategy ID must not be blank");
    }
  }
}
