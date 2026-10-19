package com.researchspace.model.collection.types.runtime;

import com.researchspace.model.collection.FieldComparisonStrategy;
import com.researchspace.model.collection.Operator;
import java.util.Set;

/** Free text runtime values support literal and wildcard matching. */
public final class RuntimeTextType extends RuntimeStoredTextType {
  private static final Set<Operator> OPERATORS =
      Set.of(
          Operator.EQUAL,
          Operator.NOT_EQUAL,
          Operator.IN,
          Operator.NOT_IN,
          Operator.CONTAINS,
          Operator.LIKE,
          Operator.EXISTS);

  @Override
  public String jsonType() {
    return "string";
  }

  @Override
  public Set<Operator> operators() {
    return OPERATORS;
  }

  @Override
  public boolean supportsWildcards() {
    return true;
  }

  @Override
  public FieldComparisonStrategy comparisonStrategy() {
    return FieldComparisonStrategy.SCALAR;
  }
}
