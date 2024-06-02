//@flow

import React, { type Node } from "react";
import BaseFormField, {
  type FormFieldArgs as BaseFormFieldArgs,
} from "../../../components/Inputs/FormField";
import { makeStyles } from "tss-react/mui";

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
 */

export type FormFieldArgs<T> = {|
  /**
   * When not disabled and not batch editing, all of the behaviour of the more
   * generic form field component is preserved.
   */
  ...BaseFormFieldArgs<T>,
|};

/**
 * Do note that when Inventory form fields are disabled they are shown in
 * black, not a grey colour, as disabled form fields are used to render the
 * preview mode of the main UI.
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

export default function FormField<T>(props: FormFieldArgs<T>): Node {
  const { classes } = useStyles();
  return <BaseFormField {...props} className={classes.formControl} />;
}
