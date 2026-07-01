import TextField from "@mui/material/TextField";
import { observer } from "mobx-react-lite";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import useStores from "../../../stores/use-stores";

type TemplateNameArgs = {
  disabled?: boolean;
};

function TemplateName({ disabled }: TemplateNameArgs): ReactNode {
  const { t } = useTranslation("inventory");
  const { importStore } = useStores();
  const error = importStore.importData?.createNewTemplate && !importStore.importData.validTemplateName;

  return (
    <TextField
      variant="standard"
      label={t("import.fields.templateName")}
      fullWidth
      disabled={disabled}
      error={error}
      id="templateNameField" // for a11y
      value={importStore.importData?.templateName ?? ""}
      helperText={error ? t("import.fields.templateNameValidation") : ""}
      onChange={({ target }) => {
        if (target instanceof HTMLInputElement && importStore.importData)
          importStore.importData?.setTemplateName(target.value);
      }}
    />
  );
}

export default observer(TemplateName);
