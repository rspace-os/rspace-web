import * as v from "valibot";
import type { FieldSnapshot } from "./displayRule";

/** Message codes shared with the Java `FieldDisplayRuleValidator`. */
export const DISPLAY_RULE_ERROR_CODES = {
  keyInvalid: "validation.fieldDisplayRule.keyInvalid",
  keyDuplicate: "validation.fieldDisplayRule.keyDuplicate",
  sourceMissing: "validation.fieldDisplayRule.sourceMissing",
  sourceSelf: "validation.fieldDisplayRule.sourceSelf",
  sourceType: "validation.fieldDisplayRule.sourceType",
  valueUnknown: "validation.fieldDisplayRule.valueUnknown",
  cycle: "validation.fieldDisplayRule.cycle",
} as const;

export const DisplayRuleErrorCodeSchema = v.picklist(Object.values(DISPLAY_RULE_ERROR_CODES));

export type DisplayRuleErrorCode = v.InferOutput<typeof DisplayRuleErrorCodeSchema>;

export const DisplayRuleErrorSchema = v.object({
  /** The field's position in the validated list. */
  fieldIndex: v.pipe(v.number(), v.integer(), v.minValue(0)),
  code: DisplayRuleErrorCodeSchema,
});

export type DisplayRuleError = v.InferOutput<typeof DisplayRuleErrorSchema>;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

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
 * unique. Each rule's source must exist, differ from the field itself, be a
 * radio or choice field and offer every value the rule names, and rules must
 * not form a cycle.
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
    if (key !== null && !UUID.test(key)) {
      errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.keyInvalid });
    } else if (key !== null && duplicateKeys.has(key)) {
      errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.keyDuplicate });
    }
    if (rule === null) return;
    const sourceIndex = indexByKey.get(rule.sourceFieldKey);
    if (sourceIndex === undefined) {
      errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.sourceMissing });
    } else if (sourceIndex === index) {
      errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.sourceSelf });
    } else {
      const source = fields[sourceIndex];
      if (!source.canBeSource) {
        errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.sourceType });
      } else if (!rule.values.every((value) => source.allowedOptions.includes(value))) {
        errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.valueUnknown });
      }
      if (onCycle[index]) {
        errors.push({ fieldIndex: index, code: DISPLAY_RULE_ERROR_CODES.cycle });
      }
    }
  });
  return errors;
}
