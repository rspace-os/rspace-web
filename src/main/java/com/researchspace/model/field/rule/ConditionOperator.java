package com.researchspace.model.field.rule;

import java.util.List;

/** How a display rule compares its source field's selected options. */
public enum ConditionOperator {
  /** At least one selected option is in the rule's values. */
  IS_ANY_OF,
  /** No selected option is in the rule's values; true when nothing is selected. */
  IS_NONE_OF,
  /** At least one option is selected. */
  HAS_VALUE,
  /** Nothing is selected. */
  HAS_NO_VALUE;

  /** Whether a rule with this operator must list option values. */
  public boolean requiresValues() {
    return this == IS_ANY_OF || this == IS_NONE_OF;
  }

  /**
   * Whether the condition holds for a visible source with these selected options.
   *
   * @param selected the source's selected options, empty when it has no value
   * @param values the rule's option values
   */
  boolean test(List<String> selected, List<String> values) {
    return switch (this) {
      case IS_ANY_OF -> selected.stream().anyMatch(values::contains);
      case IS_NONE_OF -> selected.stream().noneMatch(values::contains);
      case HAS_VALUE -> !selected.isEmpty();
      case HAS_NO_VALUE -> selected.isEmpty();
    };
  }
}
