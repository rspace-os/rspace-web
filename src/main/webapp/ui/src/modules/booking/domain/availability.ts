import { Temporal } from "@js-temporal/polyfill";
import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { bookingApiV2Headers } from "@/modules/booking/domain/apiV2";
import {
  BOOKING_READ_FIELDS,
  type Booking,
  type BookingEventKind,
  BookingSchema,
} from "@/modules/booking/domain/booking";
import {
  type OpeningHoursEntry,
  type OpeningSchedule,
  openingIntervals,
} from "@/modules/booking/domain/bookingOpeningHours";
import { type AbsoluteDisplayInterval, displayInterval, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { viewTransitionQueryMeta } from "@/modules/common/queries/viewTransition";

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
  source: AvailabilitySource;
};

export type BookingConflict = NonNullable<AvailabilitySource["booking"]> & {
  start: string;
  end: string;
  timezone: string;
};

export function bookingConflicts(
  intervals: readonly SourcedAvailabilityInterval[],
  timezone: string,
  excludedBookingId?: number,
): BookingConflict[] {
  const conflicts = new Map<number, BookingConflict>();
  for (const interval of intervals) {
    const booking = interval.source.booking;
    if (!booking || booking.id === excludedBookingId || conflicts.has(booking.id)) continue;
    conflicts.set(booking.id, {
      ...booking,
      start: interval.source.startsAt.toISOString(),
      end: interval.source.endsAt.toISOString(),
      timezone,
    });
  }
  return [...conflicts.values()];
}

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
  bufferBeforeMinutes: number;
  bufferAfterMinutes: number;
  allowDoubleBooking: boolean;
};

export type DatedCalendarAvailabilityRow = CalendarAvailabilityRow & { date: string };

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

const PageSchema = v.object({
  docs: v.array(BookingSchema),
  totalDocs: v.number(),
  totalPages: v.number(),
  page: v.number(),
  hasNextPage: v.boolean(),
});

const MAX_TARGETS_PER_BATCH = 500;
const MAX_REQUEST_BYTES = 6000; // Leaves headroom for browser/proxy headers outside application control.
const MAX_REQUEST_HEADER_BYTES = 8192;
const MAX_FILTER_ARGUMENTS = 1000;
const MAX_FILTER_COMPARISONS = 50;
const MAX_ENCODED_WHERE_BYTES = 32768;
const MAX_BOOKINGS_PER_BATCH = 1000;

function bookingWhere(globalIds: readonly string[], start: string, end: string): string {
  return `target=in=(${globalIds.join(",")});start=lt=${end};end=gt=${start};state==CONFIRMED`;
}

function availabilityRequest(
  globalIds: readonly string[],
  envelope: { start: string; end: string },
  page: number,
  token: string,
) {
  const where = bookingWhere(globalIds, envelope.start, envelope.end);
  const parameters = new URLSearchParams({
    where,
    sort: "start,id",
    page: String(page),
    limit: "100",
    depth: "1",
    "fields[bookings]": BOOKING_READ_FIELDS,
  });
  const url = `/api/v2/bookings?${parameters.toString()}`;
  const headers = bookingApiV2Headers(token);
  let bytes = new TextEncoder().encode(`GET ${url} HTTP/1.1\r\n`).length + 2;
  headers.forEach((value, name) => {
    bytes += new TextEncoder().encode(`${name}: ${value}\r\n`).length;
  });
  const encodedWhere = new URLSearchParams({ where }).toString().slice("where=".length);
  const argumentCount = globalIds.length + 3;
  const comparisonCount = 4;
  return {
    url,
    headers,
    fits:
      globalIds.length > 0 &&
      globalIds.length <= MAX_TARGETS_PER_BATCH &&
      argumentCount <= MAX_FILTER_ARGUMENTS &&
      comparisonCount <= MAX_FILTER_COMPARISONS &&
      new TextEncoder().encode(encodedWhere).length <= MAX_ENCODED_WHERE_BYTES &&
      bytes <= Math.min(MAX_REQUEST_BYTES, MAX_REQUEST_HEADER_BYTES),
  };
}

class CalendarAvailabilityRequestTooLargeError extends Error {
  constructor() {
    super("Calendar availability request exceeds the safe request budget");
    this.name = "CalendarAvailabilityRequestTooLargeError";
  }
}

