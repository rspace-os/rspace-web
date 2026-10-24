import { Temporal } from "@js-temporal/polyfill";
import type { Booking, BookingEventKind } from "@/modules/booking/domain/booking";
import {
  type OpeningHoursEntry,
  type OpeningSchedule,
  openingIntervals,
} from "@/modules/booking/domain/bookingOpeningHours";
import type { AbsoluteDisplayInterval } from "@/modules/booking/domain/bookingTime";

export type AvailabilityInterval = {
  kind: "booking" | "blockout";
  startsAt: Date;
  endsAt: Date;
};

export type AvailabilityState = "available" | "booking" | "blockout" | "overlap";

type AvailabilitySegment = {
  startsAt: number;
  endsAt: number;
  state: AvailabilityState;
};

/**
 * Bookings retain original bounds before buffer widening or display clipping.
 * Closed-hours sources describe gaps within the display interval.
 */
export type AvailabilitySource = {
  id: string;
  startsAt: Date;
  endsAt: Date;
  booking?: {
    id: number;
    kind: BookingEventKind;
    privacy: "full" | "busy";
    purpose: string | null;
    bookedBy: string | null;
    createdBy?: string | null;
  };
};

export type SourcedAvailabilityInterval = AvailabilityInterval & {
  /** Original event for labels and buffer-only conflicts; outer bounds represent occupied time. */
  source: AvailabilitySource;
};

type AvailabilitySlice<T extends AvailabilityInterval = AvailabilityInterval> = AvailabilitySegment & {
  intervals: readonly T[];
};

type CurrentDayAvailability = "available-now" | "free-later-today" | "unavailable-today";

type TimestampInterval = Omit<AvailabilitySegment, "state">;

export function timestamp(date: Date, name: string): number {
  const value = date.getTime();
  if (!Number.isFinite(value)) throw new RangeError(`${name} must be a valid Date`);
  return value;
}

function overlaps(start: number, end: number, otherStart: number, otherEnd: number): boolean {
  return start < otherEnd && end > otherStart;
}

function mergeIntervals(intervals: readonly TimestampInterval[]): TimestampInterval[] {
  const merged: TimestampInterval[] = [];
  for (const interval of intervals.toSorted(
    (left, right) => left.startsAt - right.startsAt || left.endsAt - right.endsAt,
  )) {
    const previous = merged.at(-1);
    if (previous && interval.startsAt <= previous.endsAt) previous.endsAt = Math.max(previous.endsAt, interval.endsAt);
    else merged.push({ ...interval });
  }
  return merged;
}

function buildTimeline<T extends AvailabilityInterval>(
  intervals: readonly T[],
  periodStart: Date,
  periodEnd: Date,
): { clipped: T[]; segments: AvailabilitySegment[] } {
  const start = timestamp(periodStart, "periodStart");
  const end = timestamp(periodEnd, "periodEnd");
  if (end < start) throw new RangeError("periodEnd must be after periodStart");
  if (end === start) return { clipped: [], segments: [] };
  const clipped = intervals.flatMap((interval) => {
    const startsAt = timestamp(interval.startsAt, "interval startsAt");
    const endsAt = timestamp(interval.endsAt, "interval endsAt");
    if (endsAt <= startsAt) throw new RangeError("interval endsAt must be after startsAt");
    if (endsAt <= start || startsAt >= end) return [];
    return [
      {
        ...interval,
        startsAt: new Date(Math.max(startsAt, start)),
        endsAt: new Date(Math.min(endsAt, end)),
      },
    ];
  });
  const timestampIntervals = clipped.map(({ kind, startsAt, endsAt }) => ({
    kind,
    startsAt: startsAt.getTime(),
    endsAt: endsAt.getTime(),
  }));
  const bookings = mergeIntervals(timestampIntervals.filter(({ kind }) => kind === "booking"));
  const blockouts = mergeIntervals(timestampIntervals.filter(({ kind }) => kind === "blockout"));
  const boundaries = [
    ...new Set([
      start,
      end,
      ...bookings.flatMap(({ startsAt, endsAt }) => [startsAt, endsAt]),
      ...blockouts.flatMap(({ startsAt, endsAt }) => [startsAt, endsAt]),
    ]),
  ].toSorted((left, right) => left - right);

  const segments: AvailabilitySegment[] = boundaries.slice(0, -1).map((startsAt, index) => {
    const endsAt = boundaries[index + 1];
    const booked = bookings.some((interval) => overlaps(interval.startsAt, interval.endsAt, startsAt, endsAt));
    const blocked = blockouts.some((interval) => overlaps(interval.startsAt, interval.endsAt, startsAt, endsAt));
    return {
      startsAt,
      endsAt,
      state: booked && blocked ? "overlap" : booked ? "booking" : blocked ? "blockout" : "available",
    };
  });
  return { clipped, segments };
}

export function buildAvailabilitySegments(
  intervals: readonly AvailabilityInterval[],
  periodStart: Date,
  periodEnd: Date,
): AvailabilitySegment[] {
  return buildTimeline(intervals, periodStart, periodEnd).segments;
}

export function buildAvailabilitySlices<T extends AvailabilityInterval>(
  intervals: readonly T[],
  periodStart: Date,
  periodEnd: Date,
): AvailabilitySlice<T>[] {
  const { clipped, segments } = buildTimeline(intervals, periodStart, periodEnd);
  return segments.map((segment) => ({
    ...segment,
    intervals:
      segment.state === "available"
        ? []
        : clipped.filter((interval) =>
            overlaps(interval.startsAt.getTime(), interval.endsAt.getTime(), segment.startsAt, segment.endsAt),
          ),
  }));
}

