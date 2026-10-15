import { Temporal } from "@js-temporal/polyfill";
import { type BookingTimeFormat, bookingHourCycle, resolveWallClock } from "@/modules/booking/domain/bookingTime";
import type { BookingListDocument } from "./booking";

export type BookingAgendaDay = {
  date: string;
  bookings: BookingListDocument[];
};

export function groupBookingsByStartDate(
  bookings: readonly BookingListDocument[],
  timeZone: string,
): BookingAgendaDay[] {
  const sorted = [...bookings].sort((a, b) => Temporal.Instant.compare(a.start, b.start) || a.id - b.id);
  const groups = new Map<string, BookingListDocument[]>();
  for (const booking of sorted) {
    const date = Temporal.Instant.from(booking.start).toZonedDateTimeISO(timeZone).toPlainDate().toString();
    const day = groups.get(date);
    if (day) day.push(booking);
    else groups.set(date, [booking]);
  }
  return [...groups].map(([date, dayBookings]) => ({ date, bookings: dayBookings }));
}

export function getBookingAgendaRelativeDay(date: string, todayDate: string): "today" | "tomorrow" | null {
  const today = Temporal.PlainDate.from(todayDate);
  const agendaDate = Temporal.PlainDate.from(date);
  if (agendaDate.equals(today)) return "today";
  if (agendaDate.equals(today.add({ days: 1 }))) return "tomorrow";
  return null;
}

export function formatBookingAgendaDate(date: string, locale: string, currentYear: number): string {
  const plainDate = Temporal.PlainDate.from(date);
  return plainDate.toLocaleString(locale, {
    weekday: "long",
    month: "long",
    day: "numeric",
    ...(plainDate.year === currentYear ? {} : { year: "numeric" }),
  });
}

function wallClockIsAmbiguous(zoned: Temporal.ZonedDateTime, timeZone: string): boolean {
  const localTime = `${String(zoned.hour).padStart(2, "0")}:${String(zoned.minute).padStart(2, "0")}`;
  return resolveWallClock(zoned.toPlainDate().toString(), localTime, timeZone).kind === "ambiguous";
}

function needsOffset(startZoned: Temporal.ZonedDateTime, endZoned: Temporal.ZonedDateTime, timeZone: string): boolean {
  return (
    startZoned.offset !== endZoned.offset ||
    wallClockIsAmbiguous(startZoned, timeZone) ||
    wallClockIsAmbiguous(endZoned, timeZone)
  );
}

export function formatBookingAgendaTimeRange(
  start: string,
  end: string,
  timeZone: string,
  locale: string,
  timeFormat: BookingTimeFormat = "AUTOMATIC",
): string {
  const startZoned = Temporal.Instant.from(start).toZonedDateTimeISO(timeZone);
  const endZoned = Temporal.Instant.from(end).toZonedDateTimeISO(timeZone);
  const startDate = new Date(start);
  const endDate = new Date(end);
  const formatter = new Intl.DateTimeFormat(locale, {
    timeZone,
    hour: "numeric",
    minute: "2-digit",
    hourCycle: bookingHourCycle(timeFormat),
    ...(needsOffset(startZoned, endZoned, timeZone) ? { timeZoneName: "shortOffset" } : {}),
  });
  const range = `${formatter.format(startDate)}–${formatter.format(endDate)}`;
  const startLocalDate = startZoned.toPlainDate();
  const endLocalDate = endZoned.toPlainDate();
  if (startLocalDate.equals(endLocalDate)) return range;

  const endDateLabel = endLocalDate.toLocaleString(locale, {
    month: "short",
    day: "numeric",
    ...(startLocalDate.year === endLocalDate.year ? {} : { year: "numeric" }),
  });
  return `${range} · ${endDateLabel}`;
}

export function formatBookingAgendaDateTime(
  value: string,
  start: string,
  end: string,
  timeZone: string,
  locale: string,
  timeFormat: BookingTimeFormat = "AUTOMATIC",
): string {
  const date = new Date(value);
  const startZoned = Temporal.Instant.from(start).toZonedDateTimeISO(timeZone);
  const endZoned = Temporal.Instant.from(end).toZonedDateTimeISO(timeZone);
  if (!needsOffset(startZoned, endZoned, timeZone)) {
    return new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      hourCycle: bookingHourCycle(timeFormat),
      timeZone,
    }).format(date);
  }
  return new Intl.DateTimeFormat(locale, {
    timeZone,
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hourCycle: bookingHourCycle(timeFormat),
    timeZoneName: "shortOffset",
  }).format(date);
}
