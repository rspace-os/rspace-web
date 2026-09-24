import TextField, { type TextFieldProps } from "@mui/material/TextField";
import type React from "react";

// Amounts: at most 3 decimal places, since quantities persist in a DECIMAL(19,3) column and the
// server rejects anything finer (ADR 0011 D5), so a fourth decimal is not typed rather than accepted
// and then silently blocking the wizard's Next. Temperatures: whole degrees only, as the sample
// form's storage temperature takes them.
const PARTIAL_AMOUNT = /^\d*(\.\d{0,3})?$/;
const PARTIAL_WHOLE_DEGREES = /^-?\d*$/;

/**
 * A numeric entry field, deliberately a text input and not type="number".
 *
 * A number input reports an empty value for anything that is not yet a complete number, so a lone
 * minus sign is erased the moment the last digit is deleted and cannot be typed first (Cryopreserve
 * runs at -18 °C or colder), and a controlled one fed its parsed value back drops every "0" typed
 * after the decimal point ("1.0" parses to 1). Holding the raw string lets the user type "-", "1.0"
 * and "1.05" and see exactly that. Keystrokes the field cannot accept are refused, not mangled.
 *
 * The value is the raw string, not a number: only the caller knows what an incomplete entry such
 * as "-" should mean. Every other TextField prop is passed straight through, which is how a caller
 * supplies its unit adornment through the `slotProps.input` slot.
 */
export default function NumericTextField({
  value,
  onChange,
  allowNegative = true,
  ...textFieldProps
}: {
  value: string;
  onChange: (value: string) => void;
  /** True for a temperature (whole degrees, may be negative); false for an amount (3 dp, never negative). */
  allowNegative?: boolean;
} & Omit<TextFieldProps, "value" | "onChange">): React.ReactNode {
  const partial = allowNegative ? PARTIAL_WHOLE_DEGREES : PARTIAL_AMOUNT;
  return (
    <TextField
      fullWidth
      {...textFieldProps}
      value={value}
      onChange={(e) => {
        if (partial.test(e.target.value)) onChange(e.target.value);
      }}
    />
  );
}
