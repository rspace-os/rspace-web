import { Temporal } from "@js-temporal/polyfill";
import type { DayTimelineRange } from "@/modules/booking/components/DayTimelineEvent";
import type { Booking } from "@/modules/booking/domain/booking";
import {
  type OpeningHoursEntry,
  type OpeningSchedule,
  openingIntervals,
} from "@/modules/booking/domain/bookingOpeningHours";
import {
  type AbsoluteDisplayInterval,
  displayInterval,
  instantToDayMinute,
} from "@/modules/booking/domain/bookingTime";
import type { BookingEventKind } from "@/modules/common/api/v2/generated/models/bookingEventKind";
import { instantToWire } from "@/modules/common/time/instant";

export type AvailabilityInterval = {
  kind: "booking" | "blockout";
  startsAt: Temporal.Instant;
  endsAt: Temporal.Instant;
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
  startsAt: Temporal.Instant;
  endsAt: Temporal.Instant;
  booking?: {
    id: number;
    kind: BookingEventKind;
    privacy: "full" | "busy";
    canViewConfiguration?: boolean;
    purpose: string | null;
    bookedBy: string | null;
    createdBy?: string | null;
    instrumentTimeZone?: string | null;
  };
};

export type SourcedAvailabilityInterval = AvailabilityInterval & {
  /** Original event for labels and buffer-only conflicts; outer bounds represent occupied time. */
  source: AvailabilitySource;
};

export type BookingConflict = NonNullable<AvailabilitySource["booking"]> & {
  start: string;
  end: string;
  timezone: string;
  instrumentTimeZone?: string | null;
  /** The draft only reaches the buffer around this event, not the event itself. */
  bufferOnly?: boolean;
};

export function bookingConflicts(
  intervals: readonly SourcedAvailabilityInterval[],
  timezone: string,
  excludedBookingId?: number,
): BookingConflict[] {
  const conflicts = new Map<number, BookingConflict>();
  for (const interval of intervals) {
    const booking = interval.source.booking;
    if (!booking || booking.id === excludedBookingId) continue;
    // Intervals are buffer-widened and clipped to the draft, so missing the raw event means only the buffer overlaps.
    const bufferOnly =
      Temporal.Instant.compare(interval.endsAt, interval.source.startsAt) <= 0 ||
      Temporal.Instant.compare(interval.startsAt, interval.source.endsAt) >= 0;
    const existing = conflicts.get(booking.id);
    if (existing) {
      if (!bufferOnly) existing.bufferOnly = false;
      continue;
    }
    conflicts.set(booking.id, {
      ...booking,
      start: instantToWire(interval.source.startsAt),
      end: instantToWire(interval.source.endsAt),
      timezone,
      instrumentTimeZone: booking.instrumentTimeZone ?? null,
      bufferOnly,
    });
  }
  return [...conflicts.values()];
}

type AvailabilitySlice<T extends AvailabilityInterval = AvailabilityInterval> = AvailabilitySegment & {
  intervals: readonly T[];
};

type CurrentDayAvailability = "available-now" | "free-later-today" | "unavailable-today";

