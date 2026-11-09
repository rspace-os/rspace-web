import { useQuery } from "@tanstack/react-query";
import {
  availabilityEnvelope,
  bookingInterval,
  type CalendarAvailabilityRow,
  closedIntervals,
  type SourcedAvailabilityInterval,
} from "@/modules/booking/domain/availability";
import { BOOKING_READ_FIELDS, type Booking, mapBooking } from "@/modules/booking/domain/booking";
import { type AbsoluteDisplayInterval, displayInterval } from "@/modules/booking/domain/bookingTime";
import { ApiV2ProblemError } from "@/modules/common/api/v2";
import {
  getListBookingsQueryKey,
  getListBookingsUrl,
  listBookings,
} from "@/modules/common/api/v2/generated/bookings/bookings";
import { navigationProgressQueryMeta } from "@/modules/common/queries/navigationProgress";
import { requireBookingValue as required } from "./bookingMapping";

type AvailabilityPage = Awaited<ReturnType<typeof fetchPage>>;
const MAX_TARGETS_PER_BATCH = 500;

const MAX_REQUEST_BYTES = 6000; // Leaves headroom for browser/proxy headers outside application control.

const MAX_REQUEST_HEADER_BYTES = 8192;

const MAX_FILTER_ARGUMENTS = 1000;

const MAX_FILTER_COMPARISONS = 50;

const MAX_ENCODED_WHERE_BYTES = 32768;

const MAX_BOOKINGS_PER_BATCH = 1000;

function bookingWhere(globalIds: readonly string[], start: string, end: string): string {
  return `target=in=(${globalIds.join(",")});start<${end};end>${start};state==CONFIRMED`;
}

function availabilityRequest(globalIds: readonly string[], envelope: { start: string; end: string }, page: number) {
  const where = bookingWhere(globalIds, envelope.start, envelope.end);
  const parameters = {
    where,
    sort: "start,id",
    page,
    limit: 100,
    totals: true,
    depth: 1,
    fields: { bookings: BOOKING_READ_FIELDS, instruments: "id,globalId,name,deleted" },
  };
  const url = getListBookingsUrl(parameters);
  const bytes = new TextEncoder().encode(`GET ${url} HTTP/1.1\r\n\r\n`).length;
  const encodedWhere = new URLSearchParams({ where }).toString().slice("where=".length);
  const argumentCount = globalIds.length + 3;
  const comparisonCount = 4;
  return {
    url,
    parameters,
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

  signal: AbortSignal,
) {
  throwIfAborted(signal);
  const globalIds = [...new Set(rows.map((row) => row.globalId))];
  const request = availabilityRequest(globalIds, envelope, page);
  if (!request.fits) throw new CalendarAvailabilityRequestTooLargeError();
  try {
    const result = await listBookings(request.parameters, { signal });
    if (result.totalIsLowerBound) throw new Error("Calendar availability requires an exact total");
    throwIfAborted(signal);
    return {
      ...result,
      docs: result.docs.map(mapBooking),
      totalDocs: required(result.totalDocs, "totalDocs"),
      totalPages: Math.ceil(required(result.totalDocs, "totalDocs") / result.limit),
      hasNextPage: required(result.hasNextPage, "hasNextPage"),
    };
  } catch (error) {
    if (error instanceof ApiV2ProblemError && (error.status === 414 || error.status === 431))
      throw new CalendarAvailabilityRequestTooLargeError();
    throw error;
  }
}

type AvailabilityRowInterval = {
  row: CalendarAvailabilityRow;
  interval: AbsoluteDisplayInterval;
};

async function loadAvailability(
  rowIntervals: readonly AvailabilityRowInterval[],
  envelope: { start: string; end: string },

  signal: AbortSignal,
): Promise<Booking[]> {
  const ids = [...new Set(rowIntervals.map(({ row }) => row.globalId))];
  const batches: string[][] = [];
  let currentBatch: string[] = [];
  for (const globalId of ids) {
    const nextBatch = [...currentBatch, globalId];
    if (currentBatch.length === MAX_TARGETS_PER_BATCH || !availabilityRequest(nextBatch, envelope, 1).fits) {
      if (currentBatch.length === 0) throw new CalendarAvailabilityRequestTooLargeError();
      batches.push(currentBatch);
      currentBatch = [globalId];
      if (!availabilityRequest(currentBatch, envelope, 1).fits) {
        throw new CalendarAvailabilityRequestTooLargeError();
      }
    } else {
      currentBatch = nextBatch;
    }
  }
  if (currentBatch.length > 0) batches.push(currentBatch);

  const batchResults: Booking[][] = [];
  for (const batch of batches) {
    throwIfAborted(signal);
    const batchIds = new Set(batch);
    const batchRows = rowIntervals.filter(({ row }) => batchIds.has(row.globalId));
    batchResults.push(await loadAvailabilityBatchWithRetry(batchRows, envelope, signal));
  }

  throwIfAborted(signal);
  return batchResults.flat();
}

async function loadAvailabilityBatch(
  rowIntervals: readonly AvailabilityRowInterval[],
  envelope: { start: string; end: string },

  signal: AbortSignal,
): Promise<Booking[]> {
  const rows = rowIntervals.map(({ row }) => row);
  const first = await fetchPage(rows, envelope, 1, signal);
  if (first.totalDocs > MAX_BOOKINGS_PER_BATCH) {
    throw new Error("Calendar availability batch exceeds 1,000 bookings");
  }
  assertAvailabilityPage(first, 1);
  const bookings: Booking[] = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    throwIfAborted(signal);
    const next = await fetchPage(rows, envelope, page, signal);
    assertAvailabilityPage(next, page, first);
    bookings.push(...next.docs);
    if (bookings.length > first.totalDocs) {
      throw new Error("Calendar availability pagination returned too many bookings");
    }
  }
  if (bookings.length !== first.totalDocs) {
    throw new Error("Calendar availability pagination returned an incomplete booking set");
  }
  return bookings;
}

