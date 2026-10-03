import ClearIcon from "@mui/icons-material/Clear";
import UndoIcon from "@mui/icons-material/Undo";
import Visibility from "@mui/icons-material/Visibility";
import VisibilityOff from "@mui/icons-material/VisibilityOff";
import IconButton from "@mui/material/IconButton";
import InputAdornment from "@mui/material/InputAdornment";
import TextField, { type TextFieldProps } from "@mui/material/TextField";
import type React from "react";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { Secret } from "../../util/secret";

export type { Secret } from "../../util/secret";
export { secretAfterSave } from "../../util/secret";

/*
 * V is string for a secret that is only ever entered (a login password), so
 * the field never emits null; it is Secret where a stored secret is edited.
 */
type SecretFieldArgs<V extends Secret> = Omit<
  TextFieldProps,
  "type" | "value" | "onChange" | "slotProps" | "label" | "inputRef"
> & {
  value: V;
  /** Also names the field's buttons, so it must be text; they fall back to "secret" without it. */
  label?: string;
  /**
   * Offer a clear button on a stored secret (default true). Turn it off where the secret is
   * required, as clearing it would leave a form that cannot be saved.
   */
  clearable?: boolean;
} & (
    | { readOnly?: false; onChange: (value: V) => void }
    /** Shows a secret the user may reveal but not edit, e.g. one generated for them. */
    | { readOnly: true; onChange?: undefined }
  );

/**
 * A password input with a show/hide toggle. A stored secret renders as an
 * empty field with an "(unchanged)" placeholder, and emptying the field again
 * returns it to that state; the clear button is the only way to post "" and
 * remove it, after which it offers to undo the clear.
 */
export default function SecretField<V extends Secret>({
  value,
  onChange,
  label,
  placeholder,
  disabled,
  clearable = true,
  readOnly = false,
  ...props
}: SecretFieldArgs<V>): React.ReactNode {
  const { t } = useTranslation("common");
  const [show, setShow] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const buttonName = label ?? t("inputs.secretField.defaultName");
  const isUnchanged = value === null;
  // whether the server holds a secret for this field; once seen, emptying the field means "keep it"
  const [hasStoredSecret, setHasStoredSecret] = useState(isUnchanged);
  // whether the user has cleared the stored secret, which they can undo
  const [cleared, setCleared] = useState(false);
  if (isUnchanged && (!hasStoredSecret || cleared || show)) {
    setHasStoredSecret(true);
    setCleared(false);
    setShow(false);
  }
  // the parent has reset the field to having no secret, e.g. on reuse for another config
  if (value === "" && hasStoredSecret && !cleared) setHasStoredSecret(false);

  return (
    <TextField
      {...props}
      label={label}
      inputRef={inputRef}
      disabled={disabled}
      type={show && !isUnchanged ? "text" : "password"}
      value={isUnchanged ? "" : value}
      placeholder={isUnchanged ? t("inputs.secretField.unchanged") : placeholder}
      onChange={({ target }) => {
        // null is only emitted once the field has held null, so V includes it
        onChange?.((target.value === "" && hasStoredSecret && !cleared ? null : target.value) as V);
      }}
      slotProps={{
        // MUI hides the placeholder until the label shrinks
        inputLabel: { shrink: isUnchanged || placeholder !== undefined ? true : undefined },
        htmlInput: { readOnly },
        input: {
          endAdornment: (
            <InputAdornment position="end">
              {clearable && !readOnly && hasStoredSecret && (
                <IconButton
                  aria-label={
                    cleared
                      ? t("inputs.secretField.undoClear", { label: buttonName })
                      : t("inputs.secretField.clear", { label: buttonName })
                  }
                  onClick={() => {
                    setCleared(!cleared);
                    onChange?.((cleared ? null : "") as V);
                    inputRef.current?.focus();
                  }}
                  disabled={disabled}
                  size="small"
                >
                  {cleared ? <UndoIcon fontSize="small" /> : <ClearIcon fontSize="small" />}
                </IconButton>
              )}
              <IconButton
                aria-label={
                  show
                    ? t("inputs.secretField.hide", { label: buttonName })
                    : t("inputs.secretField.show", { label: buttonName })
                }
                onClick={() => setShow(!show)}
                disabled={disabled || isUnchanged}
                edge="end"
                size="small"
              >
                {show ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
              </IconButton>
            </InputAdornment>
          ),
        },
      }}
    />
  );
}
