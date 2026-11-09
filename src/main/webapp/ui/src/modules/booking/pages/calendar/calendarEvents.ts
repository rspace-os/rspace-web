import { queryOptions, useQuery } from "@tanstack/react-query";
import { type BookingListDocument, mapBookingListDocument } from "@/modules/booking/domain/booking";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { apiV2Fields, assertApiV2Selected } from "@/modules/common/api/v2/apiV2Selected";
import {
  getSearchCalendarEventsQueryKey,
  searchCalendarEvents,
} from "@/modules/common/api/v2/generated/booking-calendar/booking-calendar";
import type { BookingsRead } from "@/modules/common/api/v2/generated/models/bookingsRead";
import type { SearchCalendarEventsParams } from "@/modules/common/api/v2/generated/models/searchCalendarEventsParams";
import { navigationProgressQueryMeta } from "@/modules/common/queries/navigationProgress";

export const CALENDAR_BOOKING_FIELDS = [
  "id",
  "version",
  "target",
  "requester",
  "canViewConfiguration",
  "timezone",
  "start",
  "end",
  "state",
  "kind",
  "purpose",
  "bookedBy",
  "createdBy",
  "privacy",
  "canEdit",
  "canCancel",
  "createdAt",
  "updatedAt",
] as const;
export type CalendarEventFilterScope = Pick<SearchCalendarEventsParams, "where" | "q">;
type CalendarEventRequest = Pick<SearchCalendarEventsParams, "start" | "end"> & {
  signal: AbortSignal;
  targetGlobalIds?: readonly string[];
  scope?: CalendarEventFilterScope;
};

function parameters(input: Omit<CalendarEventRequest, "signal">, page = 1) {
  const targetFilter = input.targetGlobalIds?.length ? `;target=in=(${input.targetGlobalIds.join(",")})` : "";
  return {
    start: input.start,
    end: input.end,
    where: `start=lt=${input.end};end=gt=${input.start}${targetFilter}${input.scope?.where ? `;(${input.scope.where})` : ""}`,
    q: input.scope?.q?.trim() || undefined,
    mine: false,
    page,
    limit: 100,
    fields: {
      bookings: apiV2Fields(CALENDAR_BOOKING_FIELDS),
      instruments: "id,globalId,name,deleted,parentContainer",
      containers: "id,globalId,name",
    },
  };
}

/** Asserts a calendar event's guaranteed fields at the fetch boundary, then maps it. */
function mapEvent(document: BookingsRead): BookingListDocument {
  assertApiV2Selected("bookings", document, CALENDAR_BOOKING_FIELDS);
  return mapBookingListDocument(document);
}

export async function loadCalendarEvents(input: CalendarEventRequest): Promise<readonly BookingListDocument[]> {
  if (input.targetGlobalIds?.length === 0) return [];
  const first = await searchCalendarEvents(parameters(input), { signal: input.signal });
  if (first.totalDocs > 1_000) throw new Error("Booking calendar exceeds 1,000 bookings");
  if (first.totalIsLowerBound) throw new Error("Booking calendar requires an exact total");
  const events = first.docs.map(mapEvent);
  const totalPages = Math.ceil(first.totalDocs / first.limit);
  for (let page = 2; page <= totalPages; page += 1) {
    const next = await searchCalendarEvents(parameters(input, page), { signal: input.signal });
    events.push(...next.docs.map(mapEvent));
  }
  return events;
}
export function calendarEventsQuery(input: Omit<CalendarEventRequest, "signal">) {
  return queryOptions({
    queryKey: [
      ...getSearchCalendarEventsQueryKey(parameters(input)),
      "calendarEvents",
      ...(input.targetGlobalIds?.length === 0 ? ["emptyTargets"] : []),
    ],
    queryFn: ({ signal }) => loadCalendarEvents({ ...input, signal }),
    staleTime: 30_000,
    meta: navigationProgressQueryMeta,
  });
}
export function useCalendarEvents({
  firstDate,
  lastDate,
  timezone,
  targetGlobalIds,
  enabled = true,
  scope = {},
}: {
  firstDate: string;
  lastDate: string;
  timezone: string;
  targetGlobalIds?: readonly string[];
  enabled?: boolean;
  scope?: CalendarEventFilterScope;
}) {
  const start = zonedDayBounds(firstDate, timezone).start;
  const end = zonedDayBounds(lastDate, timezone).end;
  // Plain useQuery: the calendar keeps its grid and known rows mounted while events load.
  return useQuery({ ...calendarEventsQuery({ start, end, targetGlobalIds, scope }), enabled });
}
