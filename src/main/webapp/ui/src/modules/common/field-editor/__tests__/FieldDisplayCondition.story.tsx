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

/** GMO permit's condition, which reads Is this a GMO?; Notes, below it in the Field menu, is unavailable. */
export function FieldDisplayConditionStory({
  onChange,
}: {
  onChange: (rule: FieldDisplayRule | null) => void;
}): React.ReactNode {
  const [fields, setFields] = useState(initialFields);
  return (
    <StyledEngineProvider injectFirst>
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
