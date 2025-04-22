import FormControl from "./FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import { observer } from "mobx-react-lite";
import React from "react";

export type InputWrapperArgs = {
  label?: string;
  children: React.ReactNode;
  value?: unknown;
  error?: boolean;
  helperText?: string | null;
  maxLength?: number;
  disabled?: boolean;
  actions?: React.ReactNode;
  inline?: boolean;
  explanation?: React.ReactNode;
  dataTestId?: string;
  required?: boolean;
};

function InputWrapper({
  label,
  children,
  value = {},
  error,
  helperText,
  maxLength,
  disabled = false,
  actions = <div></div>,
  inline = false,
  explanation,
  dataTestId,
  required,
}: InputWrapperArgs): React.ReactNode {
  /*
   * We pass `value` as `mightBeString`, rather than pulling from props, so
   * that the information about whether it is a string or not is preserved by
   * the type checker. See flow docs on `%checks`.
   */
  function showCharacterCount(mightBeString: unknown): mightBeString is string {
    return (
      !disabled &&
      typeof maxLength === "number" &&
      typeof mightBeString === "string"
    );
  }

  return (
    <FormControl
      label={label}
      inline={inline}
      actions={actions}
      dataTestId={dataTestId}
      required={required}
      error={error}
      explanation={explanation}
    >
      {children}
      {!error && showCharacterCount(value) && (
        <FormHelperText component="div">
          {`${value.length} / ${maxLength ?? ""}`}
        </FormHelperText>
      )}
      {error && showCharacterCount(value) && (
        <FormHelperText error component="div">
          {helperText ??
            `No more than ${maxLength ?? ""} characters permitted.`}
        </FormHelperText>
      )}
      {error && !showCharacterCount(value) && (
        <FormHelperText error component="div">
          {helperText ?? ""}
        </FormHelperText>
      )}
    </FormControl>
  );
}

export default observer(InputWrapper);
