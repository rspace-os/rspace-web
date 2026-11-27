import type { Namespace, TFunction } from "i18next";
import type { ResolvedFieldConfig } from "@/modules/common/collection/config";
import type { useFieldLabels } from "@/modules/common/collection/useFieldLabels";
import { customFieldOptionLabel, type RuntimeFieldSourceGroup } from "./CustomFieldPicker";
import type { FilterSelectOption } from "./FilterSelect";

// Selector names match [A-Za-z][A-Za-z0-9_-]* (dotted for related fields), so a value with ":" is never a field.
const CUSTOM_FIELD_OPTION_PREFIX = "customField:";

/** The field select's value for a runtime-field group's "Custom field…" option. */
export function customFieldOptionValue(groupKey: string): string {
  return `${CUSTOM_FIELD_OPTION_PREFIX}${groupKey}`;
}

/** The runtime-field group a field select value chooses, or null for a field. */
export function customFieldOptionGroup(value: string): string | null {
  return value.startsWith(CUSTOM_FIELD_OPTION_PREFIX) ? value.slice(CUSTOM_FIELD_OPTION_PREFIX.length) : null;
}

type FieldLabels = ReturnType<typeof useFieldLabels>;

function fieldGroupKey(via: { name: string } | undefined, groupLabelKey: string | null): string {
  if (via) return `relationship:${via.name}`;
  if (groupLabelKey) return `builtin:${groupLabelKey}`;
  return "ungrouped";
}

export function filterFieldOptions<TDocument>(
  fields: readonly ResolvedFieldConfig<TDocument>[],
  groups: readonly RuntimeFieldSourceGroup[],
  { t, text, fieldOptionLabel }: { t: TFunction<Namespace> } & Pick<FieldLabels, "text" | "fieldOptionLabel">,
): FilterSelectOption[] {
  return [
    ...fields
      .filter((candidate) => candidate.origin?.namespace === undefined)
      .map((candidate): FilterSelectOption => {
        const via = candidate.origin?.via;
        const groupLabelKey = via ? null : (candidate.origin?.groupLabelKey ?? null);
        return {
          value: candidate.name,
          label: fieldOptionLabel(candidate),
          groupKey: fieldGroupKey(via, groupLabelKey),
          groupLabelKey,
          groupLabel: via ? text(via.label) : undefined,
        };
      }),
    ...groups.map((group): FilterSelectOption => {
      const viaLabel = group.via ? text(group.via.label) : "";
      return {
        value: customFieldOptionValue(group.key),
        label: t("tableList.filters.customField.option"),
        selectedLabel: customFieldOptionLabel(group.via, t, text),
        groupKey: group.via ? `relationship:${group.via.name}` : "builtin:tableList.fieldGroups.customFields",
        groupLabelKey: viaLabel === "" ? "tableList.fieldGroups.customFields" : null,
        groupLabel: viaLabel || undefined,
      };
    }),
  ];
}
