//@flow

import React, { type Node } from "react";
import WarningIcon from "@mui/icons-material/Warning";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";

/*
 * This component is for showing a warning label just above a dialog's submit
 * button that there are unsaved changes to the contents of the dialog that
 * will be lost if the user closes the dialog without submitting.
 */
export default function WarningBar(): Node {
  return (
    <Grid
      container
      spacing={1}
      justifyContent="flex-end"
      alignItems="center"
      sx={{ mt: 0.5, pr: 1, color: "warningRed" }}
    >
      <WarningIcon />
      <Typography variant="caption">Unsaved changes</Typography>
    </Grid>
  );
}
