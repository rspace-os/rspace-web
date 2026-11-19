package com.researchspace.model.field.rule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which fields of a form, template or record are shown.
 *
 * <p>The TypeScript evaluator in {@code modules/common/field-rules} implements the same semantics;
 * both run the shared vectors in {@code src/test/resources/fieldrules}.
 */
public final class FieldDisplayRuleEvaluator {

  /** A field walked on the way to a resolved source, with the source and rule it depends on. */
  private record Step(int index, int source, FieldDisplayRule rule) {}

  private FieldDisplayRuleEvaluator() {}

  /**
   * Visibility of each field, in input order.
   *
   * <ul>
   *   <li>A field without a rule is shown.
   *   <li>A field whose source key matches no field is shown, so a dangling rule never hides data.
   *   <li>A field whose rule cannot apply to its source is shown, as if the source were missing,
   *       whatever the source's own visibility. That is a rule the validator rejects: its operator
   *       does not suit the source's kind, or its comparison value is missing, blank or not a
   *       number. Views that skip validation then neither fail nor hide data.
   *   <li>A field whose source is hidden is hidden, whatever the operator.
   *   <li>Otherwise the operator is applied to the source's value.
   *   <li>Fields on a dependency cycle, and fields depending on them, are hidden. Valid definitions
   *       cannot contain cycles; this only guards against corrupt data.
   * </ul>
   *
   * <p>When several fields share a key, the first one is the source. Runs in linear time without
   * recursion, so chain length is not limited by stack depth.
   */
  public static List<Boolean> visibility(List<FieldSnapshot> fields) {
    Map<String, Integer> indexByKey = indexByKey(fields);
    Boolean[] visible = new Boolean[fields.size()];
    for (int start = 0; start < fields.size(); start++) {
      // Walk sources until reaching a field whose visibility is known or can be decided on its
      // own, then resolve the walked fields in reverse.
      List<Step> path = new ArrayList<>();
      Set<Integer> onPath = new HashSet<>();
      int current = start;
      while (visible[current] == null) {
        FieldDisplayRule rule = fields.get(current).rule();
        Integer source = rule == null ? null : indexByKey.get(rule.sourceFieldKey());
        if (source == null || RuleValues.problem(rule, fields.get(source).sourceKind()) != null) {
          visible[current] = true;
        } else if (source == current || onPath.contains(source)) {
          visible[current] = false;
        } else {
          path.add(new Step(current, source, rule));
          onPath.add(current);
          current = source;
        }
      }
      for (int i = path.size() - 1; i >= 0; i--) {
        Step step = path.get(i);
        visible[step.index()] =
            visible[step.source()] && holds(step.rule(), fields.get(step.source()));
      }
    }
    return List.of(visible);
  }

  /** Whether an applicable rule's condition holds for a visible source. */
  private static boolean holds(FieldDisplayRule rule, FieldSnapshot source) {
    return switch (source.sourceKind()) {
      case OPTIONS -> optionsHold(rule, source.selectedOptions());
      case TEXT -> textHolds(rule, source.value());
      case NUMBER -> numberHolds(rule, source.value());
      case NONE -> throw new IllegalStateException("A rule cannot read a field of kind NONE");
    };
  }

  private static boolean optionsHold(FieldDisplayRule rule, List<String> selected) {
    return switch (rule.operator()) {
      case IS_ANY_OF -> selected.stream().anyMatch(rule.values()::contains);
      case IS_NONE_OF -> selected.stream().noneMatch(rule.values()::contains);
      case HAS_VALUE -> !selected.isEmpty();
      case HAS_NO_VALUE -> selected.isEmpty();
      default -> throw new IllegalStateException(rule.operator() + " cannot read options");
    };
  }

  private static boolean textHolds(FieldDisplayRule rule, String value) {
    String text = RuleValues.normaliseText(value);
    return switch (rule.operator()) {
      case HAS_VALUE -> !RuleValues.isBlank(value);
      case HAS_NO_VALUE -> RuleValues.isBlank(value);
      case EQUALS -> text.equals(RuleValues.normaliseText(rule.values().get(0)));
      case NOT_EQUALS -> !text.equals(RuleValues.normaliseText(rule.values().get(0)));
      case CONTAINS -> text.contains(RuleValues.normaliseText(rule.values().get(0)));
      default -> throw new IllegalStateException(rule.operator() + " cannot read text");
    };
  }

  /** A blank value or one that is not a number fails every comparison but NOT_EQUALS. */
  private static boolean numberHolds(FieldDisplayRule rule, String value) {
    switch (rule.operator()) {
      case HAS_VALUE:
        return !RuleValues.isBlank(value);
      case HAS_NO_VALUE:
        return RuleValues.isBlank(value);
      default:
        break;
    }
    BigDecimal number = RuleValues.readNumber(value);
    if (number == null) {
      return rule.operator() == ConditionOperator.NOT_EQUALS;
    }
    int comparison = number.compareTo(new BigDecimal(rule.values().get(0)));
    return switch (rule.operator()) {
      case EQUALS -> comparison == 0;
      case NOT_EQUALS -> comparison != 0;
      case LESS_THAN -> comparison < 0;
      case AT_MOST -> comparison <= 0;
      case GREATER_THAN -> comparison > 0;
      case AT_LEAST -> comparison >= 0;
      default -> throw new IllegalStateException(rule.operator() + " cannot read numbers");
    };
  }

  /** First index of each non-null key. */
  private static Map<String, Integer> indexByKey(List<FieldSnapshot> fields) {
    Map<String, Integer> indexByKey = new HashMap<>();
    for (int i = 0; i < fields.size(); i++) {
      String key = fields.get(i).key();
      if (key != null) {
        indexByKey.putIfAbsent(key, i);
      }
    }
    return indexByKey;
  }
}
