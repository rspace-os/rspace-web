import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import type { DayTimelineEvent } from "@/modules/booking/components/DayTimeline";
import { formatAgendaPeriod, sliceAcrossWallClockDay, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { viewTransitionQueryMeta } from "@/modules/common/queries/viewTransition";

export type CalendarAgendaEvent = {
  id: number;
  privacy: "full" | "busy";
  period: string;
  bookedBy?: string;
  purpose?: string;
  canEdit: boolean;
};

const CalendarBookingIdentitySchema = {
  id: v.number(),
  target: v.object({
    relationTo: v.literal("instruments"),
    value: v.object({ id: v.number(), name: v.string(), deleted: v.boolean() }),
    globalId: v.string(),
  }),
  timezone: v.string(),
  start: v.string(),
  end: v.string(),
  state: v.picklist(["CONFIRMED", "CANCELLED"]),
};

const CalendarBookingSchema = v.variant("privacy", [
  v.object({
    ...CalendarBookingIdentitySchema,
    privacy: v.literal("full"),
    purpose: v.nullable(v.string()),
    bookedBy: v.string(),
    canEdit: v.boolean(),
  }),
  v.object({
    ...CalendarBookingIdentitySchema,
    privacy: v.literal("busy"),
    purpose: v.null(),
    bookedBy: v.null(),
    canEdit: v.literal(false),
  }),
]);

type CalendarBooking = v.InferOutput<typeof CalendarBookingSchema>;

const PageSchema = v.object({
  docs: v.array(CalendarBookingSchema),
  totalDocs: v.number(),
  totalPages: v.number(),
});

async function loadDetailPage(
  target: string,
  start: string,
  end: string,
  page: number,
  token: string,
  signal: AbortSignal,
) {
  const parameters = new URLSearchParams({
    where: `target==${target};start<${end};end>${start};state==CONFIRMED`,
    sort: "start,id",
    page: String(page),
    limit: "100",
    depth: "1",
    "fields[bookings]": "id,target,timezone,start,end,state,privacy,purpose,bookedBy,canEdit",
  });
  const response = await fetch(`/api/v2/bookings?${parameters}`, {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
    signal,
  });
  if (!response.ok) throw new Error(`Booking detail request failed (${response.status})`);
  return v.parse(PageSchema, await response.json());
}

export async function loadCalendarDetail(
  target: string,
  date: string,
  timezone: string,
  token: string,
  signal: AbortSignal,
): Promise<readonly CalendarBooking[]> {
  const { start, end } = zonedDayBounds(date, timezone);
  const first = await loadDetailPage(target, start, end, 1, token, signal);
  const bookings: CalendarBooking[] = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    bookings.push(...(await loadDetailPage(target, start, end, page, token, signal)).docs);
  }
  if (bookings.some((booking) => booking.timezone !== timezone)) {
    throw new Error("Booking timezone differs from its configuration timezone");
  }
  return bookings;
}

export function adaptCalendarDetail(
  bookings: readonly CalendarBooking[],
  date: string,
  timezone: string,
): { timeline: readonly DayTimelineEvent[]; agenda: readonly CalendarAgendaEvent[] } {
  const timeline = bookings.map((booking): DayTimelineEvent => {
    const slice = sliceAcrossWallClockDay(booking.start, booking.end, date, timezone);
    if (booking.privacy === "busy") {
      return { id: String(booking.id), kind: "booking", privacy: "busy", ...slice };
    }
    return {
      id: String(booking.id),
      kind: "booking",
      privacy: "full",
      bookedBy: booking.bookedBy,
      canEdit: booking.canEdit,
      notes: booking.purpose ?? undefined,
      ...slice,
    };
  });
  const agenda = bookings.map((booking): CalendarAgendaEvent => {
    if (booking.privacy === "busy") {
      return {
        id: booking.id,
        privacy: "busy",
        period: formatAgendaPeriod(booking.start, booking.end, timezone),
        canEdit: false,
      };
    }
    return {
      id: booking.id,
      privacy: "full",
      period: formatAgendaPeriod(booking.start, booking.end, timezone),
      bookedBy: booking.bookedBy,
      purpose: booking.purpose ?? undefined,
      canEdit: booking.canEdit,
    };
  });
  return { timeline, agenda };
}

export function useCalendarDetail(
  target: string | undefined,
  date: string,
  timezone: string | undefined,
  token: string,
) {
  const bounds = timezone ? zonedDayBounds(date, timezone) : undefined;
  return useQuery({
    queryKey: ["api-v2", "bookings", "calendar-detail", target, date, bounds?.start, bounds?.end],
    queryFn: ({ signal }) => loadCalendarDetail(target ?? "", date, timezone ?? "UTC", token, signal),
    enabled: Boolean(target && timezone && token),
    meta: viewTransitionQueryMeta,
  });
}
