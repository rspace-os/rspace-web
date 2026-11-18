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

export const CONDITION_OPERATORS = ["IS_ANY_OF", "IS_NONE_OF", "HAS_VALUE", "HAS_NO_VALUE"] as const;
const OPERATORS_WITH_VALUES = ["IS_ANY_OF", "IS_NONE_OF"] as const;
const OPERATORS_WITHOUT_VALUES = ["HAS_VALUE", "HAS_NO_VALUE"] as const;

export const ConditionOperatorSchema = v.picklist(CONDITION_OPERATORS);

export type ConditionOperator = v.InferOutput<typeof ConditionOperatorSchema>;

export function operatorRequiresValues(operator: ConditionOperator): boolean {
  return (OPERATORS_WITH_VALUES as ReadonlyArray<ConditionOperator>).includes(operator);
}

const RULE_PROPERTIES: ReadonlyArray<string> = ["sourceFieldKey", "operator", "values"];

/**
 * Valibot's `strictObject` treats a key as known when `key in entries`, which
 * is also true for inherited names such as "toString" and "constructor", and
 * for an own "__proto__" from `JSON.parse`. The Java parser rejects those, so
 * own keys are checked explicitly first.
 */
const OnlyRulePropertiesSchema = v.check(
  (input: unknown) =>
    typeof input === "object" &&
    input !== null &&
    !Array.isArray(input) &&
    Object.keys(input).every((key) => RULE_PROPERTIES.includes(key)),
  "A display rule must be a JSON object with only sourceFieldKey, operator and values",
);

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
  OnlyRulePropertiesSchema,
  RuleShapeSchema,
  v.transform(
    ({
      sourceFieldKey,
      operator,
      values,
    }): {
      /** Key of the radio or choice field the condition reads; never a database id. */
      readonly sourceFieldKey: string;
      readonly operator: ConditionOperator;
      /** Option values; non-empty for IS_ANY_OF and IS_NONE_OF, empty otherwise. */
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
    /** Whether the field is a radio or choice field, the only types a rule can read. */
    canBeSource: v.boolean(),
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

/** The rule as stored JSON, via `JSON.stringify`; the same JSON value the Java side writes (`values` omitted when empty). */
export function serializeDisplayRule(rule: FieldDisplayRule): string {
  return JSON.stringify({
    sourceFieldKey: rule.sourceFieldKey,
    operator: rule.operator,
    ...(rule.values.length > 0 ? { values: rule.values } : {}),
  });
}
