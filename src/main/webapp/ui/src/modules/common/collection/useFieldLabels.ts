import type { Namespace } from "i18next";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { CollectionTranslationKey, FieldOrigin, LabelText, RelationshipLabel } from "./config";

type FieldLabelInput = {
  labelKey?: CollectionTranslationKey;
  label?: string;
  derivedLabel?: LabelText;
  origin?: Pick<FieldOrigin, "sourceLabel" | "via">;
};

export function useFieldLabels() {
  const { t } = useTranslation<Namespace>("common");

  return useMemo(() => {
    const text = (label: LabelText): string => ("text" in label ? label.text : t(label.key));
    const relationshipLabel = (via: RelationshipLabel, label: string) => {
      const owner = text(via.label);
      return owner === "" ? label : t("tableList.fields.hierarchicalLabel", { owner, field: label });
    };
    const ownFieldLabel = (field: FieldLabelInput): string => {
      if (field.derivedLabel) return text(field.derivedLabel);
      if (field.labelKey === undefined) return "";
      return t(field.labelKey);
    };
    const fieldLabel = (field: FieldLabelInput): string => {
      if (field.label !== undefined) return field.label;

      const ownLabel = ownFieldLabel(field);
      const via = field.origin?.via;
      return via ? relationshipLabel(via, ownLabel) : ownLabel;
    };
    const fieldOptionLabel = (field: FieldLabelInput): string => {
      const label = fieldLabel(field);
      const source = field.origin?.sourceLabel;
      return source ? t("tableList.fields.labelWithSource", { label, source }) : label;
    };
    return { text, fieldLabel, fieldOptionLabel };
  }, [t]);
}
