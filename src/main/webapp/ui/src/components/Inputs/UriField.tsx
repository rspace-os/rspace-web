import React from "react";
import TextField from "@mui/material/TextField";
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

export default function UriField({
  disabled,
  value,
  onChange,
  name,
  id,
}: UriFieldArgs): React.ReactNode {
  return disabled && !value ? (
    <NoValue label="None" />
  ) : (
    <TextField
      variant={disabled ? "standard" : "outlined"}
      size="small"
      disabled={disabled}
      value={value}
      onChange={onChange}
      name={name}
      inputProps={{
        inputMode: "url",
      }}
      id={id}
    />
  );
}
