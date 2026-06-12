import Box from "@mui/material/Box";
import type React from "react";
import FormControl from "../../../components/Inputs/FormControl";

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

function UploadFormControl(props: UploadFormControlArgs): React.ReactNode {
  return (
    <Box sx={{ maxWidth: 660 }}>
      <FormControl {...props} />
    </Box>
  );
}

export default UploadFormControl;
