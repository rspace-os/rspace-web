import React, { type ComponentType } from "react";
import FormControl from "../../../components/Inputs/FormControl";
import { withStyles } from "../../../util/styles";

/*
 * Because the Upload page is only a single column layout, we limit the width
 * of various form elements so that they don't excessively stretch across very
 * wide monitors.
 */

export type UploadFormControlArgs = {
  label: string;
  children: React.ReactNode;
  error?: boolean;
  helperText?: string;
};

const UploadFormControl: ComponentType<UploadFormControlArgs> = withStyles<
  UploadFormControlArgs,
  { formControl?: string; formLabel?: string }
>(() => ({
  formControl: {
    maxWidth: 660,
  },
}))((props) => {
  const { classes, ...rest } = props;
  return <FormControl classes={classes} {...rest} />;
});

UploadFormControl.displayName = "UploadFormControl";
export default UploadFormControl;
