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
  OPERATORS_BY_SOURCE_KIND,
  operatorComparesValue,
  operatorRequiresValues,
  type SourceKind,
  sourceKindOf,
} from "@/modules/common/field-rules/displayRule";
import { isBlankOption } from "@/modules/common/field-rules/ruleOptions";
import { isBlankRuleValue, isRuleNumber, trimRuleValue } from "@/modules/common/field-rules/ruleValues";
import { DISPLAY_RULE_ERROR_CODES, validateDisplayRules } from "@/modules/common/field-rules/validateDisplayRules";

/** One field as the rule editor sees it. */
export type RuleEditorField = {
  /** The field key; rules reference fields by key. */
  readonly key: string;
  /** Only read for the fields on screen, so a host may supply it through a getter. */
  readonly name: string;
  /**
   * Whether a rule can read the field. Without `sourceKind`, true means a
   * radio or choice field and false a field no rule can read.
   */
  readonly canBeSource: boolean;
  /** What a rule reading the field compares; read through `sourceKindOf`. */
  readonly sourceKind?: SourceKind;
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
   * The fields a rule on `key` may read: fields of any kind but `NONE`, other than
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
      const list = dependents.get(direct);
      if (list) list.push(field);
      else dependents.set(direct, [field]);
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
  const readable = fields.filter((field) => sourceKindOf(field) !== "NONE");
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
  return {
    key: field.key,
    canBeSource: field.canBeSource,
    sourceKind: sourceKindOf(field),
    rule,
    allowedOptions: field.options,
    selectedOptions: [],
  };
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

/**
 * A condition being edited, which may not yet be a valid rule. A text or
 * number comparison holds the value as typed, untrimmed.
 */
export type DraftCondition = {
  readonly sourceFieldKey: string | null;
  readonly operator: ConditionOperator;
  readonly values: ReadonlyArray<string>;
};

/**
 * The rule a draft describes, or null while it is incomplete. A comparison
 * value is trimmed with the shared whitespace set, so `"5 "` is stored as `5`.
 */
export function ruleFromDraft(draft: DraftCondition): FieldDisplayRule | null {
  if (draft.sourceFieldKey === null) return null;
  if (operatorRequiresValues(draft.operator)) {
    return draft.values.length === 0 ? null : createDisplayRule(draft.sourceFieldKey, draft.operator, draft.values);
  }
  if (operatorComparesValue(draft.operator)) {
    const value = draft.values[0] ?? "";
    return isBlankRuleValue(value)
      ? null
      : createDisplayRule(draft.sourceFieldKey, draft.operator, [trimRuleValue(value)]);
  }
  return createDisplayRule(draft.sourceFieldKey, draft.operator, []);
}

/**
 * What is wrong with a comparison's value as typed: `blank`, or `notANumber`
 * against a number source; null when it is usable or the operator takes no
 * typed value.
 */
export function comparisonValueProblem(kind: SourceKind, condition: DraftCondition): "blank" | "notANumber" | null {
  if (!operatorComparesValue(condition.operator)) return null;
  const value = trimRuleValue(condition.values[0] ?? "");
  if (value === "") return "blank";
  if (kind === "NUMBER" && !isRuleNumber(value)) return "notANumber";
  return null;
}

/**
 * The condition as it can still apply to `key`: a source that can no longer
 * be read (deleted, retyped, or now a cycle) is dropped. Against an option
 * source, options it no longer offers are removed; an operator that does not
 * suit the source's kind becomes the first that does, and loses its values.
 */
export function sanitiseCondition(graph: RuleGraph, key: string, condition: DraftCondition): DraftCondition {
  const source = graph.sourcesFor(key).find((field) => field.key === condition.sourceFieldKey);
  if (!source) return { operator: condition.operator, sourceFieldKey: null, values: [] };
  const kind = sourceKindOf(source);
  const operators = OPERATORS_BY_SOURCE_KIND[kind];
  if (!operators.includes(condition.operator))
    return { operator: operators[0], sourceFieldKey: source.key, values: [] };
  return {
    operator: condition.operator,
    sourceFieldKey: source.key,
    values: kind === "OPTIONS" ? condition.values.filter((value) => source.options.includes(value)) : condition.values,
  };
}
