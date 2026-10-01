import { Temporal } from "@js-temporal/polyfill";
import { bookingHourCycle } from "@/modules/booking/domain/bookingTime";
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
  const groups = new Map<string, BookingListDocument[]>();
  for (const booking of sorted) {
    const date = Temporal.Instant.from(booking.start).toZonedDateTimeISO(timeZone).toPlainDate().toString();
    const day = groups.get(date);
    if (day) day.push(booking);
    else groups.set(date, [booking]);
  }
  return [...groups].map(([date, dayBookings]) => ({ date, bookings: dayBookings }));
}

export function getBookingAgendaRelativeDay(date: string, now: number, timeZone: string): "today" | "tomorrow" | null {
  const today = Temporal.PlainDate.from(getBookingAgendaTodayDate(now, timeZone));
  const agendaDate = Temporal.PlainDate.from(date);
  if (agendaDate.equals(today)) return "today";
  if (agendaDate.equals(today.add({ days: 1 }))) return "tomorrow";
  return null;
}

export function getBookingAgendaTodayDate(now: number, timeZone: string): string {
  return Temporal.Instant.fromEpochMilliseconds(now).toZonedDateTimeISO(timeZone).toPlainDate().toString();
}

export function formatBookingAgendaDate(date: string, locale: string, currentYear: number): string {
  const plainDate = Temporal.PlainDate.from(date);
  const options: Intl.DateTimeFormatOptions = {
    weekday: "long",
    month: "long",
    day: "numeric",
    timeZone: "UTC",
    ...(plainDate.year === currentYear ? {} : { year: "numeric" }),
  };
  return new Intl.DateTimeFormat(locale, options).format(new Date(`${date}T12:00:00.000Z`));
}

function wallClockIsAmbiguous(instant: string, timeZone: string): boolean {
  const zoned = Temporal.Instant.from(instant).toZonedDateTimeISO(timeZone);
  const localTime = `${String(zoned.hour).padStart(2, "0")}:${String(zoned.minute).padStart(2, "0")}`;
  return resolveWallClock(zoned.toPlainDate().toString(), localTime, timeZone).kind === "ambiguous";
}

function needsOffset(start: string, end: string, timeZone: string): boolean {
  const startZoned = Temporal.Instant.from(start).toZonedDateTimeISO(timeZone);
  const endZoned = Temporal.Instant.from(end).toZonedDateTimeISO(timeZone);
  return (
    startZoned.offset !== endZoned.offset ||
    wallClockIsAmbiguous(start, timeZone) ||
    wallClockIsAmbiguous(end, timeZone)
  );
}

export function formatBookingAgendaTimeRange(start: string, end: string, timeZone: string, locale: string): string {
  const startDate = new Date(start);
  const endDate = new Date(end);
  const formatter = new Intl.DateTimeFormat(locale, {
    timeZone,
    hour: "numeric",
    minute: "2-digit",
    hourCycle: bookingHourCycle(),
    ...(needsOffset(start, end, timeZone) ? { timeZoneName: "shortOffset" } : {}),
  });
  const range = `${formatter.format(startDate)}–${formatter.format(endDate)}`;
  const startLocalDate = Temporal.Instant.from(start).toZonedDateTimeISO(timeZone).toPlainDate();
  const endLocalDate = Temporal.Instant.from(end).toZonedDateTimeISO(timeZone).toPlainDate();
  if (startLocalDate.equals(endLocalDate)) return range;

  const dateOptions: Intl.DateTimeFormatOptions = {
    timeZone: "UTC",
    month: "short",
    day: "numeric",
    ...(startLocalDate.year === endLocalDate.year ? {} : { year: "numeric" }),
  };
  const endDateLabel = new Intl.DateTimeFormat(locale, dateOptions).format(
    new Date(`${endLocalDate.toString()}T12:00:00.000Z`),
  );
  return `${range} · ${endDateLabel}`;
}

export function formatBookingAgendaDateTime(
  value: string,
  start: string,
  end: string,
  timeZone: string,
  locale: string,
): string {
  const date = new Date(value);
  if (!needsOffset(start, end, timeZone)) {
    return new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      hourCycle: bookingHourCycle(),
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
    hourCycle: bookingHourCycle(),
    timeZoneName: "shortOffset",
  }).format(date);
}
