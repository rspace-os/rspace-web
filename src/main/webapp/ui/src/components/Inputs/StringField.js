//@flow

import React, { type Node } from "react";
import TextField from "@mui/material/TextField";
import NoValue from "../../components/NoValue";
import { type Sx } from "../../util/styles";

export type StringFieldArgs = {|
  // required
  value: string,

  // optional
  autoFocus?: boolean,
  disabled?: boolean,
  error?: boolean,
  InputProps?: {|
    sx?: Sx,
    startAdornment?: Node,
    endAdornment?: Node,
  |},
  name?: string,
  noValueLabel?: ?string,
  onBlur?: () => void,
  onChange?: ({| target: { value: string } |}) => void,
  onFocus?: ({| target: HTMLInputElement |}) => void,
  onKeyDown?: (KeyboardEvent) => void,
  size?: "small" | "medium",
  variant?: "filled" | "outlined" | "standard",
  minLength?: number,
  "data-testid"?: string,
  fullWidth?: boolean,
  id?: string,
|};

export default function StringField({
  value,
  disabled = false,
  error,
  InputProps,
  name,
  noValueLabel,
  onBlur,
  onChange,
  onFocus,
  onKeyDown,
  variant = disabled ? "standard" : "outlined",
  ...props
}: StringFieldArgs): Node {
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
      InputProps={InputProps}
      {...props}
    />
  );
}
