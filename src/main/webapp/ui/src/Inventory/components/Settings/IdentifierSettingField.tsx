import TextField from "@mui/material/TextField";
import type React from "react";
import SecretField, { type Secret } from "../../../components/Inputs/SecretField";

type IdentifierSettingFieldArgs = {
  /** Renders the value as a masked secret, e.g. the provider password or token. */
  secret: boolean;
  label: string;
  value: Secret;
  onChange: (value: Secret) => void;
  placeholder: string;
  requiredError: string;
  storedSecretExists?: boolean;
  /**
   * Whether the field must have a value, i.e. the integration is enabled. A secret can only be
   * cleared while it is not required, so clearing never leaves a form that cannot be saved.
   */
  required: boolean;
  disabled?: boolean;
};

/** One credential or connection field on an identifier provider settings card. */
export default function IdentifierSettingField({
  secret,
  label,
  value,
  onChange,
  placeholder,
  requiredError,
  storedSecretExists,
  required,
  disabled,
}: IdentifierSettingFieldArgs): React.ReactNode {
  const missing = required && value === "";
  const shared = {
    sx: { p: 0.5, m: 1 },
    size: "small",
    fullWidth: true,
    variant: "outlined",
    label,
    value,
    placeholder,
    disabled,
    error: missing,
    helperText: missing ? requiredError : null,
  } as const;
  return secret ? (
    <SecretField
      {...shared}
      autoComplete="new-password"
      clearable={!required}
      storedSecretExists={storedSecretExists}
      onChange={onChange}
    />
  ) : (
    <TextField
      {...shared}
      autoComplete="off"
      onChange={({ target }) => onChange(target.value)}
      slotProps={{ inputLabel: { shrink: true } }}
    />
  );
}
