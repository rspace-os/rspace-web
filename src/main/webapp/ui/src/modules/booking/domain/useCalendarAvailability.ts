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
import { getListBookingsQueryKey, listBookings } from "@/modules/common/api/v2/generated/bookings/bookings";
import { navigationProgressQueryMeta } from "@/modules/common/queries/navigationProgress";
import { requireBookingValue as required } from "./bookingMapping";

function bookingWhere(globalIds: readonly string[], start: string, end: string): string {
  return `target=in=(${globalIds.join(",")});start<${end};end>${start};state==CONFIRMED`;
}

function parameters(rows: readonly CalendarAvailabilityRow[], envelope: { start: string; end: string }, page: number) {
  return {
    where: bookingWhere(
      rows.map((row) => row.globalId),
      envelope.start,
      envelope.end,
    ),
    sort: "start,id",
    page,
    limit: 100,
    totals: true,
    depth: 1,
    fields: { bookings: BOOKING_READ_FIELDS, instruments: "id,globalId,name,deleted" },
  };
}
async function fetchPage(
  rows: readonly CalendarAvailabilityRow[],
  envelope: { start: string; end: string },
  page: number,
  signal: AbortSignal,
) {
  const result = await listBookings(parameters(rows, envelope, page), { signal });
  return {
    ...result,
    docs: result.docs.map(mapBooking),
    totalDocs: required(result.totalDocs, "totalDocs"),
    totalPages: Math.ceil(required(result.totalDocs, "totalDocs") / result.limit),
  };
}

type AvailabilityRowInterval = {
  row: CalendarAvailabilityRow;
  interval: AbsoluteDisplayInterval;
};

async function loadBookings(
  rows: readonly CalendarAvailabilityRow[],
  envelope: { start: string; end: string },
  signal: AbortSignal,
): Promise<Booking[]> {
  const first = await fetchPage(rows, envelope, 1, signal);
  if (first.totalDocs > 1000) throw new Error("Calendar availability exceeds 1,000 bookings");
  const bookings: Booking[] = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    bookings.push(...(await fetchPage(rows, envelope, page, signal)).docs);
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
    await loadBookings(rows, availabilityEnvelope(rows, interval), signal),
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
  return useQuery({
    queryKey: [
      ...getListBookingsQueryKey(parameters(rows, availabilityEnvelope(rows, interval), 1)),
      "calendarAvailability",
    ],
    queryFn: ({ signal }) =>
      interval.elapsedMinutes === 0
        ? Promise.resolve([])
        : loadBookings(rows, availabilityEnvelope(rows, interval), signal),
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
