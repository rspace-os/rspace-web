/**
 * Display rules show a field only when another field in the same ELN form or
 * Inventory template meets a condition. This module mirrors the Java package
 * `com.researchspace.model.field.rule`; both run the shared vectors in
 * `src/test/resources/fieldrules/display-rule-vectors.json`.
 */

export const CONDITION_OPERATORS = ["IS_ANY_OF", "IS_NONE_OF", "HAS_VALUE", "HAS_NO_VALUE"] as const;

export type ConditionOperator = (typeof CONDITION_OPERATORS)[number];

export type FieldDisplayRule = {
  /** Key of the radio or choice field the condition reads; never a database id. */
  readonly sourceFieldKey: string;
  readonly operator: ConditionOperator;
  /** Option values; non-empty for IS_ANY_OF and IS_NONE_OF, empty otherwise. */
  readonly values: ReadonlyArray<string>;
};

/**
 * The view of a field that display rules need. ELN and Inventory each build
 * these from their own models.
 */
export type RuleField = {
  /** Null for a legacy field that has no key. */
  readonly key: string | null;
  /** Whether the field is a radio or choice field, the only types a rule can read. */
  readonly canBeSource: boolean;
  /** Null when the field is always shown. */
  readonly rule: FieldDisplayRule | null;
  /** The options a radio or choice field offers. */
  readonly allowedOptions: ReadonlyArray<string>;
  /** The options currently selected; empty when the field has no value. */
  readonly selectedOptions: ReadonlyArray<string>;
};

const RULE_PROPERTIES = new Set(["sourceFieldKey", "operator", "values"]);

export function operatorRequiresValues(operator: ConditionOperator): boolean {
  return operator === "IS_ANY_OF" || operator === "IS_NONE_OF";
}

function isConditionOperator(value: unknown): value is ConditionOperator {
  return CONDITION_OPERATORS.some((operator) => operator === value);
}

/** Empty or only `\s` whitespace; the Java side uses exactly the same character set. */
const isBlank = (value: string): boolean => /^\s*$/.test(value);

/**
 * Parses a stored rule. Unknown properties, unknown operators, non-string
 * keys or values, and values that do not suit the operator are rejected.
 *
 * @throws {Error} if `json` is not a valid rule
 */
export function parseDisplayRule(json: string): FieldDisplayRule {
  const parsed: unknown = JSON.parse(json);
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    throw new Error("A display rule must be a JSON object");
  }
  const unknownProperty = Object.keys(parsed).find((property) => !RULE_PROPERTIES.has(property));
  if (unknownProperty !== undefined) {
    throw new Error(`Unknown display rule property: ${unknownProperty}`);
  }
  const { sourceFieldKey, operator, values } = parsed as Record<string, unknown>;
  if (typeof sourceFieldKey !== "string") {
    throw new Error("A display rule needs a source field key");
  }
  if (!isConditionOperator(operator)) {
    throw new Error("A display rule needs a known operator");
  }
  const list: unknown = values ?? [];
  if (!Array.isArray(list) || !list.every((value) => typeof value === "string")) {
    throw new Error("Display rule values must be strings");
  }
  return createDisplayRule(sourceFieldKey, operator, list);
}

/**
 * Builds a rule, applying the same checks as the Java record's constructor.
 *
 * @throws {Error} if the source key is blank or the values do not suit the operator
 */
export function createDisplayRule(
  sourceFieldKey: string,
  operator: ConditionOperator,
  values: ReadonlyArray<string>,
): FieldDisplayRule {
  if (isBlank(sourceFieldKey)) {
    throw new Error("A display rule needs a source field key");
  }
  if (values.some(isBlank)) {
    throw new Error("Display rule values cannot be blank");
  }
  if (operatorRequiresValues(operator) && values.length === 0) {
    throw new Error(`${operator} needs at least one value`);
  }
  if (!operatorRequiresValues(operator) && values.length > 0) {
    throw new Error(`${operator} does not take values`);
  }
  return { sourceFieldKey, operator, values: [...values] };
}

/** The rule as stored JSON, byte-for-byte what the Java side writes. */
export function serializeDisplayRule(rule: FieldDisplayRule): string {
  return JSON.stringify({
    sourceFieldKey: rule.sourceFieldKey,
    operator: rule.operator,
    ...(rule.values.length > 0 ? { values: rule.values } : {}),
  });
}
