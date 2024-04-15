//@flow

import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import React, { type Node } from "react";

/**
 * This component is for displaying a button that has a large icon and a short
 * piece of explanatory text in addition to a label.
 */

type BigIconButtonArgs = {|
  label: string,
  icon: Node,
  explanatoryText: string,
|};

export default function BigIconButton({
  label,
  icon,
  explanatoryText,
}: BigIconButtonArgs): Node {
  return (
    <Button color="primary" variant="outlined">
      <Grid container direction="column">
        <Grid item>{icon}</Grid>
        <Grid item>{label}</Grid>
        <Grid item>
          <Typography variant="caption">{explanatoryText}</Typography>
        </Grid>
      </Grid>
    </Button>
  );
}