type TimestampInterval = Omit<AvailabilitySegment, "state">;

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
  periodStart: Temporal.Instant,
  periodEnd: Temporal.Instant,
): { clipped: T[]; segments: AvailabilitySegment[] } {
  const start = periodStart.epochMilliseconds;
  const end = periodEnd.epochMilliseconds;
  if (end < start) throw new RangeError("periodEnd must be after periodStart");
  if (end === start) return { clipped: [], segments: [] };
  const clipped = intervals.flatMap((interval) => {
    const startsAt = interval.startsAt.epochMilliseconds;
    const endsAt = interval.endsAt.epochMilliseconds;
    if (endsAt <= startsAt) throw new RangeError("interval endsAt must be after startsAt");
    if (endsAt <= start || startsAt >= end) return [];
    return [
      {
        ...interval,
        startsAt: startsAt < start ? periodStart : interval.startsAt,
        endsAt: endsAt > end ? periodEnd : interval.endsAt,
      },
    ];
  });
  const timestampIntervals = clipped.map(({ kind, startsAt, endsAt }) => ({
    kind,
    startsAt: startsAt.epochMilliseconds,
    endsAt: endsAt.epochMilliseconds,
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
  periodStart: Temporal.Instant,
  periodEnd: Temporal.Instant,
): AvailabilitySegment[] {
  return buildTimeline(intervals, periodStart, periodEnd).segments;
}

export function buildAvailabilitySlices<T extends AvailabilityInterval>(
  intervals: readonly T[],
  periodStart: Temporal.Instant,
  periodEnd: Temporal.Instant,
): AvailabilitySlice<T>[] {
  const { clipped, segments } = buildTimeline(intervals, periodStart, periodEnd);
  return segments.map((segment) => ({
    ...segment,
    intervals:
      segment.state === "available"
        ? []
        : clipped.filter((interval) =>
            overlaps(
              interval.startsAt.epochMilliseconds,
              interval.endsAt.epochMilliseconds,
              segment.startsAt,
              segment.endsAt,
            ),
          ),
  }));
}

export function classifyCurrentDayAvailability(
  intervals: readonly AvailabilityInterval[],
  dayStart: Temporal.Instant,
  dayEnd: Temporal.Instant,
  now: Temporal.Instant,
): CurrentDayAvailability {
  const segments = buildAvailabilitySegments(intervals, dayStart, dayEnd);
  const current = now.epochMilliseconds;
  if (current >= dayEnd.epochMilliseconds) return "unavailable-today";
  const currentIndex = segments.findIndex(({ startsAt, endsAt }) => startsAt <= current && current < endsAt);
  if (segments[currentIndex]?.state === "available") return "available-now";
  return segments
    .slice(currentIndex + 1)
    .some(({ startsAt, endsAt, state }) => state === "available" && endsAt > startsAt)
    ? "free-later-today"
    : "unavailable-today";
}

export type CalendarAvailabilityRow = OpeningSchedule & {
  eventKind?: BookingEventKind;
  globalId: string;
  timezone: string;
  /** Idle minutes required before and after a booking. */
  bufferBeforeMinutes: number;
  bufferAfterMinutes: number;
  allowDoubleBooking: boolean;
};

export function calendarAvailabilityRow(row: {
  eventKind?: BookingEventKind;
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
    eventKind: row.eventKind,
    globalId: row.globalId,
    timezone: row.timezone,
    openingHours: row.openingHours,
    bufferBeforeMinutes: row.bufferBeforeMinutes,
    bufferAfterMinutes: row.bufferAfterMinutes,
    allowDoubleBooking: row.allowDoubleBooking,
  };
}

function sharedBufferGapMinutes(row: Pick<CalendarAvailabilityRow, "bufferBeforeMinutes" | "bufferAfterMinutes">) {
  return row.bufferBeforeMinutes + row.bufferAfterMinutes;
}

function availabilityEnvelope(rows: readonly CalendarAvailabilityRow[], interval: AbsoluteDisplayInterval) {
  const sharedGapMinutes = Math.max(...rows.map(sharedBufferGapMinutes));
  return {
    start: Temporal.Instant.from(interval.start).subtract({ minutes: sharedGapMinutes }).toString(),
    end: Temporal.Instant.from(interval.end).add({ minutes: sharedGapMinutes }).toString(),
  };
}

function clipInterval<T extends AvailabilityInterval>(candidate: T, interval: AbsoluteDisplayInterval): T | undefined {
  const intervalStart = Temporal.Instant.from(interval.start);
  const intervalEnd = Temporal.Instant.from(interval.end);
  const startsAt = Temporal.Instant.compare(candidate.startsAt, intervalStart) < 0 ? intervalStart : candidate.startsAt;
  const endsAt = Temporal.Instant.compare(candidate.endsAt, intervalEnd) > 0 ? intervalEnd : candidate.endsAt;
  return Temporal.Instant.compare(endsAt, startsAt) > 0 ? { ...candidate, startsAt, endsAt } : undefined;
}

function sourcedInterval(
  kind: AvailabilityInterval["kind"],
  id: string,
  startsAt: string,
  endsAt: string,
  booking?: Booking,
): SourcedAvailabilityInterval {
  const sourceStartsAt = Temporal.Instant.from(startsAt);
  const sourceEndsAt = Temporal.Instant.from(endsAt);
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
              canViewConfiguration: booking.canViewConfiguration,
              purpose: booking.purpose,
              bookedBy: booking.bookedBy,
              createdBy: booking.createdBy,
              instrumentTimeZone: booking.timezone,
            },
          }
        : {}),
    },
  };
}

