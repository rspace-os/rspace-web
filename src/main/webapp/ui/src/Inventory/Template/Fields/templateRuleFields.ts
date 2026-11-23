import { computed, type IComputedValue } from "mobx";
import { type RuleEditorField, usableOptions } from "@/modules/common/field-editor/ruleEditing";
import type { Field } from "../../../stores/definitions/Field";
import FieldModel from "../../../stores/models/FieldModel";
import { FIELD_LABEL } from "../../../stores/models/FieldTypes";
import { sourceKindOfType } from "../../../stores/models/fieldVisibility";

type FieldOwner = { readonly fields?: ReadonlyArray<Field> | null };

const lists = new WeakMap<FieldOwner, IComputedValue<ReadonlyArray<RuleEditorField>>>();

/**
 * The fields of a sample or instrument template as the display condition
 * editor sees them: every field not being deleted that has a key, in order.
 *
 * Each entry carries the type's label from the type menu, which says why a
 * field no rule can read is unavailable.
 *
 * The list is a MobX computed that reads keys, types, options and rules but
 * not names, which each entry exposes through a getter. Renaming a field
 * therefore keeps the same array, so `ruleGraphFor` reuses its candidates and
 * only the editors that show that name re-render.
 */
export function templateRuleFields(owner: FieldOwner): ReadonlyArray<RuleEditorField> {
  let list = lists.get(owner);
  if (!list) {
    list = computed(() =>
      (owner.fields ?? [])
        .filter(
          (field): field is FieldModel =>
            field instanceof FieldModel && !field.deleteFieldRequest && field.fieldKey !== null,
        )
        .map(
          (field): RuleEditorField => ({
            key: field.fieldKey ?? "",
            get name() {
              return field.name;
            },
            canBeSource: sourceKindOfType(field.type) !== "NONE",
            sourceKind: sourceKindOfType(field.type),
            options: usableOptions(field.options.map((option) => option.value)),
            rule: field.displayRule,
            typeLabel: FIELD_LABEL[field.fieldType],
          }),
        ),
    );
    lists.set(owner, list);
  }
  return list.get();
}
