import { type RuleEditorField, usableOptions } from "@/modules/common/field-editor/ruleEditing";
import type { FieldDisplayRule } from "@/modules/common/field-rules/displayRule";
import { canBeSource, type EditorField, hasOptions } from "./editorModel";

const sameList = (a: ReadonlyArray<string>, b: ReadonlyArray<string>) =>
  a.length === b.length && a.every((value, i) => value === b[i]);

const sameRule = (a: FieldDisplayRule | null, b: FieldDisplayRule | null) =>
  a === b ||
  (a !== null &&
    b !== null &&
    a.sourceFieldKey === b.sourceFieldKey &&
    a.operator === b.operator &&
    sameList(a.values, b.values));

/**
 * The fields of an ELN form as the display condition editor sees them.
 *
 * Nothing here reads a name: each entry reads its name through `nameOf` when
 * it is shown. While keys, types, options, rules and order are unchanged, the
 * previous list is returned as it was, so renaming a field keeps the same
 * array and `ruleGraphFor` does not work out the candidate sources again.
 */
export function elnRuleFields(
  fields: ReadonlyArray<EditorField>,
  nameOf: (key: string) => string,
  previous: ReadonlyArray<RuleEditorField> = [],
): ReadonlyArray<RuleEditorField> {
  const next = fields.map((field) => ({
    key: field.key,
    canBeSource: canBeSource(field.type),
    options: hasOptions(field) ? usableOptions(field.options) : [],
    rule: field.rule,
  }));
  const unchanged =
    previous.length === next.length &&
    next.every((field, i) => {
      const before = previous[i];
      return (
        before.key === field.key &&
        before.canBeSource === field.canBeSource &&
        sameList(before.options, field.options) &&
        sameRule(before.rule, field.rule)
      );
    });
  if (unchanged) return previous;
  return next.map(
    (field): RuleEditorField => ({
      ...field,
      get name() {
        return nameOf(field.key);
      },
    }),
  );
}
