import { Temporal } from "@js-temporal/polyfill";
import * as v from "valibot";
import { bookingLocale } from "@/modules/booking/domain/bookingTime";

/** The absolute booking-duration limit, which also bounds opening-hour date enumeration. */
export const MAX_BOOKING_DURATION_MINUTES = 527_040;

export const WALL_TIME = /^(?:[01]\d|2[0-3]):[0-5]\d$/;

/** ISO 8601 weekdays, as `Temporal.PlainDate.dayOfWeek` and Java's `DayOfWeek` number them. */
export const ISO_WEEKDAYS = {
  MONDAY: 1,
  TUESDAY: 2,
  WEDNESDAY: 3,
  THURSDAY: 4,
  FRIDAY: 5,
  SATURDAY: 6,
  SUNDAY: 7,
} as const;

export const ALL_ISO_WEEKDAYS: readonly number[] = [1, 2, 3, 4, 5, 6, 7];

const IsoWeekdaySchema = v.pipe(v.number(), v.integer(), v.minValue(1), v.maxValue(7));

export const OpenDaysSchema = v.pipe(
  v.array(IsoWeekdaySchema),
  v.minLength(1),
  v.maxLength(7),
  v.check((days) => new Set(days).size === days.length),
);

export const OpeningExceptionSchema = v.object({
  dayOfWeek: IsoWeekdaySchema,
  start: v.pipe(v.string(), v.regex(WALL_TIME)),
  end: v.union([v.pipe(v.string(), v.regex(WALL_TIME)), v.literal("24:00")]),
});

export const OpeningExceptionsSchema = v.pipe(
  v.array(OpeningExceptionSchema),
  v.maxLength(7),
  v.check((exceptions) => new Set(exceptions.map((exception) => exception.dayOfWeek)).size === exceptions.length),
);

export type OpeningException = v.InferOutput<typeof OpeningExceptionSchema>;

/** The opening-hour part of scheduling settings: shared hours, open weekdays, and per-day exceptions. */
export type OpeningSchedule = {
  openingStart: string;
  openingEnd: string;
  openDays: readonly number[];
  openingExceptions: readonly OpeningException[];
};

export type OpeningHours = { start: string; end: string };

/** Every day, all day: the explicit opening-hours bypass for maintenance. */
export const ALWAYS_OPEN: OpeningSchedule = {
  openingStart: "00:00",
  openingEnd: "24:00",
  openDays: ALL_ISO_WEEKDAYS,
  openingExceptions: [],
};

/** A localized weekday name for an ISO weekday; 2024-01-01 is a Monday. */
export function formatIsoWeekday(
  dayOfWeek: number,
  width: "long" | "short" = "long",
  locale = bookingLocale(),
): string {
  return new Intl.DateTimeFormat(locale, { weekday: width, timeZone: "UTC" }).format(
    new Date(Date.UTC(2024, 0, dayOfWeek)),
  );
}

/** A scheduling-zone opening range as `HH:mm–HH:mm`, printing a closing midnight as `00:00`. */
export function formatOpeningRange({ start, end }: OpeningHours): string {
  return `${start}\u2013${end === "24:00" ? "00:00" : end}`;
}

/** Drops exceptions for closed days and sorts the rest by weekday, as the editor submits them. */
export function finalizeOpeningExceptions<T extends Pick<OpeningSchedule, "openDays" | "openingExceptions">>(
  settings: T,
): T {
  return {
    ...settings,
    openingExceptions: settings.openingExceptions
      .filter((exception) => settings.openDays.includes(exception.dayOfWeek))
      .toSorted((left, right) => left.dayOfWeek - right.dayOfWeek),
  };
}

export type AbsoluteInterval = { start: string; end: string };

export function validOpeningHours(start: string, end: string): boolean {
  if (!WALL_TIME.test(start)) return false;
  if (end === "24:00") return start === "00:00";
  return WALL_TIME.test(end) && start < end;
}

/** Cross-field check: every exception is on an open day and has a valid interval. */
export function validOpeningExceptions(settings: Pick<OpeningSchedule, "openDays" | "openingExceptions">): boolean {
  return settings.openingExceptions.every(
    (exception) => settings.openDays.includes(exception.dayOfWeek) && validOpeningHours(exception.start, exception.end),
  );
}

/** Closed when the weekday is not open, its exception when it has one, otherwise the shared hours. */
export function effectiveHours(settings: OpeningSchedule, dayOfWeek: number): OpeningHours | null {
  if (!settings.openDays.includes(dayOfWeek)) return null;
  const exception = settings.openingExceptions.find((candidate) => candidate.dayOfWeek === dayOfWeek);
  if (exception) return { start: exception.start, end: exception.end };
  return { start: settings.openingStart, end: settings.openingEnd };
}

/**
 * A scheduling-zone wall-clock boundary as an instant, matching Java's `LocalDateTime.atZone`: the earlier
 * offset in a repeated hour, and a nonexistent time shifted forward by the gap. `24:00` is the next date's start.
 */
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

/**
 * The opening intervals intersecting `[interval.start, interval.end)`, clipped to it, sorted, and with touching
 * intervals merged so consecutive all-day openings are continuous. Throws a `RangeError` for a reversed interval
 * or one longer than the absolute booking limit, rather than truncating it.
 */
export function openingIntervals(
  settings: OpeningSchedule,
  timezone: string,
  interval: AbsoluteInterval,
): AbsoluteInterval[] {
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

/** Whether every instant in the non-empty half-open interval falls within opening hours. */
export function coversInterval(settings: OpeningSchedule, timezone: string, interval: AbsoluteInterval): boolean {
  if (Temporal.Instant.compare(interval.start, interval.end) >= 0) return false;
  const openings = openingIntervals(settings, timezone, interval);
  // Openings are clipped to the interval, so full coverage is exactly one opening spanning all of it.
  return (
    openings.length === 1 &&
    Temporal.Instant.compare(openings[0].start, interval.start) === 0 &&
    Temporal.Instant.compare(openings[0].end, interval.end) === 0
  );
}
