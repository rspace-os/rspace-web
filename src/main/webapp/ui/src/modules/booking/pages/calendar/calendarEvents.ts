import { useQuery } from "@tanstack/react-query";
import { type BookingListDocument, mapBookingListDocument } from "@/modules/booking/domain/booking";
import { requireBookingValue as required } from "@/modules/booking/domain/bookingMapping";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import type { ApiV2WhereObject } from "@/modules/common/api/v2/apiV2Where";
import { getListBookingsQueryKey, listBookings } from "@/modules/common/api/v2/generated/bookings/bookings";
import { navigationProgressQueryMeta } from "@/modules/common/queries/navigationProgress";
import { isNonEmpty } from "@/util/ArrayUtils";

export const CALENDAR_BOOKING_FIELDS =
  "id,version,target,requester,canViewConfiguration,timezone,start,end,state,kind,purpose,bookedBy,createdBy,privacy,canEdit,canCancel,createdAt,updatedAt";

function calendarEventsWhere(
  start: string,
  end: string,
  targetGlobalIds?: readonly string[],
): ApiV2WhereObject<"Bookings"> {
  return {
    start: { lessThan: end },
    end: { greaterThan: start },
    state: "CONFIRMED",
    ...(targetGlobalIds && isNonEmpty(targetGlobalIds) ? { target: { in: targetGlobalIds } } : {}),
  };
}

async function fetchPage(
  start: string,
  end: string,
  page: number,

  signal: AbortSignal,
  targetGlobalIds?: readonly string[],
) {
  const result = await listBookings(
    {
      where: calendarEventsWhere(start, end, targetGlobalIds),
      sort: "start,id",
      page,
      limit: 100,
      totals: true,
      depth: 2,
      fields: {
        bookings: CALENDAR_BOOKING_FIELDS,
        instruments: "id,globalId,name,deleted,parentContainer",
        containers: "id,globalId,name",
      },
    },
    { signal },
  );
  if (result.totalIsLowerBound) throw new Error("Booking calendar requires an exact total");
  return {
    ...result,
    docs: result.docs.map(mapBookingListDocument),
    totalDocs: required(result.totalDocs, "totalDocs"),
    totalPages: Math.ceil(required(result.totalDocs, "totalDocs") / result.limit),
  };
}

export async function loadCalendarEvents(
  firstDate: string,
  lastDate: string,
  timezone: string,

  signal: AbortSignal,
  targetGlobalIds?: readonly string[],
): Promise<readonly BookingListDocument[]> {
  if (targetGlobalIds?.length === 0) return [];
  const start = zonedDayBounds(firstDate, timezone).start;
  const end = zonedDayBounds(lastDate, timezone).end;
  const first = await fetchPage(start, end, 1, signal, targetGlobalIds);
  if (first.totalDocs > 1_000) throw new Error("Booking calendar exceeds 1,000 bookings");
  const events: BookingListDocument[] = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    events.push(...(await fetchPage(start, end, page, signal, targetGlobalIds)).docs);
  }
  return events;
}

export function useCalendarEvents(
  firstDate: string,
  lastDate: string,
  timezone: string,

  targetGlobalIds?: readonly string[],
  enabled = true,
) {
  const start = zonedDayBounds(firstDate, timezone).start;
  const end = zonedDayBounds(lastDate, timezone).end;
  // Plain useQuery: the calendar keeps its grid and known rows mounted while events load.
  return useQuery({
    queryKey: [
      ...getListBookingsQueryKey({ where: calendarEventsWhere(start, end, targetGlobalIds) }),
      "calendarEvents",
      ...(targetGlobalIds?.length === 0 ? ["emptyTargets"] : []),
    ],
    queryFn: ({ signal }) => loadCalendarEvents(firstDate, lastDate, timezone, signal, targetGlobalIds),
    enabled: enabled,
    staleTime: 30_000,
    meta: navigationProgressQueryMeta,
  });
}
