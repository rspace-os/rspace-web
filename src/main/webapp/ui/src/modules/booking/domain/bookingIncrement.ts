import { Temporal } from "@js-temporal/polyfill";
import {
  ALL_ISO_WEEKDAYS,
  effectiveHours,
  isOpenAllDay,
  longestOpeningMinutes,
  MAX_BOOKING_DURATION_MINUTES,
  type OpeningSchedule,
  validOpeningHoursSchedule,
} from "@/modules/booking/domain/bookingOpeningHours";
import { resolveWallClock, type WallClockResolution } from "@/modules/booking/domain/bookingTime";

export const INCREMENT_UNITS = ["MINUTES", "HOURS", "DAYS"] as const;
export type IncrementUnit = (typeof INCREMENT_UNITS)[number];

/** Minutes per displayed unit. A Day is a calendar day; 1440 is only its nominal size. */
export const INCREMENT_UNIT_MINUTES: Readonly<Record<IncrementUnit, number>> = { MINUTES: 1, HOURS: 60, DAYS: 1440 };

export type IncrementSettings = {
  slotGranularityMinutes: number;
  incrementUnit: IncrementUnit;
};

/** Elapsed increments count minutes; day increments count calendar days at the same local time. */
type IncrementRule = { kind: "elapsed"; minutes: number } | { kind: "days"; days: number };

export type IncrementViolation = "precision" | "order" | "duration" | "dayEndMissing";

export const MINUTE_NS = 60_000_000_000n;

/** A whole number of minutes within the absolute cap, and a whole number of days for Days. */
export function validIncrement(settings: Pick<IncrementSettings, "slotGranularityMinutes" | "incrementUnit">) {
  const minutes = settings.slotGranularityMinutes;
  return (
    Number.isInteger(minutes) &&
    minutes >= 1 &&
    minutes <= MAX_BOOKING_DURATION_MINUTES &&
    (settings.incrementUnit !== "DAYS" || minutes % INCREMENT_UNIT_MINUTES.DAYS === 0)
  );
}

export function incrementRule(settings: IncrementSettings): IncrementRule {
  return settings.incrementUnit === "DAYS"
    ? { kind: "days", days: settings.slotGranularityMinutes / INCREMENT_UNIT_MINUTES.DAYS }
    : { kind: "elapsed", minutes: settings.slotGranularityMinutes };
}

/** Every ISO weekday is open from 00:00 to 24:00, which Day increments require. */
export function openAllDayEveryDay(schedule: OpeningSchedule): boolean {
  return ALL_ISO_WEEKDAYS.every((day) => isOpenAllDay(effectiveHours(schedule, day)));
}

/** The structural fit of an increment to the weekly schedule, not availability on any particular date. */
export function incrementFitsSchedule(settings: OpeningSchedule & IncrementSettings): boolean {
  if (settings.incrementUnit === "DAYS") return openAllDayEveryDay(settings);
  return settings.slotGranularityMinutes <= longestOpeningMinutes(settings);
}

/** Settings-form check: a malformed schedule is reported by its own fields, not as an increment error. */
export function validIncrementForSchedule(settings: OpeningSchedule & IncrementSettings): boolean {
  if (!validIncrement(settings)) return false;
  const scheduleValid = validOpeningHoursSchedule(settings.openingHours);
  return !scheduleValid || incrementFitsSchedule(settings);
}

/**
 * Item maximum, in elapsed minutes; 0 means no item limit. Elapsed increments need a multiple of the
 * increment. Days need at least the nominal increment, without divisibility, so a clock-change day can fit.
 */
export function validMaximumForIncrement(
  maximumMinutes: number,
  settings: Pick<IncrementSettings, "slotGranularityMinutes" | "incrementUnit">,
): boolean {
  if (maximumMinutes === 0) return true;
  const increment = settings.slotGranularityMinutes;
  if (!Number.isInteger(maximumMinutes) || maximumMinutes > MAX_BOOKING_DURATION_MINUTES || increment <= 0) {
    return false;
  }
  if (maximumMinutes < increment) return false;
  return settings.incrementUnit === "DAYS" || maximumMinutes % increment === 0;
}

function onWholeMinute(instant: Temporal.Instant): boolean {
  return instant.epochNanoseconds % MINUTE_NS === 0n;
}

function localTimeString(zoned: Temporal.ZonedDateTime): string {
  return zoned.toPlainTime().toString({ smallestUnit: "minute" });
}

/**
 * The first increment rule an interval breaks, or null. Opening hours, maxima and conflicts are separate checks.
 * Days compare the scheduling timezone's local time and date, never elapsed time.
 */
