import TextField from "@mui/material/TextField";
import type React from "react";
// biome-ignore lint/style/useImportType: initial biome migration
import { ColumnFieldMap } from "../../../stores/models/ImportModel";

type FieldNameStringFieldArgs = {
  columnFieldMap: ColumnFieldMap;
};

export default function FieldNameStringField({ columnFieldMap }: FieldNameStringFieldArgs): React.ReactNode {
  const error = !columnFieldMap.validFieldName;
  return (
    <TextField
      value={columnFieldMap.fieldName}
      onChange={({ target }) => {
        if (target instanceof HTMLInputElement) columnFieldMap.setFieldName(target.value);
      }}
      variant="standard"
      error={error}
      helperText={error ? "You either already have a field with that name or that name is not permitted." : ""}
    />
  );
}
