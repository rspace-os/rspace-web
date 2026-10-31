import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { type BookingListDocument, BookingListDocumentSchema } from "@/modules/booking/domain/booking";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { navigationProgressQueryMeta } from "@/modules/common/queries/navigationProgress";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";

const PageSchema = v.object({
  docs: v.array(BookingListDocumentSchema),
  totalDocs: v.number(),
  totalPages: v.number(),
});

export const CALENDAR_BOOKING_FIELDS =
  "id,version,target,requesterId,canViewConfiguration,timezone,start,end,state,kind,purpose,bookedBy,createdBy,privacy,canEdit,canCancel,createdAt,updatedAt";

type CalendarEventFilterScope = { where?: string; q?: string; mine?: boolean };

async function fetchPage(
  start: string,
  end: string,
  page: number,
  token: string,
  signal: AbortSignal,
  targetGlobalIds?: readonly string[],
  scope: CalendarEventFilterScope = {},
) {
  const targetFilter = targetGlobalIds && targetGlobalIds.length > 0 ? `;target=in=(${targetGlobalIds.join(",")})` : "";
  const parameters = new URLSearchParams({
    where: `start=lt=${end};end=gt=${start}${targetFilter}${scope.where ? `;(${scope.where})` : ""}`,
    start,
    end,
    sort: "start,id",
    page: String(page),
    limit: "100",
    depth: "2",
    "fields[bookings]": CALENDAR_BOOKING_FIELDS,
    "fields[instruments]": "id,globalId,name,deleted,parentContainer",
    "fields[containers]": "id,globalId,name",
  });
  if (scope.q?.trim()) parameters.set("q", scope.q.trim());
  if (scope.mine) parameters.set("mine", "true");
  const response = await fetchWithUiToken(`/api/v2/booking-calendar/events?${parameters}`, {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
    signal,
  });
  if (!response.ok) throw new Error(`Booking calendar request failed (${response.status})`);
  return parseOrThrow(PageSchema, await response.json());
}

export async function loadCalendarEvents({
  start,
  end,
  token,
  signal,
  targetGlobalIds,
  scope = {},
}: {
  start: string;
  end: string;
  token: string;
  signal: AbortSignal;
  /** Omit for all authorized targets; [] means no matching targets and skips the request. */
  targetGlobalIds?: readonly string[];
  scope?: CalendarEventFilterScope;
}): Promise<readonly BookingListDocument[]> {
  if (targetGlobalIds?.length === 0) return [];
  const first = await fetchPage(start, end, 1, token, signal, targetGlobalIds, scope);
  if (first.totalDocs > 1_000) throw new Error("Booking calendar exceeds 1,000 bookings");
  const events: BookingListDocument[] = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    events.push(...(await fetchPage(start, end, page, token, signal, targetGlobalIds, scope)).docs);
  }
  return events;
}

export function useCalendarEvents({
  firstDate,
  lastDate,
  timezone,
  token,
  targetGlobalIds,
  enabled = true,
  authScope = token,
  scope = {},
}: {
  firstDate: string;
  lastDate: string;
  timezone: string;
  token: string;
  /** Omit for all authorized targets; [] means no matching targets and skips the request. */
  targetGlobalIds?: readonly string[];
  enabled?: boolean;
  /** Cache identity for the authenticated caller; keep distinct across callers because event visibility differs. */
  authScope?: string | number;
  scope?: CalendarEventFilterScope;
}) {
  const start = zonedDayBounds(firstDate, timezone).start;
  const end = zonedDayBounds(lastDate, timezone).end;
  return useQuery({
    queryKey: [
      "api-v2",
      "bookings",
      "calendar-events",
      start,
      end,
      targetGlobalIds?.join(",") ?? "all",
      authScope,
      scope.where,
      scope.q,
      Boolean(scope.mine),
    ],
    queryFn: ({ signal }) => loadCalendarEvents({ start, end, token, signal, targetGlobalIds, scope }),
    enabled: enabled && token.length > 0,
    staleTime: 30_000,
    meta: navigationProgressQueryMeta,
  });
}
