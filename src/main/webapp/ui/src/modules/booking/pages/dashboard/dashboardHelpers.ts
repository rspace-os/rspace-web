import { dateInTimeZone } from "@/modules/booking/bookingDates";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";

export function monthStartForTimeZone(timeZone: string): Date {
  return dateInTimeZone(`${todayInTimeZone(timeZone).slice(0, 7)}-01`, timeZone);
}

export function calendarDateKey(date: Date): string {
  const year = String(date.getFullYear()).padStart(4, "0");
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
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
