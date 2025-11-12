import { TextField as MuiTextField, TextFieldProps as MuiTextFieldProps } from "@mui/material";
import { FieldApi } from "@tanstack/react-form";

/**
 * Props for the TextField component
 * Extends Material UI TextField props but makes some required for form integration
 */
export interface TextFieldProps extends Omit<MuiTextFieldProps, "name" | "value" | "onChange" | "onBlur" | "error" | "helperText"> {
  /** The field instance from TanStack Form */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  field: FieldApi<any, any, any, any, any, any, any, any, any, any, any, any, any, any, any, any, any, any, any, any, any, any, any>;
  /** Label for the text field */
  label?: string;
  /** Placeholder text */
  placeholder?: string;
  /** Whether the field is required */
  required?: boolean;
  /** Whether the field is disabled */
  disabled?: boolean;
  /** Custom helper text (will be overridden by validation errors) */
  helperText?: string;
}

/**
 * TextField component that integrates Material UI TextField with TanStack Form
 *
 * @example
 * ```tsx
 * <form.Field name="email">
 *   {(field) => (
 *     <TextField
 *       field={field}
 *       label="Email"
 *       type="email"
 *       required
 *     />
 *   )}
 * </form.Field>
 * ```
 *
 * @example With validation
 * ```tsx
 * <form.Field
 *   name="username"
 *   validators={{
 *     onChange: ({ value }) =>
 *       value.length < 3 ? "Username must be at least 3 characters" : undefined,
 *   }}
 * >
 *   {(field) => (
 *     <TextField
 *       field={field}
 *       label="Username"
 *       required
 *     />
 *   )}
 * </form.Field>
 * ```
 */
export function TextField({
  field,
  label,
  placeholder,
  required = false,
  disabled = false,
  helperText,
  ...muiProps
}: TextFieldProps) {
  const errorMessage = field.state.meta.errors?.[0] as string | undefined;
  const hasError = field.state.meta.errors.length > 0;

  return (
    <MuiTextField
      name={field.name as string}
      value={(field.state.value as string) ?? ""}
      onChange={(e) => field.handleChange(e.target.value)}
      onBlur={field.handleBlur}
      error={hasError}
      helperText={hasError ? errorMessage : helperText}
      label={label}
      placeholder={placeholder}
      required={required}
      disabled={disabled}
      fullWidth
      {...muiProps}
    />
  );
}

