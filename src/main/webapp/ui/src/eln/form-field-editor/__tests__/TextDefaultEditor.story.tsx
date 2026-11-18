import { ThemeProvider } from "@mui/material/styles";
import { type ReactNode, useState } from "react";
import materialTheme from "@/theme";
import TextDefaultEditor from "../TextDefaultEditor";

/** A text field's default, edited in place; `onChange` sees each value the editor reports. */
export function TextDefaultEditorStory({
  initial = "",
  onChange,
}: {
  initial?: string;
  onChange?: (value: string) => void;
}): ReactNode {
  const [value, setValue] = useState(initial);
  return (
    <ThemeProvider theme={materialTheme}>
      <TextDefaultEditor
        fieldName="Notes"
        value={value}
        editable
        onChange={(next) => {
          onChange?.(next);
          setValue(next);
        }}
      />
    </ThemeProvider>
  );
}
