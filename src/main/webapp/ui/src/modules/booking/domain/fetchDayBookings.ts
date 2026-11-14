import { queryOptions, skipToken } from "@tanstack/react-query";
import { ApiV2ProblemError } from "@/modules/common/api/v2";
import {
  getListBookingsQueryKey,
  getListBookingsUrl,
  listBookings,
} from "@/modules/common/api/v2/generated/bookings/bookings";
import { BOOKING_READ_FIELDS, type Booking, mapBooking } from "./booking";
import { requireBookingValue as required } from "./bookingMapping";

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

function availabilityRequest(globalIds: readonly string[], envelope: { start: string; end: string }, cursor: string) {
  const where = bookingWhere(globalIds, envelope.start, envelope.end) + cursor;
  const parameters = {
    where,
    sort: "start,id",
    page: 1,
    limit: 100,
    depth: 1,
    fields: { bookings: BOOKING_READ_FIELDS, instruments: "id,globalId,name,deleted" },
  };
  const url = getListBookingsUrl(parameters);
  const bytes = new TextEncoder().encode(`GET ${url} HTTP/1.1\r\n\r\n`).length;
  const encodedWhere = new URLSearchParams({ where }).toString().slice("where=".length);
  const argumentCount = globalIds.length + 6;
  const comparisonCount = 7;
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
  globalIds: readonly string[],
  envelope: { start: string; end: string },
  cursor: string,
  signal: AbortSignal,
) {
  throwIfAborted(signal);
  const request = availabilityRequest(globalIds, envelope, cursor);
  if (!request.fits) throw new CalendarAvailabilityRequestTooLargeError();
  try {
    const result = await listBookings(request.parameters, { signal });
    throwIfAborted(signal);
    return {
      ...result,
      docs: required(result.docs, "docs").map(mapBooking),
      page: required(result.page, "page"),
      hasNextPage: required(result.hasNextPage, "hasNextPage"),
    };
  } catch (error) {
    if (error instanceof ApiV2ProblemError && (error.status === 414 || error.status === 431))
      throw new CalendarAvailabilityRequestTooLargeError();
    throw error;
  }
}

async function loadAvailabilityBatch(
  globalIds: readonly string[],
  envelope: { start: string; end: string },
  signal: AbortSignal,
): Promise<Booking[]> {
  const bookings: Booking[] = [];
  let cursor = "";
  for (let pages = 0; ; pages += 1) {
    throwIfAborted(signal);
    const result = await fetchPage(globalIds, envelope, cursor, signal);
    assertAvailabilityPage(result, 1);
    bookings.push(...result.docs);
    if (bookings.length > MAX_BOOKINGS_PER_BATCH || (result.hasNextPage && pages >= 9)) {
      throw new Error("Calendar availability batch exceeds 1,000 bookings or 10 pages");
    }
    if (!result.hasNextPage) break;
    const last = result.docs.at(-1);
    if (!last) throw new Error("Calendar availability returned inconsistent pagination metadata");
    const nextCursor = `;(start=gt=${last.start},(start==${last.start};id=gt=${last.id}))`;
    if (cursor === nextCursor) throw new Error("Booking schedule changed during pagination");
    cursor = nextCursor;
  }
  if (new Set(bookings.map(({ id }) => id)).size !== bookings.length) {
    throw new Error("Booking schedule changed during pagination");
  }
  return bookings;
}

function assertAvailabilityPage(page: Awaited<ReturnType<typeof fetchPage>>, requestedPage: number): void {
  if (page.page !== requestedPage || page.docs.length > 100 || (page.hasNextPage && page.docs.length === 0)) {
    throw new Error("Calendar availability returned inconsistent pagination metadata");
  }
}

async function loadAvailabilityBatchWithRetry(
  globalIds: readonly string[],
  envelope: { start: string; end: string },
  signal: AbortSignal,
): Promise<Booking[]> {
  try {
    return await loadAvailabilityBatch(globalIds, envelope, signal);
  } catch (error) {
    if (!(error instanceof CalendarAvailabilityRequestTooLargeError)) throw error;
    throwIfAborted(signal);
    if (globalIds.length === 1) throw error;
    const midpoint = Math.floor(globalIds.length / 2);
    const parts = [globalIds.slice(0, midpoint), globalIds.slice(midpoint)];
    const results: Booking[][] = [];
    for (const part of parts) {
      results.push(await loadAvailabilityBatchWithRetry(part, envelope, signal));
    }
    return results.flat();
  }
}

export async function fetchDayBookings(
  globalIds: readonly string[],
  envelope: { start: string; end: string },
  signal: AbortSignal,
): Promise<Booking[]> {
  if (globalIds.length === 0) return [];
  const ids = [...new Set(globalIds)];
  const batches: string[][] = [];
  let currentBatch: string[] = [];
  for (const globalId of ids) {
    const nextBatch = [...currentBatch, globalId];
    if (
      currentBatch.length === MAX_TARGETS_PER_BATCH ||
      !availabilityRequest(
        nextBatch,
        envelope,
        `;(start=gt=${envelope.start},(start==${envelope.start};id=gt=9007199254740991))`,
      ).fits
    ) {
      if (currentBatch.length === 0) throw new CalendarAvailabilityRequestTooLargeError();
      batches.push(currentBatch);
      currentBatch = [globalId];
      if (
        !availabilityRequest(
          currentBatch,
          envelope,
          `;(start=gt=${envelope.start},(start==${envelope.start};id=gt=9007199254740991))`,
        ).fits
      ) {
        throw new CalendarAvailabilityRequestTooLargeError();
      }
    } else {
      currentBatch = nextBatch;
    }
  }
  if (currentBatch.length > 0) batches.push(currentBatch);
  const results: Booking[][] = [];
  for (const batch of batches) {
    throwIfAborted(signal);
    results.push(await loadAvailabilityBatchWithRetry(batch, envelope, signal));
  }
  throwIfAborted(signal);
  const bookings = results.flat();
  if (new Set(bookings.map(({ id }) => id)).size !== bookings.length) {
    throw new Error("Booking schedule changed during pagination");
  }
  return bookings;
}

export function dayScheduleQuery(
  globalId: string | undefined,
  bounds: { start: string; end: string },
  _scope: unknown,
) {
  return queryOptions({
    queryKey: [
      ...getListBookingsQueryKey(availabilityRequest(globalId ? [globalId] : [], bounds, "").parameters),
      "daySchedule",
    ],
    queryFn: globalId === undefined ? skipToken : ({ signal }) => fetchDayBookings([globalId], bounds, signal),
    enabled: globalId !== undefined,
    staleTime: 30_000,
  });
}
