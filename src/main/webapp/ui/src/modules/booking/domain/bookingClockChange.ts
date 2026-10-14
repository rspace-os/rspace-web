import { Temporal } from "@js-temporal/polyfill";
import { MAX_BOOKING_DURATION_MINUTES } from "@/modules/booking/domain/bookingOpeningHours";

export type ClockChange = { instant: string; offsetBefore: string; offsetAfter: string; deltaMinutes: number };

export type ClockChangeCrossing = {
  timezone: string;
  changes: ClockChange[];
  /** One shift into or out of daylight saving time, by the zone's own names, rather than another offset change. */
  daylightSaving: boolean;
  elapsedMinutes: number;
  /** Local calendar dates from the start's date to the end's date in `timezone`. */
  calendarDays: number;
};

const NS_PER_SECOND = 1_000_000_000n;
const NS_PER_MINUTE = 60n * NS_PER_SECOND;
/**
 * Offsets are sampled this often. The polyfill's own transition search steps by two weeks and misses a change that
 * is reversed within that step (Gaza's Ramadan suspensions); no zone changes twice within six hours.
 */
const SAMPLE_STEP = 6n * 60n * NS_PER_MINUTE;
/** No event, maintenance included, may exceed this, so a longer draft is already rejected and gets no note. */
const LONGEST_EVENT = BigInt(MAX_BOOKING_DURATION_MINUTES) * NS_PER_MINUTE;

function offsetAt(epochNanoseconds: bigint, timezone: string): number {
  return Temporal.Instant.fromEpochNanoseconds(epochNanoseconds).toZonedDateTimeISO(timezone).offsetNanoseconds;
}

/** The first whole second in `(low, high]` with a different offset from `low`'s, by bisection. */
function transitionBetween(low: bigint, high: bigint, timezone: string): bigint {
  const before = offsetAt(low, timezone);
  let earlier = low;
  let later = high;
  while (later - earlier > NS_PER_SECOND) {
    const middle = earlier + (later - earlier) / 2n;
    if (offsetAt(middle, timezone) === before) earlier = middle;
    else later = middle;
  }
  // Offsets change on whole seconds, and at most two lie in this window.
  const firstSecond = earlier - (((earlier % NS_PER_SECOND) + NS_PER_SECOND) % NS_PER_SECOND) + NS_PER_SECOND;
  return firstSecond <= later && offsetAt(firstSecond, timezone) !== before ? firstSecond : later;
}

function sampledTransitions(start: bigint, end: bigint, timezone: string): bigint[] {
  const instants: bigint[] = [];
  let previous = start;
  let previousOffset = offsetAt(start, timezone);
  while (previous < end) {
    const next = previous + SAMPLE_STEP < end ? previous + SAMPLE_STEP : end;
    const nextOffset = offsetAt(next, timezone);
    if (nextOffset === previousOffset) {
      previous = next;
      continue;
    }
    const transition = transitionBetween(previous, next, timezone);
    instants.push(transition);
    previous = transition;
    previousOffset = offsetAt(transition, timezone);
  }
  return instants;
}

function deltaAt(epochNanoseconds: bigint, timezone: string): number {
  return offsetAt(epochNanoseconds, timezone) - offsetAt(epochNanoseconds - 1n, timezone);
}

const DAYLIGHT_NAME = /\b(daylight|summer)\b/i;

/** The zone's long English name at an instant, such as "Central European Summer Time", or "" when it has none. */
function zoneName(epochNanoseconds: bigint, timezone: string): string {
  const parts = new Intl.DateTimeFormat("en-US", { timeZone: timezone, timeZoneName: "long" }).formatToParts(
    new Date(Number(epochNanoseconds / 1_000_000n)),
  );
  return parts.find((part) => part.type === "timeZoneName")?.value ?? "";
}

/**
 * A daylight-saving shift: the zone's own names show daylight or summer time on exactly one side. Offsets alone
 * cannot tell a seasonal shift from a permanent standard-time change between nearby seasonal ones; when the names
 * are generic ("GMT+11") or neither says daylight, the wording stays neutral.
 */
function daylightSavingShift(transition: bigint, timezone: string): boolean {
  return (
    DAYLIGHT_NAME.test(zoneName(transition - 1_000_000n, timezone)) !==
    DAYLIGHT_NAME.test(zoneName(transition, timezone))
  );
}

function offsetLabel(epochNanoseconds: bigint, timezone: string): string {
  return Temporal.Instant.fromEpochNanoseconds(epochNanoseconds).toZonedDateTimeISO(timezone).offset;
}

/**
 * The offset transitions in `timezone` with `start < transition <= end`, or undefined when there are none. A
 * transition exactly at the end counts because it changes the end's displayed local time; equal offsets at both
 * endpoints do not hide two changes in between.
 */
export function clockChangeCrossing(start: string, end: string, timezone: string): ClockChangeCrossing | undefined {
  try {
    return crossing(start, end, timezone);
  } catch (error) {
    // An unparseable instant or a zone Temporal does not know (such as "GMT+01:00") gets no note, never a crash.
    if (error instanceof RangeError) return undefined;
    throw error;
  }
}

function crossing(start: string, end: string, timezone: string): ClockChangeCrossing | undefined {
  let startInstant: Temporal.Instant;
  let endInstant: Temporal.Instant;
  try {
    startInstant = Temporal.Instant.from(start);
    endInstant = Temporal.Instant.from(end);
  } catch {
    return undefined;
  }
  const startNs = startInstant.epochNanoseconds;
  const endNs = endInstant.epochNanoseconds;
  if (endNs <= startNs || endNs - startNs > LONGEST_EVENT) return undefined;
  const instants = sampledTransitions(startNs, endNs, timezone);
  if (instants.length === 0) return undefined;
  const changes = instants.map((instant) => ({
    instant: Temporal.Instant.fromEpochNanoseconds(instant).toString(),
    offsetBefore: offsetLabel(instant - 1n, timezone),
    offsetAfter: offsetLabel(instant, timezone),
    deltaMinutes: deltaAt(instant, timezone) / Number(NS_PER_MINUTE),
  }));
  const zonedStart = startInstant.toZonedDateTimeISO(timezone);
  const zonedEnd = endInstant.toZonedDateTimeISO(timezone);
  return {
    timezone,
    changes,
    daylightSaving: changes.length === 1 && daylightSavingShift(instants[0], timezone),
    elapsedMinutes: Math.round(endInstant.since(startInstant).total("minutes")),
    calendarDays: zonedStart.toPlainDate().until(zonedEnd.toPlainDate(), { largestUnit: "day" }).days,
  };
}