function throwIfAborted(signal: AbortSignal): void {
  if (signal.aborted) throw signal.reason ?? new DOMException("The operation was aborted", "AbortError");
}

async function fetchPage(
  rows: readonly CalendarAvailabilityRow[],
  envelope: { start: string; end: string },
  page: number,
  token: string,
  signal: AbortSignal,
) {
  throwIfAborted(signal);
  const globalIds = [...new Set(rows.map((row) => row.globalId))];
  const request = availabilityRequest(globalIds, envelope, page, token);
  if (!request.fits) throw new CalendarAvailabilityRequestTooLargeError();
  const response = await fetch(request.url, {
    headers: request.headers,
    signal,
  });
  if (response.status === 414 || response.status === 431) throw new CalendarAvailabilityRequestTooLargeError();
  if (!response.ok) throw new Error(`Booking availability request failed (${response.status})`);
  const result = v.parse(PageSchema, await response.json());
  throwIfAborted(signal);
  return result;
}

function sharedBufferGapMinutes(row: Pick<CalendarAvailabilityRow, "bufferBeforeMinutes" | "bufferAfterMinutes">) {
  return Math.max(row.bufferBeforeMinutes, row.bufferAfterMinutes);
}

function availabilityEnvelope(rows: readonly CalendarAvailabilityRow[], interval: AbsoluteDisplayInterval) {
  const sharedGapMinutes = Math.max(...rows.map(sharedBufferGapMinutes));
  return {
    start: Temporal.Instant.from(interval.start).subtract({ minutes: sharedGapMinutes }).toString(),
    end: Temporal.Instant.from(interval.end).add({ minutes: sharedGapMinutes }).toString(),
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
  const sharedGapMinutes = sharedBufferGapMinutes(row);
  return clipInterval(
    {
      ...candidate,
      startsAt: new Date(candidate.startsAt.getTime() - sharedGapMinutes * 60_000),
      endsAt: new Date(candidate.endsAt.getTime() + sharedGapMinutes * 60_000),
    },
    interval,
  );
}

type AvailabilityRowInterval = {
  row: CalendarAvailabilityRow;
  interval: AbsoluteDisplayInterval;
};

async function loadAvailability(
  rowIntervals: readonly AvailabilityRowInterval[],
  envelope: { start: string; end: string },
  token: string,
  signal: AbortSignal,
): Promise<ReadonlyMap<string, readonly SourcedAvailabilityInterval[]>> {
  const ids = [...new Set(rowIntervals.map(({ row }) => row.globalId))];
  const batches: string[][] = [];
  let currentBatch: string[] = [];
  for (const globalId of ids) {
    const nextBatch = [...currentBatch, globalId];
    if (currentBatch.length === MAX_TARGETS_PER_BATCH || !availabilityRequest(nextBatch, envelope, 1, token).fits) {
      if (currentBatch.length === 0) throw new CalendarAvailabilityRequestTooLargeError();
      batches.push(currentBatch);
      currentBatch = [globalId];
      if (!availabilityRequest(currentBatch, envelope, 1, token).fits) {
        throw new CalendarAvailabilityRequestTooLargeError();
      }
    } else {
      currentBatch = nextBatch;
    }
  }
  if (currentBatch.length > 0) batches.push(currentBatch);

  const batchResults: ReadonlyMap<string, readonly SourcedAvailabilityInterval[]>[] = [];
  for (const batch of batches) {
    throwIfAborted(signal);
    const batchIds = new Set(batch);
    const batchRows = rowIntervals.filter(({ row }) => batchIds.has(row.globalId));
    batchResults.push(await loadAvailabilityBatchWithRetry(batchRows, envelope, token, signal));
  }

  throwIfAborted(signal);
  const result = new Map<string, SourcedAvailabilityInterval[]>();
  for (const batchResult of batchResults) {
    for (const [globalId, intervals] of batchResult) result.set(globalId, [...intervals]);
  }
  return result;
}

async function loadAvailabilityBatch(
  rowIntervals: readonly AvailabilityRowInterval[],
  envelope: { start: string; end: string },
  token: string,
  signal: AbortSignal,
): Promise<ReadonlyMap<string, readonly SourcedAvailabilityInterval[]>> {
  const rows = rowIntervals.map(({ row }) => row);
  const first = await fetchPage(rows, envelope, 1, token, signal);
  if (first.totalDocs > MAX_BOOKINGS_PER_BATCH) {
    throw new Error("Calendar availability batch exceeds 1,000 bookings");
  }
  assertAvailabilityPage(first, 1);
  const bookings: Booking[] = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    throwIfAborted(signal);
    const next = await fetchPage(rows, envelope, page, token, signal);
    assertAvailabilityPage(next, page, first);
    bookings.push(...next.docs);
    if (bookings.length > first.totalDocs) {
      throw new Error("Calendar availability pagination returned too many bookings");
    }
  }
  if (bookings.length !== first.totalDocs) {
    throw new Error("Calendar availability pagination returned an incomplete booking set");
  }
  const result = new Map<string, SourcedAvailabilityInterval[]>();
  const rowIntervalById = new Map<string, AvailabilityRowInterval>();
  for (const rowInterval of rowIntervals) {
    const { row, interval } = rowInterval;
    result.set(row.globalId, closedIntervals(row, interval));
    rowIntervalById.set(row.globalId, rowInterval);
  }
  for (const booking of bookings) {
    if (booking.state !== "CONFIRMED" || booking.target === null) continue;
    const rowInterval = rowIntervalById.get(booking.target.globalId);
    if (!rowInterval || (rowInterval.row.allowDoubleBooking && booking.kind === "BOOKING")) continue;
    const clipped = bookingInterval(booking, rowInterval.row, rowInterval.interval);
    if (clipped) result.get(rowInterval.row.globalId)?.push(clipped);
  }
  return result;
}

