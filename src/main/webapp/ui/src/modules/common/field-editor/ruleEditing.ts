/**
 * Plain helpers for editing the display rule of one field at a time. They know
 * nothing about Inventory or ELN models: each product maps its fields to
 * `RuleEditorField` and stores the rule that `FieldDisplayCondition` reports.
 */
import { uniq } from "es-toolkit";
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
  /** Only read for the fields on screen, so a host may supply it through a getter. */
  readonly name: string;
  /** Whether the field is a radio or choice field, the only types a rule can read. */
  readonly canBeSource: boolean;
  /** The options a rule may name, already passed through {@link usableOptions}. */
  readonly options: ReadonlyArray<string>;
  readonly rule: FieldDisplayRule | null;
};

/** Options a rule may use: blank options and repeats of an earlier option are left out. */
export function usableOptions(options: ReadonlyArray<string>): Array<string> {
  return uniq(options.filter((option) => !isBlankOption(option)));
}

/**
 * Who may read whom, for every field of one form or template, worked out once.
 * Nothing here reads a field's name.
 */
export type RuleGraph = {
  readonly byKey: ReadonlyMap<string, RuleEditorField>;
  /** The field's position in the list. */
  indexOf(key: string): number;
  /**
   * The fields a rule on `key` may read: radio and choice fields other than
   * itself that do not already depend on it, directly or through other rules.
   */
  sourcesFor(key: string): ReadonlyArray<RuleEditorField>;
  /** The fields whose rule reads `key` directly. */
  dependentsOf(key: string): ReadonlyArray<RuleEditorField>;
};

/**
 * Builds the graph in time linear in the total length of the rule chains.
 * Each rule has one source, so a field's ancestors are a single chain; every
 * field on that chain gets the field as a descendant, and a source is excluded
 * for a target exactly when it is one of the target's descendants.
 */
export function buildRuleGraph(fields: ReadonlyArray<RuleEditorField>): RuleGraph {
  const byKey = new Map(fields.map((field) => [field.key, field]));
  const index = new Map(fields.map((field, position) => [field.key, position]));
  const descendants = new Map<string, Set<string>>();
  const dependents = new Map<string, Array<RuleEditorField>>();
  for (const field of fields) {
    const direct = field.rule?.sourceFieldKey;
    if (direct !== undefined && byKey.has(direct)) {
      dependents.set(direct, [...(dependents.get(direct) ?? []), field]);
    }
    const seen = new Set([field.key]);
    let ancestor = direct;
    // a chain that loops (an invalid stored state) stops where it repeats
    while (ancestor !== undefined && !seen.has(ancestor) && byKey.has(ancestor)) {
      seen.add(ancestor);
      let set = descendants.get(ancestor);
      if (!set) {
        set = new Set();
        descendants.set(ancestor, set);
      }
      set.add(field.key);
      ancestor = byKey.get(ancestor)?.rule?.sourceFieldKey;
    }
  }
  const readable = fields.filter((field) => field.canBeSource);
  const sources = new Map<string, ReadonlyArray<RuleEditorField>>();
  return {
    byKey,
    indexOf: (key) => index.get(key) ?? -1,
    sourcesFor: (key) => {
      let result = sources.get(key);
      if (!result) {
        const excluded = descendants.get(key);
        result = readable.filter((field) => field.key !== key && !excluded?.has(field.key));
        sources.set(key, result);
      }
      return result;
    },
    dependentsOf: (key) => dependents.get(key) ?? [],
  };
}

const graphs = new WeakMap<ReadonlyArray<RuleEditorField>, RuleGraph>();

/**
 * The graph for this exact array, built once however many fields ask for it.
 * Hosts pass every field's editor the same array, and keep it the same while
 * only names change, so renaming a field builds nothing.
 */
export function ruleGraphFor(fields: ReadonlyArray<RuleEditorField>): RuleGraph {
  let graph = graphs.get(fields);
  if (!graph) {
    graph = buildRuleGraph(fields);
    graphs.set(fields, graph);
  }
  return graph;
}

function snapshot(field: RuleEditorField, rule: FieldDisplayRule | null): FieldSnapshot {
  return { key: field.key, canBeSource: field.canBeSource, rule, allowedOptions: field.options, selectedOptions: [] };
}

/** Key format and uniqueness are the host's concern, not the rule editor's. */
const KEY_ERRORS: ReadonlySet<string> = new Set([
  DISPLAY_RULE_ERROR_CODES.keyInvalid,
  DISPLAY_RULE_ERROR_CODES.keyDuplicate,
]);

/**
 * Whether giving `key` this rule would leave it invalid by the shared
 * validator: a missing or unreadable source, an option the source does not
 * offer, or a cycle. Errors on other fields are left to the save, so an
 * existing problem elsewhere does not block editing. It runs the validator
 * once, so call it when a rule is about to be applied, not while rendering.
 */
export function ruleIsInvalid(fields: ReadonlyArray<RuleEditorField>, key: string, rule: FieldDisplayRule): boolean {
  const target = fields.findIndex((field) => field.key === key);
  return validateDisplayRules(fields.map((field) => snapshot(field, field.key === key ? rule : field.rule))).some(
    ({ fieldIndex, code }) => fieldIndex === target && !KEY_ERRORS.has(code),
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
 * The condition as it can still apply to `key`: a source that can no longer
 * be read (deleted, retyped, or now a cycle) is dropped, and options the
 * source no longer offers are removed.
 */
export function sanitiseCondition(graph: RuleGraph, key: string, condition: DraftCondition): DraftCondition {
  const source = graph.sourcesFor(key).find((field) => field.key === condition.sourceFieldKey);
  return {
    operator: condition.operator,
    sourceFieldKey: source?.key ?? null,
    values: source ? condition.values.filter((value) => source.options.includes(value)) : [],
  };
}
