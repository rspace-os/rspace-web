/**
 * Display rules show a field only when another field in the same ELN form or
 * Inventory template meets a condition. This module mirrors the Java package
 * `com.researchspace.model.field.rule`; both run the shared vectors in
 * `src/test/resources/fieldrules/display-rule-vectors.json`.
 *
 * The types are inferred from Valibot schemas, so the stored-JSON contract, the
 * runtime checks and the TypeScript types cannot drift apart.
 */
import * as v from "valibot";

const OPERATORS_WITH_VALUES = ["IS_ANY_OF", "IS_NONE_OF"] as const;
const OPERATORS_WITHOUT_VALUES = ["HAS_VALUE", "HAS_NO_VALUE"] as const;
const COMPARISON_OPERATORS = [
  "EQUALS",
  "NOT_EQUALS",
  "CONTAINS",
  "LESS_THAN",
  "AT_MOST",
  "GREATER_THAN",
  "AT_LEAST",
] as const;

export const CONDITION_OPERATORS = [
  ...OPERATORS_WITH_VALUES,
  ...OPERATORS_WITHOUT_VALUES,
  ...COMPARISON_OPERATORS,
] as const;

export const ConditionOperatorSchema = v.picklist(CONDITION_OPERATORS);

export type ConditionOperator = v.InferOutput<typeof ConditionOperatorSchema>;

/** What a field offers to the rules that read it; the same as Java's `SourceKind`. */
export const SOURCE_KINDS = ["OPTIONS", "TEXT", "NUMBER", "NONE"] as const;

export const SourceKindSchema = v.picklist(SOURCE_KINDS);

export type SourceKind = v.InferOutput<typeof SourceKindSchema>;

/** The operators a rule may use against a source of each kind, in the order an editor offers them. */
export const OPERATORS_BY_SOURCE_KIND: Readonly<Record<SourceKind, ReadonlyArray<ConditionOperator>>> = {
  OPTIONS: ["IS_ANY_OF", "IS_NONE_OF", "HAS_VALUE", "HAS_NO_VALUE"],
  TEXT: ["EQUALS", "NOT_EQUALS", "CONTAINS", "HAS_VALUE", "HAS_NO_VALUE"],
  NUMBER: ["EQUALS", "NOT_EQUALS", "LESS_THAN", "AT_MOST", "GREATER_THAN", "AT_LEAST", "HAS_VALUE", "HAS_NO_VALUE"],
  NONE: [],
};

/** Whether a rule with this operator must list option values. */
export function operatorRequiresValues(operator: ConditionOperator): boolean {
  return (OPERATORS_WITH_VALUES as ReadonlyArray<ConditionOperator>).includes(operator);
}

/** Whether this operator compares the source with exactly one text or number value. */
export function operatorComparesValue(operator: ConditionOperator): boolean {
  return (COMPARISON_OPERATORS as ReadonlyArray<ConditionOperator>).includes(operator);
}

const RuleShapeSchema = v.variant(
  "operator",
  [
    v.strictObject({
      sourceFieldKey: v.string(),
      operator: v.picklist(OPERATORS_WITH_VALUES),
      values: v.pipe(v.array(v.string()), v.minLength(1, "needs at least one value")),
    }),
    v.strictObject({
      sourceFieldKey: v.string(),
      operator: v.picklist(OPERATORS_WITHOUT_VALUES),
      values: v.nullish(v.pipe(v.array(v.string()), v.maxLength(0, "does not take values"))),
    }),
    // Any count parses; the validator reports a count other than one, and evaluation ignores such a rule.
    v.strictObject({
      sourceFieldKey: v.string(),
      operator: v.picklist(COMPARISON_OPERATORS),
      values: v.nullish(v.array(v.string())),
    }),
  ],
  "A display rule needs a known operator",
);

/**
 * A display rule, from any value: the same checks as the Java record's
 * constructor and parser. The output is branded, so a rule can only come from
 * this schema (through `parseDisplayRule` or `createDisplayRule`), never from a
 * hand-built object literal that skipped the checks.
 */
export const FieldDisplayRuleSchema = v.pipe(
  v.unknown(),
  RuleShapeSchema,
  v.transform(
    ({
      sourceFieldKey,
      operator,
      values,
    }): {
      /** Key of the field the condition reads; never a database id. */
      readonly sourceFieldKey: string;
      readonly operator: ConditionOperator;
      /**
       * Option values, non-empty, for IS_ANY_OF and IS_NONE_OF; empty for
       * HAS_VALUE and HAS_NO_VALUE; for the comparison operators the one text
       * or number compared.
       */
      readonly values: ReadonlyArray<string>;
    } => ({ sourceFieldKey, operator, values: values ?? [] }),
  ),
  v.brand("FieldDisplayRule"),
);

