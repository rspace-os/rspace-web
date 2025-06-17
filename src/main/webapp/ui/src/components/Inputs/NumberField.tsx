import React from "react";
import TextField from "@mui/material/TextField";
import NoValue from "../../components/NoValue";

export type NumberFieldArgs = {
  // required
  value: string | number;

  // optional
  autoFocus?: boolean;
  datatestid?: string;
  disabled?: boolean;
  placeholder?: string;
  error?: boolean;
  fullWidth?: boolean;
  helperText?: React.ReactNode;
  inputProps?: {
    inputMode?:
      | "text"
      | "search"
      | "email"
      | "tel"
      | "url"
      | "none"
      | "numeric"
      | "decimal";
    step?: number | "any";
    lang?: string;
    min?: number;
    max?: number;
    style?: object;
  };
  InputProps?: {
    startAdornment?: React.ReactNode;
    endAdornment?: React.ReactNode;
  };
  name?: string;
  onChange?: (
    /*
     * The returned value has to be a string because it is perfectly valid
     * for the user to input a string that is not a valid encoding of a
     * number on their way to entering something value, and that should be
     * storable in state variables. Examples include the empty string and
     * strings ending in the letter "e".
     */
    event: React.ChangeEvent<HTMLInputElement>
  ) => void;
  onFocus?: (event: React.FocusEvent<HTMLInputElement>) => void;
  onBlur?: (event: React.FocusEvent<HTMLInputElement>) => void;
  onKeyDown?: (event: { key: string }) => void;
  size?: "small" | "medium";
  variant?: "filled" | "outlined" | "standard";
  ariaLabel?: string;
  className?: string;
  id?: string;
  noValueLabel?: string | null;
};

export default function NumberField({
  value,
  disabled,
  placeholder,
  error = false,
  helperText = null,
  inputProps = {},
  noValueLabel,
  datatestid,
  ariaLabel,
  ...props
}: NumberFieldArgs): React.ReactNode {
  return disabled && value === "" ? (
    <NoValue label={noValueLabel ?? "None"} />
  ) : (
    <TextField
      data-test-id={datatestid}
      type="number"
      error={error}
      disabled={disabled}
      placeholder={placeholder}
      helperText={helperText}
      value={value}
      inputProps={{
        /*
         * This has to be text because "decimal" and "numeric" do not guarantee
         * a minus key, and most of numerical field support negative values.
         */
        inputMode: "text",
        lang: "en", // force dot in decimal numbers in compatible browsers
        ["aria-label"]: ariaLabel,
        ...inputProps,
      }}
      {...props}
      variant={props.variant ?? "standard"}
      onInput={(e: React.ChangeEvent<HTMLInputElement>) => {
        /*
         * onChange only fires if the field is in a valid state and so it would
         * be impossible to show an error state if the user inputted an invalid
         * input like "2e" or a value that does not adhere to the min/max/step
         * rules. Instead, by using onInput, it always fires and callers of
         * this component can use `target.checkValidity()` inside their
         * onChange props to check whether the user's current input is valid
         * even if `target.value` is the empty string.
         */
        props.onChange?.(e);
      }}
      onChange={() => {}}
    />
  );
}
