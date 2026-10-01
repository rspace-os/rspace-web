import { Temporal } from "@js-temporal/polyfill";
import { useQuery } from "@tanstack/react-query";
import type { DayTimelineRange } from "@/modules/booking/components/DayTimelineEvent";
import type { AvailabilityInterval, SourcedAvailabilityInterval } from "@/modules/booking/domain/availability";
import type { Booking } from "@/modules/booking/domain/booking";
import {
  type OpeningException,
  type OpeningSchedule,
  openingIntervals,
} from "@/modules/booking/domain/bookingOpeningHours";
import {
  type AbsoluteDisplayInterval,
  displayInterval,
  instantToDayMinute,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import { fetchDayBookings } from "@/modules/booking/domain/fetchDayBookings";
import { viewTransitionQueryMeta } from "@/modules/common/queries/viewTransition";

export type CalendarAvailabilityRow = OpeningSchedule & {
  globalId: string;
  timezone: string;
  bufferBeforeMinutes: number;
  bufferAfterMinutes: number;
  allowDoubleBooking: boolean;
};

export type DatedCalendarAvailabilityRow = CalendarAvailabilityRow & { date: string };

export function calendarAvailabilityRow(row: {
  globalId: string;
  timezone?: string;
  openingStart?: string;
  openingEnd?: string;
  openDays?: readonly number[];
  openingExceptions?: readonly OpeningException[];
  bufferBeforeMinutes?: number;
  bufferAfterMinutes?: number;
  allowDoubleBooking?: boolean;
}): CalendarAvailabilityRow | undefined {
  if (
    row.timezone === undefined ||
    row.openingStart === undefined ||
    row.openingEnd === undefined ||
    row.openDays === undefined ||
    row.openingExceptions === undefined ||
    row.bufferBeforeMinutes === undefined ||
    row.bufferAfterMinutes === undefined ||
    row.allowDoubleBooking === undefined
  ) {
    return undefined;
  }
  return {
    globalId: row.globalId,
    timezone: row.timezone,
    openingStart: row.openingStart,
    openingEnd: row.openingEnd,
    openDays: row.openDays,
    openingExceptions: row.openingExceptions,
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
    `opening-hours:${row.globalId}:${new Date(startsAt).toISOString()}:${new Date(endsAt).toISOString()}`,
    startsAt,
    endsAt,
  );
  return clipInterval(candidate, interval);
}

/** The gaps between opening intervals, including wholly closed weekdays, within the display interval. */
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

/** Closed periods on the displayed `date`, in the timeline's elapsed-minute coordinates for `timezone`. */
export function closedDayRanges(row: ClosureSchedule, date: string, timezone: string): DayTimelineRange[] {
  const interval = displayInterval(date, timezone, "00:00", "24:00");
  if (interval.elapsedMinutes === 0) return [];
  return closedIntervals(row, interval).map((closed) => ({
    startMinute: instantToDayMinute(closed.startsAt.toISOString(), date, timezone),
    endMinute: instantToDayMinute(closed.endsAt.toISOString(), date, timezone),
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
  return clipInterval(
    {
      ...candidate,
      startsAt: new Date(candidate.startsAt.getTime() - row.bufferBeforeMinutes * 60_000),
      endsAt: new Date(candidate.endsAt.getTime() + row.bufferAfterMinutes * 60_000),
    },
    interval,
  );
}

type AvailabilityRowInterval = {
  row: CalendarAvailabilityRow;
  interval: AbsoluteDisplayInterval;
};

/**
 * One row's busy time within `interval`: its closed periods, then each confirmed event on its target,
 * widened by its buffers. Maintenance always counts; bookings do unless the row allows double booking.
 */
export function rowAvailabilityIntervals(
  row: CalendarAvailabilityRow,
  interval: AbsoluteDisplayInterval,
  bookings: readonly Booking[],
): SourcedAvailabilityInterval[] {
  const intervals = closedIntervals(row, interval);
  for (const booking of bookings) {
    if (booking.state !== "CONFIRMED" || booking.target?.globalId !== row.globalId) continue;
    if (row.allowDoubleBooking && booking.kind === "BOOKING") continue;
    const clipped = bookingInterval(booking, row, interval);
    if (clipped) intervals.push(clipped);
  }
  return intervals;
}

async function loadAvailability(
  rowIntervals: readonly AvailabilityRowInterval[],
  envelope: { start: string; end: string },
  token: string,
  signal: AbortSignal,
): Promise<ReadonlyMap<string, readonly SourcedAvailabilityInterval[]>> {
  const rows = rowIntervals.map(({ row }) => row);
  const bookings = await fetchDayBookings(
    rows.map((row) => row.globalId),
    envelope,
    token,
    signal,
  );
  const bookingsByTarget = new Map<string, Booking[]>();
  for (const booking of bookings) {
    if (booking.target === null) continue;
    const targetBookings = bookingsByTarget.get(booking.target.globalId);
    if (targetBookings) targetBookings.push(booking);
    else bookingsByTarget.set(booking.target.globalId, [booking]);
  }
  return new Map(
    rowIntervals.map(({ row, interval }) => [
      row.globalId,
      rowAvailabilityIntervals(row, interval, bookingsByTarget.get(row.globalId) ?? []),
    ]),
  );
}

export async function loadCalendarAvailability(
  rows: readonly CalendarAvailabilityRow[],
  intervalOrDate: AbsoluteDisplayInterval | string,
  token: string,
  signal: AbortSignal,
): Promise<ReadonlyMap<string, readonly SourcedAvailabilityInterval[]>> {
  if (rows.length === 0) return new Map();
  const interval =
    typeof intervalOrDate === "string"
      ? displayInterval(intervalOrDate, rows[0].timezone, "00:00", "24:00")
      : intervalOrDate;
  if (interval.elapsedMinutes === 0) return new Map(rows.map((row) => [row.globalId, []]));
  return loadAvailability(
    rows.map((row) => ({ row, interval })),
    availabilityEnvelope(rows, interval),
    token,
    signal,
  );
}

export async function loadDatedCalendarAvailability(
  rows: readonly DatedCalendarAvailabilityRow[],
  token: string,
  signal: AbortSignal,
): Promise<ReadonlyMap<string, readonly SourcedAvailabilityInterval[]>> {
  if (rows.length === 0) return new Map();
  const bounds = rows.map((row) => zonedDayBounds(row.date, row.timezone));
  const before = Math.max(...rows.map((row) => row.bufferBeforeMinutes));
  const after = Math.max(...rows.map((row) => row.bufferAfterMinutes));
  const envelope = {
    start: Temporal.Instant.from(
      bounds.reduce((value, bound) => (bound.start < value ? bound.start : value), bounds[0].start),
    )
      .subtract({ minutes: after })
      .toString(),
    end: Temporal.Instant.from(bounds.reduce((value, bound) => (bound.end > value ? bound.end : value), bounds[0].end))
      .add({ minutes: before })
      .toString(),
  };
  return loadAvailability(
    rows.map((row) => ({ row, interval: displayInterval(row.date, row.timezone, "00:00", "24:00") })),
    envelope,
    token,
    signal,
  );
}

export function useCalendarAvailability(
  rows: readonly CalendarAvailabilityRow[],
  intervalOrDate: AbsoluteDisplayInterval | string,
  token: string,
  authScope: string | number = token,
) {
  const interval =
    typeof intervalOrDate === "string"
      ? displayInterval(intervalOrDate, rows[0]?.timezone ?? "UTC", "00:00", "24:00")
      : intervalOrDate;
  const sortedRows = rows
    .map((row) => [
      row.globalId,
      row.timezone,
      row.openingStart,
      row.openingEnd,
      row.openDays.join(","),
      JSON.stringify(row.openingExceptions),
      row.bufferBeforeMinutes,
      row.bufferAfterMinutes,
      row.allowDoubleBooking,
    ])
    .toSorted();
  return useQuery({
    queryKey: ["api-v2", "bookings", "calendar-availability", sortedRows, interval.start, interval.end, authScope],
    queryFn: ({ signal }) => loadCalendarAvailability(rows, interval, token, signal),
    enabled: rows.length > 0 && token.length > 0,
    staleTime: 30_000,
    meta: viewTransitionQueryMeta,
  });
}
