import { Temporal } from "@js-temporal/polyfill";
import {
  type IncrementSettings,
  type IncrementViolation,
  incrementRule,
  incrementViolation,
} from "@/modules/booking/domain/bookingIncrement";
import {
  coversInterval,
  MAX_BOOKING_DURATION_MINUTES,
  type OpeningSchedule,
} from "@/modules/booking/domain/bookingOpeningHours";
import {
  type BookingWindowDraft,
  resolveWallClock,
  type WallClockResolution,
  wallClockInstant,
} from "@/modules/booking/domain/bookingTime";

export type ResolvedBookingWindow = { start: string; end: string };

function resolution(date: string, time: string, timezone: string): WallClockResolution | undefined {
  if (!date || !time) return undefined;
  try {
    return resolveWallClock(date, time, timezone);
  } catch {
    return undefined;
  }
}

/** The zone each endpoint's wall clock is read in; a single zone applies to both. */
export type EndpointTimezones = string | { start: string; end: string };

export function endpointTimezones(timezones: EndpointTimezones): { start: string; end: string } {
  return typeof timezones === "string" ? { start: timezones, end: timezones } : timezones;
}

export function resolveBookingWindow(
  draft: BookingWindowDraft,
  timezones: EndpointTimezones,
): { window?: ResolvedBookingWindow; start?: WallClockResolution; end?: WallClockResolution; orderInvalid: boolean } {
  const zones = endpointTimezones(timezones);
  const start = resolution(draft.startDate, draft.startTime, zones.start);
  const end = resolution(draft.endDate, draft.endTime, zones.end);
  const startInstant = wallClockInstant(start, draft.startOccurrence);
  const endInstant = wallClockInstant(end, draft.endOccurrence);
  const orderInvalid = Boolean(startInstant && endInstant && Temporal.Instant.compare(endInstant, startInstant) <= 0);
  return {
    start,
    end,
    orderInvalid,
    window: startInstant && endInstant && !orderInvalid ? { start: startInstant, end: endInstant } : undefined,
  };
}

export type BookingWindowPolicy = OpeningSchedule &
  IncrementSettings & {
    schedulingTimezone: string;
    /** The configurable item limit; 0 means only the absolute limit applies. */
    maxBookingDurationMinutes: number;
    /** False is the explicit maintenance bypass: closed weekdays and hours are not checked. */
    enforceOpeningHours?: boolean;
    allowPolicyMismatch?: boolean;
  };

/** Resolve and validate synchronously so rendering and submission use the same window. */
export function validateBookingWindow(
  value: BookingWindowDraft,
  timezones: EndpointTimezones,
  {
    schedulingTimezone: resolvedSchedulingTimezone,
    slotGranularityMinutes,
    incrementUnit,
    maxBookingDurationMinutes,
    enforceOpeningHours = true,
    allowPolicyMismatch = false,
    ...openingSchedule
  }: BookingWindowPolicy,
) {
  const result = resolveBookingWindow(value, timezones);
  const rule = incrementRule({ slotGranularityMinutes, incrementUnit });
  const violation: IncrementViolation | null = result.window
    ? incrementViolation(rule, result.window.start, result.window.end, resolvedSchedulingTimezone)
    : null;
  const incrementInvalid = violation !== null;
  // The same-time Day end that does not exist, named so the message can say which date and time.
  const missingDayEnd =
    violation === "dayEndMissing" && result.window
      ? {
          date: Temporal.Instant.from(result.window.end)
            .toZonedDateTimeISO(resolvedSchedulingTimezone)
            .toPlainDate()
            .toString(),
          time: Temporal.Instant.from(result.window.start)
            .toZonedDateTimeISO(resolvedSchedulingTimezone)
            .toPlainTime()
            .toString({ smallestUnit: "minute" }),
        }
      : undefined;
  // The smaller of the item limit and the absolute limit; checked first so an unbounded draft never
  // reaches the opening-hours date walk.
  const maximumDurationLimitMinutes =
    maxBookingDurationMinutes > 0
      ? Math.min(maxBookingDurationMinutes, MAX_BOOKING_DURATION_MINUTES)
      : MAX_BOOKING_DURATION_MINUTES;
  const elapsedMinutes = result.window
    ? (Temporal.Instant.from(result.window.end).epochMilliseconds -
        Temporal.Instant.from(result.window.start).epochMilliseconds) /
      60_000
    : undefined;
  const maximumDurationInvalid = elapsedMinutes !== undefined && elapsedMinutes > maximumDurationLimitMinutes;
  const openingInvalid = Boolean(
    enforceOpeningHours &&
      result.window &&
      !maximumDurationInvalid &&
      !coversInterval(openingSchedule, resolvedSchedulingTimezone, result.window),
  );
  const policyInvalid = incrementInvalid || openingInvalid || maximumDurationInvalid;
  const resolvedWindow = policyInvalid && !allowPolicyMismatch ? undefined : result.window;
  return {
    ...result,
    window: resolvedWindow,
    elapsedMinutes,
    rule,
    incrementViolation: violation,
    missingDayEnd,
    openingInvalid,
    maximumDurationInvalid,
    maximumDurationLimitMinutes,
    policyInvalid,
  };
}
