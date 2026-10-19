package com.researchspace.model.collection;

import java.util.Set;

/** Parsing and wire metadata for a runtime value stored in a shared text column. */
public interface RuntimeFieldType {
  int MAX_VALUE_LENGTH = 1000;

  String jsonType();

  Set<Operator> operators();

  boolean supportsWildcards();

  FieldComparisonStrategy comparisonStrategy();

  /** Validates and parses a filter literal without changing its storage representation. */
  Object parse(String value);

  /** Projects a stored value into its JSON shape; null and empty strings represent absence. */
  Object serialize(String stored);
}
