import TextField from "@mui/material/TextField";
import type React from "react";
import { useTranslation } from "react-i18next";
import NoValue from "../../components/NoValue";

export type UriFieldArgs = {
  // required
  value: string;

  // optional
  disabled?: boolean;
  name?: string;
  onChange?: (event: React.ChangeEvent<HTMLInputElement>) => void;
  id?: string;
};

export default function UriField({ disabled, value, onChange, name, id }: UriFieldArgs): React.ReactNode {
  const { t } = useTranslation("common");

  return disabled && !value ? (
    <NoValue label={t("values.none")} />
  ) : (
    <TextField
      variant={disabled ? "standard" : "outlined"}
      size="small"
      disabled={disabled}
      value={value}
      onChange={onChange}
      name={name}
      id={id}
      slotProps={{
        htmlInput: {
          inputMode: "url",
        },
      }}
    />
  );
}
