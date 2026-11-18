import Box from "@mui/material/Box";
import { action } from "mobx";
import { observer } from "mobx-react-lite";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import DisplayRulesPanel from "@/modules/common/field-editor/DisplayRulesPanel";
import { type RuleChange, type RuleEditorField, usableOptions } from "@/modules/common/field-editor/ruleEditing";
import type { Field } from "../../../stores/definitions/Field";
import FieldModel, { hasOptions } from "../../../stores/models/FieldModel";

type TemplateDisplayRulesArgs = {
  /** The template's fields, in display order. */
  fields: ReadonlyArray<Field>;
  editable: boolean;
};

/** The display rules panel of a sample or instrument template, above its fields. */
function TemplateDisplayRules({ fields, editable }: TemplateDisplayRulesArgs): ReactNode {
  const { t } = useTranslation("common");
  const liveFields = fields.filter(
    (field): field is FieldModel => field instanceof FieldModel && !field.deleteFieldRequest && field.fieldKey !== null,
  );
  if (liveFields.length === 0) return null;

  const ruleFields: Array<RuleEditorField> = liveFields.map((field) => ({
    key: field.fieldKey ?? "",
    name: field.name || t("fieldEditor.card.unnamed"),
    canBeSource: hasOptions(field.type),
    options: usableOptions(field.options.map((option) => option.value)),
    rule: field.displayRule,
  }));

  const applyChanges = action((changes: ReadonlyArray<RuleChange>) => {
    for (const { key, rule } of changes) {
      liveFields.find((field) => field.fieldKey === key)?.setDisplayRule(rule);
    }
  });

  return (
    <Box sx={{ mb: 3 }}>
      <DisplayRulesPanel fields={ruleFields} onChange={applyChanges} editable={editable} />
    </Box>
  );
}

export default observer(TemplateDisplayRules);
