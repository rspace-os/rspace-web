import * as v from "valibot";
import { BOOKING_READ_FIELDS, type Booking, BookingSchema } from "./booking";

const PageSchema = v.object({
  docs: v.array(BookingSchema),
  totalDocs: v.number(),
  totalPages: v.number(),
  page: v.number(),
  hasNextPage: v.boolean(),
});

function bookingWhere(globalIds: readonly string[], start: string, end: string): string {
  return `target=in=(${globalIds.join(",")});start=lt=${end};end=gt=${start};state==CONFIRMED`;
}

async function fetchPage(
  globalIds: readonly string[],
  envelope: { start: string; end: string },
  page: number,
  token: string,
  signal: AbortSignal,
) {
  const parameters = new URLSearchParams({
    where: bookingWhere(globalIds, envelope.start, envelope.end),
    sort: "start,id",
    page: String(page),
    limit: "100",
    depth: "1",
    "fields[bookings]": BOOKING_READ_FIELDS,
  });
  const response = await fetch(`/api/v2/bookings?${parameters}`, {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
    signal,
  });
  if (!response.ok) throw new Error(`Booking availability request failed (${response.status})`);
  return v.parse(PageSchema, await response.json());
}

export async function fetchDayBookings(
  globalIds: readonly string[],
  envelope: { start: string; end: string },
  token: string,
  signal: AbortSignal,
): Promise<Booking[]> {
  if (!globalIds.length) return [];
  const first = await fetchPage(globalIds, envelope, 1, token, signal);
  if (first.totalDocs > 1000 || first.totalPages > 10) throw new Error("Calendar availability exceeds 1,000 bookings");
  const bookings = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    const next = await fetchPage(globalIds, envelope, page, token, signal);
    if (next.totalDocs !== first.totalDocs || next.totalPages !== first.totalPages || next.page !== page) {
      throw new Error("Booking schedule changed during pagination");
    }
    bookings.push(...next.docs);
    if (bookings.length > 1000) throw new Error("Calendar availability exceeds 1,000 bookings");
  }
  if (bookings.length !== first.totalDocs || new Set(bookings.map(({ id }) => id)).size !== bookings.length) {
    throw new Error("Booking schedule changed during pagination");
  }
  return bookings;
}
