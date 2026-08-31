import { Temporal } from "@js-temporal/polyfill";
import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import type { AvailabilityInterval, SourcedAvailabilityInterval } from "@/modules/booking/domain/availability";
import { type BookingSummary, BookingSummarySchema } from "@/modules/booking/domain/booking";
import {
  type AbsoluteDisplayInterval,
  addCalendarDays,
  displayInterval,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import { viewTransitionQueryMeta } from "@/modules/common/queries/viewTransition";

export type CalendarAvailabilityRow = {
  globalId: string;
  timezone: string;
  openingStart: string;
  openingEnd: string;
  bufferBeforeMinutes: number;
  bufferAfterMinutes: number;
  allowDoubleBooking: boolean;
};

export type DatedCalendarAvailabilityRow = CalendarAvailabilityRow & { date: string };

export function calendarAvailabilityRow(row: {
  globalId: string;
  timezone?: string;
  openingStart?: string;
  openingEnd?: string;
  bufferBeforeMinutes?: number;
  bufferAfterMinutes?: number;
  allowDoubleBooking?: boolean;
}): CalendarAvailabilityRow | undefined {
  if (
    row.timezone === undefined ||
    row.openingStart === undefined ||
    row.openingEnd === undefined ||
    row.bufferBeforeMinutes === undefined ||
    row.bufferAfterMinutes === undefined ||
    row.allowDoubleBooking === undefined
  ) {
    return undefined;
  }
  return {
    globalId: row.globalId,
    timezone: row.timezone,
    openingStart: row.openingStart,
    openingEnd: row.openingEnd,
    bufferBeforeMinutes: row.bufferBeforeMinutes,
    bufferAfterMinutes: row.bufferAfterMinutes,
    allowDoubleBooking: row.allowDoubleBooking,
  };
}

const PageSchema = v.object({
  docs: v.array(BookingSummarySchema),
  totalDocs: v.number(),
  totalPages: v.number(),
  page: v.number(),
  hasNextPage: v.boolean(),
});

function bookingWhere(globalIds: readonly string[], start: string, end: string): string {
  return `target=in=(${globalIds.join(",")});start<${end};end>${start};state==CONFIRMED`;
}

async function fetchPage(
  rows: readonly CalendarAvailabilityRow[],
  envelope: { start: string; end: string },
  page: number,
  token: string,
  signal: AbortSignal,
) {
  const parameters = new URLSearchParams({
    where: bookingWhere(
      rows.map((row) => row.globalId),
      envelope.start,
      envelope.end,
    ),
    sort: "start,id",
    page: String(page),
    limit: "100",
    depth: "1",
    "fields[bookings]": "id,target,timezone,start,end,state,kind",
  });
  const response = await fetch(`/api/v2/bookings?${parameters}`, {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
    signal,
  });
  if (!response.ok) throw new Error(`Booking availability request failed (${response.status})`);
  return v.parse(PageSchema, await response.json());
}

function availabilityEnvelope(rows: readonly CalendarAvailabilityRow[], interval: AbsoluteDisplayInterval) {
  const before = Math.max(...rows.map((row) => row.bufferBeforeMinutes));
  const after = Math.max(...rows.map((row) => row.bufferAfterMinutes));
  return {
    start: Temporal.Instant.from(interval.start).subtract({ minutes: after }).toString(),
    end: Temporal.Instant.from(interval.end).add({ minutes: before }).toString(),
  };
}

function schedulingDates(row: CalendarAvailabilityRow, interval: AbsoluteDisplayInterval): string[] {
  const first = Temporal.Instant.from(interval.start).toZonedDateTimeISO(row.timezone).toPlainDate().toString();
  const last = Temporal.Instant.from(interval.end)
    .subtract({ nanoseconds: 1 })
    .toZonedDateTimeISO(row.timezone)
    .toPlainDate()
    .toString();
  const dates = [first];
  while (dates.at(-1) !== last) dates.push(addCalendarDays(dates.at(-1) ?? first, 1));
  return dates;
}

function clipInterval<T extends AvailabilityInterval>(candidate: T, interval: AbsoluteDisplayInterval): T | undefined {
  const start = Math.max(candidate.startsAt.getTime(), Date.parse(interval.start));
  const end = Math.min(candidate.endsAt.getTime(), Date.parse(interval.end));
  return end > start ? { ...candidate, startsAt: new Date(start), endsAt: new Date(end) } : undefined;
}

function sourcedInterval(
  kind: AvailabilityInterval["kind"],
  id: string,
  startsAt: string | number,
  endsAt: string | number,
): SourcedAvailabilityInterval {
  const sourceStartsAt = new Date(startsAt);
  const sourceEndsAt = new Date(endsAt);
  return {
    kind,
    startsAt: sourceStartsAt,
    endsAt: sourceEndsAt,
    source: { id, startsAt: sourceStartsAt, endsAt: sourceEndsAt },
  };
}

function closedInterval(
  row: CalendarAvailabilityRow,
  startsAt: string,
  endsAt: string,
  interval: AbsoluteDisplayInterval,
): SourcedAvailabilityInterval | undefined {
  const candidate = sourcedInterval(
    "blockout",
    `opening-hours:${row.globalId}:${new Date(startsAt).toISOString()}:${new Date(endsAt).toISOString()}`,
    startsAt,
    endsAt,
  );
  return clipInterval(candidate, interval);
}

function closedIntervals(
  row: CalendarAvailabilityRow,
  interval: AbsoluteDisplayInterval,
): SourcedAvailabilityInterval[] {
  if (row.openingStart === "00:00" && row.openingEnd === "24:00") return [];
  return schedulingDates(row, interval).flatMap((dateValue) => {
    const date = Temporal.PlainDate.from(dateValue);
    const day = zonedDayBounds(dateValue, row.timezone);
    const openingStart = date
      .toZonedDateTime({ timeZone: row.timezone, plainTime: row.openingStart })
      .toInstant()
      .toString();
    const openingEnd =
      row.openingEnd === "24:00"
        ? day.end
        : date.toZonedDateTime({ timeZone: row.timezone, plainTime: row.openingEnd }).toInstant().toString();
    return [
      closedInterval(row, day.start, openingStart, interval),
      closedInterval(row, openingEnd, day.end, interval),
    ].filter((value): value is SourcedAvailabilityInterval => value !== undefined);
  });
}

function bookingInterval(
  booking: BookingSummary,
  row: CalendarAvailabilityRow,
  interval: AbsoluteDisplayInterval,
): SourcedAvailabilityInterval | undefined {
  const candidate = sourcedInterval("booking", `booking:${booking.id}`, booking.start, booking.end);
  return clipInterval(
    {
      ...candidate,
      startsAt: new Date(candidate.startsAt.getTime() - row.bufferBeforeMinutes * 60_000),
      endsAt: new Date(candidate.endsAt.getTime() + row.bufferAfterMinutes * 60_000),
    },
    interval,
  );
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
  const rows = rowIntervals.map(({ row }) => row);
  const first = await fetchPage(rows, envelope, 1, token, signal);
  if (first.totalDocs > 1000) throw new Error("Calendar availability exceeds 1,000 bookings");
  const bookings: BookingSummary[] = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    bookings.push(...(await fetchPage(rows, envelope, page, token, signal)).docs);
  }
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
  token: string,
  signal: AbortSignal,
): Promise<ReadonlyMap<string, readonly SourcedAvailabilityInterval[]>> {
  if (rows.length === 0) return new Map();
  const interval =
    typeof intervalOrDate === "string"
      ? displayInterval(intervalOrDate, rows[0].timezone, "00:00", "24:00")
      : intervalOrDate;
  return loadAvailability(
    rows.map((row) => ({ row, interval })),
    availabilityEnvelope(rows, interval),
    token,
    signal,
  );
}

