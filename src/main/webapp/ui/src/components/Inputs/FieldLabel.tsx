import React from "react";
import FormLabel from "@mui/material/FormLabel";
import { type SxProps, type Theme } from "@mui/material/styles";
import { Heading } from "../DynamicHeadingLevel";

/**
 * The label of a form field, rendered consistently across all of RSpace's form
 * field components (FormField, the Inventory FormField/BatchFormField, and the
 * custom FormControl). Previously each of these rendered their own `<FormLabel>`
 * with subtly different element choices and styling; centralising it here keeps
 * the visual behaviour of every form field label the same.
 *
 * The element that the label renders as depends on the kind of field:
 *
 *   - When the field is disabled (the Inventory forms use disabled fields to
 *     render a read-only preview rather than an inaccessible state) there is no
 *     interactive input for a `<label>` to point at, so the label is rendered
 *     as a Heading. It is styled to read as a field label rather than as a
 *     large page heading.
 *   - When the field groups several interactive elements of equal standing
 *     (`asFieldset`), the label is rendered as a `<legend>`.
 *   - Otherwise the label is a plain `<label>` whose `for` attribute points at
 *     the input via `htmlFor`.
 */
export type FieldLabelProps = {
  /**
   * The label content. Usually a short string, but a node is permitted so that
   * callers can compose additional content (e.g. explanatory text) inside the
   * label element.
   */
  children: React.ReactNode;

  /**
   * Applied to the rendered FormLabel so that it can be referenced by an
   * `aria-labelledby` attribute elsewhere.
   */
  id?: string;

  /**
   * When true, the label is rendered as a Heading rather than an interactive
   * `<label>`/`<legend>`, for fields that are disabled / shown as a read-only
   * preview.
   */
  disabled?: boolean;

  /**
   * When true (and not disabled), the label is rendered as a `<legend>`, for
   * fields that wrap several interactive elements of equal standing.
   */
  asFieldset?: boolean;

  /**
   * The id of the input element this label is for; sets the `for` attribute.
   * It has no effect when `disabled` or `asFieldset` is true, as neither a
   * Heading nor a `<legend>` references an input via a `for` attribute.
   */
  htmlFor?: string;

  /** Adorns the label with the usual required-field asterisk. */
  required?: boolean;

  /** Extra styling, merged after the standard field-label styling. */
  sx?: SxProps<Theme>;

  /** Forwarded to the underlying FormLabel `classes` prop. */
  classes?: { root?: string };
};

export default function FieldLabel({
  children,
  id,
  disabled,
  asFieldset,
  htmlFor,
  required,
  sx,
  classes,
}: FieldLabelProps): React.ReactNode {
  const component = disabled
    ? (props: { children: React.ReactNode; id?: string }) => (
        <Heading
          {...props}
          sx={{ typography: "subtitle2", textTransform: "uppercase", fontWeight: "bold" }}
        />
      )
    : asFieldset
      ? "legend"
      : "label";

  return (
    <FormLabel
      id={id}
      component={component}
      classes={classes}
      required={required}
      // reset styles added by the browser when setting the component prop
      sx={[{ mt: 0, textAlign: "left" }, ...(Array.isArray(sx) ? sx : [sx])]}
      {...(!disabled && !asFieldset && typeof htmlFor === "string"
        ? { htmlFor }
        : {})}
    >
      {children}
    </FormLabel>
  );
}
