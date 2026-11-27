import { Temporal } from "@js-temporal/polyfill";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";

/** The first day of the current month in `timeZone`. */
export function monthStartForTimeZone(timeZone: string): Temporal.PlainDate {
  return Temporal.PlainDate.from(todayInTimeZone(timeZone)).with({ day: 1 });
}

const utcMidnight = (date: Temporal.PlainDate) => date.toZonedDateTime("UTC").epochMilliseconds;

export function formatDayPickerDate(
  date: Temporal.PlainDate,
  locale: string,
  options: Intl.DateTimeFormatOptions,
): string {
  return new Intl.DateTimeFormat(locale, { ...options, timeZone: "UTC" }).format(utcMidnight(date));
}

export function formatCalendarDate(dateKey: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(utcMidnight(Temporal.PlainDate.from(dateKey)));
}

/** Busy responses are intentionally reduced before passing them to any display component. */
export function dashboardBooking(row: BookingListDocument): BookingListDocument {
  return row.privacy === "busy" ? { ...row, canViewConfiguration: false, purpose: null } : row;
}
