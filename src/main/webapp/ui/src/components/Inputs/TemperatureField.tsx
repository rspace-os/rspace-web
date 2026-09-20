import TextField, { type TextFieldProps } from "@mui/material/TextField";
import type React from "react";

const PARTIAL_TEMPERATURE = /^-?\d*(\.\d*)?$/;

/**
 * A temperature entry field, deliberately a text input and not type="number".
 *
 * A number input reports an empty value for anything that is not yet a complete number, so a lone
 * minus sign is erased the moment the last digit is deleted, and cannot be typed as the first
 * character of a negative temperature either. Holding the raw string lets the user clear the
 * digits, leave the sign in place, and keep typing. Cryopreserve runs at -18 °C or colder, so
 * passing through a lone minus is part of ordinary use.
 *
 * The value is the raw string, not a number: only the caller knows what an incomplete entry such
 * as "-" should mean. Every other TextField prop is passed straight through, which is how a caller
 * supplies its unit adornment through the `slotProps.input` slot.
 */
export default function TemperatureField({
  value,
  onChange,
  ...textFieldProps
}: {
  value: string;
  onChange: (value: string) => void;
} & Omit<TextFieldProps, "value" | "onChange">): React.ReactNode {
  return (
    <TextField
      fullWidth
      {...textFieldProps}
      value={value}
      onChange={(e) => {
        if (PARTIAL_TEMPERATURE.test(e.target.value)) onChange(e.target.value);
      }}
    />
  );
}
