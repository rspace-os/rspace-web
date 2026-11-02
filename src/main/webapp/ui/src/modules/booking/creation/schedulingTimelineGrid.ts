import { Temporal } from "@js-temporal/polyfill";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";

export type SchedulingTimelineGridContext = {
  date: string;
  displayTimezone: string;
  schedulingTimezone: string;
  slotGranularityMinutes: number;
};

const MINUTE_NS = 60_000_000_000n;
const CANDIDATE_CACHE_LIMIT = 8;
const candidateCache = new Map<string, readonly Temporal.Instant[]>();

export function schedulingTimelineCandidates(context: SchedulingTimelineGridContext): readonly Temporal.Instant[] {
  const { date, displayTimezone, schedulingTimezone, slotGranularityMinutes } = context;
  if (!Number.isInteger(slotGranularityMinutes) || slotGranularityMinutes <= 0) return [];

  const key = `${date}|${displayTimezone}|${schedulingTimezone}|${slotGranularityMinutes}`;
  const cached = candidateCache.get(key);
  if (cached) return cached;

  const bounds = zonedDayBounds(date, displayTimezone);
  const start = Temporal.Instant.from(bounds.start).epochNanoseconds;
  const end = Temporal.Instant.from(bounds.end).epochNanoseconds;
  const remainder = ((start % MINUTE_NS) + MINUTE_NS) % MINUTE_NS;
  let candidateNs = start + (remainder === 0n ? 0n : MINUTE_NS - remainder);
  const candidates: Temporal.Instant[] = [];

  for (; candidateNs <= end; candidateNs += MINUTE_NS) {
    const instant = Temporal.Instant.fromEpochNanoseconds(candidateNs);
    const scheduled = instant.toZonedDateTimeISO(schedulingTimezone);
    if (
      (scheduled.hour * 60 + scheduled.minute) % slotGranularityMinutes === 0 &&
      scheduled.second === 0 &&
      scheduled.millisecond === 0 &&
      scheduled.microsecond === 0 &&
      scheduled.nanosecond === 0
    ) {
      candidates.push(instant);
    }
  }

  if (candidateCache.size === CANDIDATE_CACHE_LIMIT) {
    const oldest = candidateCache.keys().next().value;
    if (oldest !== undefined) candidateCache.delete(oldest);
  }
  candidateCache.set(key, candidates);
  return candidates;
}

/** Snap a timeline pointer target to the nearest slot in the item's scheduling timezone. */
export function closestSchedulingTimelineSlot(
  targetInstant: string,
  context: SchedulingTimelineGridContext,
): string | undefined {
  try {
    const target = Temporal.Instant.from(targetInstant);
    const candidates = schedulingTimelineCandidates(context);
    let low = 0;
    let high = candidates.length;
    while (low < high) {
      const middle = Math.floor((low + high) / 2);
      if (Temporal.Instant.compare(candidates[middle], target) < 0) low = middle + 1;
      else high = middle;
    }

    const earlier = candidates[low - 1];
    const later = candidates[low];
    if (!earlier) return later?.toString();
    if (!later) return earlier.toString();
    return target.epochNanoseconds - earlier.epochNanoseconds <= later.epochNanoseconds - target.epochNanoseconds
      ? earlier.toString()
      : later.toString();
  } catch {
    return undefined;
  }
}