export async function loadDatedCalendarAvailability(
  rows: readonly DatedCalendarAvailabilityRow[],
  token: string,
  signal: AbortSignal,
): Promise<ReadonlyMap<string, readonly SourcedAvailabilityInterval[]>> {
  if (rows.length === 0) return new Map();
  const bounds = rows.map((row) => zonedDayBounds(row.date, row.timezone));
  const before = Math.max(...rows.map((row) => row.bufferBeforeMinutes));
  const after = Math.max(...rows.map((row) => row.bufferAfterMinutes));
  const envelope = {
    start: Temporal.Instant.from(
      bounds.reduce((value, bound) => (bound.start < value ? bound.start : value), bounds[0].start),
    )
      .subtract({ minutes: after })
      .toString(),
    end: Temporal.Instant.from(bounds.reduce((value, bound) => (bound.end > value ? bound.end : value), bounds[0].end))
      .add({ minutes: before })
      .toString(),
  };
  return loadAvailability(
    rows.map((row) => ({ row, interval: displayInterval(row.date, row.timezone, "00:00", "24:00") })),
    envelope,
    token,
    signal,
  );
}

export function useCalendarAvailability(
  rows: readonly CalendarAvailabilityRow[],
  intervalOrDate: AbsoluteDisplayInterval | string,
  token: string,
) {
  const interval =
    typeof intervalOrDate === "string"
      ? displayInterval(intervalOrDate, rows[0]?.timezone ?? "UTC", "00:00", "24:00")
      : intervalOrDate;
  const sortedRows = rows
    .map((row) => [
      row.globalId,
      row.timezone,
      row.openingStart,
      row.openingEnd,
      row.bufferBeforeMinutes,
      row.bufferAfterMinutes,
      row.allowDoubleBooking,
    ])
    .toSorted();
  return useQuery({
    queryKey: ["api-v2", "bookings", "calendar-availability", sortedRows, interval.start, interval.end],
    queryFn: ({ signal }) => loadCalendarAvailability(rows, interval, token, signal),
    enabled: rows.length > 0 && token.length > 0,
    staleTime: 30_000,
    meta: viewTransitionQueryMeta,
  });
}