function mapAvailability(rowIntervals: readonly AvailabilityRowInterval[], bookings: readonly Booking[]) {
  const result = new Map<string, SourcedAvailabilityInterval[]>();
  const rowIntervalById = new Map<string, AvailabilityRowInterval>();
  for (const rowInterval of rowIntervals) {
    const { row, interval } = rowInterval;
    result.set(row.globalId, closedIntervals(row, interval));
    rowIntervalById.set(row.globalId, rowInterval);
  }
  for (const booking of bookings) {
    if (booking.state !== "CONFIRMED") continue;
    const rowInterval = rowIntervalById.get(booking.target.globalId);
    if (!rowInterval || (rowInterval.row.allowDoubleBooking && booking.kind === "BOOKING")) continue;
    const clipped = bookingInterval(booking, rowInterval.row, rowInterval.interval);
    if (clipped) result.get(rowInterval.row.globalId)?.push(clipped);
  }
  return result;
}

function assertAvailabilityPage(page: AvailabilityPage, requestedPage: number, firstPage?: AvailabilityPage): void {
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

  signal: AbortSignal,
): Promise<Booking[]> {
  try {
    return await loadAvailabilityBatch(rowIntervals, envelope, signal);
  } catch (error) {
    if (!(error instanceof CalendarAvailabilityRequestTooLargeError)) throw error;
    throwIfAborted(signal);
    const ids = [...new Set(rowIntervals.map(({ row }) => row.globalId))];
    if (ids.length === 1) throw error;
    const midpoint = Math.floor(ids.length / 2);
    const parts = [new Set(ids.slice(0, midpoint)), new Set(ids.slice(midpoint))];
    const results: Booking[][] = [];
    for (const part of parts) {
      const rows = rowIntervals.filter(({ row }) => part.has(row.globalId));
      results.push(await loadAvailabilityBatchWithRetry(rows, envelope, signal));
    }
    return results.flat();
  }
}

export async function loadCalendarAvailability(
  rows: readonly CalendarAvailabilityRow[],
  intervalOrDate: AbsoluteDisplayInterval | string,

  signal: AbortSignal,
): Promise<ReadonlyMap<string, readonly SourcedAvailabilityInterval[]>> {
  if (rows.length === 0) return new Map();
  const interval =
    typeof intervalOrDate === "string"
      ? displayInterval(intervalOrDate, rows[0].timezone, "00:00", "24:00")
      : intervalOrDate;
  if (interval.elapsedMinutes === 0) return new Map(rows.map((row) => [row.globalId, []]));
  return mapAvailability(
    rows.map((row) => ({ row, interval })),
    await loadAvailability(
      rows.map((row) => ({ row, interval })),
      availabilityEnvelope(rows, interval),
      signal,
    ),
  );
}

export function useCalendarAvailability(
  rows: readonly CalendarAvailabilityRow[],
  intervalOrDate: AbsoluteDisplayInterval | string,
) {
  const interval =
    typeof intervalOrDate === "string"
      ? displayInterval(intervalOrDate, rows[0]?.timezone ?? "UTC", "00:00", "24:00")
      : intervalOrDate;
  const envelope =
    rows.length > 0 ? availabilityEnvelope(rows, interval) : { start: interval.start, end: interval.end };
  return useQuery({
    queryKey: [
      ...getListBookingsQueryKey(
        availabilityRequest(
          rows.map((row) => row.globalId),
          envelope,
          1,
        ).parameters,
      ),
      "calendarAvailability",
    ],
    queryFn: ({ signal }) =>
      interval.elapsedMinutes === 0
        ? Promise.resolve([])
        : loadAvailability(
            rows.map((row) => ({ row, interval })),
            envelope,
            signal,
          ),
    select: (bookings) =>
      mapAvailability(
        rows.map((row) => ({ row, interval })),
        bookings,
      ),
    enabled: rows.length > 0,
    staleTime: 30_000,
    meta: navigationProgressQueryMeta,
  });
}
