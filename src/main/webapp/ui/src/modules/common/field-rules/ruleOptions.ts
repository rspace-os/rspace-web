import { createDisplayRule, type FieldDisplayRule, operatorRequiresValues } from "./displayRule";

/**
 * The rule without the removed options. A rule that names no option is kept;
 * a rule left without any of its options is dropped (null).
 */
export function withoutOptions(rule: FieldDisplayRule, removedOptions: ReadonlyArray<string>): FieldDisplayRule | null {
  if (!operatorRequiresValues(rule.operator)) return rule;
  const values = rule.values.filter((value) => !removedOptions.includes(value));
  if (values.length === rule.values.length) return rule;
  if (values.length === 0) return null;
  return createDisplayRule(rule.sourceFieldKey, rule.operator, values);
}
