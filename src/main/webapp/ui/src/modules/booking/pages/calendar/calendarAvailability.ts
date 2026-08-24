import { Temporal } from "@js-temporal/polyfill";
import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import type { AvailabilityInterval } from "@/modules/booking/domain/availability";
import { type BookingSummary, BookingSummarySchema } from "@/modules/booking/domain/booking";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
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
    "fields[bookings]": "id,target,timezone,start,end,state",
  });
  const response = await fetch(`/api/v2/bookings?${parameters}`, {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
    signal,
  });
  if (!response.ok) throw new Error(`Booking availability request failed (${response.status})`);
  return v.parse(PageSchema, await response.json());
}

function availabilityEnvelope(rows: readonly DatedCalendarAvailabilityRow[]) {
  const bounds = rows.map((row) => zonedDayBounds(row.date, row.timezone));
  const before = Math.max(...rows.map((row) => row.bufferBeforeMinutes));
  const after = Math.max(...rows.map((row) => row.bufferAfterMinutes));
  return {
    start: Temporal.Instant.from(
      bounds.reduce((value, bound) => (bound.start < value ? bound.start : value), bounds[0].start),
    )
      .subtract({ minutes: after })
      .toString(),
    end: Temporal.Instant.from(bounds.reduce((value, bound) => (bound.end > value ? bound.end : value), bounds[0].end))
      .add({ minutes: before })
      .toString(),
  };
}

function closedIntervals(row: DatedCalendarAvailabilityRow): AvailabilityInterval[] {
  if (row.openingStart === "00:00" && row.openingEnd === "24:00") return [];
  const date = Temporal.PlainDate.from(row.date);
  const day = zonedDayBounds(row.date, row.timezone);
  const openingStart = date
    .toZonedDateTime({ timeZone: row.timezone, plainTime: row.openingStart })
    .toInstant()
    .toString();
  const openingEnd = date.toZonedDateTime({ timeZone: row.timezone, plainTime: row.openingEnd }).toInstant().toString();
  return [
    { kind: "blockout" as const, startsAt: new Date(day.start), endsAt: new Date(openingStart) },
    { kind: "blockout" as const, startsAt: new Date(openingEnd), endsAt: new Date(day.end) },
  ].filter((interval) => interval.endsAt > interval.startsAt);
}

export async function loadDatedCalendarAvailability(
  rows: readonly DatedCalendarAvailabilityRow[],
  token: string,
  signal: AbortSignal,
): Promise<ReadonlyMap<string, readonly AvailabilityInterval[]>> {
  if (rows.length === 0) return new Map();
  const envelope = availabilityEnvelope(rows);
  const first = await fetchPage(rows, envelope, 1, token, signal);
  if (first.totalDocs > 1000) throw new Error("Calendar availability exceeds 1,000 bookings");
  const bookings: BookingSummary[] = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    bookings.push(...(await fetchPage(rows, envelope, page, token, signal)).docs);
  }
  const result = new Map<string, AvailabilityInterval[]>();
  for (const row of rows) result.set(row.globalId, closedIntervals(row));
  const rowById = new Map(rows.map((row) => [row.globalId, row]));
  for (const booking of bookings) {
    if (booking.state !== "CONFIRMED") continue;
    const row = rowById.get(booking.target.globalId);
    if (!row) continue;
    if (row.allowDoubleBooking) continue;
    const bounds = zonedDayBounds(row.date, row.timezone);
    const start = Math.max(Date.parse(booking.start) - row.bufferBeforeMinutes * 60_000, Date.parse(bounds.start));
    const end = Math.min(Date.parse(booking.end) + row.bufferAfterMinutes * 60_000, Date.parse(bounds.end));
    if (end <= start) continue;
    result.get(row.globalId)?.push({ kind: "booking", startsAt: new Date(start), endsAt: new Date(end) });
  }
  return result;
}

export function loadCalendarAvailability(
  rows: readonly CalendarAvailabilityRow[],
  date: string,
  token: string,
  signal: AbortSignal,
) {
  return loadDatedCalendarAvailability(
    rows.map((row) => ({ ...row, date })),
    token,
    signal,
  );
}

export function useCalendarAvailability(rows: readonly CalendarAvailabilityRow[], date: string, token: string) {
  const datedRows = rows.map((row) => ({ ...row, date }));
  const sortedRows = datedRows
    .map((row) => [
      row.globalId,
      row.date,
      row.timezone,
      row.openingStart,
      row.openingEnd,
      row.bufferBeforeMinutes,
      row.bufferAfterMinutes,
      row.allowDoubleBooking,
    ])
    .toSorted();
  const envelope = datedRows.length === 0 ? null : availabilityEnvelope(datedRows);
  return useQuery({
    queryKey: ["api-v2", "bookings", "calendar-availability", sortedRows, envelope?.start, envelope?.end],
    queryFn: ({ signal }) => loadDatedCalendarAvailability(datedRows, token, signal),
    enabled: rows.length > 0 && token.length > 0,
    meta: viewTransitionQueryMeta,
  });
}
