//@flow
import "date-fns";
import Grid from "@mui/material/Grid";
import React, { type Node } from "react";
import NoValue from "../../components/NoValue";
import { AdapterDateFns } from "@mui/x-date-pickers/AdapterDateFns";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { TimePicker } from "@mui/x-date-pickers/TimePicker";
import TextField from "@mui/material/TextField";
import { enGB } from "date-fns/locale";

export type TimeFieldArgs = {|
  // required
  onChange: ({ target: { value: string } }) => void,
  value: string,

  // optional
  disabled?: boolean,
  id?: string,
|};

export default function TimeField({
  disabled,
  value,
  onChange,
  id,
}: TimeFieldArgs): Node {
  return (
    <Grid container spacing={0}>
      {disabled && !value ? (
        <NoValue label="None" />
      ) : (
        <Grid item md={6} xs={12}>
          <LocalizationProvider
            dateAdapter={AdapterDateFns}
            adapterLocale={enGB}
          >
            <TimePicker
              value={value}
              onChange={(newValue) => {
                onChange({ target: { value: newValue } });
              }}
              clearable
              renderInput={(params) => (
                <TextField
                  {...params}
                  style={{
                    maxWidth: "10em",
                  }}
                  variant="standard"
                  id={id}
                />
              )}
              disabled={disabled}
            />
          </LocalizationProvider>
        </Grid>
      )}
    </Grid>
  );
}
