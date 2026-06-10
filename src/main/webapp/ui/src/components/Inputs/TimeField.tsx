import Grid from "@mui/material/Grid";
import React from "react";
import NoValue from "../../components/NoValue";
import { AdapterDateFns } from "@mui/x-date-pickers/AdapterDateFns";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { TimePicker } from "@mui/x-date-pickers/TimePicker";
import { format, isValid, parse } from "date-fns";
import { enGB } from "date-fns/locale";

export type TimeFieldArgs = {
  onChange: (event: { target: { value: string | null } }) => void;
  value: string;
  disabled?: boolean;
  id?: string;
};

export default function TimeField({
  disabled,
  value,
  onChange,
  id,
}: TimeFieldArgs): React.ReactNode {
  const parsedValue = value ? parse(value, "HH:mm", new Date()) : null;
  const pickerValue =
    parsedValue && isValid(parsedValue) ? parsedValue : null;

  return (
    <Grid container spacing={0}>
      {disabled && !value ? (
        <NoValue label="None" />
      ) : (
        <Grid size={{ md: 6, xs: 12 }}>
          <LocalizationProvider
            dateAdapter={AdapterDateFns}
            adapterLocale={enGB}
          >
            <TimePicker
              value={pickerValue}
              onChange={(newValue) => {
                onChange({
                  target: {
                    value: newValue ? format(newValue, "HH:mm") : null,
                  },
                });
              }}
              slotProps={{
                textField: {
                  /*
                   * error state is handled by the FormField that this
                   * component is rendered within. We do not want to use the
                   * error handling built into the TimePicker as it cannot
                   * handle our custom validation logic that includes things
                   * like checking if the field is mandatory and if not then
                   * allowing empty value.
                   */
                  error: false,
                  style: {
                    maxWidth: "10em",
                  },
                  variant: "standard",
                  id,
                },
              }}
              disabled={disabled}
            />
          </LocalizationProvider>
        </Grid>
      )}
    </Grid>
  );
}
