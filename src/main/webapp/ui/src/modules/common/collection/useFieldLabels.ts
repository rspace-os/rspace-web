import type { Namespace } from "i18next";
import { useCallback } from "react";
import { useTranslation } from "react-i18next";
import type { FieldOrigin, LabelText, RelationshipLabel } from "./config";

type FieldLabelInput = {
  labelKey: string;
  label?: string;
  derivedLabel?: LabelText;
  origin?: Pick<FieldOrigin, "sourceLabel" | "via">;
};

export function useFieldLabels() {
  const { t } = useTranslation<Namespace>("common");

  const text = useCallback(
    (label: LabelText): string => {
      if ("text" in label) return label.text;
      const key = label.key as never;
      const options = { defaultValue: label.key };
      return t(key, options);
    },
    [t],
  );
  const relationshipLabel = useCallback(
    (via: RelationshipLabel, label: string) => {
      const owner = text(via.label);
      return owner === ""
        ? label
        : t("tableList.fields.hierarchicalLabel", {
            owner,
            field: label,
          });
    },
    [t, text],
  );

  const fieldLabel = useCallback(
    (field: FieldLabelInput): string => {
      if (field.label !== undefined) return field.label;

      const ownLabel = text(field.derivedLabel ?? { key: field.labelKey });
      const via = field.origin?.via;
      return via ? relationshipLabel(via, ownLabel) : ownLabel;
    },
    [relationshipLabel, text],
  );

  const fieldOptionLabel = useCallback(
    (field: FieldLabelInput): string => {
      const label = fieldLabel(field);
      const source = field.origin?.sourceLabel;
      return source ? t("tableList.fields.labelWithSource", { label, source }) : label;
    },
    [t, fieldLabel],
  );

  return { text, fieldLabel, fieldOptionLabel };
}
