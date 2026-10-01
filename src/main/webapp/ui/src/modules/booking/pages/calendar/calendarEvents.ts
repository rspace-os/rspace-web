import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { type BookingListDocument, BookingListDocumentSchema } from "@/modules/booking/domain/booking";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { viewTransitionQueryMeta } from "@/modules/common/queries/viewTransition";

const PageSchema = v.object({
  docs: v.array(BookingListDocumentSchema),
  totalDocs: v.number(),
  totalPages: v.number(),
});

export const CALENDAR_BOOKING_FIELDS =
  "id,version,target,requesterId,canViewConfiguration,timezone,start,end,state,kind,purpose,bookedBy,createdBy,privacy,canEdit,canCancel,createdAt,updatedAt";

async function fetchPage(
  start: string,
  end: string,
  page: number,
  token: string,
  signal: AbortSignal,
  targetGlobalIds?: readonly string[],
) {
  const targetFilter = targetGlobalIds && targetGlobalIds.length > 0 ? `;target=in=(${targetGlobalIds.join(",")})` : "";
  const parameters = new URLSearchParams({
    where: `start<${end};end>${start};state==CONFIRMED${targetFilter}`,
    sort: "start,id",
    page: String(page),
    limit: "100",
    depth: "1",
    "fields[bookings]": CALENDAR_BOOKING_FIELDS,
  });
  const response = await fetch(`/api/v2/bookings?${parameters}`, {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
    signal,
  });
  if (!response.ok) throw new Error(`Booking calendar request failed (${response.status})`);
  return parseOrThrow(PageSchema, await response.json());
}

export async function loadCalendarEvents(
  firstDate: string,
  lastDate: string,
  timezone: string,
  token: string,
  signal: AbortSignal,
  targetGlobalIds?: readonly string[],
): Promise<readonly BookingListDocument[]> {
  const start = zonedDayBounds(firstDate, timezone).start;
  const end = zonedDayBounds(lastDate, timezone).end;
  const first = await fetchPage(start, end, 1, token, signal, targetGlobalIds);
  if (first.totalDocs > 1_000) throw new Error("Booking calendar exceeds 1,000 bookings");
  const events: BookingListDocument[] = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    events.push(...(await fetchPage(start, end, page, token, signal, targetGlobalIds)).docs);
  }
  return events;
}

export function useCalendarEvents(
  firstDate: string,
  lastDate: string,
  timezone: string,
  token: string,
  targetGlobalIds?: readonly string[],
  enabled = true,
) {
  const start = zonedDayBounds(firstDate, timezone).start;
  const end = zonedDayBounds(lastDate, timezone).end;
  return useQuery({
    queryKey: ["api-v2", "bookings", "calendar-events", start, end, targetGlobalIds?.join(",") ?? "all"],
    queryFn: ({ signal }) => loadCalendarEvents(firstDate, lastDate, timezone, token, signal, targetGlobalIds),
    enabled: enabled && token.length > 0,
    staleTime: 30_000,
    meta: viewTransitionQueryMeta,
  });
}
