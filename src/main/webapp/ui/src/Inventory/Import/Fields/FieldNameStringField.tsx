import TextField from "@mui/material/TextField";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { ColumnFieldMap } from "../../../stores/models/ImportModel";

type FieldNameStringFieldArgs = {
  columnFieldMap: ColumnFieldMap;
};

export default function FieldNameStringField({ columnFieldMap }: FieldNameStringFieldArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const error = !columnFieldMap.validFieldName;
  return (
    <TextField
      value={columnFieldMap.fieldName}
      onChange={({ target }) => {
        if (target instanceof HTMLInputElement) columnFieldMap.setFieldName(target.value);
      }}
      variant="standard"
      error={error}
      helperText={error ? t("import.fields.fieldNameValidation") : ""}
    />
  );
}
