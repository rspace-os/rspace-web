import { Temporal } from "@js-temporal/polyfill";
import {
  coversInterval,
  MAX_BOOKING_DURATION_MINUTES,
  type OpeningSchedule,
} from "@/modules/booking/domain/bookingOpeningHours";
import {
  type BookingWindowDraft,
  isBookingInstantAlignedToGranularity,
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

export type BookingWindowPolicy = OpeningSchedule & {
  schedulingTimezone: string;
  slotGranularityMinutes: number;
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
    maxBookingDurationMinutes,
    enforceOpeningHours = true,
    allowPolicyMismatch = false,
    ...openingSchedule
  }: BookingWindowPolicy,
) {
  const result = resolveBookingWindow(value, timezones);
  const granularityInvalid = Boolean(
    result.window &&
      (!isBookingInstantAlignedToGranularity(result.window.start, resolvedSchedulingTimezone, slotGranularityMinutes) ||
        !isBookingInstantAlignedToGranularity(result.window.end, resolvedSchedulingTimezone, slotGranularityMinutes)),
  );
  // The smaller of the item limit and the absolute limit; checked first so an unbounded draft never
  // reaches the opening-hours date walk.
  const maximumDurationLimitMinutes =
    maxBookingDurationMinutes > 0
      ? Math.min(maxBookingDurationMinutes, MAX_BOOKING_DURATION_MINUTES)
      : MAX_BOOKING_DURATION_MINUTES;
  const maximumDurationInvalid = Boolean(
    result.window &&
      Temporal.Instant.from(result.window.end).epochMilliseconds -
        Temporal.Instant.from(result.window.start).epochMilliseconds >
        maximumDurationLimitMinutes * 60_000,
  );
  const openingInvalid = Boolean(
    enforceOpeningHours &&
      result.window &&
      !maximumDurationInvalid &&
      !coversInterval(openingSchedule, resolvedSchedulingTimezone, result.window),
  );
  const policyInvalid = granularityInvalid || openingInvalid || maximumDurationInvalid;
  const resolvedWindow = policyInvalid && !allowPolicyMismatch ? undefined : result.window;
  return {
    ...result,
    window: resolvedWindow,
    granularityInvalid,
    openingInvalid,
    maximumDurationInvalid,
    maximumDurationLimitMinutes,
    policyInvalid,
  };
}
