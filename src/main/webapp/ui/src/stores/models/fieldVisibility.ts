import type { FieldDisplayRule, FieldSnapshot } from "@/modules/common/field-rules/displayRule";
import { displayRuleVisibility } from "@/modules/common/field-rules/evaluateDisplayRules";

function canBeSource(type: string | undefined): boolean {
  return type === "radio" || type === "choice";
}

/** Just the parts of an Inventory field that display rules read. */
export type RuleReadableField = {
  fieldKey?: string | null;
  displayRule?: FieldDisplayRule | null;
  type?: string;
  options?: ReadonlyArray<{ value: string }>;
  selectedOptions?: ReadonlyArray<string> | null;
};

export function fieldSnapshot(field: RuleReadableField): FieldSnapshot {
  const source = canBeSource(field.type);
  return {
    key: field.fieldKey ?? null,
    canBeSource: source,
    rule: field.displayRule ?? null,
    allowedOptions: source ? (field.options ?? []).map((option) => option.value) : [],
    selectedOptions: source ? [...(field.selectedOptions ?? [])] : [],
  };
}

/** The fields that display rules hide, given the fields' current values. */
export function hiddenFieldsOf<T extends RuleReadableField>(fields: ReadonlyArray<T>): Set<T> {
  const visible = displayRuleVisibility(fields.map(fieldSnapshot));
  return new Set(fields.filter((_field, index) => !visible[index]));
}
