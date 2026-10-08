import { TZDate } from "react-day-picker";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";

export function monthStartForTimeZone(timeZone: string): Date {
  const [year, month] = todayInTimeZone(timeZone).split("-").map(Number);
  return new TZDate(year, month - 1, 1, timeZone);
}

export function dateInTimeZone(date: string, timeZone: string): Date {
  const [year, month, day] = date.split("-").map(Number);
  return new TZDate(year, month - 1, day, timeZone);
}

export function formatDayPickerDate(date: Date, locale: string, options: Intl.DateTimeFormatOptions): string {
  const dateKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
  return new Intl.DateTimeFormat(locale, { ...options, timeZone: "UTC" }).format(new Date(`${dateKey}T00:00:00Z`));
}

export function formatCalendarDate(dateKey: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${dateKey}T00:00:00Z`));
}

/** Busy responses are intentionally reduced before passing them to any display component. */
export function dashboardBooking(row: BookingListDocument): BookingListDocument {
  return row.privacy === "busy" ? { ...row, canViewConfiguration: false, purpose: null } : row;
}
