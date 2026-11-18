/**
 * Plain helpers for editing display rules across a whole form or template.
 * They know nothing about Inventory or ELN models: each product maps its
 * fields to `RuleEditorField` and applies the returned `RuleChange`s itself.
 */
import { groupBy, uniq } from "es-toolkit";
import {
  type ConditionOperator,
  createDisplayRule,
  type FieldDisplayRule,
  type FieldSnapshot,
  operatorRequiresValues,
} from "@/modules/common/field-rules/displayRule";
import { isBlankOption } from "@/modules/common/field-rules/ruleOptions";
import { DISPLAY_RULE_ERROR_CODES, validateDisplayRules } from "@/modules/common/field-rules/validateDisplayRules";

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
type RuleGroup = {
  readonly rule: FieldDisplayRule;
  readonly targetKeys: ReadonlyArray<string>;
};

/** Options a rule may use: blank options and repeats of an earlier option are left out. */
export function usableOptions(options: ReadonlyArray<string>): Array<string> {
  return uniq(options.filter((option) => !isBlankOption(option)));
}

export function groupDisplayRules(fields: ReadonlyArray<RuleEditorField>): Array<RuleGroup> {
  const withRules = fields.filter(
    (field): field is RuleEditorField & { rule: FieldDisplayRule } => field.rule !== null,
  );
  const groups = groupBy(withRules, ({ rule }) =>
    JSON.stringify([rule.sourceFieldKey, rule.operator, [...rule.values].sort()]),
  );
  return Object.values(groups).map((members) => ({
    rule: members[0].rule,
    targetKeys: members.map((field) => field.key),
  }));
}

function snapshot(field: RuleEditorField, rule: FieldDisplayRule | null): FieldSnapshot {
  return { key: field.key, canBeSource: field.canBeSource, rule, allowedOptions: field.options, selectedOptions: [] };
}

/** Key format and uniqueness are the host's concern, not the rule editor's. */
const KEY_ERRORS: ReadonlySet<string> = new Set([
  DISPLAY_RULE_ERROR_CODES.keyInvalid,
  DISPLAY_RULE_ERROR_CODES.keyDuplicate,
]);

/** Applies the changes to the fields, as the host would. */
function withChanges(
  fields: ReadonlyArray<RuleEditorField>,
  changes: ReadonlyArray<RuleChange>,
): Array<RuleEditorField> {
  return fields.map((field) => {
    const change = changes.find(({ key }) => key === field.key);
    return change ? { ...field, rule: change.rule } : field;
  });
}

/**
 * Whether the changes would leave a changed field with an invalid rule, by the
 * shared validator: a missing or unreadable source, an option the source no
 * longer offers, or a cycle. Errors on fields the changes do not touch are
 * left to the save, so an existing problem elsewhere does not block editing.
 */
export function changesAreInvalid(fields: ReadonlyArray<RuleEditorField>, changes: ReadonlyArray<RuleChange>): boolean {
  const changed = new Set(changes.filter(({ rule }) => rule !== null).map(({ key }) => key));
  const after = withChanges(fields, changes);
  return validateDisplayRules(after.map((field) => snapshot(field, field.rule))).some(
    ({ fieldIndex, code }) => changed.has(after[fieldIndex].key) && !KEY_ERRORS.has(code),
  );
}

/** Whether giving `targetKey` a rule that reads `sourceKey` would make a field depend on itself. */
function createsCycle(fields: ReadonlyArray<RuleEditorField>, targetKey: string, sourceKey: string): boolean {
  if (targetKey === sourceKey) return true;
  const probe = createDisplayRule(sourceKey, "HAS_VALUE", []);
  const snapshots = fields.map((field) => snapshot(field, field.key === targetKey ? probe : field.rule));
  const target = fields.findIndex((field) => field.key === targetKey);
  return validateDisplayRules(snapshots).some(
    ({ fieldIndex, code }) => fieldIndex === target && code === DISPLAY_RULE_ERROR_CODES.cycle,
  );
}

/** Fields a rule showing `targetKeys` may read. */
export function sourceCandidates(
  fields: ReadonlyArray<RuleEditorField>,
  targetKeys: ReadonlyArray<string>,
): Array<RuleEditorField> {
  return fields.filter(
    (field) => field.canBeSource && targetKeys.every((target) => !createsCycle(fields, target, field.key)),
  );
}

/**
 * Fields a rule reading `sourceKey` may show: `ownKeys` (the rule's targets),
 * and fields without a rule that nobody else has claimed, unless that would
 * make a cycle.
 */
export function targetCandidates(
  fields: ReadonlyArray<RuleEditorField>,
  sourceKey: string | null,
  ownKeys: ReadonlyArray<string>,
  claimedKeys: ReadonlySet<string> = new Set(),
): Array<RuleEditorField> {
  return fields.filter(
    (field) =>
      (ownKeys.includes(field.key) || (field.rule === null && !claimedKeys.has(field.key))) &&
      (sourceKey === null || !createsCycle(fields, field.key, sourceKey)),
  );
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

/**
 * The draft as it can still apply to the current fields: a source that can no
 * longer be read (deleted, retyped, or now a cycle) is dropped, and options the
 * source no longer offers are removed.
 */
export function sanitiseDraft(
  fields: ReadonlyArray<RuleEditorField>,
  condition: DraftCondition,
  targetKeys: ReadonlyArray<string>,
): { condition: DraftCondition; targetKeys: Array<string> } {
  const liveTargets = targetKeys.filter((key) => fields.some((field) => field.key === key));
  const source = sourceCandidates(fields, liveTargets).find((field) => field.key === condition.sourceFieldKey);
  return {
    condition: {
      operator: condition.operator,
      sourceFieldKey: source?.key ?? null,
      values: source ? condition.values.filter((value) => source.options.includes(value)) : [],
    },
    targetKeys: liveTargets,
  };
}
