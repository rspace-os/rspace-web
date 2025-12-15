import useStores from "../../../stores/use-stores";
import TextField from "@mui/material/TextField";
import { observer } from "mobx-react-lite";
import React, { type ReactNode } from "react";

type TemplateNameArgs = {
  disabled?: boolean;
};

function TemplateName({ disabled }: TemplateNameArgs): ReactNode {
  const { importStore } = useStores();
  const error =
    importStore.importData?.createNewTemplate &&
    !importStore.importData.validTemplateName;

  return (
    <TextField
      variant="standard"
      label="Template Name"
      fullWidth
      disabled={disabled}
      error={error}
      id="templateNameField" // for a11y
      value={importStore.importData?.templateName ?? ""}
      helperText={
        error
          ? "A name for the new template is required and should be no longer than 255 characters."
          : ""
      }
      onChange={({ target }) => {
        if (
          target instanceof HTMLInputElement &&
          Boolean(importStore.importData)
        )
          importStore.importData?.setTemplateName(target.value);
      }}
    />
  );
}

export default observer(TemplateName);
