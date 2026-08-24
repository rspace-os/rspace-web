import TextField from "@mui/material/TextField";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

type TemplateNameArgs = {
  disabled?: boolean;
  value: string;
  onChange: (value: string) => void;
  error: boolean;
};

function TemplateName({ disabled, value, onChange, error }: TemplateNameArgs): ReactNode {
  const { t } = useTranslation("inventory");

  return (
    <TextField
      variant="standard"
      label={t("import.fields.templateName")}
      fullWidth
      disabled={disabled}
      error={error}
      id="templateNameField"
      value={value}
      helperText={error ? t("import.fields.templateNameValidation") : ""}
      onChange={({ target }) => {
        if (target instanceof HTMLInputElement) onChange(target.value);
      }}
    />
  );
}

export default TemplateName;