function assertAvailabilityPage(
  page: v.InferOutput<typeof PageSchema>,
  requestedPage: number,
  firstPage?: v.InferOutput<typeof PageSchema>,
): void {
  if (
    !Number.isSafeInteger(page.totalDocs) ||
    page.totalDocs < 0 ||
    !Number.isSafeInteger(page.totalPages) ||
    page.totalPages < 0 ||
    page.totalPages > 10 ||
    (page.totalDocs === 0 ? page.totalPages > 1 : page.totalPages === 0) ||
    page.page !== requestedPage ||
    page.docs.length > 100 ||
    (page.totalDocs > 0 && page.docs.length === 0) ||
    page.hasNextPage !== requestedPage < page.totalPages
  ) {
    throw new Error("Calendar availability returned inconsistent pagination metadata");
  }
  if (firstPage && (page.totalDocs !== firstPage.totalDocs || page.totalPages !== firstPage.totalPages)) {
    throw new Error("Calendar availability changed while its pages were being read");
  }
}

async function loadAvailabilityBatchWithRetry(
  rowIntervals: readonly AvailabilityRowInterval[],
  envelope: { start: string; end: string },
  token: string,
  signal: AbortSignal,
): Promise<ReadonlyMap<string, readonly SourcedAvailabilityInterval[]>> {
  try {
    return await loadAvailabilityBatch(rowIntervals, envelope, token, signal);
  } catch (error) {
    if (!(error instanceof CalendarAvailabilityRequestTooLargeError)) throw error;
    throwIfAborted(signal);
    const ids = [...new Set(rowIntervals.map(({ row }) => row.globalId))];
    if (ids.length === 1) throw error;
    const midpoint = Math.floor(ids.length / 2);
    const parts = [new Set(ids.slice(0, midpoint)), new Set(ids.slice(midpoint))];
    const results: ReadonlyMap<string, readonly SourcedAvailabilityInterval[]>[] = [];
    for (const part of parts) {
      const rows = rowIntervals.filter(({ row }) => part.has(row.globalId));
      results.push(await loadAvailabilityBatchWithRetry(rows, envelope, token, signal));
    }
    const merged = new Map<string, SourcedAvailabilityInterval[]>();
    for (const result of results) {
      for (const [globalId, intervals] of result) merged.set(globalId, [...intervals]);
    }
    return merged;
  }
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
  const gap = Math.max(...rows.map(sharedBufferGapMinutes));
  const envelope = {
    start: Temporal.Instant.from(
      bounds.reduce((value, bound) => (bound.start < value ? bound.start : value), bounds[0].start),
    )
      .subtract({ minutes: gap })
      .toString(),
    end: Temporal.Instant.from(bounds.reduce((value, bound) => (bound.end > value ? bound.end : value), bounds[0].end))
      .add({ minutes: gap })
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
      JSON.stringify(row.openingHours),
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
