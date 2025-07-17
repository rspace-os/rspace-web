import React from "react";
import BaseFormField, {
  type FormFieldArgs as BaseFormFieldArgs,
} from "./FormField";
import ChooseToEdit from "../../../components/Inputs/ChooseToEdit";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import NoValue from "../../../components/NoValue";
import { makeStyles } from "tss-react/mui";
import { Heading } from "../../../components/DynamicHeadingLevel";
import clsx from "clsx";

/**
 * This component renders form fields specifically used by the main Inventory
 * forms to create, view, and edit samples, subsamples, containers, and
 * templates. It MUST NOT be used anywhere else as it has some esoterical
 * behaviour specific to this use case. For all other forms, use the form field
 * component in ../../../components/Inputs/FormField.js
 *
 * Inventory form fields are used not just when editing and creating records
 * but also when viewing them. The fields are simply disabled and the styles
 * modified to ensure that they continue to have sufficient contrast. This is a
 * bit of hack to simplify the code but is worth keeping as it leaves open the
 * option to allow for editing each field of these forms separately, rather
 * than having to enter a completely separate edit mode.
 *
 * Furthermore, Inventory records can be batch edited with multiple records of
 * the same type being able to simultaneously have their fields set to the same
 * value. This adds more complexity that isn't necessary elsewhere.
 */

/**
 * When not disabled, all of the behaviour of the more generic form field
 * component is preserved.
 */
export type FormFieldArgs<T> = BaseFormFieldArgs<T> & {
  /**
   * Whilst disabled is included from BaseFormFieldArgs, it is required here as
   * it used by the checkbox when batch editing.
   */
  disabled: boolean;

  /**
   * If `value` is falsey and `disabled` is true then a nominal value is
   * rendered instead of a blank bit of whitespace. If this value is specified
   * then it will be shown, else the string "No Value" will be.
   *
   * If this is specified then there is no need to also pass it to the
   * component rendered by the `renderInput` prop as when `disabled` is true
   * and `value` is falsey then `renderInput` will not be invoked and each of
   * the input components (TextField, StringField, NumberField, etc) will only
   * render the noValueLabel under the same conditions i.e. it will never be
   * executed there.
   */
  noValueLabel?: string | null;

  /**
   * These form fields are designed to support both singular editing and batch
   * editing. When batch editing each field being edited must be manually
   * enabled so that each record will only be modified where those specific
   * fields have been set; all other fields are left unchanged. These two props
   * control this logic:
   *   - `canChooseWhichToEdit` sets whether the user can manually enable the
   *     field. If so, a checkbox is made available to enable the field.
   *     Whether the field is actually enabled continues to be controlled by
   *     the `disabled` prop.
   *   - `setDisabled` is the event handler of when the user has tapped the
   *     checkbox. It MUST then set the `disabled` prop to passed value.
   */
  canChooseWhichToEdit?: boolean;
  setDisabled?: (value: boolean) => void;
};

/**
 * Do note that when Inventory form fields are disabled they are shown in
 * black, not a grey colour, as disabled form fields are used to render the
 * preview mode of the main UI. Coupled with the fact that this component
 * contains a bunch of logic and props for batch editing makes this component
 * unsuitable for any applications besides the main Inventory forms.
 */
const useStyles = makeStyles()(() => ({
  formControl: {
    "& .MuiInputBase-root.Mui-disabled, & .MuiFormControlLabel-label.Mui-disabled":
      {
        color: "black !important",
        "& input": {
          WebkitTextFillColor: "unset",
        },
        "& .MuiSvgIcon-root.MuiSelect-icon": {
          display: "none",
        },
      },
    "& .MuiSelect-root.MuiSelect-select.MuiSelect-outlined": {
      padding: "11px 10px 10px 10px",
    },
    "& .Mui-disabled::before": {
      borderBottom: "0px !important",
    },
  },
}));

export default function FormField<T>(props: FormFieldArgs<T>): React.ReactNode {
  const {
    disabled,
    renderInput,
    value,
    label,
    noValueLabel,
    canChooseWhichToEdit,
    setDisabled,
    asFieldset,
    ...rest
  } = props;

  /*
   * This ID links together the ChooseToEdit checkbox, used when batching
   * editing, with the root of the form field to signal to accessibility
   * technologies that the former controls the later. When ChooseToEdit is not
   * in use, this ID is not required so it is excluded from the rendered DOM.
   */
  const controlledId = React.useId();

  /*
   * This ID links together the root of the form field and the inner label when
   * the field is disabled and we're showing the nominal value. We don't need
   * to cover the other cases here as the generic form field component has its
   * own mechanism for linking the its root to its label.
   */
  const labelId = React.useId();

  const { classes } = useStyles();

  /*
   * If the field is not being edited and there is currently no value, we show
   * a placeholder label. As there is no interactive input component, we cannot
   * show a HTMLLabelElement as it would not have another element to refer to
   * in its `for` attribute. As such, we show a heading instead. Similarly,
   * `asFieldset` is ignored here as the form field is disabled and so a
   * HTMLFieldSetElement and HTMLCaptionElement do not need to be used anymore
   * than HTMLLabelElement does.
   */
  if (disabled && !value) {
    return (
      <FormControl
        fullWidth
        {...(canChooseWhichToEdit ? { id: controlledId } : {})}
        role="group"
        aria-disabled={true}
        aria-labelledby={labelId}
        className={classes.formControl}
      >
        <FormLabel component={Heading} sx={{ mt: 0 }} id={labelId}>
          {label}
        </FormLabel>
        <NoValue label={noValueLabel ?? "No Value"} />
        {canChooseWhichToEdit ? (
          <ChooseToEdit
            ariaControls={controlledId}
            checked={!disabled}
            onChange={(checked) => {
              setDisabled?.(checked);
            }}
          />
        ) : null}
      </FormControl>
    );
  }

  return (
    <BaseFormField
      {...rest}
      disabled={disabled}
      value={value}
      label={label}
      {...(canChooseWhichToEdit ? { id: controlledId } : {})}
      asFieldset={asFieldset}
      className={clsx(classes.formControl, rest.className)}
      renderInput={(inputProps) => (
        <>
          {canChooseWhichToEdit ? (
            <ChooseToEdit
              checked={!disabled}
              onChange={(checked) => {
                setDisabled?.(checked);
              }}
              ariaControls={controlledId}
            />
          ) : null}
          {renderInput(inputProps)}
        </>
      )}
    />
  );
}
