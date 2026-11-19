package com.researchspace.model.field.rule;

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
   *   <li>A field whose source is hidden is hidden, whatever the operator.
   *   <li>Otherwise the operator is applied to the source's selected options.
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
        if (source == null) {
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
            visible[step.source()]
                && step.rule()
                    .operator()
                    .test(fields.get(step.source()).selectedOptions(), step.rule().values());
      }
    }
    return List.of(visible);
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
