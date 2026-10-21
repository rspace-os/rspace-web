package com.researchspace.model.collection.types.runtime;

import com.researchspace.model.collection.FieldComparisonStrategy;
import com.researchspace.model.collection.Operator;
import java.util.Set;

/** A single selected runtime option compares using its stored text. */
public final class RuntimeRadioType extends RuntimeStoredTextType {
  private static final Set<Operator> OPERATORS =
      Set.of(Operator.EQUAL, Operator.NOT_EQUAL, Operator.IN, Operator.NOT_IN, Operator.EXISTS);

  @Override
  public String jsonType() {
    return "string";
  }

  @Override
  public Set<Operator> operators() {
    return OPERATORS;
  }

  @Override
  public FieldComparisonStrategy comparisonStrategy() {
    return FieldComparisonStrategy.SCALAR;
  }
}
