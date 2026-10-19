// PROTOTYPE ONLY (Opus 5.5). Throwaway shared code for the vertical day timeline stories; do not import from production.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import { Temporal } from "@js-temporal/polyfill";
import type { DayTimelineEvent } from "@/modules/booking/components/DayTimeline";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { openingIntervals } from "@/modules/booking/domain/bookingOpeningHours";
import { type BookingWindowDraft, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import type { FixtureEvent } from "./verticalDayTimelineOpusFixtures";

const MINUTE_MS = 60_000;
/** Epoch minutes. Absolute, so repeated wall-clock hours never collide. */
export type EpochRange = { start: number; end: number };
export type AdjustMode = "move" | "start" | "end";
export type SlotGrid = { dayStart: number; dayEnd: number; slots: number[]; valid: Set<number> };

export const instantMinute = (instant: string) => Temporal.Instant.from(instant).epochMilliseconds / MINUTE_MS;
export const minuteInstant = (minute: number) =>
  Temporal.Instant.fromEpochMilliseconds(Math.round(minute * MINUTE_MS)).toString();
export const sameRange = (left: EpochRange | undefined, right: EpochRange | undefined) =>
  left?.start === right?.start && left?.end === right?.end;

/**
 * Every instant in the displayed zoned day (end inclusive) that sits on a slot in the
 * scheduling timezone's wall clock, the same rule validateBookingWindow applies.
 * ponytail: assumes whole-minute UTC offsets; historic LMT offsets would need second precision.
 */
export function slotGrid(
  date: string,
  displayTimezone: string,
  schedulingTimezone: string,
  granularity: number,
): SlotGrid {
  const bounds = zonedDayBounds(date, displayTimezone);
  const dayStart = instantMinute(bounds.start);
  const dayEnd = instantMinute(bounds.end);
  const slots: number[] = [];
  let cursor = dayStart;
  let zoned = Temporal.Instant.from(bounds.start).toZonedDateTimeISO(schedulingTimezone);
  while (cursor <= dayEnd) {
    const offset = zoned.offsetNanoseconds / (MINUTE_MS * 1_000_000);
    const transition = zoned.getTimeZoneTransition("next");
    const segmentEnd = transition ? Math.min(dayEnd + 1, transition.epochMilliseconds / MINUTE_MS) : dayEnd + 1;
    for (let minute = Math.ceil(cursor); minute < segmentEnd; minute++) {
      const wallMinute = (((minute + offset) % 1440) + 1440) % 1440;
      if (wallMinute % granularity === 0) slots.push(minute);
    }
    if (!transition || segmentEnd > dayEnd) break;
    cursor = segmentEnd;
    zoned = transition;
  }
  return { dayStart, dayEnd, slots, valid: new Set(slots) };
}

/**
 * One calculation for keyboard and pointer. Move keeps the duration and only accepts starts
 * whose end is also a valid slot; resize never crosses the other endpoint. No valid
 * candidate leaves the range unchanged. Pointer ties go to the earlier instant.
 */
export function adjustRange(
  range: EpochRange,
  mode: AdjustMode,
  grid: SlotGrid,
  request: { kind: "step"; direction: -1 | 1 } | { kind: "pointer"; target: number },
): EpochRange {
  const duration = range.end - range.start;
  const accepts = (slot: number) =>
    mode === "start" ? slot < range.end : mode === "end" ? slot > range.start : grid.valid.has(slot + duration);
  const current = mode === "end" ? range.end : range.start;
  let chosen: number | undefined;
  if (request.kind === "step" && request.direction < 0) {
    for (const slot of grid.slots) {
      if (slot >= current) break;
      if (accepts(slot)) chosen = slot;
    }
  } else if (request.kind === "step") {
    chosen = grid.slots.find((slot) => slot > current && accepts(slot));
  } else {
    let best = Number.POSITIVE_INFINITY;
    for (const slot of grid.slots) {
      const distance = Math.abs(slot - request.target);
      if (accepts(slot) && distance < best) {
        best = distance;
        chosen = slot;
      }
    }
  }
  if (chosen === undefined) return range;
  if (mode === "start") return { start: chosen, end: range.end };
  if (mode === "end") return { start: range.start, end: chosen };
  return { start: chosen, end: chosen + duration };
}

export type Placed<T> = { item: T; start: number; end: number; lane: number; lanes: number; cluster: number };

/** Deterministic half-open lanes: sort by start, end, id; first free lane inside each overlap group. */
export function placeInLanes<T extends { id: string }>(entries: readonly { item: T; start: number; end: number }[]) {
  const sorted = [...entries].sort((a, b) => a.start - b.start || a.end - b.end || a.item.id.localeCompare(b.item.id));
  const placed: Placed<T>[] = [];
  let group: Placed<T>[] = [];
  let laneEnds: number[] = [];
  let groupEnd = Number.NEGATIVE_INFINITY;
  let cluster = 0;
  const flush = () => {
    for (const entry of group) entry.lanes = laneEnds.length;
    group = [];
    laneEnds = [];
    cluster++;
  };
  for (const entry of sorted) {
    if (group.length > 0 && entry.start >= groupEnd) flush();
    let lane = laneEnds.findIndex((end) => end <= entry.start);
    if (lane < 0) {
      lane = laneEnds.length;
      laneEnds.push(entry.end);
    } else laneEnds[lane] = entry.end;
    const next = { ...entry, lane, lanes: 1, cluster };
    group.push(next);
    placed.push(next);
    groupEnd = Math.max(groupEnd, entry.end);
  }
  flush();
  return placed;
}

export function openingRanges(grid: SlotGrid, item: BookableItemOption): EpochRange[] {
  try {
    return openingIntervals(item, item.timezone, {
      start: Temporal.Instant.fromEpochMilliseconds(grid.dayStart * MINUTE_MS).toString(),
      end: Temporal.Instant.fromEpochMilliseconds(grid.dayEnd * MINUTE_MS).toString(),
    }).map((interval) => ({ start: instantMinute(interval.start), end: instantMinute(interval.end) }));
  } catch {
    // An invalid opening configuration leaves the whole day shaded as closed.
    return [];
  }
}

export function sameDraft(left: BookingWindowDraft, right: BookingWindowDraft) {
  return (
    left.startDate === right.startDate &&
    left.startTime === right.startTime &&
    left.startOccurrence === right.startOccurrence &&
    left.endDate === right.endDate &&
    left.endTime === right.endTime &&
    left.endOccurrence === right.endOccurrence
  );
}

export function formatInterval(start: string, end: string, timezone: string, locale: string) {
  return new Intl.DateTimeFormat(locale, {
    timeZone: timezone,
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    timeZoneName: "shortOffset",
  }).formatRange(new Date(start), new Date(end));
}

export function toDayEvent(event: FixtureEvent, dayStart: number, item: BookableItemOption): DayTimelineEvent {
  const base = {
    id: String(event.id),
    startMinute: instantMinute(event.start) - dayStart,
    endMinute: instantMinute(event.end) - dayStart,
    startInstant: event.start,
    endInstant: event.end,
    instrumentTimeZone: item.timezone,
  };
  const reference = { name: item.name, globalId: item.globalId };
  // Busy-only rows never receive private fields; the fixture type cannot carry them.
  if (event.privacy === "busy") return { ...base, kind: "booking", privacy: "busy" };
  if (event.kind === "MAINTENANCE") {
    return {
      ...base,
      kind: "blockout",
      title: "Maintenance",
      item: reference,
      createdBy: event.bookedBy,
      notes: event.purpose,
    };
  }
  return {
    ...base,
    kind: "booking",
    privacy: "full",
    title: item.name,
    bookedBy: event.bookedBy,
    item: reference,
    canEdit: false,
    notes: event.purpose,
  };
}
