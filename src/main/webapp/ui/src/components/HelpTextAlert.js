//@flow

import Grid from "@mui/material/Grid";
import Alert from "@mui/material/Alert";
import React, { type Node } from "react";
import AlertTitle from "@mui/material/AlertTitle";

type HelpTextAlertProps = {
  text: string | Node,
  title?: string,
  condition: boolean,
  severity: string,
};

export default function HelpTextAlert({
  text,
  title,
  condition,
  severity,
}: HelpTextAlertProps): Node {
  return condition ? (
    <Grid item>
      <Alert severity={severity}>
        {title && <AlertTitle>{title}</AlertTitle>}
        {text}
      </Alert>
    </Grid>
  ) : null;
}
