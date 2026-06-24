import Grid from "@mui/material/Grid";
import { AdapterDateFns } from "@mui/x-date-pickers/AdapterDateFns";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { TimePicker } from "@mui/x-date-pickers/TimePicker";
import { format, isValid, parse } from "date-fns";
import { enGB } from "date-fns/locale";
import type React from "react";
import { useTranslation } from "react-i18next";
import NoValue from "../../components/NoValue";

const TIME_FORMAT = "HH:mm";

export type TimeFieldArgs = {
  onChange: (event: { target: { value: string | null } }) => void;
  /*
   * FieldModel stores a time field's value as a Date object (it parses the
   * "HH:mm" string on the way in), whereas other callers pass the "HH:mm"
   * string directly. Accept both, plus null/empty. Passing a non-string
   * straight to date-fns `parse` throws ("dateString.match is not a
   * function"), which is what broke complex templates after the MUI v9
   * upgrade; parseTimeFieldValue handles each shape instead.
   */
  value: string | Date | null;
  disabled?: boolean;
  id?: string;
};

export default function TimeField({ disabled, value, onChange, id }: TimeFieldArgs): React.ReactNode {
  const { t } = useTranslation("common");
  const pickerValue = parseTimeFieldValue(value);

  return (
    <Grid container spacing={0}>
      {disabled && !value ? (
        <NoValue label={t("values.none")} />
      ) : (
        <Grid size={{ md: 6, xs: 12 }}>
          <LocalizationProvider dateAdapter={AdapterDateFns} adapterLocale={enGB}>
            <TimePicker
              value={pickerValue}
              format={TIME_FORMAT}
              ampm={false}
              onChange={(newValue) => {
                onChange({
                  target: {
                    value: newValue && isValid(newValue) ? format(newValue, TIME_FORMAT) : null,
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

/**
 * Resolves the various shapes a time value can arrive in (a Date from
 * FieldModel, an "HH:mm" string from other callers, or null/empty) to the Date
 * the picker expects, or null when there is no valid value. Crucially it never
 * hands a non-string to date-fns `parse`, which throws on one.
 */
function parseTimeFieldValue(value: string | Date | null): Date | null {
  if (!value) {
    return null;
  }
  if (value instanceof Date) {
    return isValid(value) ? value : null;
  }
  if (typeof value === "string") {
    const parsed = parse(value, TIME_FORMAT, new Date());
    return isValid(parsed) ? parsed : null;
  }
  return null;
}
