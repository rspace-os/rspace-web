import { type FieldDisplayRule, type FieldSnapshot, sourceKindOf } from "./displayRule";
import { isBlankRuleValue, normaliseRuleText, readRuleNumber } from "./ruleValues";
import { ruleProblem } from "./validateDisplayRules";

function optionsHold({ operator, values }: FieldDisplayRule, selected: ReadonlyArray<string>): boolean {
  switch (operator) {
    case "IS_ANY_OF":
      return selected.some((option) => values.includes(option));
    case "IS_NONE_OF":
      return !selected.some((option) => values.includes(option));
    case "HAS_VALUE":
      return selected.length > 0;
    case "HAS_NO_VALUE":
      return selected.length === 0;
    default:
      throw new Error(`${operator} cannot read options`);
  }
}

function textHolds({ operator, values }: FieldDisplayRule, value: string): boolean {
  if (operator === "HAS_VALUE") return !isBlankRuleValue(value);
  if (operator === "HAS_NO_VALUE") return isBlankRuleValue(value);
  const text = normaliseRuleText(value);
  const operand = normaliseRuleText(values[0]);
  switch (operator) {
    case "EQUALS":
      return text === operand;
    case "NOT_EQUALS":
      return text !== operand;
    case "CONTAINS":
      return text.includes(operand);
    default:
      throw new Error(`${operator} cannot read text`);
  }
}

/** A blank value or one that is not a number fails every comparison but NOT_EQUALS. */
function numberHolds({ operator, values }: FieldDisplayRule, value: string): boolean {
  if (operator === "HAS_VALUE") return !isBlankRuleValue(value);
  if (operator === "HAS_NO_VALUE") return isBlankRuleValue(value);
  const number = readRuleNumber(value);
  if (number === null) return operator === "NOT_EQUALS";
  const comparison = number.comparedTo(values[0]);
  switch (operator) {
    case "EQUALS":
      return comparison === 0;
    case "NOT_EQUALS":
      return comparison !== 0;
    case "LESS_THAN":
      return comparison < 0;
    case "AT_MOST":
      return comparison <= 0;
    case "GREATER_THAN":
      return comparison > 0;
    case "AT_LEAST":
      return comparison >= 0;
    default:
      throw new Error(`${operator} cannot read numbers`);
  }
}

/** Whether an applicable rule's condition holds for a visible source. */
function conditionHolds(rule: FieldDisplayRule, source: FieldSnapshot): boolean {
  const kind = sourceKindOf(source);
  switch (kind) {
    case "OPTIONS":
      return optionsHold(rule, source.selectedOptions);
    case "TEXT":
      return textHolds(rule, source.value ?? "");
    case "NUMBER":
      return numberHolds(rule, source.value ?? "");
    case "NONE":
      throw new Error("A rule cannot read a field of kind NONE");
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
 *  - a field whose rule cannot apply to its source is shown, as if the source
 *    were missing, whatever the source's own visibility. That is a rule the
 *    validator rejects: its operator does not suit the source's kind, or its
 *    comparison value is missing, blank or not a number. Views that skip
 *    validation then neither fail nor hide data;
 *  - a field whose source is hidden is hidden, whatever the operator;
 *  - otherwise the operator is applied to the source's value;
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
      if (rule === null || source === undefined || ruleProblem(rule, sourceKindOf(fields[source])) !== null) {
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
      visible[index] = visible[source] === true && conditionHolds(rule, fields[source]);
    }
  }
  return visible.map((shown) => shown === true);
}
