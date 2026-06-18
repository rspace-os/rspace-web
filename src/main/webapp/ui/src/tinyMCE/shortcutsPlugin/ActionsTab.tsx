import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import React from "react";
import { humanize } from "../../util/shortcuts";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function ActionsTab(props: any) {
  return Object.keys(props.actionShortcuts).map((k) => (
    <React.Fragment key={k}>
      <Grid size={6}>
        <Typography variant="overline" sx={{ display: "block", marginTop: "8px" }}>
          {props.config.actions[k].description}
        </Typography>
      </Grid>
      <Grid size={6}>
        <TextField
          variant="standard"
          fullWidth
          label=""
          id={k}
          value={humanize(props.actionShortcuts[k])}
          error={props.hasError && k === props.selectedKey}
          helperText={k === props.selectedKey && props.hasError ? props.errorMessage : null}
          onKeyDown={(e) => props.detectShortcut(k, e)}
          onKeyUp={props.onKeyUp}
          margin="dense"
          slotProps={{
            htmlInput: { style: { lineHeight: "20px" } },
          }}
        />
      </Grid>
    </React.Fragment>
  ));
}
