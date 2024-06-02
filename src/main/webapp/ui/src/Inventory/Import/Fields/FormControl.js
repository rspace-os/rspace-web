//@flow

import React, { type ComponentType, type Node } from "react";
import FormControl from "../../../components/Inputs/FormControl";
import { withStyles } from "Styles";

/*
 * Because the Upload page is only a single column layout, we limit the width
 * of various form elements so that they don't excessively stretch across very
 * wide monitors.
 */

type UploadFormControlArgs = {|
  label: string,
  children: Node,

  error?: boolean,
  helperText?: string,
|};

const UploadFormControl: ComponentType<UploadFormControlArgs> = withStyles<
  UploadFormControlArgs,
  { formControl?: string, formLabel?: string }
>(() => ({
  formControl: {
    maxWidth: 660,
  },
}))(({ classes, ...props }) => <FormControl classes={classes} {...props} />);

UploadFormControl.displayName = "UploadFormControl";
export default UploadFormControl;
