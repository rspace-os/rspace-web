import { queryOptions, skipToken } from "@tanstack/react-query";
import * as v from "valibot";
import { bookingKeys } from "@/modules/booking/domain/bookingKeys";
import { apiV2Headers } from "@/modules/common/api/apiV2Headers";
import { v2PageEnvelope } from "@/modules/common/queries/v2Pagination";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import { BOOKING_READ_FIELDS, type Booking, BookingSchema } from "./booking";

const PageSchema = v2PageEnvelope(BookingSchema);

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
    "fields[instruments]": "id,globalId,name,deleted",
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
  globalIds: readonly string[],
  envelope: { start: string; end: string },
  page: number,
  token: string,
  signal: AbortSignal,
) {
  throwIfAborted(signal);
  const request = availabilityRequest(globalIds, envelope, page, token);
  if (!request.fits) throw new CalendarAvailabilityRequestTooLargeError();
  const response = await fetchWithUiToken(request.url, {
    headers: request.headers,
    signal,
  });
  if (response.status === 414 || response.status === 431) throw new CalendarAvailabilityRequestTooLargeError();
  if (!response.ok) throw new Error(`Booking availability request failed (${response.status})`);
  const result = v.parse(PageSchema, await response.json());
  throwIfAborted(signal);
  return result;
}

async function loadAvailabilityBatch(
  globalIds: readonly string[],
  envelope: { start: string; end: string },
  token: string,
  signal: AbortSignal,
): Promise<Booking[]> {
  const bookings: Booking[] = [];
  let requestedPage = 1;
  for (let pages = 0; ; pages += 1) {
    throwIfAborted(signal);
    const result = await fetchPage(globalIds, envelope, requestedPage, token, signal);
    assertAvailabilityPage(result, requestedPage);
    bookings.push(...result.docs);
    if (bookings.length > MAX_BOOKINGS_PER_BATCH || (result.hasNextPage && pages >= 9)) {
      throw new Error("Calendar availability batch exceeds 1,000 bookings or 10 pages");
    }
    if (!result.hasNextPage) break;
    requestedPage = result.nextPage ?? requestedPage + 1;
  }
  if (new Set(bookings.map(({ id }) => id)).size !== bookings.length) {
    throw new Error("Booking schedule changed during pagination");
  }
  return bookings;
}

function assertAvailabilityPage(page: v.InferOutput<typeof PageSchema>, requestedPage: number): void {
  if (
    page.page !== requestedPage ||
    page.docs.length > 100 ||
    (page.hasNextPage && page.docs.length === 0) ||
    (page.hasNextPage && page.nextPage !== null && page.nextPage <= requestedPage)
  ) {
    throw new Error("Calendar availability returned inconsistent pagination metadata");
  }
}

async function loadAvailabilityBatchWithRetry(
  globalIds: readonly string[],
  envelope: { start: string; end: string },
  token: string,
  signal: AbortSignal,
): Promise<Booking[]> {
  try {
    return await loadAvailabilityBatch(globalIds, envelope, token, signal);
  } catch (error) {
    if (!(error instanceof CalendarAvailabilityRequestTooLargeError)) throw error;
    throwIfAborted(signal);
    if (globalIds.length === 1) throw error;
    const midpoint = Math.floor(globalIds.length / 2);
    const parts = [globalIds.slice(0, midpoint), globalIds.slice(midpoint)];
    const results: Booking[][] = [];
    for (const part of parts) {
      results.push(await loadAvailabilityBatchWithRetry(part, envelope, token, signal));
    }
    return results.flat();
  }
}

export async function fetchDayBookings(
  globalIds: readonly string[],
  envelope: { start: string; end: string },
  token: string,
  signal: AbortSignal,
): Promise<Booking[]> {
  if (globalIds.length === 0) return [];
  const ids = [...new Set(globalIds)];
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
  const results: Booking[][] = [];
  for (const batch of batches) {
    throwIfAborted(signal);
    results.push(await loadAvailabilityBatchWithRetry(batch, envelope, token, signal));
  }
  throwIfAborted(signal);
  const bookings = results.flat();
  if (new Set(bookings.map(({ id }) => id)).size !== bookings.length) {
    throw new Error("Booking schedule changed during pagination");
  }
  return bookings;
}

export const dayScheduleQuery = (
  globalId: string | undefined,
  bounds: { start: string; end: string },
  token: string,
  scope: unknown,
) =>
  queryOptions({
    queryKey: [...bookingKeys.all, "day-schedule", scope, globalId, bounds.start, bounds.end] as const,
    queryFn: globalId === undefined ? skipToken : ({ signal }) => fetchDayBookings([globalId], bounds, token, signal),
    staleTime: 30_000,
  });