export function incrementViolation(
  rule: IncrementRule,
  start: string,
  end: string,
  timezone: string,
): IncrementViolation | null {
  let startInstant: Temporal.Instant;
  let endInstant: Temporal.Instant;
  try {
    startInstant = Temporal.Instant.from(start);
    endInstant = Temporal.Instant.from(end);
  } catch {
    return "precision";
  }
  if (!onWholeMinute(startInstant) || !onWholeMinute(endInstant)) return "precision";
  if (Temporal.Instant.compare(endInstant, startInstant) <= 0) return "order";
  if (rule.kind === "elapsed") {
    const duration = endInstant.epochNanoseconds - startInstant.epochNanoseconds;
    return duration % (BigInt(rule.minutes) * MINUTE_NS) === 0n ? null : "duration";
  }
  const zonedStart = startInstant.toZonedDateTimeISO(timezone);
  const zonedEnd = endInstant.toZonedDateTimeISO(timezone);
  const days = zonedStart.toPlainDate().until(zonedEnd.toPlainDate(), { largestUnit: "day" }).days;
  const wholeIncrements = days > 0 && days % rule.days === 0;
  if (wholeIncrements && Temporal.PlainTime.compare(zonedStart.toPlainTime(), zonedEnd.toPlainTime()) === 0) {
    return null;
  }
  if (
    wholeIncrements &&
    resolveWallClock(zonedEnd.toPlainDate().toString(), localTimeString(zonedStart), timezone).kind === "nonexistent"
  ) {
    return "dayEndMissing";
  }
  return "duration";
}

/** The same-local-time end `increments` Day increments after `start`, with its gap or overlap resolution. */
export function dayIncrementEnd(
  start: string | Temporal.Instant,
  timezone: string,
  dayCount: number,
): { date: string; time: string; resolution: WallClockResolution } {
  const zoned = Temporal.Instant.from(start).toZonedDateTimeISO(timezone);
  const date = zoned.toPlainDate().add({ days: dayCount }).toString();
  const time = localTimeString(zoned);
  return { date, time, resolution: resolveWallClock(date, time, timezone) };
}

/** The elapsed end `start + increments × minutes`. */
function elapsedIncrementEnd(start: string | Temporal.Instant, minutes: number, increments: number): string {
  return Temporal.Instant.from(start)
    .add({ minutes: minutes * increments })
    .toString();
}

/** Nearest positive multiple to a ratio; ties choose the smaller (earlier) multiple. */
function nearestPositiveMultiple(ratio: number): number {
  const lower = Math.floor(ratio);
  const upper = lower + 1;
  const chosen = ratio - lower <= upper - ratio ? lower : upper;
  return Math.max(1, chosen);
}

type EndSnap =
  | { kind: "instant"; instant: string }
  | { kind: "ambiguous"; date: string; time: string; earlier: string; later: string }
  | { kind: "dayEndMissing"; date: string; time: string };

/**
 * The valid end nearest to a typed end for a fixed start. Elapsed increments compare elapsed time; Days compare
 * civil date/time in the scheduling timezone and keep the start's local time. Ties choose the earlier candidate.
 * A selected target in a gap is reported rather than replaced by another date.
 */
export function snapEndToIncrement(rule: IncrementRule, start: string, typedEnd: string, timezone: string): EndSnap {
  const startInstant = Temporal.Instant.from(start);
  const endInstant = Temporal.Instant.from(typedEnd);
  if (rule.kind === "elapsed") {
    const elapsed = Number(endInstant.epochNanoseconds - startInstant.epochNanoseconds) / Number(MINUTE_NS);
    const increments = nearestPositiveMultiple(elapsed / rule.minutes);
    return { kind: "instant", instant: elapsedIncrementEnd(startInstant, rule.minutes, increments) };
  }
  const startCivil = startInstant.toZonedDateTimeISO(timezone).toPlainDateTime();
  const endCivil = endInstant.toZonedDateTimeISO(timezone).toPlainDateTime();
  const civilDays = startCivil.until(endCivil, { largestUnit: "hour" }).total("hours") / 24;
  const dayCount = nearestPositiveMultiple(civilDays / rule.days) * rule.days;
  const target = dayIncrementEnd(startInstant, timezone, dayCount);
  if (target.resolution.kind === "nonexistent") return { kind: "dayEndMissing", date: target.date, time: target.time };
  if (target.resolution.kind === "ambiguous") {
    return {
      kind: "ambiguous",
      date: target.date,
      time: target.time,
      earlier: target.resolution.earlier,
      later: target.resolution.later,
    };
  }
  return { kind: "instant", instant: target.resolution.instant };
}
