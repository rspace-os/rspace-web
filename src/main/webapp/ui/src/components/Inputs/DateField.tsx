import { AdapterDateFns } from "@mui/x-date-pickers/AdapterDateFns";
import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { isValid, parse, parseISO } from "date-fns";
import { enGB } from "date-fns/locale";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import NoValue from "../../components/NoValue";

const DATE_FORMAT = "yyyy-MM-dd";

export type DateFieldArgs = {
  // required
  value: string | Date | null;

  // optional
  label?: React.ReactNode;
  alert?: React.ReactNode;
  disabled?: boolean;
  noValueLabel?: string | null;
  /**
   * `value` is null when the picker is empty. `valid` is false while it holds
   * an incomplete or invalid date; limits such as `minDate` are not checked.
   */
  onChange?: (event: { target: { value: Date | null; valid: boolean } }) => void;
  minDate?: Date;
  maxDate?: Date;
  disableFuture?: boolean;
  placeholder?: string;
  "data-test-id"?: string;
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
  "data-test-id": dataTestId,
  variant = "standard",
  disableWidthLimit = false,
  id,
}: DateFieldArgs): React.ReactNode {
  const { t } = useTranslation("common");
  const parsedValue = parseDateFieldValue(value);
  const error = Boolean(value) && !(parsedValue && isValid(parsedValue));
  const textFieldSlotProps = {
    variant,
    error,
    helperText: error ? t("inputs.dateField.invalidDate") : "",
    style: {
      maxWidth: disableWidthLimit ? "initial" : "10em",
    },
    id,
    "data-test-id": dataTestId,
    slotProps: {
      htmlInput: {
        placeholder,
      },
    },
  };

  return disabled && !value ? (
    <NoValue label={noValueLabel ?? t("values.none")} />
  ) : (
    <>
      <LocalizationProvider dateAdapter={AdapterDateFns} adapterLocale={enGB}>
        <DatePicker
          label={label}
          value={parsedValue}
          onChange={(newValue) => {
            onChange?.({ target: { value: newValue, valid: newValue === null || isValid(newValue) } });
          }}
          format={DATE_FORMAT}
          slotProps={{
            textField: textFieldSlotProps,
          }}
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

function parseDateFieldValue(value: string | Date | null): Date | null {
  if (!value) {
    return null;
  }

  // an invalid date goes back to the picker as it published it, so that clearing it is still a change
  if (value instanceof Date) {
    return value;
  }

  const parsedIsoDate = parseISO(value);
  if (isValid(parsedIsoDate)) {
    return parsedIsoDate;
  }

  const parsedDate = parse(value, DATE_FORMAT, new Date());
  if (isValid(parsedDate)) {
    return parsedDate;
  }

  return null;
}

export default observer(DateField);
