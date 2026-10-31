import { Temporal } from "@js-temporal/polyfill";
import {
  dayIncrementEnd,
  type IncrementSettings,
  type IncrementViolation,
  incrementRule,
  incrementViolation,
  snapEndToIncrement,
} from "@/modules/booking/domain/bookingIncrement";
import {
  ALWAYS_OPEN,
  coversInterval,
  MAX_BOOKING_DURATION_MINUTES,
  type OpeningSchedule,
} from "@/modules/booking/domain/bookingOpeningHours";
import {
  addCalendarDays,
  dayMinuteToZonedTime,
  instantToDayMinute,
  resolveWallClock,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";

/** Everything a timeline needs to tell a valid interval from an invalid one, without server-only checks. */
export type TimelinePolicy = OpeningSchedule &
  IncrementSettings & {
    schedulingTimezone: string;
    /** The item limit; 0 means only the absolute limit applies. */
    maxBookingDurationMinutes: number;
    /** False is the maintenance exemption from opening hours. */
    enforceOpeningHours: boolean;
  };

export type TimelineRange = { start: string; end: string };
export type TimelineEdge = "move" | "start" | "end";
export type TimelineContext = { date: string; displayTimezone: string; policy: TimelinePolicy };
export type RangeFailure = IncrementViolation | "maximum" | "openingHours";
export type HiddenEdgeAction =
  | { kind: "enabled"; range: TimelineRange }
  | { kind: "disabled"; reason: RangeFailure | "midnightSkipped" | "midnightRepeated" };

type PolicySource = OpeningSchedule & IncrementSettings & { timezone: string; maxBookingDurationMinutes: number };

const MINUTE_NS = 60_000_000_000n;
/**
 * How far a move or step searches for a valid start, in minutes either way.
 * ponytail: lazy nearest-first search with per-start caching; a pointer far inside closed hours costs about 130 ms
 * on the first frame and a few milliseconds after. Precompute valid start intervals from the opening schedule if
 * the first frame shows as jank.
 */
const SEARCH_RADIUS_MINUTES = 1440;

/** A booking follows the item's rules; maintenance keeps its exemptions from increment, hours and maximum. */
export function timelinePolicy(item: PolicySource, eventKind: "BOOKING" | "MAINTENANCE" = "BOOKING"): TimelinePolicy {
  if (eventKind === "MAINTENANCE") {
    return {
      ...ALWAYS_OPEN,
      slotGranularityMinutes: 1,
      incrementUnit: "MINUTES",
      schedulingTimezone: item.timezone,
      maxBookingDurationMinutes: 0,
      enforceOpeningHours: false,
    };
  }
  return {
    openingHours: item.openingHours,
    slotGranularityMinutes: item.slotGranularityMinutes,
    incrementUnit: item.incrementUnit,
    schedulingTimezone: item.timezone,
    maxBookingDurationMinutes: item.maxBookingDurationMinutes,
    enforceOpeningHours: true,
  };
}

/** No increment, opening-hours or maximum limits; for a draft before an item is chosen. */
export function unrestrictedTimelinePolicy(timezone: string): TimelinePolicy {
  return {
    ...ALWAYS_OPEN,
    slotGranularityMinutes: 1,
    incrementUnit: "MINUTES",
    schedulingTimezone: timezone,
    maxBookingDurationMinutes: 0,
    enforceOpeningHours: false,
  };
}

/** The first rule a complete interval breaks, or null; permissions and conflicts stay with the server. */
function rangeFailure(
  start: string | Temporal.Instant,
  end: string | Temporal.Instant,
  policy: TimelinePolicy,
): RangeFailure | null {
  const violation = incrementViolation(
    incrementRule(policy),
    start.toString(),
    end.toString(),
    policy.schedulingTimezone,
  );
  if (violation) return violation;
  const elapsedMinutes = Temporal.Instant.from(end).since(Temporal.Instant.from(start)).total("minutes");
  const limit =
    policy.maxBookingDurationMinutes > 0
      ? Math.min(policy.maxBookingDurationMinutes, MAX_BOOKING_DURATION_MINUTES)
      : MAX_BOOKING_DURATION_MINUTES;
  if (elapsedMinutes > limit) return "maximum";
  if (
    policy.enforceOpeningHours &&
    !coversInterval(policy, policy.schedulingTimezone, { start: start.toString(), end: end.toString() })
  ) {
    return "openingHours";
  }
  return null;
}

function toWholeMinute(instant: Temporal.Instant): Temporal.Instant {
  return instant.round({ smallestUnit: "minute" });
}

function compare(left: Temporal.Instant, right: Temporal.Instant): number {
  return Temporal.Instant.compare(left, right);
}

function distance(left: Temporal.Instant, right: Temporal.Instant): bigint {
  const difference = left.epochNanoseconds - right.epochNanoseconds;
  return difference < 0n ? -difference : difference;
}

function localDate(instant: Temporal.Instant, timezone: string): Temporal.PlainDate {
  return instant.toZonedDateTimeISO(timezone).toPlainDate();
}

function dateDifference(from: Temporal.PlainDate, to: Temporal.PlainDate): number {
  return from.until(to, { largestUnit: "day" }).days;
}

/** Instants for one local date and the time of `reference`, both occurrences of a repeated time, none in a gap. */
function sameLocalTimeOn(date: Temporal.PlainDate, reference: Temporal.Instant, timezone: string): Temporal.Instant[] {
  const time = reference.toZonedDateTimeISO(timezone).toPlainTime().toString({ smallestUnit: "minute" });
  const resolution = resolveWallClock(date.toString(), time, timezone);
  if (resolution.kind === "unique") return [Temporal.Instant.from(resolution.instant)];
  if (resolution.kind === "ambiguous") {
    return [Temporal.Instant.from(resolution.earlier), Temporal.Instant.from(resolution.later)];
  }
  return [];
}

export type TimelineAdjuster = {
  /** Whether each endpoint falls on the displayed day; a hidden one shows a continuation instead. */
  visible: { start: boolean; end: boolean };
  /** A distinct valid endpoint exists on the displayed day while the other endpoint stays fixed. */
  canResize: (edge: "start" | "end") => boolean;
  /** The nearest valid interval for a pointer target: the new start for a move, the moved endpoint otherwise. */
  adjust: (edge: TimelineEdge, target: string | Temporal.Instant) => TimelineRange | undefined;
  /** The previous or next valid interval for keyboard adjustment. */
  step: (edge: TimelineEdge, direction: "previous" | "next") => TimelineRange | undefined;
  /** The midnight action for an endpoint outside the displayed day, or undefined when the endpoint is visible. */
  hiddenEdge: (edge: "start" | "end") => HiddenEdgeAction | undefined;
};

/**
 * Valid adjustments of one interval on one displayed day. Moves keep the duration basis: elapsed time for minute
 * and hour increments, the calendar-day count for Day increments. Every candidate is checked as a complete interval.
 */
export function timelineAdjuster(range: TimelineRange, context: TimelineContext): TimelineAdjuster | undefined {
  let start: Temporal.Instant;
  let end: Temporal.Instant;
  try {
    start = Temporal.Instant.from(range.start);
    end = Temporal.Instant.from(range.end);
  } catch {
    return undefined;
  }
  if (compare(end, start) <= 0) return undefined;

  const { policy, date, displayTimezone } = context;
  const timezone = policy.schedulingTimezone;
  const rule = incrementRule(policy);
  const bounds = zonedDayBounds(date, displayTimezone);
  const dayStart = Temporal.Instant.from(bounds.start);
  const dayEnd = Temporal.Instant.from(bounds.end);
  const startVisible = compare(start, dayStart) >= 0 && compare(start, dayEnd) < 0;
  const endVisible = compare(end, dayStart) > 0 && compare(end, dayEnd) <= 0;
  const elapsed = end.epochNanoseconds - start.epochNanoseconds;
  const dayCount = Math.max(1, dateDifference(localDate(start, timezone), localDate(end, timezone)));

  const validity = new Map<string, boolean>();
  const valid = (candidateStart: Temporal.Instant, candidateEnd: Temporal.Instant) => {
    const key = `${candidateStart.epochNanoseconds}|${candidateEnd.epochNanoseconds}`;
    let result = validity.get(key);
    if (result === undefined) {
      result = rangeFailure(candidateStart, candidateEnd, policy) === null;
      validity.set(key, result);
    }
    return result;
  };
  const asRange = (candidateStart: Temporal.Instant, candidateEnd: Temporal.Instant): TimelineRange => ({
    start: candidateStart.toString(),
    end: candidateEnd.toString(),
  });

  // A moved Day booking keeps its calendar-day count. A derived end on a skipped or repeated local time is not
  // offered: the timeline cannot ask which occurrence to use, so the form does that instead.
  const movedEnd = (candidateStart: Temporal.Instant): Temporal.Instant | undefined => {
    if (rule.kind === "elapsed")
      return Temporal.Instant.fromEpochNanoseconds(candidateStart.epochNanoseconds + elapsed);
    // Not moving keeps the end the user already resolved, including a chosen occurrence of a repeated time.
    if (compare(candidateStart, start) === 0) return end;
    const target = dayIncrementEnd(candidateStart, timezone, dayCount).resolution;
    return target.kind === "unique" ? Temporal.Instant.from(target.instant) : undefined;
  };
  // Pointer frames revisit the same starts, so each start's outcome is computed once per interval.
  const moves = new Map<bigint, TimelineRange | null>();
  const moveCandidate = (candidateStart: Temporal.Instant): TimelineRange | undefined => {
    const cached = moves.get(candidateStart.epochNanoseconds);
    if (cached !== undefined) return cached ?? undefined;
    let result: TimelineRange | null = null;
    const candidateEnd = movedEnd(candidateStart);
    // The moved interval must stay on the displayed day; its true start is never clamped into it.
    if (
      candidateEnd &&
      compare(candidateStart, dayEnd) < 0 &&
      compare(candidateEnd, dayStart) > 0 &&
      valid(candidateStart, candidateEnd)
    ) {
      result = asRange(candidateStart, candidateEnd);
    }
    moves.set(candidateStart.epochNanoseconds, result);
    return result ?? undefined;
  };

  let resizeLists: Partial<Record<"start" | "end", Temporal.Instant[]>> = {};
  // Endpoints on the displayed day that keep the other endpoint fixed, in timeline order.
  const resizeCandidates = (edge: "start" | "end"): Temporal.Instant[] => {
    const cached = resizeLists[edge];
    if (cached) return cached;
    const candidates: Temporal.Instant[] = [];
    if (rule.kind === "elapsed") {
      const fixed = edge === "start" ? end : start;
      const sign = edge === "start" ? -1 : 1;
      // Skip straight to the first increment that can reach the displayed day.
      const minutesToDay = Number(
        (edge === "end"
          ? dayStart.epochNanoseconds - fixed.epochNanoseconds
          : fixed.epochNanoseconds - dayEnd.epochNanoseconds) / MINUTE_NS,
      );
      const firstIncrement = Math.max(1, Math.floor(minutesToDay / rule.minutes));
      for (let increments = firstIncrement; ; increments++) {
        const candidate = fixed.add({ minutes: sign * increments * rule.minutes });
        if (edge === "end" ? compare(candidate, dayEnd) > 0 : compare(candidate, dayStart) < 0) break;
        const onDay = edge === "end" ? compare(candidate, dayStart) > 0 : compare(candidate, dayEnd) < 0;
        if (onDay) candidates.push(candidate);
      }
    } else {
      const fixed = edge === "start" ? end : start;
      const fixedDate = localDate(fixed, timezone);
      const first = localDate(dayStart, timezone);
      const last = localDate(dayEnd, timezone);
      for (let date = first; Temporal.PlainDate.compare(date, last) <= 0; date = date.add({ days: 1 })) {
        const offset = dateDifference(fixedDate, date) * (edge === "start" ? -1 : 1);
        if (offset <= 0 || offset % rule.days !== 0) continue;
        for (const candidate of sameLocalTimeOn(date, fixed, timezone)) {
          const onDay =
            edge === "end"
              ? compare(candidate, dayStart) > 0 && compare(candidate, dayEnd) <= 0
              : compare(candidate, dayStart) >= 0 && compare(candidate, dayEnd) < 0;
          if (onDay) candidates.push(candidate);
        }
      }
    }
    candidates.sort(compare);
    resizeLists = { ...resizeLists, [edge]: candidates };
    return candidates;
  };
  const resized = (edge: "start" | "end", candidate: Temporal.Instant) => {
    const candidateStart = edge === "start" ? candidate : start;
    const candidateEnd = edge === "end" ? candidate : end;
    return compare(candidateEnd, candidateStart) > 0 && valid(candidateStart, candidateEnd)
      ? asRange(candidateStart, candidateEnd)
      : undefined;
  };
  const current = (edge: "start" | "end") => (edge === "start" ? start : end);

  const canResize = (edge: "start" | "end") =>
    (edge === "start" ? startVisible : endVisible) &&
    resizeCandidates(edge).some(
      (candidate) => compare(candidate, current(edge)) !== 0 && resized(edge, candidate) !== undefined,
    );

  const adjust = (edge: TimelineEdge, rawTarget: string | Temporal.Instant): TimelineRange | undefined => {
    let target: Temporal.Instant;
    try {
      target = Temporal.Instant.from(rawTarget);
    } catch {
      return undefined;
    }
    if (edge === "move") {
      // Whole-minute starts in order of distance from the exact target; at equal distance the earlier wins.
      const exact = target.epochNanoseconds;
      let lower = target.round({ smallestUnit: "minute", roundingMode: "floor" }).epochNanoseconds;
      let upper = lower + MINUTE_NS;
      const radius = BigInt(SEARCH_RADIUS_MINUTES) * MINUTE_NS;
      while (exact - lower <= radius || upper - exact <= radius) {
        const takeLower = exact - lower <= upper - exact;
        const candidate = takeLower ? lower : upper;
        if (takeLower) lower -= MINUTE_NS;
        else upper += MINUTE_NS;
        const next = moveCandidate(Temporal.Instant.fromEpochNanoseconds(candidate));
        if (next) return next;
      }
      return undefined;
    }
    if (!(edge === "start" ? startVisible : endVisible)) return undefined;
    const ordered = resizeCandidates(edge).toSorted((left, right) => {
      const byDistance = distance(left, target) - distance(right, target);
      return byDistance === 0n ? compare(left, right) : byDistance < 0n ? -1 : 1;
    });
    for (const candidate of ordered) {
      const next = resized(edge, candidate);
      if (next) return next;
    }
    return undefined;
  };

  const step = (edge: TimelineEdge, direction: "previous" | "next"): TimelineRange | undefined => {
    const sign = direction === "next" ? 1 : -1;
    if (edge === "move") {
      for (let offset = 1; offset <= SEARCH_RADIUS_MINUTES; offset++) {
        const next = moveCandidate(start.add({ minutes: sign * offset }));
        if (next) return next;
      }
      return undefined;
    }
    if (!(edge === "start" ? startVisible : endVisible)) return undefined;
    const candidates = resizeCandidates(edge);
    const ordered = direction === "next" ? candidates : candidates.toReversed();
    for (const candidate of ordered) {
      if (compare(candidate, current(edge)) * sign <= 0) continue;
      const next = resized(edge, candidate);
      if (next) return next;
    }
    return undefined;
  };

  const hiddenEdge = (edge: "start" | "end"): HiddenEdgeAction | undefined => {
    const hidden = edge === "start" ? compare(start, dayStart) < 0 : compare(end, dayEnd) > 0;
    if (!hidden) return undefined;
    // The displayed day's own midnight, in the display timezone the user is looking at.
    const midnightDate = edge === "start" ? date : addCalendarDays(date, 1);
    const midnight = resolveWallClock(midnightDate, "00:00", displayTimezone);
    if (midnight.kind === "nonexistent") return { kind: "disabled", reason: "midnightSkipped" };
    if (midnight.kind === "ambiguous") return { kind: "disabled", reason: "midnightRepeated" };
    const boundary = Temporal.Instant.from(midnight.instant);
    const candidateStart = edge === "start" ? boundary : start;
    const candidateEnd = edge === "end" ? boundary : end;
    const failure =
      compare(candidateEnd, candidateStart) <= 0 ? "order" : rangeFailure(candidateStart, candidateEnd, policy);
    return failure
      ? { kind: "disabled", reason: failure }
      : { kind: "enabled", range: asRange(candidateStart, candidateEnd) };
  };

  return { visible: { start: startVisible, end: endVisible }, canResize, adjust, step, hiddenEdge };
}

/**
 * A creation proposal from a rough start and end: the start stays at its whole minute and the end
 * becomes the nearest whole number of increments. Day increments keep the start's local time and may end on a
 * later date. An end on a skipped or repeated local time gives no proposal, since only the form can ask which
 * occurrence to use.
 */
function proposeTimelineRange(roughStart: string, roughEnd: string, policy: TimelinePolicy): TimelineRange | undefined {
  const rule = incrementRule(policy);
  const timezone = policy.schedulingTimezone;
  const wholeStart = toWholeMinute(Temporal.Instant.from(roughStart)).toString();
  const start = wholeStart;
  const snap = snapEndToIncrement(rule, start, roughEnd, timezone);
  return snap.kind === "instant" ? { start, end: snap.instant } : undefined;
}

/** `proposeTimelineRange` in a displayed day's elapsed-minute coordinates, which may extend past the day. */
export function proposeDayRange(
  range: { startMinute: number; endMinute: number },
  date: string,
  displayTimezone: string,
  policy: TimelinePolicy,
): { startMinute: number; endMinute: number } | undefined {
  const instant = (minute: number) => dayMinuteToZonedTime(date, displayTimezone, minute).toInstant().toString();
  const proposal = proposeTimelineRange(instant(range.startMinute), instant(range.endMinute), policy);
  if (!proposal) return undefined;
  return {
    startMinute: instantToDayMinute(proposal.start, date, displayTimezone),
    endMinute: instantToDayMinute(proposal.end, date, displayTimezone),
  };
}
