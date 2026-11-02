import { useQuery } from "@tanstack/react-query";
import {
  availabilityEnvelope,
  bookingInterval,
  type CalendarAvailabilityRow,
  closedIntervals,
  type SourcedAvailabilityInterval,
} from "@/modules/booking/domain/availability";
import { type AbsoluteDisplayInterval, displayInterval } from "@/modules/booking/domain/bookingTime";
import { navigationProgressQueryMeta } from "@/modules/common/queries/navigationProgress";
import { fetchDayBookings } from "./fetchDayBookings";

type AvailabilityRowInterval = {
  row: CalendarAvailabilityRow;
  interval: AbsoluteDisplayInterval;
};

async function loadAvailability(
  rowIntervals: readonly AvailabilityRowInterval[],
  envelope: { start: string; end: string },
  token: string,
  signal: AbortSignal,
): Promise<ReadonlyMap<string, readonly SourcedAvailabilityInterval[]>> {
  const bookings = await fetchDayBookings(
    rowIntervals.map(({ row }) => row.globalId),
    envelope,
    token,
    signal,
  );
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
  token: string,
  signal: AbortSignal,
): Promise<ReadonlyMap<string, readonly SourcedAvailabilityInterval[]>> {
  if (rows.length === 0) return new Map();
  const interval =
    typeof intervalOrDate === "string"
      ? displayInterval(intervalOrDate, rows[0].timezone, "00:00", "24:00")
      : intervalOrDate;
  if (interval.elapsedMinutes === 0) return new Map(rows.map((row) => [row.globalId, []]));
  return loadAvailability(
    rows.map((row) => ({ row, interval })),
    availabilityEnvelope(rows, interval),
    token,
    signal,
  );
}

export function useCalendarAvailability(
  rows: readonly CalendarAvailabilityRow[],
  intervalOrDate: AbsoluteDisplayInterval | string,
  token: string,
  authScope: string | number = token,
) {
  const interval =
    typeof intervalOrDate === "string"
      ? displayInterval(intervalOrDate, rows[0]?.timezone ?? "UTC", "00:00", "24:00")
      : intervalOrDate;
  const sortedRows = rows
    .map((row) => [
      row.globalId,
      row.timezone,
      JSON.stringify(row.openingHours),
      row.bufferBeforeMinutes,
      row.bufferAfterMinutes,
      row.allowDoubleBooking,
    ])
    .toSorted();
  return useQuery({
    queryKey: ["api-v2", "bookings", "calendar-availability", sortedRows, interval.start, interval.end, authScope],
    queryFn: ({ signal }) => loadCalendarAvailability(rows, interval, token, signal),
    enabled: rows.length > 0 && token.length > 0,
    staleTime: 30_000,
    meta: navigationProgressQueryMeta,
  });
}
