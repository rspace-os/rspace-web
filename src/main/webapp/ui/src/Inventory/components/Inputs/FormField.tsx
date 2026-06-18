import { formControlLabelClasses } from "@mui/material/FormControlLabel";
import { formLabelClasses } from "@mui/material/FormLabel";
import { inputBaseClasses } from "@mui/material/InputBase";
import { selectClasses } from "@mui/material/Select";
import { svgIconClasses } from "@mui/material/SvgIcon";
import type React from "react";
import BaseFormField, { type FormFieldArgs as BaseFormFieldArgs } from "../../../components/Inputs/FormField";

/**
 * When not disabled and not batch editing, all of the behaviour of the more
 * generic form field component is preserved.
 */
export type FormFieldArgs<T> = BaseFormFieldArgs<T>;

/*
 * Inventory form fields are shown in black when disabled (rather than the
 * standard grey) because disabled fields are used to render the read-only
 * preview mode, not an inaccessible state.
 */
export const INVENTORY_FORM_FIELD_SX = {
  [`& .${inputBaseClasses.root}.${inputBaseClasses.disabled}, & .${formControlLabelClasses.label}.${formControlLabelClasses.disabled}`]:
    {
      color: "black !important",
      "& input": {
        color: "unset",
      },
      [`& .${svgIconClasses.root}.${selectClasses.icon}`]: {
        display: "none",
      },
    },
  [`& .${selectClasses.root}.${selectClasses.select}.${selectClasses.outlined}`]: {
    padding: "11px 10px 10px 10px",
  },
  [`& .${inputBaseClasses.disabled}::before`]: {
    borderBottom: "0px !important",
  },
  [`& > .${formLabelClasses.root}`]: {
    textTransform: "uppercase",
  },
} as const;

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
export default function FormField<T>(props: FormFieldArgs<T>): React.ReactNode {
  return <BaseFormField {...props} sx={INVENTORY_FORM_FIELD_SX} />;
}
