import { Temporal } from "@js-temporal/polyfill";
import { TZDate } from "react-day-picker";

/** The react-day-picker day for a calendar date in the picker's `timeZone`. */
export function pickerDate(date: Temporal.PlainDate, timeZone: string): Date {
  return new TZDate(date.year, date.month - 1, date.day, timeZone);
}

/** The calendar date react-day-picker means by `date` in its `timeZone`, whatever the browser's zone. */
export function plainDateFromPicker(date: Date, timeZone: string): Temporal.PlainDate {
  return Temporal.Instant.fromEpochMilliseconds(date.getTime()).toZonedDateTimeISO(timeZone).toPlainDate();
}