export function classifyCurrentDayAvailability(
  intervals: readonly AvailabilityInterval[],
  dayStart: Date,
  dayEnd: Date,
  now: Date,
): CurrentDayAvailability {
  const segments = buildAvailabilitySegments(intervals, dayStart, dayEnd);
  const current = timestamp(now, "now");
  if (current >= dayEnd.getTime()) return "unavailable-today";
  const currentIndex = segments.findIndex(({ startsAt, endsAt }) => startsAt <= current && current < endsAt);
  if (segments[currentIndex]?.state === "available") return "available-now";
  return segments
    .slice(currentIndex + 1)
    .some(({ startsAt, endsAt, state }) => state === "available" && endsAt > startsAt)
    ? "free-later-today"
    : "unavailable-today";
}

export type CalendarAvailabilityRow = OpeningSchedule & {
  globalId: string;
  timezone: string;
  /** With bufferAfterMinutes, defines one shared gap using their maximum; see sharedBufferGapMinutes. */
  bufferBeforeMinutes: number;
  /** Shares the gap defined by bufferBeforeMinutes, matching server scheduling checks. */
  bufferAfterMinutes: number;
  allowDoubleBooking: boolean;
};

export function calendarAvailabilityRow(row: {
  globalId: string;
  timezone?: string;
  openingHours?: readonly OpeningHoursEntry[];
  bufferBeforeMinutes?: number;
  bufferAfterMinutes?: number;
  allowDoubleBooking?: boolean;
}): CalendarAvailabilityRow | undefined {
  if (
    row.timezone === undefined ||
    row.openingHours === undefined ||
    row.bufferBeforeMinutes === undefined ||
    row.bufferAfterMinutes === undefined ||
    row.allowDoubleBooking === undefined
  ) {
    return undefined;
  }
  return {
    globalId: row.globalId,
    timezone: row.timezone,
    openingHours: row.openingHours,
    bufferBeforeMinutes: row.bufferBeforeMinutes,
    bufferAfterMinutes: row.bufferAfterMinutes,
    allowDoubleBooking: row.allowDoubleBooking,
  };
}

function availabilityEnvelope(rows: readonly CalendarAvailabilityRow[], interval: AbsoluteDisplayInterval) {
  const before = Math.max(...rows.map((row) => row.bufferBeforeMinutes));
  const after = Math.max(...rows.map((row) => row.bufferAfterMinutes));
  return {
    start: Temporal.Instant.from(interval.start).subtract({ minutes: after }).toString(),
    end: Temporal.Instant.from(interval.end).add({ minutes: before }).toString(),
  };
}

function clipInterval<T extends AvailabilityInterval>(candidate: T, interval: AbsoluteDisplayInterval): T | undefined {
  const start = Math.max(candidate.startsAt.getTime(), Date.parse(interval.start));
  const end = Math.min(candidate.endsAt.getTime(), Date.parse(interval.end));
  return end > start ? { ...candidate, startsAt: new Date(start), endsAt: new Date(end) } : undefined;
}

function sourcedInterval(
  kind: AvailabilityInterval["kind"],
  id: string,
  startsAt: string | number,
  endsAt: string | number,
  booking?: Booking,
): SourcedAvailabilityInterval {
  const sourceStartsAt = new Date(startsAt);
  const sourceEndsAt = new Date(endsAt);
  return {
    kind,
    startsAt: sourceStartsAt,
    endsAt: sourceEndsAt,
    source: {
      id,
      startsAt: sourceStartsAt,
      endsAt: sourceEndsAt,
      ...(booking
        ? {
            booking: {
              id: booking.id,
              kind: booking.kind,
              privacy: booking.privacy,
              purpose: booking.purpose,
              bookedBy: booking.bookedBy,
              createdBy: booking.createdBy,
            },
          }
        : {}),
    },
  };
}

function closedInterval(
  row: CalendarAvailabilityRow,
  startsAt: string,
  endsAt: string,
  interval: AbsoluteDisplayInterval,
): SourcedAvailabilityInterval | undefined {
  const candidate = sourcedInterval(
    "blockout",
    `opening-hours:${row.globalId}:${new Date(startsAt).toISOString()}:${new Date(endsAt).toISOString()}`,
    startsAt,
    endsAt,
  );
  return clipInterval(candidate, interval);
}

function closedIntervals(
  row: CalendarAvailabilityRow,
  interval: AbsoluteDisplayInterval,
): SourcedAvailabilityInterval[] {
  const closed: SourcedAvailabilityInterval[] = [];
  let cursor = interval.start;
  for (const opening of [
    ...openingIntervals(row, row.timezone, interval),
    { start: interval.end, end: interval.end },
  ]) {
    const gap = closedInterval(row, cursor, opening.start, interval);
    if (gap) closed.push(gap);
    cursor = opening.end;
  }
  return closed;
}

function bookingInterval(
  booking: Booking,
  row: CalendarAvailabilityRow,
  interval: AbsoluteDisplayInterval,
): SourcedAvailabilityInterval | undefined {
  const candidate = sourcedInterval(
    booking.kind === "MAINTENANCE" ? "blockout" : "booking",
    `booking:${booking.id}`,
    booking.start,
    booking.end,
    booking,
  );
  return clipInterval(
    {
      ...candidate,
      startsAt: new Date(candidate.startsAt.getTime() - row.bufferBeforeMinutes * 60_000),
      endsAt: new Date(candidate.endsAt.getTime() + row.bufferAfterMinutes * 60_000),
    },
    interval,
  );
}

export { availabilityEnvelope, bookingInterval, closedIntervals };
