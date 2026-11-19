import type { FieldDisplayRule, FieldSnapshot, SourceKind } from "@/modules/common/field-rules/displayRule";
import { displayRuleVisibility } from "@/modules/common/field-rules/evaluateDisplayRules";

/**
 * What a display rule reading an Inventory field of this type compares:
 * options for radio and choice, text for plain text (`string`), a number for
 * `number`. Formatted text and every other type cannot be read.
 */
export function sourceKindOfType(type: string | undefined): SourceKind {
  switch (type) {
    case "radio":
    case "choice":
      return "OPTIONS";
    case "string":
      return "TEXT";
    case "number":
      return "NUMBER";
    default:
      return "NONE";
  }
}

/** Just the parts of an Inventory field that display rules read. */
export type RuleReadableField = {
  fieldKey?: string | null;
  displayRule?: FieldDisplayRule | null;
  type?: string;
  options?: ReadonlyArray<{ value: string }>;
  selectedOptions?: ReadonlyArray<string> | null;
  content?: unknown;
};

/**
 * The value a text or number rule reads, or null for a field of another kind.
 * While editing, a number field holds the string typed; loaded content was
 * read through `parseFloat`, so it is compared as `String(content)`.
 */
export function ruleSourceValue(field: RuleReadableField): string | null {
  const kind = sourceKindOfType(field.type);
  if (kind !== "TEXT" && kind !== "NUMBER") return null;
  return field.content === null || field.content === undefined ? "" : String(field.content);
}

export function fieldSnapshot(field: RuleReadableField): FieldSnapshot {
  const sourceKind = sourceKindOfType(field.type);
  const options = sourceKind === "OPTIONS";
  return {
    key: field.fieldKey ?? null,
    canBeSource: sourceKind !== "NONE",
    sourceKind,
    value: ruleSourceValue(field) ?? "",
    rule: field.displayRule ?? null,
    allowedOptions: options ? (field.options ?? []).map((option) => option.value) : [],
    selectedOptions: options ? [...(field.selectedOptions ?? [])] : [],
  };
}

/** The fields that display rules hide, given the fields' current values. */
export function hiddenFieldsOf<T extends RuleReadableField>(fields: ReadonlyArray<T>): Set<T> {
  return hiddenFieldsReading(fields, []);
}

/**
 * The value of each text or number field that some display rule reads, by
 * position, and null for every other field.
 */
export function scalarSourceValues(fields: ReadonlyArray<RuleReadableField>): Array<string | null> {
  const read = new Set(fields.map((field) => field.displayRule?.sourceFieldKey));
  return fields.map((field) => (field.fieldKey && read.has(field.fieldKey) ? ruleSourceValue(field) : null));
}

/**
 * The fields that display rules hide when each text or number source reads
 * `scalarValues[index]` instead of its current value; a null or missing entry
 * reads the current value. Options are always read as they are now.
 */
export function hiddenFieldsReading<T extends RuleReadableField>(
  fields: ReadonlyArray<T>,
  scalarValues: ReadonlyArray<string | null>,
): Set<T> {
  const visible = displayRuleVisibility(
    fields.map((field, index) => {
      const snapshot = fieldSnapshot(field);
      const value = scalarValues[index];
      return value === null || value === undefined ? snapshot : { ...snapshot, value };
    }),
  );
  return new Set(fields.filter((_field, index) => !visible[index]));
}
