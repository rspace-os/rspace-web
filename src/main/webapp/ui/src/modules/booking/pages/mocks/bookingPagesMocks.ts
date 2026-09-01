import { HttpResponse, http, type RequestHandler } from "msw";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import {
  bookableItemDetailsHandlers,
  bookableItemsHandlers,
  sampleBookingEvents,
} from "@/modules/booking/pages/bookable-items/mocks/bookableItemsMocks";
import {
  busyBooking,
  collectionResponse,
  currentUser,
  otherBooking,
  ownBooking,
} from "@/modules/booking/pages/calendar/__tests__/calendarTestHarness";
import { CALENDAR_BOOKING_FIELDS } from "@/modules/booking/pages/calendar/calendarEvents";

export const availabilityBookingFields = "id,target,timezone,start,end,state,kind";
export const calendarBookingFields = CALENDAR_BOOKING_FIELDS;

export const bookingPageRequests: {
  collectionQueries: string[];
  bookingRequests: number;
  bookingQuery: URL | undefined;
  calendarBookingRequests: URL[];
  createdPayloads: Array<Record<string, unknown>>;
} = {
  collectionQueries: [],
  bookingRequests: 0,
  bookingQuery: undefined,
  calendarBookingRequests: [],
  createdPayloads: [],
};

const availabilityBookings = [
  ...sampleBookingEvents.map(({ id, target, timezone, start, end, state }) => ({
    id,
    target,
    timezone,
    start,
    end,
    state,
  })),
  {
    id: 47,
    target: sampleBookingEvents[1].target,
    timezone: sampleBookingEvents[1].timezone,
    start: "2026-08-17T12:30:00Z",
    end: "2026-08-17T14:00:00Z",
    state: "CONFIRMED",
  },
] as const;

export function resetBookingPageRequests(): void {
  bookingPageRequests.collectionQueries = [];
  bookingPageRequests.bookingRequests = 0;
  bookingPageRequests.bookingQuery = undefined;
  bookingPageRequests.calendarBookingRequests = [];
  bookingPageRequests.createdPayloads = [];
}

export function bookingPagesHandlers(): RequestHandler[] {
  return [
    oauthTokenHandler(),
    http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
    ...bookableItemDetailsHandlers(),
    ...bookableItemsHandlers((request) => {
      bookingPageRequests.collectionQueries.push(decodeURIComponent(new URL(request.url).search));
    }),
    http.get("/api/v2/bookings", ({ request }) => {
      const url = new URL(request.url);
      const fields = url.searchParams.get("fields[bookings]");
      if (fields === calendarBookingFields) {
        bookingPageRequests.calendarBookingRequests.push(url);
        return HttpResponse.json(collectionResponse([ownBooking, otherBooking, busyBooking]));
      }
      if (fields === availabilityBookingFields) {
        bookingPageRequests.bookingRequests += 1;
        bookingPageRequests.bookingQuery = url;
        return HttpResponse.json({
          docs: availabilityBookings,
          totalDocs: availabilityBookings.length,
          totalPages: 1,
          page: 1,
          hasNextPage: false,
        });
      }
      return undefined;
    }),
    http.post("/api/v2/bookings", async ({ request }) => {
      const payload = (await request.json()) as Record<string, unknown>;
      bookingPageRequests.createdPayloads.push(payload);
      const targetId = (payload.target as { value?: number } | undefined)?.value ?? 123;
      const target = targetId === 124 ? sampleBookingEvents[2].target : sampleBookingEvents[0].target;
      return HttpResponse.json({
        id: 900 + bookingPageRequests.createdPayloads.length,
        version: 0,
        target,
        timezone: "Europe/Berlin",
        start: payload.start,
        end: payload.end,
        state: "CONFIRMED",
        kind: payload.kind,
        purpose: payload.purpose ?? null,
        bookedBy: "Ada Lovelace (ada)",
        createdBy: "Ada Lovelace (ada)",
        privacy: "full",
        canEdit: true,
        canCancel: true,
        createdAt: "2026-08-17T08:00:00Z",
        updatedAt: "2026-08-17T08:00:00Z",
      });
    }),
  ];
}
