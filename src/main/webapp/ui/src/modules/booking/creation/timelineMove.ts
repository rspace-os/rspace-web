import { Temporal } from "@js-temporal/polyfill";
import { wallClockDraftFromInstants, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { type BookingWindowPolicy, validateBookingWindow } from "./ZonedBookingWindowFields";

export type TimelineMoveRange = { start: string; end: string };

export type TimelineMovePolicy = Pick<
  BookingWindowPolicy,
  "maxBookingDurationMinutes" | "openingHours" | "enforceOpeningHours"
>;

export type TimelineMoveContext = {
  date: string;
  displayTimezone: string;
  policy: BookingWindowPolicy;
};

export type TimelineMoveRequest =
  | { kind: "nearest"; target: string | Temporal.Instant }
  | { kind: "step"; direction: "previous" | "next" };

const MINUTE_NS = 60_000_000_000n;
const SEARCH_RADIUS_MINUTES = 1440;

function onSchedulingGrid(instant: Temporal.Instant, policy: BookingWindowPolicy): boolean {
  const increment = policy.slotGranularityMinutes;
  if (!Number.isInteger(increment) || increment <= 0) return false;
  const scheduled = instant.toZonedDateTimeISO(policy.schedulingTimezone);
  return (
    (scheduled.hour * 60 + scheduled.minute) % increment === 0 &&
    scheduled.second === 0 &&
    scheduled.millisecond === 0 &&
    scheduled.microsecond === 0 &&
    scheduled.nanosecond === 0
  );
}

function movedRange(
  candidateStart: Temporal.Instant,
  elapsedNanoseconds: bigint,
  context: TimelineMoveContext,
  dayStart: Temporal.Instant,
  dayEnd: Temporal.Instant,
): TimelineMoveRange | undefined {
  const candidateEnd = Temporal.Instant.fromEpochNanoseconds(candidateStart.epochNanoseconds + elapsedNanoseconds);
  if (
    Temporal.Instant.compare(candidateStart, dayEnd) >= 0 ||
    Temporal.Instant.compare(candidateEnd, dayStart) <= 0 ||
    !onSchedulingGrid(candidateStart, context.policy) ||
    !onSchedulingGrid(candidateEnd, context.policy)
  ) {
    return undefined;
  }

  const draft = wallClockDraftFromInstants(candidateStart.toString(), candidateEnd.toString(), context.displayTimezone);
  if (
    !validateBookingWindow(draft, context.displayTimezone, {
      ...context.policy,
      allowPolicyMismatch: false,
    }).window
  ) {
    return undefined;
  }

  return { start: candidateStart.toString(), end: candidateEnd.toString() };
}

/** Move one complete historical booking interval to the nearest policy-valid start. */
export function moveTimelineRange(
  range: TimelineMoveRange,
  request: TimelineMoveRequest,
  context: TimelineMoveContext,
): TimelineMoveRange | undefined {
  let start: Temporal.Instant;
  let end: Temporal.Instant;
  let target: Temporal.Instant | undefined;
  try {
    start = Temporal.Instant.from(range.start);
    end = Temporal.Instant.from(range.end);
    if (request.kind === "nearest") {
      target = Temporal.Instant.from(request.target);
    }
  } catch {
    return undefined;
  }
  if (Temporal.Instant.compare(end, start) <= 0) return undefined;

  // Historical movement preserves elapsed time; a future Day rule must derive scheduling-zone calendar days here.
  const elapsedNanoseconds = end.epochNanoseconds - start.epochNanoseconds;
  if (
    context.policy.maxBookingDurationMinutes > 0 &&
    elapsedNanoseconds > BigInt(context.policy.maxBookingDurationMinutes) * MINUTE_NS
  ) {
    return undefined;
  }
  const bounds = zonedDayBounds(context.date, context.displayTimezone);
  const dayStart = Temporal.Instant.from(bounds.start);
  const dayEnd = Temporal.Instant.from(bounds.end);
  const candidate = (candidateStart: Temporal.Instant) =>
    movedRange(candidateStart, elapsedNanoseconds, context, dayStart, dayEnd);

  if (request.kind === "step") {
    const sign = request.direction === "next" ? 1 : -1;
    for (let minutes = 1; minutes <= SEARCH_RADIUS_MINUTES; minutes++) {
      const moved = candidate(start.add({ minutes: sign * minutes }));
      if (moved) return moved;
    }
    return undefined;
  }

  if (!target) return undefined;
  const exact = target.epochNanoseconds;
  let lower = target.round({ smallestUnit: "minute", roundingMode: "floor" });
  let upper = lower.add({ minutes: 1 });
  const radius = BigInt(SEARCH_RADIUS_MINUTES) * MINUTE_NS;

  while (true) {
    const lowerDistance = exact - lower.epochNanoseconds;
    const upperDistance = upper.epochNanoseconds - exact;
    const lowerInRange = lowerDistance <= radius;
    const upperInRange = upperDistance <= radius;
    if (!lowerInRange && !upperInRange) return undefined;

    const takeLower = !upperInRange || (lowerInRange && lowerDistance <= upperDistance);
    const candidateStart = takeLower ? lower : upper;
    if (takeLower) lower = lower.subtract({ minutes: 1 });
    else upper = upper.add({ minutes: 1 });

    const moved = candidate(candidateStart);
    if (moved) return moved;
  }
}
