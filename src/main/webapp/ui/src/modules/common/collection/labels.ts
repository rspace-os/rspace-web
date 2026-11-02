import i18n from "@/modules/common/i18n";
import type { CollectionTranslationKey } from "./config";

export function hierarchicalFieldLabel(viaLabel: string, ownLabel: string): string {
  return viaLabel === ""
    ? ownLabel
    : i18n.t("common:tableList.fields.hierarchicalLabel", { owner: viaLabel, field: ownLabel });
}

export function fieldLabel(
  field: { labelKey: string; label?: string },
  translate: (key: CollectionTranslationKey) => string,
): string {
  return field.label ?? translate(field.labelKey as CollectionTranslationKey);
}

export function fieldOptionLabel(
  field: { labelKey: string; label?: string; origin?: { sourceLabel?: string } },
  translate: (key: CollectionTranslationKey) => string,
): string {
  const label = fieldLabel(field, translate);
  return field.origin?.sourceLabel
    ? i18n.t("common:tableList.fields.labelWithSource", { label, source: field.origin.sourceLabel })
    : label;
}
