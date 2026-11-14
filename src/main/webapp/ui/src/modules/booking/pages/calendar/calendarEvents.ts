import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { type BookingListDocument, mapBookingListDocument } from "@/modules/booking/domain/booking";
import { requireBookingValue as required } from "@/modules/booking/domain/bookingMapping";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import {
  getSearchCalendarEventsQueryKey,
  getSearchCalendarEventsQueryOptions,
  searchCalendarEvents,
} from "@/modules/common/api/v2/generated/booking-calendar/booking-calendar";
import type { ApiV2ListResultMapStringObject } from "@/modules/common/api/v2/generated/models/apiV2ListResultMapStringObject";
import type { SearchCalendarEventsParams } from "@/modules/common/api/v2/generated/models/searchCalendarEventsParams";
import { BookingsReadSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { navigationProgressQueryMeta } from "@/modules/common/queries/navigationProgress";

export const CALENDAR_BOOKING_FIELDS =
  "id,version,target,location,requester,canViewConfiguration,timezone,start,end,state,kind,purpose,bookedBy,createdBy,privacy,canEdit,canCancel,createdAt,updatedAt";
export type CalendarEventFilterScope = Partial<Pick<SearchCalendarEventsParams, "where" | "q" | "mine">>;
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
    mine: input.scope?.mine ?? false,
    page,
    limit: 100,
    fields: {
      bookings: CALENDAR_BOOKING_FIELDS,
      instruments: "id,globalId,name,deleted,parentContainer",
      containers: "id,globalId,name",
    },
  };
}

async function loadPages(input: CalendarEventRequest): Promise<ApiV2ListResultMapStringObject> {
  if (input.targetGlobalIds?.length === 0) return { docs: [], totalDocs: 0, page: 1, limit: 100, hasNextPage: false };
  const first = await searchCalendarEvents(parameters(input), { signal: input.signal });
  if (required(first.totalDocs, "totalDocs") > 1_000) throw new Error("Booking calendar exceeds 1,000 bookings");
  const events = [...required(first.docs, "docs")];
  if (first.totalIsLowerBound) throw new Error("Booking calendar requires an exact total");
  const totalPages = Math.ceil(required(first.totalDocs, "totalDocs") / required(first.limit, "limit"));
  for (let page = 2; page <= totalPages; page += 1) {
    events.push(
      ...required((await searchCalendarEvents(parameters(input, page), { signal: input.signal })).docs, "docs"),
    );
  }
  return { ...first, docs: events };
}

function mapEvents(response: ApiV2ListResultMapStringObject): BookingListDocument[] {
  return required(response.docs, "docs").map((document) => {
    v.assert(BookingsReadSchema, document);
    return mapBookingListDocument(document);
  });
}

export async function loadCalendarEvents(input: CalendarEventRequest): Promise<readonly BookingListDocument[]> {
  return mapEvents(await loadPages(input));
}
export function calendarEventsQuery(input: Omit<CalendarEventRequest, "signal">, _scope: unknown) {
  const params = parameters(input);
  return getSearchCalendarEventsQueryOptions<BookingListDocument[]>(params, {
    query: {
      queryKey: [
        ...getSearchCalendarEventsQueryKey(params),
        "calendarEvents",
        ...(input.targetGlobalIds?.length === 0 ? ["emptyTargets"] : []),
      ],
      queryFn: ({ signal }) => loadPages({ ...input, signal }),
      select: mapEvents,
      staleTime: 30_000,
      meta: navigationProgressQueryMeta,
    },
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
  authScope?: string | number;
  scope?: CalendarEventFilterScope;
}) {
  const start = zonedDayBounds(firstDate, timezone).start;
  const end = zonedDayBounds(lastDate, timezone).end;
  return useQuery({ ...calendarEventsQuery({ start, end, targetGlobalIds, scope }, undefined), enabled });
}
