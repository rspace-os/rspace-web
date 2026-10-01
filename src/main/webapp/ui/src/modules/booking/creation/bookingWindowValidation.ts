import { Temporal } from "@js-temporal/polyfill";
import {
  coversInterval,
  MAX_BOOKING_DURATION_MINUTES,
  type OpeningHoursEntry,
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

export function resolveBookingWindow(
  draft: BookingWindowDraft,
  displayTimezone: string,
): { window?: ResolvedBookingWindow; start?: WallClockResolution; end?: WallClockResolution; orderInvalid: boolean } {
  const start = resolution(draft.startDate, draft.startTime, displayTimezone);
  const end = resolution(draft.endDate, draft.endTime, displayTimezone);
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

export type BookingWindowPolicy = {
  schedulingTimezone: string;
  slotGranularityMinutes: number;
  maxBookingDurationMinutes: number;
  openingHours: readonly OpeningHoursEntry[];
  enforceOpeningHours?: boolean;
  allowPolicyMismatch?: boolean;
};

/** Resolve and validate synchronously so rendering and submission use the same window. */
export function validateBookingWindow(
  value: BookingWindowDraft,
  displayTimezone: string,
  {
    schedulingTimezone: resolvedSchedulingTimezone,
    slotGranularityMinutes,
    maxBookingDurationMinutes,
    openingHours,
    enforceOpeningHours = true,
    allowPolicyMismatch = false,
  }: BookingWindowPolicy,
) {
  const result = resolveBookingWindow(value, displayTimezone);
  const granularityInvalid = Boolean(
    result.window &&
      (!isBookingInstantAlignedToGranularity(result.window.start, resolvedSchedulingTimezone, slotGranularityMinutes) ||
        !isBookingInstantAlignedToGranularity(result.window.end, resolvedSchedulingTimezone, slotGranularityMinutes)),
  );
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
      !coversInterval({ openingHours }, resolvedSchedulingTimezone, result.window),
  );
  const policyInvalid = granularityInvalid || openingInvalid || maximumDurationInvalid;
  const resolvedWindow = policyInvalid && !allowPolicyMismatch ? undefined : result.window;
  return {
    ...result,
    window: resolvedWindow,
    granularityInvalid,
    openingInvalid,
    maximumDurationInvalid,
    policyInvalid,
  };
}
