import type { Temporal } from "@js-temporal/polyfill";
import { queryOptions } from "@tanstack/react-query";
import { uniqBy } from "es-toolkit";
import {
  BOOKING_READ_FIELDS,
  type BookingListDocument,
  mapBookingListDocument,
} from "@/modules/booking/domain/booking";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { apiV2Fields, assertApiV2Selected } from "@/modules/common/api/v2/apiV2Selected";
import type { ApiV2WhereObject } from "@/modules/common/api/v2/apiV2Where";
import { getListBookingsQueryKey, listBookings } from "@/modules/common/api/v2/generated/bookings/bookings";
import type { BookingsRead } from "@/modules/common/api/v2/generated/models/bookingsRead";
import { epochMillisecondsOf } from "@/modules/common/time/instant";
import { upcomingBookingWhere } from "../my-bookings/bookingCountFilter";

export const DASHBOARD_BOOKING_FIELDS = [...BOOKING_READ_FIELDS, "requester"] as const;
type DashboardBookingPage = Awaited<ReturnType<typeof fetchDashboardBookingPage>>;

const MAX_MONTHLY_PAGES = 10;
export const MAX_MONTHLY_RAW_DOCUMENTS = 1_000;

export class TooManyDashboardBookingsError extends Error {
  constructor() {
    super("Too many bookings to display");
    this.name = "TooManyDashboardBookingsError";
  }
}

type BookingRequest = {
  requesterId: number;

  signal?: AbortSignal;
};

type UpcomingDashboardBookingsRequest = BookingRequest & {
  asOf: string;
};

type MonthlyDashboardBookingsRequest = BookingRequest & {
  start: string;
  end: string;
};

function throwIfAborted(signal?: AbortSignal): void {
  if (!signal?.aborted) return;
  throw signal.reason ?? new DOMException("The operation was aborted", "AbortError");
}

type BookingsWhere = ApiV2WhereObject<"Bookings">;

export function monthlyBookingWhere(requesterId: number, start: string, end: string): BookingsWhere {
  return {
    "requester.value": requesterId,
    kind: "BOOKING",
    state: "CONFIRMED",
    start: { lessThan: end },
    end: { greaterThan: start },
  };
}

/** Keyset pagination: bookings after the last one read, in start then ID order. */
function afterBooking(where: BookingsWhere, last: { start: Temporal.Instant; id: number }): BookingsWhere {
  return {
    ...where,
    // Full precision: a cursor cut to the wire format's milliseconds would read sub-millisecond ties again.
    or: [
      { start: { greaterThan: last.start.toString() } },
      { start: last.start.toString(), id: { greaterThan: last.id } },
    ],
  };
}

async function fetchDashboardBookingPage(
  where: BookingsWhere,
  page: number,

  signal?: AbortSignal,
  limit = 100,
) {
  throwIfAborted(signal);
  const result = await listBookings(dashboardParameters(where, page, limit), { signal });
  return { ...result, docs: result.docs.map(mapDashboardDocument) };
}
/** Asserts a dashboard booking's guaranteed fields at the fetch boundary, then maps it. */
function mapDashboardDocument(document: BookingsRead): BookingListDocument {
  assertApiV2Selected("bookings", document, DASHBOARD_BOOKING_FIELDS);
  return mapBookingListDocument(document);
}
function dashboardParameters(where: BookingsWhere, page: number, limit = 100) {
  return {
    where,
    sort: "start,id",
    page,
    limit,
    depth: 1,
    fields: { bookings: apiV2Fields(DASHBOARD_BOOKING_FIELDS) },
  };
}

type TimedBooking = { booking: BookingListDocument; start: number; end: number };

/** Each booking with its bounds parsed once, in start then ID order. */
function timedBookings(bookings: readonly BookingListDocument[]): TimedBooking[] {
  return bookings
    .map((booking) => ({
      booking,
      start: booking.start.epochMilliseconds,
      end: booking.end.epochMilliseconds,
    }))
    .sort((left, right) => left.start - right.start || left.booking.id - right.booking.id);
}

function deduplicateAndSort(bookings: readonly BookingListDocument[]): BookingListDocument[] {
  return timedBookings(uniqBy(bookings, (booking) => booking.id)).map(({ booking }) => booking);
}

function validatePage(page: DashboardBookingPage, requestedPage: number): number | null {
  if (page.page !== requestedPage) throw new Error("Dashboard booking pagination did not advance");
  return page.hasNextPage ? page.page + 1 : null;
}

