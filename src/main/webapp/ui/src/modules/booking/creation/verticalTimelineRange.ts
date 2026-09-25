import { Temporal } from "@js-temporal/polyfill";
import {
  type BookingWindowDraft,
  isBookingInstantAlignedToGranularity,
  wallClockDraftFromInstants,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";

export type VerticalTimelineRange = { start: string; end: string };
export type VerticalTimelineRangeEdge = "move" | "start" | "end";
export type VerticalTimelineRangeContext = {
  date: string;
  displayTimezone: string;
  schedulingTimezone: string;
  slotGranularityMinutes: number;
};

type Candidate = Temporal.Instant;
const MINUTE_NS = 60_000_000_000n;
const CANDIDATE_CACHE_LIMIT = 8;
const candidateCache = new Map<string, readonly Candidate[]>();

function cachedCandidates(key: string, create: () => Candidate[]): readonly Candidate[] {
  const cached = candidateCache.get(key);
  if (cached) return cached;

  const candidates = create();
  if (candidateCache.size === CANDIDATE_CACHE_LIMIT) {
    const oldest = candidateCache.keys().next().value;
    if (oldest !== undefined) candidateCache.delete(oldest);
  }
  candidateCache.set(key, candidates);
  return candidates;
}

function contextKey(context: VerticalTimelineRangeContext): string {
  return `${context.date}|${context.displayTimezone}|${context.schedulingTimezone}|${context.slotGranularityMinutes}`;
}

function candidatesForDay(context: VerticalTimelineRangeContext): readonly Candidate[] {
  if (!Number.isInteger(context.slotGranularityMinutes) || context.slotGranularityMinutes <= 0) return [];

  return cachedCandidates(`${contextKey(context)}|slots`, () => {
    const bounds = zonedDayBounds(context.date, context.displayTimezone);
    const dayStart = Temporal.Instant.from(bounds.start);
    const dayEnd = Temporal.Instant.from(bounds.end);
    const endNs = dayEnd.epochNanoseconds;
    const remainder = ((dayStart.epochNanoseconds % MINUTE_NS) + MINUTE_NS) % MINUTE_NS;
    let candidateNs = dayStart.epochNanoseconds + (remainder === 0n ? 0n : MINUTE_NS - remainder);
    const candidates: Candidate[] = [];

    for (; candidateNs <= endNs; candidateNs += MINUTE_NS) {
      const instant = Temporal.Instant.fromEpochNanoseconds(candidateNs);
      if (
        isBookingInstantAlignedToGranularity(
          instant.toString(),
          context.schedulingTimezone,
          context.slotGranularityMinutes,
        )
      ) {
        candidates.push(instant);
      }
    }

    return candidates;
  });
}

function resolvedRange(range: VerticalTimelineRange): { start: Temporal.Instant; end: Temporal.Instant } | undefined {
  try {
    const start = Temporal.Instant.from(range.start);
    const end = Temporal.Instant.from(range.end);
    return Temporal.Instant.compare(end, start) > 0 ? { start, end } : undefined;
  } catch {
    return undefined;
  }
}

/** Cross-day and legacy off-grid ranges stay visible but cannot be adjusted in the day canvas. */
export function canAdjustVerticalTimelineRange(
  range: VerticalTimelineRange,
  context: VerticalTimelineRangeContext,
): boolean {
  const resolved = resolvedRange(range);
  if (!resolved) return false;

  try {
    const bounds = zonedDayBounds(context.date, context.displayTimezone);
    return (
      Temporal.Instant.compare(resolved.start, bounds.start) >= 0 &&
      Temporal.Instant.compare(resolved.end, bounds.end) <= 0 &&
      isBookingInstantAlignedToGranularity(range.start, context.schedulingTimezone, context.slotGranularityMinutes) &&
      isBookingInstantAlignedToGranularity(range.end, context.schedulingTimezone, context.slotGranularityMinutes)
    );
  } catch {
    return false;
  }
}

function candidatesForMove(
  range: { start: Temporal.Instant; end: Temporal.Instant },
  context: VerticalTimelineRangeContext,
): readonly Candidate[] {
  const originalDuration = range.end.epochNanoseconds - range.start.epochNanoseconds;

  return cachedCandidates(`${contextKey(context)}|move|${originalDuration}`, () => {
    const dayEnd = Temporal.Instant.from(zonedDayBounds(context.date, context.displayTimezone).end);
    return candidatesForDay(context).filter((instant) => {
      if (Temporal.Instant.compare(instant, dayEnd) >= 0) return false;
      const end = Temporal.Instant.fromEpochNanoseconds(instant.epochNanoseconds + originalDuration);
      return (
        Temporal.Instant.compare(end, dayEnd) <= 0 &&
        isBookingInstantAlignedToGranularity(end.toString(), context.schedulingTimezone, context.slotGranularityMinutes)
      );
    });
  });
}

function lowerBound(candidates: readonly Candidate[], target: Temporal.Instant): number {
  let low = 0;
  let high = candidates.length;
  while (low < high) {
    const middle = Math.floor((low + high) / 2);
    if (Temporal.Instant.compare(candidates[middle], target) < 0) low = middle + 1;
    else high = middle;
  }
  return low;
}

function upperBound(candidates: readonly Candidate[], target: Temporal.Instant): number {
  let low = 0;
  let high = candidates.length;
  while (low < high) {
    const middle = Math.floor((low + high) / 2);
    if (Temporal.Instant.compare(candidates[middle], target) <= 0) low = middle + 1;
    else high = middle;
  }
  return low;
}

function candidatesForRange(
  range: { start: Temporal.Instant; end: Temporal.Instant },
  edge: VerticalTimelineRangeEdge,
  context: VerticalTimelineRangeContext,
): { candidates: readonly Candidate[]; first: number; last: number } {
  if (edge === "move") {
    const candidates = candidatesForMove(range, context);
    return { candidates, first: 0, last: candidates.length };
  }

  const candidates = candidatesForDay(context);
  return edge === "start"
    ? { candidates, first: 0, last: lowerBound(candidates, range.end) }
    : { candidates, first: upperBound(candidates, range.start), last: candidates.length };
}

function makeRange(
  original: { start: Temporal.Instant; end: Temporal.Instant },
  edge: VerticalTimelineRangeEdge,
  candidate: Candidate,
): VerticalTimelineRange | undefined {
  let start = original.start;
  let end = original.end;
  if (edge === "move") {
    start = candidate;
    end = Temporal.Instant.fromEpochNanoseconds(
      candidate.epochNanoseconds + original.end.epochNanoseconds - original.start.epochNanoseconds,
    );
  } else if (edge === "start") {
    start = candidate;
  } else {
    end = candidate;
  }
  if (Temporal.Instant.compare(start, original.start) === 0 && Temporal.Instant.compare(end, original.end) === 0) {
    return undefined;
  }
  return { start: start.toString(), end: end.toString() };
}

function closestCandidate(
  candidates: readonly Candidate[],
  target: Temporal.Instant,
  first: number,
  last: number,
): Candidate | undefined {
  if (first >= last) return undefined;
  let low = first;
  let high = last;
  const targetNs = target.epochNanoseconds;

  while (low < high) {
    const middle = Math.floor((low + high) / 2);
    const candidateNs = candidates[middle].epochNanoseconds;
    if (candidateNs < targetNs) low = middle + 1;
    else high = middle;
  }

  const later = low < last ? candidates[low] : undefined;
  const earlier = low > first ? candidates[low - 1] : undefined;
  if (!earlier) return later;
  if (!later) return earlier;
  const earlierDistance = targetNs - earlier.epochNanoseconds;
  const laterDistance = later.epochNanoseconds - targetNs;
  return earlierDistance <= laterDistance ? earlier : later;
}

/** Snap pointer input to the closest valid scheduling-zone slot; ties go to the earlier instant. */
export function adjustVerticalTimelineRange(
  range: VerticalTimelineRange,
  edge: VerticalTimelineRangeEdge,
  targetInstant: string,
  context: VerticalTimelineRangeContext,
): VerticalTimelineRange | undefined {
  if (!canAdjustVerticalTimelineRange(range, context)) return undefined;

  const original = resolvedRange(range);
  if (!original) return undefined;

  let target: Temporal.Instant;
  try {
    target = Temporal.Instant.from(targetInstant);
  } catch {
    return undefined;
  }

  const current = edge === "end" ? original.end : original.start;
  const eligible = candidatesForRange(original, edge, context);
  const selected = closestCandidate(eligible.candidates, target, eligible.first, eligible.last);

  if (!selected || Temporal.Instant.compare(selected, current) === 0) return undefined;
  return makeRange(original, edge, selected);
}

/** Move to the previous or next valid slot for keyboard adjustment. */
export function stepVerticalTimelineRange(
  range: VerticalTimelineRange,
  edge: VerticalTimelineRangeEdge,
  direction: "previous" | "next",
  context: VerticalTimelineRangeContext,
): VerticalTimelineRange | undefined {
  if (!canAdjustVerticalTimelineRange(range, context)) return undefined;

  const original = resolvedRange(range);
  if (!original) return undefined;
  const current = edge === "end" ? original.end : original.start;
  const eligible = candidatesForRange(original, edge, context);
  const index =
    direction === "next" ? upperBound(eligible.candidates, current) : lowerBound(eligible.candidates, current) - 1;
  const selected = index >= eligible.first && index < eligible.last ? eligible.candidates[index] : undefined;

  return selected ? makeRange(original, edge, selected) : undefined;
}

export function verticalTimelineRangeToDraft(
  range: VerticalTimelineRange,
  displayTimezone: string,
): BookingWindowDraft {
  return wallClockDraftFromInstants(range.start, range.end, displayTimezone);
}
