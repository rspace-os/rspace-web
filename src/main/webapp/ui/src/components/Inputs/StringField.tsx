import React from "react";
import TextField, { TextFieldProps } from "@mui/material/TextField";
import NoValue from "../../components/NoValue";

export type StringFieldArgs = {
  value: string;
  autoFocus?: boolean;
  disabled?: boolean;
  error?: boolean;
  name?: string;
  noValueLabel?: string | null | undefined;
  onBlur?: () => void;
  onChange?: (event: { target: { value: string } }) => void;
  onFocus?: (event: React.FocusEvent<HTMLInputElement>) => void;
  onKeyDown?: (event: React.KeyboardEvent<HTMLDivElement>) => void;
  size?: "small" | "medium";
  slotProps?: TextFieldProps["slotProps"];
  variant?: "filled" | "outlined" | "standard";
  minLength?: number;
  "data-testid"?: string;
  fullWidth?: boolean;
  id?: string;
};

export default function StringField({
  value,
  disabled = false,
  error,
  name,
  noValueLabel,
  onBlur,
  onChange,
  onFocus,
  onKeyDown,
  slotProps,
  variant = disabled ? "standard" : "outlined",
  ...props
}: StringFieldArgs): React.ReactNode {
  return disabled && !value ? (
    <NoValue label={noValueLabel ?? "None"} />
  ) : (
    <TextField
      onBlur={onBlur}
      variant={variant ?? "stardard"}
      disabled={disabled}
      value={value}
      onChange={onChange}
      name={name}
      size="small"
      onFocus={onFocus}
      onKeyDown={onKeyDown}
      error={error}
      {...props}
      slotProps={slotProps}
    />
  );
}
