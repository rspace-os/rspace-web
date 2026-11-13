import { Temporal } from "@js-temporal/polyfill";
import * as v from "valibot";
import type { BookingOpeningHoursEntry } from "@/modules/common/api/v2/generated/models/bookingOpeningHoursEntry";
import { BookingOpeningHoursEntrySchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { requireBookingValue } from "./bookingMapping";

/** The absolute booking-duration limit, which also bounds opening-hour date enumeration. */
export const MAX_BOOKING_DURATION_MINUTES = 527_040;

export const ALL_ISO_WEEKDAYS: readonly number[] = [1, 2, 3, 4, 5, 6, 7];

export const WALL_TIME = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
export const OpeningHoursStartSchema = v.pipe(v.string(), v.regex(WALL_TIME));
export const OpeningHoursEndSchema = v.union([v.pipe(v.string(), v.regex(WALL_TIME)), v.literal("24:00")]);

export type OpeningHoursEntry = BookingOpeningHoursEntry;

/** Check schedule policy and sort without mutating the wire response. */
export function mapOpeningHours(entries: readonly BookingOpeningHoursEntry[]): OpeningHoursEntry[] {
  if (entries.length < 1 || entries.length > 7) throw new Error("Invalid opening-hours schedule length");
  const days = new Set<number>();
  const mapped = entries.map((entry) => {
    const dayOfWeek = requireBookingValue(entry.dayOfWeek, "dayOfWeek");
    const start = requireBookingValue(entry.start, "start");
    const end = requireBookingValue(entry.end, "end");
    if (
      !Number.isInteger(dayOfWeek) ||
      dayOfWeek < 1 ||
      dayOfWeek > 7 ||
      days.has(dayOfWeek) ||
      !validOpeningHours(start, end)
    )
      throw new Error("Invalid opening-hours entry");
    days.add(dayOfWeek);
    return { dayOfWeek, start, end };
  });
  return mapped.toSorted((left, right) => left.dayOfWeek - right.dayOfWeek);
}

/** The opening-hour pair used both by schedule entries and absolute intervals. */
export type OpeningInterval = Pick<BookingOpeningHoursEntry, "start" | "end">;

/** The opening-hour part of scheduling settings. */
export type OpeningSchedule = { openingHours: readonly BookingOpeningHoursEntry[] };

/** Every day, all day: the explicit opening-hours bypass for maintenance. */
export const ALWAYS_OPEN: OpeningSchedule = {
  openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
};

/** A localized weekday name for an ISO weekday; 2024-01-01 is a Monday. */
export function formatIsoWeekday(dayOfWeek: number, width: "long" | "short" = "long", locale = "en-US"): string {
  return new Intl.DateTimeFormat(locale, { weekday: width, timeZone: "UTC" }).format(
    new Date(Date.UTC(2024, 0, dayOfWeek)),
  );
}

/** Whether `start`-`end` is one opening interval. A `24:00` end closes at midnight, so it follows any valid start. */
export function validOpeningHours(start: string, end: string): boolean {
  if (!WALL_TIME.test(start)) return false;
  if (end === "24:00") return true;
  return WALL_TIME.test(end) && start < end;
}

/** The complete local day, from midnight to the next midnight, which Open all day selects. */
export function isOpenAllDay(hours: OpeningInterval | null | undefined): boolean {
  return hours?.start === "00:00" && hours.end === "24:00";
}

/** A valid interval shorter than the whole day, as Open all day remembers it; undefined otherwise. */
export function customOpeningHours(hours: OpeningInterval): OpeningInterval | undefined {
  return validOpeningHours(hours.start, hours.end) && !isOpenAllDay(hours)
    ? { start: hours.start, end: hours.end }
    : undefined;
}

/** A non-empty schedule with unique weekdays and valid per-day intervals. */
export function validOpeningHoursSchedule(openingHours: unknown): openingHours is OpeningHoursEntry[] {
  if (
    !Array.isArray(openingHours) ||
    openingHours.some(
      (entry: unknown) =>
        typeof entry !== "object" ||
        entry === null ||
        Object.keys(entry).some((key) => !["dayOfWeek", "start", "end"].includes(key)),
    )
  )
    return false;
  const parsed = v.safeParse(v.array(BookingOpeningHoursEntrySchema), openingHours);
  if (!parsed.success) return false;
  try {
    mapOpeningHours(parsed.output);
    return true;
  } catch {
    return false;
  }
}

/** Closed when no entry exists for the weekday. */
export function effectiveHours(settings: OpeningSchedule, dayOfWeek: number): OpeningInterval | null {
  const entry = settings.openingHours.find((candidate) => candidate.dayOfWeek === dayOfWeek);
  return entry ? { start: entry.start, end: entry.end } : null;
}

/** The local minute at a wall-time boundary; `24:00` is the next day's 00:00. */
export function minuteOfWallTime(time: string): number {
  return time === "24:00" ? 1440 : Number(time.slice(0, 2)) * 60 + Number(time.slice(3));
}

export type OpeningPeriod = { start: number; end: number };

/** Continuous opening periods in two unrolled weeks, so a period crossing Sunday into Monday is whole. */
export function openingPeriods(settings: OpeningSchedule): OpeningPeriod[] {
  const periods: OpeningPeriod[] = [];
  for (let day = 0; day < 14; day++) {
    const hours = effectiveHours(settings, (day % 7) + 1);
    if (!hours || !validOpeningHours(hours.start, hours.end)) continue;
    const start = day * 1440 + minuteOfWallTime(hours.start);
    const end = day * 1440 + minuteOfWallTime(hours.end);
    const previous = periods.at(-1);
    if (previous && previous.end === start) previous.end = end;
    else periods.push({ start, end });
  }
  return periods;
}

/** Longest continuous weekly opening, including periods touching across the week boundary. */
export function longestOpeningMinutes(settings: OpeningSchedule): number {
  return Math.max(
    0,
    ...openingPeriods(settings).map(({ start, end }) => {
      const duration = end - start;
      return duration >= 10080 ? MAX_BOOKING_DURATION_MINUTES : duration;
    }),
  );
}

/** Matches Java's compatible DST resolution; 24:00 is the next date's midnight. */
export function resolveSchedulingBoundary(date: string, time: string, timezone: string): Temporal.Instant {
  const plainDate = Temporal.PlainDate.from(date);
  const [boundaryDate, boundaryTime] = time === "24:00" ? [plainDate.add({ days: 1 }), "00:00"] : [plainDate, time];
  return boundaryDate
    .toPlainDateTime(Temporal.PlainTime.from(boundaryTime))
    .toZonedDateTime(timezone, { disambiguation: "compatible" })
    .toInstant();
}

function laterInstant(first: Temporal.Instant, second: Temporal.Instant): Temporal.Instant {
  return Temporal.Instant.compare(first, second) >= 0 ? first : second;
}

function earlierInstant(first: Temporal.Instant, second: Temporal.Instant): Temporal.Instant {
  return Temporal.Instant.compare(first, second) <= 0 ? first : second;
}

/** Sorted, clipped openings in an absolute interval, merging touching weekdays. */
export function openingIntervals(
  settings: OpeningSchedule,
  timezone: string,
  interval: OpeningInterval,
): OpeningInterval[] {
  const start = Temporal.Instant.from(interval.start);
  const end = Temporal.Instant.from(interval.end);
  const comparison = Temporal.Instant.compare(start, end);
  if (comparison > 0) throw new RangeError("Opening-hours interval end must not be before its start");
  if (end.since(start).total("minutes") > MAX_BOOKING_DURATION_MINUTES) {
    throw new RangeError(`Opening-hours interval exceeds ${MAX_BOOKING_DURATION_MINUTES} minutes`);
  }
  if (comparison === 0) return [];

  const lastDate = end.subtract({ nanoseconds: 1 }).toZonedDateTimeISO(timezone).toPlainDate();
  const merged: { start: Temporal.Instant; end: Temporal.Instant }[] = [];
  for (
    let date = start.toZonedDateTimeISO(timezone).toPlainDate();
    Temporal.PlainDate.compare(date, lastDate) <= 0;
    date = date.add({ days: 1 })
  ) {
    const hours = effectiveHours(settings, date.dayOfWeek);
    if (!hours) continue;
    const opening = laterInstant(resolveSchedulingBoundary(date.toString(), hours.start, timezone), start);
    const closing = earlierInstant(resolveSchedulingBoundary(date.toString(), hours.end, timezone), end);
    if (Temporal.Instant.compare(opening, closing) >= 0) continue;
    const previous = merged.at(-1);
    if (previous && Temporal.Instant.compare(opening, previous.end) <= 0) {
      previous.end = laterInstant(previous.end, closing);
    } else {
      merged.push({ start: opening, end: closing });
    }
  }
  return merged.map((opening) => ({ start: opening.start.toString(), end: opening.end.toString() }));
}

/** Every instant of a non-empty booking window must be open. */
export function coversInterval(settings: OpeningSchedule, timezone: string, interval: OpeningInterval): boolean {
  if (Temporal.Instant.compare(interval.start, interval.end) >= 0) return false;
  const openings = openingIntervals(settings, timezone, interval);
  return (
    openings.length === 1 &&
    Temporal.Instant.compare(openings[0].start, interval.start) === 0 &&
    Temporal.Instant.compare(openings[0].end, interval.end) === 0
  );
}
