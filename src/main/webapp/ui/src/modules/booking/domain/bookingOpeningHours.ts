import { Temporal } from "@js-temporal/polyfill";
import * as v from "valibot";

/** ISO 8601 weekdays, matching Java's DayOfWeek numbering. */
export const ALL_ISO_WEEKDAYS: readonly number[] = [1, 2, 3, 4, 5, 6, 7];

const WALL_TIME = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
const IsoWeekdaySchema = v.pipe(v.number(), v.integer(), v.minValue(1), v.maxValue(7));

export const OpeningHoursEntrySchema = v.pipe(
  v.strictObject({
    dayOfWeek: IsoWeekdaySchema,
    start: v.pipe(v.string(), v.regex(WALL_TIME)),
    end: v.union([v.pipe(v.string(), v.regex(WALL_TIME)), v.literal("24:00")]),
  }),
  v.forward(
    v.check((entry) => validOpeningHours(entry.start, entry.end)),
    ["end"],
  ),
);

/** One independently configured weekday; a missing entry means that weekday is closed. */
export type OpeningHoursEntry = v.InferOutput<typeof OpeningHoursEntrySchema>;

/** Complete replacement schedule; parsed output uses canonical weekday order. */
export const OpeningHoursSchema = v.pipe(
  v.array(OpeningHoursEntrySchema),
  v.minLength(1),
  v.maxLength(7),
  v.check((entries) => new Set(entries.map((entry) => entry.dayOfWeek)).size === entries.length),
  v.transform((entries) => entries.toSorted((left, right) => left.dayOfWeek - right.dayOfWeek)),
);

export type OpeningInterval = { start: string; end: string };

export type OpeningSchedule = { openingHours: readonly OpeningHoursEntry[] };

/** The absolute duration cap also bounds date enumeration. */
export const MAX_BOOKING_DURATION_MINUTES = 527_040;

/** A localized weekday name; 2024-01-01 is a Monday. */
export function formatIsoWeekday(dayOfWeek: number, width: "long" | "short" = "long", locale = "en-US"): string {
  return new Intl.DateTimeFormat(locale, { weekday: width, timeZone: "UTC" }).format(
    new Date(Date.UTC(2024, 0, dayOfWeek)),
  );
}

/** A 24:00 end closes at the next midnight and follows any valid start. */
export function validOpeningHours(start: string, end: string): boolean {
  if (!WALL_TIME.test(start)) return false;
  if (end === "24:00") return true;
  return WALL_TIME.test(end) && start < end;
}

export function isOpenAllDay(hours: OpeningInterval | null | undefined): boolean {
  return hours?.start === "00:00" && hours.end === "24:00";
}

/** The last valid custom pair that Open all day can restore. */
export function customOpeningHours(hours: OpeningInterval): OpeningInterval | undefined {
  return validOpeningHours(hours.start, hours.end) && !isOpenAllDay(hours)
    ? { start: hours.start, end: hours.end }
    : undefined;
}

/** A missing weekday is closed. */
export function effectiveHours(settings: OpeningSchedule, dayOfWeek: number): OpeningInterval | null {
  const entry = settings.openingHours.find((candidate) => candidate.dayOfWeek === dayOfWeek);
  return entry ? { start: entry.start, end: entry.end } : null;
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
