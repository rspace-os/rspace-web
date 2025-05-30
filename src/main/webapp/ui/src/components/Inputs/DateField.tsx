import React from "react";
import { observer } from "mobx-react-lite";
import NoValue from "../../components/NoValue";
import { isValidDate } from "../../util/Util";
import TextField from "@mui/material/TextField";
import { AdapterDateFns } from "@mui/x-date-pickers/AdapterDateFns";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import { enGB } from "date-fns/locale";

export type DateFieldArgs = {
  // required
  value: string | null;

  // optional
  label?: React.ReactNode;
  alert?: React.ReactNode;
  disabled?: boolean;
  noValueLabel?: string | null;
  onChange?: (event: { target: { value: Date | null } }) => void;
  minDate?: Date;
  maxDate?: Date;
  disableFuture?: boolean;
  placeholder?: string;
  datatestid?: string;
  variant?: "standard" | "outlined";
  id?: string;

  /*
   * By default date fields are limited to 10 chars because that's all the
   * space that's need to show a date. However, it may be desirable to have a
   * wider field if there's a long label/placeholder
   * */
  disableWidthLimit?: boolean;
};

function DateField({
  label,
  disabled,
  value,
  onChange,
  alert,
  noValueLabel,
  minDate,
  maxDate,
  disableFuture,
  placeholder,
  datatestid,
  variant = "standard",
  disableWidthLimit = false,
  id,
}: DateFieldArgs): React.ReactNode {
  const error = !(!value || isValidDate(value));

  return disabled && !value ? (
    <NoValue label={noValueLabel ?? "None"} />
  ) : (
    <>
      <LocalizationProvider dateAdapter={AdapterDateFns} adapterLocale={enGB}>
        <DatePicker
          label={label}
          value={value}
          onChange={(newValue) => {
            onChange?.({ target: { value: newValue } });
          }}
          // @ts-expect-error It's not documents, but inputProps.placeholder does work
          inputProps={{
            placeholder: placeholder ?? "",
          }}
          inputFormat="y-M-d"
          renderInput={(params) => (
            <TextField
              {...params}
              variant={variant}
              error={error}
              helperText={error ? "Invalid date." : ""}
              style={{
                maxWidth: disableWidthLimit ? "initial" : "10em",
              }}
              data-test-id={datatestid}
              id={id}
            />
          )}
          disabled={disabled}
          minDate={minDate}
          maxDate={maxDate}
          disableFuture={disableFuture}
        />
      </LocalizationProvider>
      {alert}
    </>
  );
}

export default observer(DateField);
