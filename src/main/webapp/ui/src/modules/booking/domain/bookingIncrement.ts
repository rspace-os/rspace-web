import { Temporal } from "@js-temporal/polyfill";
import {
  ALL_ISO_WEEKDAYS,
  effectiveHours,
  isOpenAllDay,
  longestOpeningMinutes,
  MAX_BOOKING_DURATION_MINUTES,
  type OpeningSchedule,
  openingPeriods,
  validOpeningHoursSchedule,
} from "@/modules/booking/domain/bookingOpeningHours";
import { resolveWallClock, type WallClockResolution } from "@/modules/booking/domain/bookingTime";

export const INCREMENT_UNITS = ["MINUTES", "HOURS", "DAYS"] as const;
export type IncrementUnit = (typeof INCREMENT_UNITS)[number];

/** Minutes per displayed unit. A Day is a calendar day; 1440 is only its nominal size. */
export const INCREMENT_UNIT_MINUTES: Readonly<Record<IncrementUnit, number>> = { MINUTES: 1, HOURS: 60, DAYS: 1440 };

/** Increments this long or longer cannot align to the clock, whatever the stored flag says. */
const ALIGNMENT_LIMIT_MINUTES = 1440;

export type IncrementSettings = {
  slotGranularityMinutes: number;
  incrementUnit: IncrementUnit;
  alignToIncrement: boolean;
};

/** Elapsed increments count minutes; day increments count calendar days at the same local time. */
type IncrementRule = { kind: "elapsed"; minutes: number; aligned: boolean } | { kind: "days"; days: number };

export type IncrementViolation = "precision" | "order" | "alignment" | "duration" | "dayEndMissing";

export const MINUTE_NS = 60_000_000_000n;

export function alignmentAvailable(settings: Pick<IncrementSettings, "slotGranularityMinutes" | "incrementUnit">) {
  return settings.incrementUnit !== "DAYS" && settings.slotGranularityMinutes < ALIGNMENT_LIMIT_MINUTES;
}

/** The stored flag applies only below one day; it is ignored, never rewritten, above that. */
export function effectiveAlignment(settings: IncrementSettings): boolean {
  return settings.alignToIncrement && alignmentAvailable(settings);
}

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
    : { kind: "elapsed", minutes: settings.slotGranularityMinutes, aligned: effectiveAlignment(settings) };
}

/** Every ISO weekday is open from 00:00 to 24:00, which Day increments require. */
export function openAllDayEveryDay(schedule: OpeningSchedule): boolean {
  return ALL_ISO_WEEKDAYS.every((day) => isOpenAllDay(effectiveHours(schedule, day)));
}

/** Whether some start on the per-day midnight grid leaves room for a whole increment inside one opening period. */
function alignedIncrementFitsOpening(schedule: OpeningSchedule, minutes: number): boolean {
  if (!Number.isInteger(minutes) || minutes < 1) return false;
  return openingPeriods(schedule).some(({ start, end }) => {
    if (end - start >= 10080) return true;
    for (let day = Math.floor(start / 1440); day * 1440 < end; day++) {
      const dayStart = day * 1440;
      const from = Math.max(start, dayStart);
      const gridStart = dayStart + Math.ceil((from - dayStart) / minutes) * minutes;
      if (gridStart < dayStart + 1440 && gridStart + minutes <= end) return true;
    }
    return false;
  });
}

/** The structural fit of an increment to the weekly schedule, not availability on any particular date. */
export function incrementFitsSchedule(settings: OpeningSchedule & IncrementSettings): boolean {
  if (settings.incrementUnit === "DAYS") return openAllDayEveryDay(settings);
  if (effectiveAlignment(settings)) return alignedIncrementFitsOpening(settings, settings.slotGranularityMinutes);
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

/** Local minute of day in the scheduling timezone. */
function localMinuteOfDay(instant: string | Temporal.Instant, timezone: string): number {
  const zoned = Temporal.Instant.from(instant).toZonedDateTimeISO(timezone);
  return zoned.hour * 60 + zoned.minute;
}

export function isStartAligned(rule: IncrementRule, start: string | Temporal.Instant, timezone: string): boolean {
  return rule.kind !== "elapsed" || !rule.aligned || localMinuteOfDay(start, timezone) % rule.minutes === 0;
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
    if (!isStartAligned(rule, startInstant, timezone)) return "alignment";
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

/** Instants of the per-day midnight grid near `instant`, both occurrences of a repeated time, none in a gap. */
function nearbyGridStarts(instant: string | Temporal.Instant, minutes: number, timezone: string): string[] {
  const zoned = Temporal.Instant.from(instant).toZonedDateTimeISO(timezone);
  const minute = zoned.hour * 60 + zoned.minute;
  const candidates: string[] = [];
  // Near a clock change, wide enough to include a grid point up to three hours of wall time away across it.
  const windowNs = BigInt(2 * minutes + 180) * MINUTE_NS;
  const nearChange = [zoned.getTimeZoneTransition("next"), zoned.getTimeZoneTransition("previous")].some(
    (transition) => {
      if (!transition) return false;
      const gap = transition.epochNanoseconds - zoned.epochNanoseconds;
      return (gap < 0n ? -gap : gap) <= windowNs;
    },
  );
  const reach = nearChange ? 2 * minutes + 180 : 2 * minutes;
  for (const dayOffset of [-1, 0, 1]) {
    const date = zoned.toPlainDate().add({ days: dayOffset });
    const centre = minute - dayOffset * 1440;
    const first = Math.max(0, Math.floor((centre - reach) / minutes) * minutes);
    for (let gridMinute = first; gridMinute < 1440 && gridMinute <= centre + reach; gridMinute += minutes) {
      const time = Temporal.PlainTime.from({ hour: Math.floor(gridMinute / 60), minute: gridMinute % 60 }).toString({
        smallestUnit: "minute",
      });
      const resolution = resolveWallClock(date.toString(), time, timezone);
      if (resolution.kind === "unique") candidates.push(resolution.instant);
      else if (resolution.kind === "ambiguous") candidates.push(resolution.earlier, resolution.later);
    }
  }
  return candidates.toSorted((left, right) => Temporal.Instant.compare(left, right));
}

/** The nearest existing start on the per-day midnight grid in the scheduling timezone; ties choose the earlier. */
export function snapStartToGrid(start: string, minutes: number, timezone: string): string {
  const target = Temporal.Instant.from(start);
  if (target.epochNanoseconds % MINUTE_NS === 0n && localMinuteOfDay(target, timezone) % minutes === 0) return start;
  const distance = (candidate: string) =>
    Math.abs(Number(Temporal.Instant.from(candidate).epochNanoseconds - target.epochNanoseconds));
  const candidates = nearbyGridStarts(target, minutes, timezone);
  return candidates.reduce(
    (chosen, candidate) => (distance(candidate) < distance(chosen) ? candidate : chosen),
    candidates[0] ?? start,
  );
}
