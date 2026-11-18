import { queryOptions, skipToken } from "@tanstack/react-query";
import { ApiV2ProblemError } from "@/modules/common/api/v2/ApiV2ProblemError";
import { apiV2Fields } from "@/modules/common/api/v2/apiV2Selected";
import { type ApiV2WhereObject, apiV2WhereToRsql } from "@/modules/common/api/v2/apiV2Where";
import {
  getListBookingsQueryKey,
  getListBookingsUrl,
  listBookings,
} from "@/modules/common/api/v2/generated/bookings/bookings";
import { isNonEmpty } from "@/util/ArrayUtils";
import { BOOKING_READ_FIELDS, type Booking, mapSelectedBookings } from "./booking";

const MAX_TARGETS_PER_BATCH = 500;
const MAX_REQUEST_BYTES = 6000; // Leaves headroom for browser/proxy headers outside application control.
const MAX_REQUEST_HEADER_BYTES = 8192;
const MAX_FILTER_ARGUMENTS = 1000;
const MAX_FILTER_COMPARISONS = 50;
const MAX_ENCODED_WHERE_BYTES = 32768;
const MAX_BOOKINGS_PER_BATCH = 1000;

/** Keyset position after the last booking read, in start then ID order. */
type Cursor = { start: string; id: number } | null;

function bookingWhere(globalIds: readonly string[], envelope: { start: string; end: string }, cursor: Cursor) {
  const where: ApiV2WhereObject<"Bookings"> = {
    // An empty target list never fetches; its disabled query key simply has no target filter.
    ...(isNonEmpty(globalIds) ? { target: { in: globalIds } } : {}),
    start: { lessThan: envelope.end },
    end: { greaterThan: envelope.start },
    state: "CONFIRMED",
  };
  if (!cursor) return where;
  return {
    ...where,
    or: [{ start: { greaterThan: cursor.start } }, { start: cursor.start, id: { greaterThan: cursor.id } }],
  };
}

export function dayBookingsWhere(globalIds: readonly string[], envelope: { start: string; end: string }) {
  return bookingWhere(globalIds, envelope, null);
}

function availabilityRequest(globalIds: readonly string[], envelope: { start: string; end: string }, cursor: Cursor) {
  const where = bookingWhere(globalIds, envelope, cursor);
  const parameters = {
    where,
    sort: "start,id",
    page: 1,
    limit: 100,
    depth: 1,
    fields: { bookings: apiV2Fields(BOOKING_READ_FIELDS), instruments: "id,globalId,name,deleted" },
  };
  const url = getListBookingsUrl(parameters);
  const bytes = new TextEncoder().encode(`GET ${url} HTTP/1.1\r\n\r\n`).length;
  const encodedWhere = new URLSearchParams({ where: apiV2WhereToRsql(where) }).toString().slice("where=".length);
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
  cursor: Cursor,
  signal: AbortSignal,
) {
  throwIfAborted(signal);
  const request = availabilityRequest(globalIds, envelope, cursor);
  if (!request.fits) throw new CalendarAvailabilityRequestTooLargeError();
  try {
    const result = await listBookings(request.parameters, { signal });
    throwIfAborted(signal);
    return { ...result, docs: mapSelectedBookings(result.docs) };
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
  let cursor: Cursor = null;
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
    const nextCursor = { start: last.start, id: last.id };
    if (cursor?.start === nextCursor.start && cursor.id === nextCursor.id)
      throw new Error("Booking schedule changed during pagination");
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

/** The longest cursor a batch can need, so that batches still fit once pagination starts. */
function largestCursor(envelope: { start: string }): Cursor {
  return { start: envelope.start, id: Number.MAX_SAFE_INTEGER };
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
      !availabilityRequest(nextBatch, envelope, largestCursor(envelope)).fits
    ) {
      if (currentBatch.length === 0) throw new CalendarAvailabilityRequestTooLargeError();
      batches.push(currentBatch);
      currentBatch = [globalId];
      if (!availabilityRequest(currentBatch, envelope, largestCursor(envelope)).fits) {
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
      ...getListBookingsQueryKey(availabilityRequest(globalId ? [globalId] : [], bounds, null).parameters),
      "daySchedule",
    ],
    queryFn: globalId === undefined ? skipToken : ({ signal }) => fetchDayBookings([globalId], bounds, signal),
    enabled: globalId !== undefined,
    staleTime: 30_000,
  });
}
