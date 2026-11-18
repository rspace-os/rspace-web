/**
 * Plain helpers for editing display rules across a whole form or template.
 * They know nothing about Inventory or ELN models: each product maps its
 * fields to `RuleEditorField` and applies the returned `RuleChange`s itself.
 */
import {
  type ConditionOperator,
  createDisplayRule,
  type FieldDisplayRule,
  operatorRequiresValues,
} from "@/modules/common/field-rules/displayRule";

/** One field as the rule editor sees it. */
export type RuleEditorField = {
  /** The field key; rules reference fields by key. */
  readonly key: string;
  readonly name: string;
  /** Whether the field is a radio or choice field, the only types a rule can read. */
  readonly canBeSource: boolean;
  /** The options a rule may name, already passed through {@link usableOptions}. */
  readonly options: ReadonlyArray<string>;
  readonly rule: FieldDisplayRule | null;
};

/** A new rule for one field; null clears the field's rule. */
export type RuleChange = {
  readonly key: string;
  readonly rule: FieldDisplayRule | null;
};

/** Fields whose stored rules are identical, shown as one rule with several targets. */
export type RuleGroup = {
  /** Stable while the group's first target stays the same. */
  readonly id: string;
  readonly rule: FieldDisplayRule;
  readonly targetKeys: ReadonlyArray<string>;
};

/** Options a rule may use: blank options and repeats of an earlier option are left out. */
export function usableOptions(options: Iterable<string>): Array<string> {
  const seen = new Set<string>();
  for (const option of options) {
    if (!/^\s*$/.test(option)) seen.add(option);
  }
  return [...seen];
}

function ruleSignature(rule: FieldDisplayRule): string {
  return JSON.stringify([rule.sourceFieldKey, rule.operator, [...rule.values].sort()]);
}

export function groupDisplayRules(fields: ReadonlyArray<RuleEditorField>): Array<RuleGroup> {
  const groups = new Map<string, { id: string; rule: FieldDisplayRule; targetKeys: Array<string> }>();
  for (const field of fields) {
    if (field.rule === null) continue;
    const signature = ruleSignature(field.rule);
    const group = groups.get(signature);
    if (group) group.targetKeys.push(field.key);
    else groups.set(signature, { id: field.key, rule: field.rule, targetKeys: [field.key] });
  }
  return [...groups.values()];
}

/** Whether the field with `fieldKey` is shown only through a chain of rules that reads `ancestorKey`. */
export function dependsOn(fields: ReadonlyArray<RuleEditorField>, fieldKey: string, ancestorKey: string): boolean {
  const byKey = new Map(fields.map((field) => [field.key, field]));
  const visited = new Set<string>();
  let current = byKey.get(fieldKey);
  while (current?.rule && !visited.has(current.key)) {
    visited.add(current.key);
    if (current.rule.sourceFieldKey === ancestorKey) return true;
    current = byKey.get(current.rule.sourceFieldKey);
  }
  return false;
}

/** Whether giving `targetKey` a rule that reads `sourceKey` would make a field depend on itself. */
export function wouldCreateCycle(
  fields: ReadonlyArray<RuleEditorField>,
  targetKey: string,
  sourceKey: string,
): boolean {
  return targetKey === sourceKey || dependsOn(fields, sourceKey, targetKey);
}

/** Fields a rule showing `targetKeys` may read. */
export function sourceCandidates(
  fields: ReadonlyArray<RuleEditorField>,
  targetKeys: ReadonlyArray<string>,
): Array<RuleEditorField> {
  return fields.filter(
    (field) => field.canBeSource && targetKeys.every((target) => !wouldCreateCycle(fields, target, field.key)),
  );
}

/**
 * Fields a rule reading `sourceKey` may show: its own targets, and fields
 * without a rule that nobody else has claimed, unless that would make a cycle.
 */
export function targetCandidates(
  fields: ReadonlyArray<RuleEditorField>,
  sourceKey: string | null,
  ownTargetKeys: ReadonlyArray<string>,
  claimedKeys: ReadonlySet<string> = new Set(),
): Array<RuleEditorField> {
  return fields.filter(
    (field) =>
      (ownTargetKeys.includes(field.key) || (field.rule === null && !claimedKeys.has(field.key))) &&
      (sourceKey === null || !wouldCreateCycle(fields, field.key, sourceKey)),
  );
}

/** Fields whose rule reads `sourceKey`. */
export function dependentsOf<T extends Pick<RuleEditorField, "rule">>(
  fields: ReadonlyArray<T>,
  sourceKey: string | null,
): Array<T> {
  if (sourceKey === null) return [];
  return fields.filter((field) => field.rule?.sourceFieldKey === sourceKey);
}

/** Fields whose rule reads `sourceKey` and names one of `removedOptions`. */
export function dependentsUsingOptions<T extends Pick<RuleEditorField, "rule">>(
  fields: ReadonlyArray<T>,
  sourceKey: string | null,
  removedOptions: ReadonlyArray<string>,
): Array<T> {
  return dependentsOf(fields, sourceKey).filter((field) =>
    field.rule?.values.some((value) => removedOptions.includes(value)),
  );
}

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

/** A condition being edited, which may not yet be a valid rule. */
export type DraftCondition = {
  readonly sourceFieldKey: string | null;
  readonly operator: ConditionOperator;
  readonly values: ReadonlyArray<string>;
};

/** The rule a draft describes, or null while it is incomplete. */
export function ruleFromDraft(draft: DraftCondition): FieldDisplayRule | null {
  if (draft.sourceFieldKey === null) return null;
  if (operatorRequiresValues(draft.operator) && draft.values.length === 0) return null;
  return createDisplayRule(
    draft.sourceFieldKey,
    draft.operator,
    operatorRequiresValues(draft.operator) ? draft.values : [],
  );
}