type ClosureSchedule = OpeningSchedule & { globalId: string; timezone: string };

function closedInterval(
  row: ClosureSchedule,
  startsAt: string,
  endsAt: string,
  interval: AbsoluteDisplayInterval,
): SourcedAvailabilityInterval | undefined {
  const candidate = sourcedInterval(
    "blockout",
    `opening-hours:${row.globalId}:${instantToWire(Temporal.Instant.from(startsAt))}:${instantToWire(Temporal.Instant.from(endsAt))}`,
    startsAt,
    endsAt,
  );
  return clipInterval(candidate, interval);
}

function closedIntervals(row: ClosureSchedule, interval: AbsoluteDisplayInterval): SourcedAvailabilityInterval[] {
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

/** Closed periods on the displayed `date`, in timeline elapsed-minute coordinates for `timezone`. */
export function closedDayRanges(row: ClosureSchedule, date: string, timezone: string): DayTimelineRange[] {
  const interval = displayInterval(date, timezone, "00:00", "24:00");
  if (interval.elapsedMinutes === 0) return [];
  return closedIntervals(row, interval).map((closed) => ({
    startMinute: instantToDayMinute(instantToWire(closed.startsAt), date, timezone),
    endMinute: instantToDayMinute(instantToWire(closed.endsAt), date, timezone),
  }));
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
  const maintenanceDraft = row.eventKind === "MAINTENANCE";
  const existingMaintenance = booking.kind === "MAINTENANCE";
  const before = existingMaintenance
    ? maintenanceDraft
      ? 0
      : row.bufferAfterMinutes
    : maintenanceDraft
      ? row.bufferBeforeMinutes
      : sharedBufferGapMinutes(row);
  const after = existingMaintenance
    ? maintenanceDraft
      ? 0
      : row.bufferBeforeMinutes
    : maintenanceDraft
      ? row.bufferAfterMinutes
      : sharedBufferGapMinutes(row);
  return clipInterval(
    {
      ...candidate,
      startsAt: candidate.startsAt.subtract({ minutes: before }),
      endsAt: candidate.endsAt.add({ minutes: after }),
    },
    interval,
  );
}

/** Busy and closed intervals for one row, using bookings fetched for the shared target envelope. */
export function rowAvailabilityIntervals(
  row: CalendarAvailabilityRow,
  interval: AbsoluteDisplayInterval,
  bookings: readonly Booking[],
): SourcedAvailabilityInterval[] {
  const intervals = closedIntervals(row, interval);
  for (const booking of bookings) {
    if (booking.state !== "CONFIRMED" || booking.target?.globalId !== row.globalId) continue;
    if (row.allowDoubleBooking && row.eventKind !== "MAINTENANCE" && booking.kind === "BOOKING") continue;
    const clipped = bookingInterval(booking, row, interval);
    if (clipped) intervals.push(clipped);
  }
  return intervals;
}

export { availabilityEnvelope };