export async function fetchUpcomingDashboardBookings({
  requesterId,
  asOf,

  signal,
}: UpcomingDashboardBookingsRequest): Promise<BookingListDocument[]> {
  const page = await fetchDashboardBookingPage(upcomingBookingWhere(requesterId, asOf), 1, signal, 5);
  return deduplicateAndSort(page.docs).slice(0, 5);
}

export async function fetchMonthlyDashboardBookings({
  requesterId,
  start,
  end,

  signal,
}: MonthlyDashboardBookingsRequest): Promise<BookingListDocument[]> {
  const where = monthlyBookingWhere(requesterId, start, end);
  const bookings: BookingListDocument[] = [];
  let cursor: { start: Temporal.Instant; id: number } | null = null;
  let pagesFetched = 0;

  while (true) {
    const page = await fetchDashboardBookingPage(cursor ? afterBooking(where, cursor) : where, 1, signal);
    pagesFetched += 1;
    const nextPage = validatePage(page, 1);
    bookings.push(...page.docs);
    if (page.docs.length > 100 || bookings.length > MAX_MONTHLY_RAW_DOCUMENTS)
      throw new TooManyDashboardBookingsError();
    if (nextPage === null) break;
    if (pagesFetched >= MAX_MONTHLY_PAGES) throw new TooManyDashboardBookingsError();
    const last = page.docs.at(-1);
    if (!last || page.docs.length > 100) throw new Error("Dashboard booking pagination did not advance");
    const nextCursor = { start: last.start, id: last.id };
    if (cursor?.start.equals(nextCursor.start) && nextCursor.id === cursor.id)
      throw new Error("Dashboard booking pagination did not advance");
    cursor = nextCursor;
  }

  return deduplicateAndSort(bookings);
}

/**
 * The six visible weeks around `month`, starting on `weekStart`, an ISO weekday (1 Monday to 7 Sunday) that callers
 * take from the browser region with `firstDayOfWeek(bookingDateTimeLocale())`.
 */
export function dashboardMonthInterval(
  month: Temporal.PlainDate,
  timeZone: string,
  weekStart: number,
): {
  start: string;
  end: string;
  dates: string[];
} {
  const firstOfMonth = month.with({ day: 1 });
  const firstVisibleDate = firstOfMonth.subtract({ days: (firstOfMonth.dayOfWeek - weekStart + 7) % 7 });
  const dates = Array.from({ length: 42 }, (_, index) => firstVisibleDate.add({ days: index }).toString());
  return {
    start: zonedDayBounds(dates[0], timeZone).start,
    end: zonedDayBounds(dates[dates.length - 1], timeZone).end,
    dates,
  };
}

export function groupDashboardBookingsByDay(
  bookings: readonly BookingListDocument[],
  dates: readonly string[],
  timeZone: string,
): Map<string, BookingListDocument[]> {
  const grouped = new Map<string, BookingListDocument[]>(dates.map((date) => [date, []]));
  const bounds = dates.map((date) => {
    const day = zonedDayBounds(date, timeZone);
    return { date, start: epochMillisecondsOf(day.start), end: epochMillisecondsOf(day.end) };
  });

  // Sorted once up front, so each day's list fills in order.
  for (const { booking, start, end } of timedBookings(bookings)) {
    for (const day of bounds) {
      if (start >= day.end || end <= day.start) continue;
      grouped.get(day.date)?.push(booking);
    }
  }
  return grouped;
}

export function upcomingDashboardBookingsQuery(requesterId: number, _timeZone: string, asOf: string) {
  return queryOptions({
    queryKey: getListBookingsQueryKey(dashboardParameters(upcomingBookingWhere(requesterId, asOf), 1, 5)),
    queryFn: ({ signal }) => fetchUpcomingDashboardBookings({ requesterId, asOf, signal }),
    retry: false,
    refetchOnWindowFocus: true,
  });
}
export function monthlyDashboardBookingsQuery(requesterId: number, _timeZone: string, start: string, end: string) {
  const parameters = dashboardParameters(monthlyBookingWhere(requesterId, start, end), 1);
  return queryOptions({
    queryKey: [...getListBookingsQueryKey(parameters), "dashboardMonthly"],
    queryFn: ({ signal }) => fetchMonthlyDashboardBookings({ requesterId, start, end, signal }),
    retry: false,
    refetchOnWindowFocus: true,
  });
}
