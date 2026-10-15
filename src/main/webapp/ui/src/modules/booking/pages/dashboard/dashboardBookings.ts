import { Temporal } from "@js-temporal/polyfill";
import { queryOptions } from "@tanstack/react-query";
import type * as v from "valibot";
import {
  BOOKING_READ_FIELDS,
  type BookingListDocument,
  BookingListDocumentSchema,
} from "@/modules/booking/domain/booking";
import { bookingKeys } from "@/modules/booking/domain/bookingKeys";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { apiV2Headers } from "@/modules/common/api/apiV2Headers";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2PageEnvelope } from "@/modules/common/queries/v2Pagination";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import { upcomingBookingWhere } from "../my-bookings/bookingCountFilter";

const DASHBOARD_BOOKING_FIELDS = `${BOOKING_READ_FIELDS},requesterId`;
const DashboardBookingPageSchema = v2PageEnvelope(BookingListDocumentSchema);
type DashboardBookingPage = v.InferOutput<typeof DashboardBookingPageSchema>;

const MAX_MONTHLY_PAGES = 10;
const MAX_MONTHLY_RAW_DOCUMENTS = 1_000;

export class TooManyDashboardBookingsError extends Error {
  constructor() {
    super("Too many bookings to display");
    this.name = "TooManyDashboardBookingsError";
  }
}

type BookingRequest = {
  requesterId: number;
  token: string;
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

async function fetchDashboardBookingPage(
  where: string,
  page: number,
  token: string,
  signal?: AbortSignal,
  limit = 100,
): Promise<DashboardBookingPage> {
  throwIfAborted(signal);
  const parameters = new URLSearchParams({
    where,
    sort: "start,id",
    page: String(page),
    limit: String(limit),
    depth: "1",
    "fields[bookings]": DASHBOARD_BOOKING_FIELDS,
  });
  const response = await fetchWithUiToken(`/api/v2/bookings?${parameters}`, {
    headers: apiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Dashboard bookings request failed (${response.status})`);
  return parseOrThrow(DashboardBookingPageSchema, await response.json());
}

function compareBookings(left: BookingListDocument, right: BookingListDocument): number {
  const byStart = Date.parse(left.start) - Date.parse(right.start);
  return byStart || left.id - right.id;
}

function deduplicateAndSort(bookings: readonly BookingListDocument[]): BookingListDocument[] {
  const byId = new Map<number, BookingListDocument>();
  for (const booking of bookings) {
    if (!byId.has(booking.id)) byId.set(booking.id, booking);
  }
  return [...byId.values()].sort(compareBookings);
}

function validatePage(page: DashboardBookingPage, requestedPage: number): number | null {
  if (page.page !== requestedPage) throw new Error("Dashboard booking pagination did not advance");
  if (!page.hasNextPage && page.nextPage !== null) {
    throw new Error("Malformed dashboard booking pagination");
  }
  if (page.hasNextPage) {
    if (page.nextPage === null || page.nextPage !== page.page + 1) {
      throw new Error("Dashboard booking pagination did not advance");
    }
    return page.nextPage;
  }
  return null;
}

export async function fetchUpcomingDashboardBookings({
  requesterId,
  asOf,
  token,
  signal,
}: UpcomingDashboardBookingsRequest): Promise<BookingListDocument[]> {
  const page = await fetchDashboardBookingPage(upcomingBookingWhere(requesterId, asOf), 1, token, signal, 5);
  return deduplicateAndSort(page.docs).slice(0, 5);
}

export async function fetchMonthlyDashboardBookings({
  requesterId,
  start,
  end,
  token,
  signal,
}: MonthlyDashboardBookingsRequest): Promise<BookingListDocument[]> {
  const where = `requesterId==${requesterId};kind==BOOKING;state==CONFIRMED;start=lt=${end};end=gt=${start}`;
  const bookings: BookingListDocument[] = [];
  let pageNumber = 1;
  let pagesFetched = 0;

  while (true) {
    const page = await fetchDashboardBookingPage(where, pageNumber, token, signal);
    pagesFetched += 1;
    const nextPage = validatePage(page, pageNumber);
    bookings.push(...page.docs);
    if (bookings.length > MAX_MONTHLY_RAW_DOCUMENTS) throw new TooManyDashboardBookingsError();
    if (nextPage === null) break;
    if (pagesFetched >= MAX_MONTHLY_PAGES) throw new TooManyDashboardBookingsError();
    pageNumber = nextPage;
  }

  return deduplicateAndSort(bookings);
}

export function dashboardMonthInterval(
  month: Date,
  timeZone: string,
): {
  start: string;
  end: string;
  dates: string[];
} {
  const firstOfMonth = Temporal.PlainDate.from({
    year: month.getFullYear(),
    month: month.getMonth() + 1,
    day: 1,
  });
  const firstVisibleDate = firstOfMonth.subtract({ days: firstOfMonth.dayOfWeek - 1 });
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
    return { date, start: Date.parse(day.start), end: Date.parse(day.end) };
  });

  for (const booking of bookings) {
    const bookingStart = Date.parse(booking.start);
    const bookingEnd = Date.parse(booking.end);
    for (const day of bounds) {
      if (bookingStart >= day.end || bookingEnd <= day.start) continue;
      grouped.get(day.date)?.push(booking);
    }
  }

  for (const dayBookings of grouped.values()) dayBookings.sort(compareBookings);
  return grouped;
}

export const upcomingDashboardBookingsQuery = (requesterId: number, timeZone: string, asOf: string, token: string) =>
  queryOptions({
    queryKey: [...bookingKeys.upcoming(), requesterId, timeZone, asOf] as const,
    queryFn: ({ signal }) => fetchUpcomingDashboardBookings({ requesterId, asOf, token, signal }),
    retry: false,
    refetchOnWindowFocus: true,
  });

export const monthlyDashboardBookingsQuery = (
  requesterId: number,
  timeZone: string,
  start: string,
  end: string,
  token: string,
) =>
  queryOptions({
    queryKey: [...bookingKeys.all, "dashboard", "monthly", requesterId, timeZone, start, end] as const,
    queryFn: ({ signal }) => fetchMonthlyDashboardBookings({ requesterId, start, end, token, signal }),
    retry: false,
    refetchOnWindowFocus: true,
  });