export type FieldDisplayRule = v.InferOutput<typeof FieldDisplayRuleSchema>;

/** A display rule as stored: a JSON string. As with `JSON.parse`, a repeated property takes its last value. */
export const StoredFieldDisplayRuleSchema = v.pipe(v.string(), v.parseJson(), FieldDisplayRuleSchema);

/**
 * An immutable copy of the parts of one field that display rules read, taken
 * at a point in time: the values on screen, or the result of a save being
 * checked. It is not the stored field. ELN and Inventory each build snapshots
 * from their own models; the schema also parses them from untyped sources such
 * as data attributes on the legacy ELN pages.
 */
export const FieldSnapshotSchema = v.pipe(
  v.object({
    /** Null for a legacy field that has no key. */
    key: v.nullable(v.string()),
    /**
     * Whether a rule can read the field. When `sourceKind` is absent, true
     * means a radio or choice field (`OPTIONS`) and false means `NONE`.
     */
    canBeSource: v.boolean(),
    /** What the field offers to rules that read it; read through `sourceKindOf`. */
    sourceKind: v.optional(SourceKindSchema),
    /** The raw text of a `TEXT` or `NUMBER` field; absent or empty when it has none. */
    value: v.optional(v.string()),
    /** Null when the field is always shown. */
    rule: v.nullable(FieldDisplayRuleSchema),
    /** The options a radio or choice field offers. */
    allowedOptions: v.pipe(v.array(v.string()), v.readonly()),
    /** The options currently selected; empty when the field has no value. */
    selectedOptions: v.pipe(v.array(v.string()), v.readonly()),
  }),
  v.readonly(),
);

export type FieldSnapshot = v.InferOutput<typeof FieldSnapshotSchema>;

/**
 * The field's effective source kind: `sourceKind` when given, otherwise
 * `OPTIONS` or `NONE` from `canBeSource`. Eligibility, operator choice,
 * validation and evaluation all decide from this.
 */
export function sourceKindOf(field: Pick<FieldSnapshot, "canBeSource" | "sourceKind">): SourceKind {
  return field.sourceKind ?? (field.canBeSource ? "OPTIONS" : "NONE");
}

/**
 * Parses a stored rule. Unknown properties, unknown operators, non-string keys
 * or values, and values that do not suit the operator are rejected.
 *
 * Agreement with the Java parser holds within Jackson's default stream limits
 * (nesting depth, number length). `JSON.parse` has no such limits, so a
 * contrived rule whose overridden duplicate property exceeds them is accepted
 * here and rejected by Java; real rules never come close.
 *
 * @throws {v.ValiError} if `json` is not a valid rule
 */
export function parseDisplayRule(json: string): FieldDisplayRule {
  return v.parse(StoredFieldDisplayRuleSchema, json);
}

/**
 * Builds a rule, applying the same checks as the Java record's constructor.
 *
 * @throws {v.ValiError} if the values do not suit the operator
 */
export function createDisplayRule(
  sourceFieldKey: string,
  operator: ConditionOperator,
  values: ReadonlyArray<string>,
): FieldDisplayRule {
  return v.parse(FieldDisplayRuleSchema, { sourceFieldKey, operator, values: [...values] });
}

/** Whether two rules, or two absent rules, are the same condition, whatever the order of their values. */
export function sameDisplayRule(a: FieldDisplayRule | null, b: FieldDisplayRule | null): boolean {
  if (a === null || b === null) return a === b;
  if (a.sourceFieldKey !== b.sourceFieldKey || a.operator !== b.operator) return false;
  const values = new Set(a.values);
  return a.values.length === b.values.length && b.values.every((value) => values.has(value));
}

/** The rule as stored JSON, via `JSON.stringify`; the same JSON value the Java side writes (`values` omitted when empty). */
export function serializeDisplayRule(rule: FieldDisplayRule): string {
  return JSON.stringify({
    sourceFieldKey: rule.sourceFieldKey,
    operator: rule.operator,
    ...(rule.values.length > 0 ? { values: rule.values } : {}),
  });
}

/**
 * The length of the rule as the server stores it, which is Java's
 * `FieldDisplayRule.toJson()`. That output escapes every non-ASCII UTF-16 code
 * unit as `\uXXXX` (six characters), whereas `JSON.stringify` leaves them raw;
 * all other escaping is the same. The stored JSON is ASCII, so this is also its
 * size in bytes.
 */
export function storedDisplayRuleLength(rule: FieldDisplayRule): number {
  const json = serializeDisplayRule(rule);
  let length = json.length;
  for (let i = 0; i < json.length; i++) {
    if (json.charCodeAt(i) > 0x7f) length += 5;
  }
  return length;
}
