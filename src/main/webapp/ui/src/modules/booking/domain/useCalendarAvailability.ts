import { queryOptions, useQuery } from "@tanstack/react-query";
import type { CalendarAvailabilityRow } from "@/modules/booking/domain/availability";
import { bookingKeys } from "@/modules/booking/domain/bookingKeys";
import { type AbsoluteDisplayInterval, displayInterval } from "@/modules/booking/domain/bookingTime";
import { navigationProgressQueryMeta } from "@/modules/common/queries/navigationProgress";
import { useQueryIdentityScope } from "@/modules/common/queries/queryIdentityScope";
import { availabilityEnvelope, rowAvailabilityIntervals, type SourcedAvailabilityInterval } from "./availability";
import type { Booking } from "./booking";
import { fetchDayBookings } from "./fetchDayBookings";

export function useCalendarAvailability(
  rows: readonly CalendarAvailabilityRow[],
  intervalOrDate: AbsoluteDisplayInterval | string,
  token: string,
  authScope?: string | number,
) {
  const scope = useQueryIdentityScope(authScope);
  const interval =
    typeof intervalOrDate === "string"
      ? displayInterval(intervalOrDate, rows[0]?.timezone ?? "UTC", "00:00", "24:00")
      : intervalOrDate;
  return useQuery({
    ...calendarAvailabilityQuery(rows, interval, token, scope),
    enabled: rows.length > 0 && token.length > 0,
  });
}

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
  const bookingsByTarget = new Map<string, Booking[]>();
  for (const booking of bookings) {
    if (booking.target === null) continue;
    const targetBookings = bookingsByTarget.get(booking.target.globalId);
    if (targetBookings) targetBookings.push(booking);
    else bookingsByTarget.set(booking.target.globalId, [booking]);
  }
  return new Map(
    rowIntervals.map(({ row, interval }) => [
      row.globalId,
      rowAvailabilityIntervals(row, interval, bookingsByTarget.get(row.globalId) ?? []),
    ]),
  );
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

export const calendarAvailabilityQuery = (
  rows: readonly CalendarAvailabilityRow[],
  interval: AbsoluteDisplayInterval,
  token: string,
  scope: unknown,
) =>
  queryOptions({
    queryKey: [
      ...bookingKeys.calendarAvailability(),
      scope,
      rows
        .map((row) => [
          row.globalId,
          row.timezone,
          JSON.stringify(row.openingHours),
          row.bufferBeforeMinutes,
          row.bufferAfterMinutes,
          row.allowDoubleBooking,
        ])
        .toSorted(),
      interval.start,
      interval.end,
    ] as const,
    queryFn: ({ signal }) => loadCalendarAvailability(rows, interval, token, signal),
    staleTime: 30_000,
    meta: navigationProgressQueryMeta,
  });
