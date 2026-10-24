import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import {
  availabilityEnvelope,
  bookingInterval,
  type CalendarAvailabilityRow,
  closedIntervals,
  type SourcedAvailabilityInterval,
} from "@/modules/booking/domain/availability";
import { BOOKING_READ_FIELDS, type Booking, BookingSchema } from "@/modules/booking/domain/booking";
import { type AbsoluteDisplayInterval, displayInterval } from "@/modules/booking/domain/bookingTime";
import { apiV2Headers } from "@/modules/common/api/apiV2Headers";
import { navigationProgressQueryMeta } from "@/modules/common/queries/navigationProgress";

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
  const headers = apiV2Headers(token);
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
    meta: navigationProgressQueryMeta,
  });
}
