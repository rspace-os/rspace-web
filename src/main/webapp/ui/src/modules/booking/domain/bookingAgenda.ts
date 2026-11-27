import { Temporal } from "@js-temporal/polyfill";
import type { BookingListDocument } from "./booking";
import { resolveWallClock } from "./bookingTime";

export type BookingAgendaDay = {
  date: string;
  bookings: BookingListDocument[];
};

export function groupBookingsByStartDate(
  bookings: readonly BookingListDocument[],
  timeZone: string,
): BookingAgendaDay[] {
  const sorted = [...bookings].sort((a, b) => Temporal.Instant.compare(a.start, b.start) || a.id - b.id);
  const groups = Map.groupBy(sorted, (booking) => booking.start.toZonedDateTimeISO(timeZone).toPlainDate().toString());
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
  start: Temporal.Instant,
  end: Temporal.Instant,
  timeZone: string,
  locale: string,
): string {
  const startZoned = start.toZonedDateTimeISO(timeZone);
  const endZoned = end.toZonedDateTimeISO(timeZone);
  const formatter = new Intl.DateTimeFormat(locale, {
    timeZone,
    hour: "numeric",
    minute: "2-digit",
    ...(needsOffset(startZoned, endZoned, timeZone) ? { timeZoneName: "shortOffset" } : {}),
  });
  const range = `${formatter.format(start.epochMilliseconds)}–${formatter.format(end.epochMilliseconds)}`;
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
  value: Temporal.Instant,
  start: Temporal.Instant,
  end: Temporal.Instant,
  timeZone: string,
  locale: string,
): string {
  const epochMilliseconds = value.epochMilliseconds;
  const startZoned = start.toZonedDateTimeISO(timeZone);
  const endZoned = end.toZonedDateTimeISO(timeZone);
  if (!needsOffset(startZoned, endZoned, timeZone)) {
    return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short", timeZone }).format(
      epochMilliseconds,
    );
  }
  return new Intl.DateTimeFormat(locale, {
    timeZone,
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZoneName: "shortOffset",
  }).format(epochMilliseconds);
}
