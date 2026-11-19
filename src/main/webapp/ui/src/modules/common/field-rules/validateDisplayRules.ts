import * as v from "valibot";
import { type FieldSnapshot, storedDisplayRuleLength } from "./displayRule";

/** Message codes shared with the Java `FieldDisplayRuleValidator`. */
export const DISPLAY_RULE_ERROR_CODES = {
  keyInvalid: "validation.fieldDisplayRule.keyInvalid",
  keyDuplicate: "validation.fieldDisplayRule.keyDuplicate",
  sourceMissing: "validation.fieldDisplayRule.sourceMissing",
  sourceSelf: "validation.fieldDisplayRule.sourceSelf",
  sourceType: "validation.fieldDisplayRule.sourceType",
  valueUnknown: "validation.fieldDisplayRule.valueUnknown",
  valueDuplicate: "validation.fieldDisplayRule.valueDuplicate",
  valueTooMany: "validation.fieldDisplayRule.valueTooMany",
  ruleTooLong: "validation.fieldDisplayRule.ruleTooLong",
  cycle: "validation.fieldDisplayRule.cycle",
} as const;

/** The most values one rule may list; the same as Java's `FieldDisplayRuleValidator.MAX_VALUES`. */
export const MAX_DISPLAY_RULE_VALUES = 1000;

/**
 * The longest a rule may be as stored (see `storedDisplayRuleLength`); the
 * same as Java's `FieldDisplayRuleValidator.MAX_JSON_LENGTH`. That is half of
 * the 65,535-byte TEXT column that holds an Inventory rule; ELN's LONGTEXT
 * column has no practical limit, but the same bound keeps the two alike.
 */
export const MAX_DISPLAY_RULE_JSON_LENGTH = 32_768;

export const DisplayRuleErrorCodeSchema = v.picklist(Object.values(DISPLAY_RULE_ERROR_CODES));

export type DisplayRuleErrorCode = v.InferOutput<typeof DisplayRuleErrorCodeSchema>;

export const DisplayRuleErrorSchema = v.pipe(
  v.object({
    /** The field's position in the validated list. */
    fieldIndex: v.pipe(v.number(), v.integer(), v.minValue(0)),
    code: DisplayRuleErrorCodeSchema,
  }),
  v.readonly(),
);

export type DisplayRuleError = v.InferOutput<typeof DisplayRuleErrorSchema>;

/**
 * A field key: a UUID (Valibot's check) in canonical lower case, as the Java
 * side requires through `UUID.toString()`.
 */
const FieldKeySchema = v.pipe(
  v.string(),
  v.uuid(),
  v.check((key) => key === key.toLowerCase()),
);

/**
 * Which fields lie on a cycle of rule sources. Each rule has one source, so
 * following sources from a field either ends or loops; every field is walked
 * once, so this runs in linear time.
 */
function fieldsOnCycle(fields: ReadonlyArray<FieldSnapshot>, indexByKey: ReadonlyMap<string, number>): Array<boolean> {
  const UNVISITED = 0;
  const ON_WALK = 1;
  const DONE = 2;
  const state: Array<number> = new Array(fields.length).fill(UNVISITED);
  const onCycle: Array<boolean> = new Array(fields.length).fill(false);
  for (let start = 0; start < fields.length; start++) {
    if (state[start] !== UNVISITED) continue;
    const walk: Array<number> = [];
    let current = start;
    for (;;) {
      state[current] = ON_WALK;
      walk.push(current);
      const { rule } = fields[current];
      const next = rule === null ? undefined : indexByKey.get(rule.sourceFieldKey);
      if (next === undefined || state[next] === DONE) break;
      if (state[next] === ON_WALK) {
        for (let i = walk.indexOf(next); i < walk.length; i++) onCycle[walk[i]] = true;
        break;
      }
      current = next;
    }
    for (const index of walk) state[index] = DONE;
  }
  return onCycle;
}

/**
 * Validates every key and rule in a whole form or template at once, with the
 * same checks and error order as the Java `FieldDisplayRuleValidator`. Keys
 * may be null (legacy fields), but non-null keys must be lowercase UUIDs and
 * unique. Each rule must list each value once, at most
 * `MAX_DISPLAY_RULE_VALUES` values, and fit in `MAX_DISPLAY_RULE_JSON_LENGTH`
 * characters as stored. Each rule's source must exist, differ from the field
 * itself, be a radio or choice field and offer every value the rule names, and
 * rules must not form a cycle.
 */
export function validateDisplayRules(fields: ReadonlyArray<FieldSnapshot>): Array<DisplayRuleError> {
  const indexByKey = new Map<string, number>();
  const duplicateKeys = new Set<string>();
  fields.forEach(({ key }, index) => {
    if (key === null) return;
    if (indexByKey.has(key)) duplicateKeys.add(key);
    else indexByKey.set(key, index);
  });

  const onCycle = fieldsOnCycle(fields, indexByKey);
  const errors: Array<DisplayRuleError> = [];
  fields.forEach(({ key, rule }, index) => {
    if (key !== null && !v.is(FieldKeySchema, key)) {
      errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.keyInvalid });
    } else if (key !== null && duplicateKeys.has(key)) {
      errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.keyDuplicate });
    }
    if (rule === null) return;
    if (rule.values.length > MAX_DISPLAY_RULE_VALUES) {
      errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.valueTooMany });
    } else if (
      // Each UTF-16 unit is stored as at least one character, so values that
      // together exceed the budget are too long; skip serialising them.
      rule.values.reduce((total, value) => total + value.length, 0) > MAX_DISPLAY_RULE_JSON_LENGTH ||
      storedDisplayRuleLength(rule) > MAX_DISPLAY_RULE_JSON_LENGTH
    ) {
      errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.ruleTooLong });
    } else if (new Set(rule.values).size < rule.values.length) {
      errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.valueDuplicate });
    }
    const sourceIndex = indexByKey.get(rule.sourceFieldKey);
    if (sourceIndex === undefined) {
      errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.sourceMissing });
    } else if (sourceIndex === index) {
      errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.sourceSelf });
    } else {
      const source = fields[sourceIndex];
      const allowed = new Set(source.allowedOptions);
      if (!source.canBeSource) {
        errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.sourceType });
      } else if (!rule.values.every((value) => allowed.has(value))) {
        errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.valueUnknown });
      }
      if (onCycle[index]) {
        errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.cycle });
      }
    }
  });
  return errors;
}
