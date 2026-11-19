import type { ConditionOperator, FieldDisplayRule, FieldSnapshot } from "./displayRule";

function conditionHolds(
  operator: ConditionOperator,
  selected: ReadonlyArray<string>,
  values: ReadonlyArray<string>,
): boolean {
  switch (operator) {
    case "IS_ANY_OF":
      return selected.some((option) => values.includes(option));
    case "IS_NONE_OF":
      return !selected.some((option) => values.includes(option));
    case "HAS_VALUE":
      return selected.length > 0;
    case "HAS_NO_VALUE":
      return selected.length === 0;
  }
}

/** First index of each non-null key. */
function indexByKey(fields: ReadonlyArray<FieldSnapshot>): Map<string, number> {
  const index = new Map<string, number>();
  fields.forEach(({ key }, i) => {
    if (key !== null && !index.has(key)) index.set(key, i);
  });
  return index;
}

/**
 * Visibility of each field, in input order. Same semantics as the Java
 * `FieldDisplayRuleEvaluator`:
 *  - a field without a rule is shown;
 *  - a field whose source key matches no field is shown, so a dangling rule
 *    never hides data;
 *  - a field whose source is hidden is hidden, whatever the operator;
 *  - otherwise the operator is applied to the source's selected options;
 *  - fields on a dependency cycle, and fields depending on them, are hidden
 *    (valid definitions cannot contain cycles; this only guards against
 *    corrupt data).
 * When several fields share a key, the first one is the source. Runs in
 * linear time without recursion, so chain length is not limited by stack depth.
 */
export function displayRuleVisibility(fields: ReadonlyArray<FieldSnapshot>): Array<boolean> {
  const sources = indexByKey(fields);
  const visible: Array<boolean | undefined> = new Array(fields.length).fill(undefined);

  for (let start = 0; start < fields.length; start++) {
    // Walk sources until reaching a field whose visibility is known or can be
    // decided on its own, then resolve the walked fields in reverse.
    const path: Array<{ index: number; source: number; rule: FieldDisplayRule }> = [];
    const onPath = new Set<number>();
    let current = start;
    while (visible[current] === undefined) {
      const { rule } = fields[current];
      const source = rule === null ? undefined : sources.get(rule.sourceFieldKey);
      if (rule === null || source === undefined) {
        visible[current] = true;
      } else if (source === current || onPath.has(source)) {
        visible[current] = false;
      } else {
        path.push({ index: current, source, rule });
        onPath.add(current);
        current = source;
      }
    }
    for (let i = path.length - 1; i >= 0; i--) {
      const { index, source, rule } = path[i];
      visible[index] =
        visible[source] === true && conditionHolds(rule.operator, fields[source].selectedOptions, rule.values);
    }
  }
  return visible.map((shown) => shown === true);
}
