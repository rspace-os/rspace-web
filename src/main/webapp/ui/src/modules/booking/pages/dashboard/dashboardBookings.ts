import { Temporal } from "@js-temporal/polyfill";
import {
  BOOKING_READ_FIELDS,
  type BookingListDocument,
  mapBookingListDocument,
} from "@/modules/booking/domain/booking";
import { requireBookingValue as required } from "@/modules/booking/domain/bookingMapping";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import {
  getListBookingsQueryKey,
  getListBookingsQueryOptions,
  listBookings,
} from "@/modules/common/api/v2/generated/bookings/bookings";
import { upcomingBookingWhere } from "../my-bookings/bookingCountFilter";

const DASHBOARD_BOOKING_FIELDS = `${BOOKING_READ_FIELDS},requester`;
type DashboardBookingPage = Awaited<ReturnType<typeof fetchDashboardBookingPage>>;

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

  signal?: AbortSignal,
  limit = 100,
) {
  throwIfAborted(signal);
  const result = await listBookings(dashboardParameters(where, page, limit), { signal });
  return {
    ...result,
    docs: result.docs.map(mapBookingListDocument),
    hasNextPage: required(result.hasNextPage, "hasNextPage"),
  };
}
function dashboardParameters(where: string, page: number, limit = 100) {
  return { where, sort: "start,id", page, limit, depth: 1, fields: { bookings: DASHBOARD_BOOKING_FIELDS } };
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
  const where = `requester.value==${requesterId};kind==BOOKING;state==CONFIRMED;start=lt=${end};end=gt=${start}`;
  const bookings: BookingListDocument[] = [];
  let cursor = "";
  let pagesFetched = 0;

  while (true) {
    const page = await fetchDashboardBookingPage(where + cursor, 1, signal);
    pagesFetched += 1;
    const nextPage = validatePage(page, 1);
    bookings.push(...page.docs);
    if (page.docs.length > 100 || bookings.length > MAX_MONTHLY_RAW_DOCUMENTS)
      throw new TooManyDashboardBookingsError();
    if (nextPage === null) break;
    if (pagesFetched >= MAX_MONTHLY_PAGES) throw new TooManyDashboardBookingsError();
    const last = page.docs.at(-1);
    if (!last || page.docs.length > 100) throw new Error("Dashboard booking pagination did not advance");
    const nextCursor = `;(start=gt=${last.start},(start==${last.start};id=gt=${last.id}))`;
    if (nextCursor === cursor) throw new Error("Dashboard booking pagination did not advance");
    cursor = nextCursor;
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

export function upcomingDashboardBookingsQuery(requesterId: number, _timeZone: string, asOf: string) {
  return getListBookingsQueryOptions<BookingListDocument[]>(
    dashboardParameters(upcomingBookingWhere(requesterId, asOf), 1, 5),
    {
      query: {
        select: (result) => deduplicateAndSort(result.docs.map(mapBookingListDocument)).slice(0, 5),
        retry: false,
        refetchOnWindowFocus: true,
      },
    },
  );
}
export function monthlyDashboardBookingsQuery(requesterId: number, _timeZone: string, start: string, end: string) {
  const parameters = dashboardParameters(
    `requester.value==${requesterId};kind==BOOKING;state==CONFIRMED;start=lt=${end};end=gt=${start}`,
    1,
  );
  return getListBookingsQueryOptions<BookingListDocument[]>(parameters, {
    query: {
      queryKey: [...getListBookingsQueryKey(parameters), "dashboardMonthly"],
      queryFn: async ({ signal }) => ({
        docs: await fetchMonthlyDashboardBookings({ requesterId, start, end, signal }),
        page: 1,
        limit: 100,
      }),
      select: (result) => result.docs.map(mapBookingListDocument),
      retry: false,
      refetchOnWindowFocus: true,
    },
  });
}
