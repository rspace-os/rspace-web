import Grid from "@mui/material/Grid";
import Alert, { type AlertColor } from "@mui/material/Alert";
import React from "react";
import AlertTitle from "@mui/material/AlertTitle";

type HelpTextAlertProps = {
  text: string | React.ReactNode;
  title?: string;
  condition: boolean;
  severity: AlertColor;
};

export default function HelpTextAlert({
  text,
  title,
  condition,
  severity,
}: HelpTextAlertProps): React.ReactNode {
  return condition ? (
    <Grid item>
      <Alert severity={severity}>
        {title && <AlertTitle>{title}</AlertTitle>}
        {text}
      </Alert>
    </Grid>
  ) : null;
}
