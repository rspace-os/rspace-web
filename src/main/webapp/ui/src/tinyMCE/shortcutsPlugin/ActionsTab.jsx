"use strict";
import React from "react";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import TextField from "@mui/material/TextField";
import { humanize } from "../../util/shortcuts";

export default function ActionsTab(props) {
  return Object.keys(props.actionShortcuts).map((k) => (
    <React.Fragment key={k}>
      <Grid item xs={6}>
        <Typography
          variant="overline"
          display="block"
          style={{ marginTop: "8px" }}
        >
          {props.config.actions[k].description}
        </Typography>
      </Grid>
      <Grid item xs={6}>
        <TextField
          variant="standard"
          fullWidth
          label=""
          id={k}
          value={humanize(props.actionShortcuts[k])}
          error={props.hasError && k == props.selectedKey}
          helperText={
            k == props.selectedKey && props.hasError ? props.errorMessage : null
          }
          onKeyDown={(e) => props.detectShortcut(k, e)}
          onKeyUp={props.onKeyUp}
          margin="dense"
          inputProps={{ style: { lineHeight: "20px" } }}
        />
      </Grid>
    </React.Fragment>
  ));
}
