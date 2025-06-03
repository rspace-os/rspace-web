import React from "react";
import { observer } from "mobx-react-lite";
import FormControl from "@mui/material/FormControl";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import { isoToLocale } from "../../../util/Util";
import Grid from "@mui/material/Grid";

type DateArgs = {
  date: string;
  label: string;
};

function Date({ date, label }: DateArgs): React.ReactNode {
  return (
    <Grid item>
      <FormControl component="fieldset" style={{ alignItems: "flex-start" }}>
        <FormLabel component="legend">{label}</FormLabel>
        <FormGroup>{isoToLocale(date)}</FormGroup>
      </FormControl>
    </Grid>
  );
}

export default observer(Date);
