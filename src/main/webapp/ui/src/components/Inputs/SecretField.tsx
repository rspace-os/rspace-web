import ClearIcon from "@mui/icons-material/Clear";
import Visibility from "@mui/icons-material/Visibility";
import VisibilityOff from "@mui/icons-material/VisibilityOff";
import IconButton from "@mui/material/IconButton";
import InputAdornment from "@mui/material/InputAdornment";
import TextField, { type TextFieldProps } from "@mui/material/TextField";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";

/**
 * A secret as the server exchanges it (RSDEV-1525): `null` is a stored secret,
 * which is never returned to the browser, and posting `null` back keeps it;
 * `""` means there is none; any other string is a new secret being entered.
 */
export type Secret = string | null;

/** What a secret field should hold once its value has been saved: stored (null), or "" if cleared. */
export function secretAfterSave(value: Secret): Secret {
  return value === "" ? "" : null;
}

/*
 * V is string for a secret that is only ever entered (a login password), so
 * the field never emits null; it is Secret where a stored secret is edited.
 */
type SecretFieldArgs<V extends Secret> = Omit<TextFieldProps, "type" | "value" | "onChange" | "slotProps"> & {
  value: V;
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
 * remove it.
 */
export default function SecretField<V extends Secret>({
  value,
  onChange,
  placeholder,
  disabled,
  clearable = true,
  readOnly = false,
  ...props
}: SecretFieldArgs<V>): React.ReactNode {
  const { t } = useTranslation("common");
  const [show, setShow] = useState(false);
  const isUnchanged = value === null;
  // whether the server holds a secret for this field; once seen, emptying the field means "keep it"
  const [hasStoredSecret, setHasStoredSecret] = useState(isUnchanged);
  if (isUnchanged && !hasStoredSecret) setHasStoredSecret(true);

  return (
    <TextField
      {...props}
      disabled={disabled}
      type={show && !isUnchanged ? "text" : "password"}
      value={isUnchanged ? "" : value}
      placeholder={isUnchanged ? t("inputs.secretField.unchanged") : placeholder}
      onChange={({ target }) => {
        // null is only emitted once the field has held null, so V includes it
        onChange?.((target.value === "" && hasStoredSecret ? null : target.value) as V);
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
                  aria-label={t("inputs.secretField.clear")}
                  onClick={() => {
                    setHasStoredSecret(false);
                    onChange?.("" as V);
                  }}
                  disabled={disabled}
                  size="small"
                >
                  <ClearIcon fontSize="small" />
                </IconButton>
              )}
              <IconButton
                aria-label={show ? t("inputs.secretField.hide") : t("inputs.secretField.show")}
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
