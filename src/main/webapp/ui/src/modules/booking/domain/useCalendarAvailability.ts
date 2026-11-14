import { useQuery } from "@tanstack/react-query";
import {
  availabilityEnvelope,
  bookingInterval,
  type CalendarAvailabilityRow,
  closedIntervals,
  type SourcedAvailabilityInterval,
} from "@/modules/booking/domain/availability";
import { BOOKING_READ_FIELDS, type Booking } from "@/modules/booking/domain/booking";
import { type AbsoluteDisplayInterval, displayInterval } from "@/modules/booking/domain/bookingTime";
import type { ErrorType } from "@/modules/common/api/v2/apiV2Fetch";
import { getListBookingsQueryKey } from "@/modules/common/api/v2/generated/bookings/bookings";
import { navigationProgressQueryMeta } from "@/modules/common/queries/navigationProgress";
import { fetchDayBookings } from "./fetchDayBookings";

type AvailabilityRowInterval = {
  row: CalendarAvailabilityRow;
  interval: AbsoluteDisplayInterval;
};

function mapAvailability(rowIntervals: readonly AvailabilityRowInterval[], bookings: readonly Booking[]) {
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
    await fetchDayBookings(
      rows.map((row) => row.globalId),
      availabilityEnvelope(rows, interval),
      signal,
    ),
  );
}

export function useCalendarAvailability(
  rows: readonly CalendarAvailabilityRow[],
  intervalOrDate: AbsoluteDisplayInterval | string,
  _authScope?: string | number,
) {
  const interval =
    typeof intervalOrDate === "string"
      ? displayInterval(intervalOrDate, rows[0]?.timezone ?? "UTC", "00:00", "24:00")
      : intervalOrDate;
  const envelope =
    rows.length > 0 ? availabilityEnvelope(rows, interval) : { start: interval.start, end: interval.end };
  return useQuery<Booking[], ErrorType<unknown>, ReadonlyMap<string, readonly SourcedAvailabilityInterval[]>>({
    queryKey: [
      ...getListBookingsQueryKey({
        where: `target=in=(${[...new Set(rows.map((row) => row.globalId))].sort().join(",")});start=lt=${envelope.end};end=gt=${envelope.start};state==CONFIRMED`,
        sort: "start,id",
        page: 1,
        limit: 100,
        depth: 1,
        fields: { bookings: BOOKING_READ_FIELDS, instruments: "id,globalId,name,deleted" },
      }),
      "calendarAvailability",
    ],
    queryFn: ({ signal }) =>
      interval.elapsedMinutes === 0
        ? Promise.resolve([])
        : fetchDayBookings(
            rows.map((row) => row.globalId),
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
