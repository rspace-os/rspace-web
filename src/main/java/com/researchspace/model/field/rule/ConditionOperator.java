package com.researchspace.model.field.rule;

import static com.researchspace.model.field.rule.SourceKind.NUMBER;
import static com.researchspace.model.field.rule.SourceKind.OPTIONS;
import static com.researchspace.model.field.rule.SourceKind.TEXT;

import java.util.Set;

/** How a display rule compares its source field's value. */
public enum ConditionOperator {
  /** At least one selected option is in the rule's values. */
  IS_ANY_OF(OPTIONS),
  /** No selected option is in the rule's values; true when nothing is selected. */
  IS_NONE_OF(OPTIONS),
  /** The source has a value: an option is selected, or the text is not blank. */
  HAS_VALUE(OPTIONS, TEXT, NUMBER),
  /** The source has no value. */
  HAS_NO_VALUE(OPTIONS, TEXT, NUMBER),
  /** The source's text or number equals the rule's value. */
  EQUALS(TEXT, NUMBER),
  /** The source's text or number does not equal the rule's value; true when it has none. */
  NOT_EQUALS(TEXT, NUMBER),
  /** The source's text contains the rule's value. */
  CONTAINS(TEXT),
  /** The source's number is less than the rule's value. */
  LESS_THAN(NUMBER),
  /** The source's number is less than or equal to the rule's value. */
  AT_MOST(NUMBER),
  /** The source's number is greater than the rule's value. */
  GREATER_THAN(NUMBER),
  /** The source's number is greater than or equal to the rule's value. */
  AT_LEAST(NUMBER);

  private final Set<SourceKind> sourceKinds;

  ConditionOperator(SourceKind... sourceKinds) {
    this.sourceKinds = Set.of(sourceKinds);
  }

  /** Whether a rule with this operator must list option values. */
  public boolean requiresValues() {
    return this == IS_ANY_OF || this == IS_NONE_OF;
  }

  /** Whether a rule with this operator takes no values. */
  public boolean takesNoValues() {
    return this == HAS_VALUE || this == HAS_NO_VALUE;
  }

  /** Whether this operator compares the source with exactly one text or number value. */
  public boolean comparesValue() {
    return !requiresValues() && !takesNoValues();
  }

  /** Whether a rule with this operator can read a source of this kind. */
  public boolean appliesTo(SourceKind kind) {
    return sourceKinds.contains(kind);
  }
}
