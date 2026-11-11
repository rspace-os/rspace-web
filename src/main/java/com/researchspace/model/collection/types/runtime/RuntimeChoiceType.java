package com.researchspace.model.collection.types.runtime;

import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.collection.FieldComparisonStrategy;
import com.researchspace.model.collection.Operator;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Multiple runtime choices stored as a JSON array or a legacy single option. */
public final class RuntimeChoiceType extends RuntimeStoredTextType {
  private static final Set<Operator> OPERATORS =
      Set.of(Operator.CONTAINS, Operator.IN, Operator.EXISTS);

  @Override
  public String jsonType() {
    return "array";
  }

  @Override
  public Set<Operator> operators() {
    return OPERATORS;
  }

  @Override
  public FieldComparisonStrategy comparisonStrategy() {
    return FieldComparisonStrategy.CHOICE_TEXT;
  }

  @Override
  protected Object serializeValue(String stored) {
    String trimmed = stored.trim();
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
      return List.of(stored);
    }
    String[] options = JacksonUtil.fromJson(trimmed, String[].class);
    if (options == null) {
      throw new IllegalArgumentException("Couldn't convert stored choice options to an array");
    }
    return Arrays.asList(options);
  }
}
