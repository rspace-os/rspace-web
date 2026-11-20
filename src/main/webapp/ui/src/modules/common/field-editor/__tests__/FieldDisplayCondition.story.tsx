import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import type React from "react";
import { useState } from "react";
import { createDisplayRule, type FieldDisplayRule } from "@/modules/common/field-rules/displayRule";
import materialTheme from "@/theme";
import FieldDisplayCondition from "../FieldDisplayCondition";
import type { RuleEditorField } from "../ruleEditing";

const initialFields: ReadonlyArray<RuleEditorField> = [
  { key: "gmo", name: "Is this a GMO?", canBeSource: true, options: ["Yes", "No"], rule: null },
  { key: "notes", name: "Notes", canBeSource: false, options: [], rule: null, typeLabel: "Formatted text" },
  {
    key: "permit",
    name: "GMO permit",
    canBeSource: false,
    options: [],
    rule: createDisplayRule("gmo", "IS_ANY_OF", ["Yes"]),
  },
];

/** Only Notes besides GMO permit, so the Field menu has nothing to choose. */
export const noAvailableFields: ReadonlyArray<RuleEditorField> = [
  initialFields[1],
  { ...initialFields[2], rule: null },
];

/**
 * GMO permit's condition, which reads Is this a GMO?; Notes, below it in the
 * Field menu, is unavailable. Styled in cascade layers, as the app is.
 */
export function FieldDisplayConditionStory({
  onChange,
  fields: startingFields = initialFields,
}: {
  onChange: (rule: FieldDisplayRule | null) => void;
  fields?: ReadonlyArray<RuleEditorField>;
}): React.ReactNode {
  const [fields, setFields] = useState(startingFields);
  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <FieldDisplayCondition
          fieldKey="permit"
          fields={fields}
          editable
          onChange={(rule) => {
            onChange(rule);
            setFields((current) => current.map((field) => (field.key === "permit" ? { ...field, rule } : field)));
          }}
        />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
