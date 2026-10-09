import { HttpResponse, http, type RequestHandler } from "msw";
import * as v from "valibot";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { classifyCurrentDayAvailability } from "@/modules/booking/domain/availability";
import { BOOKING_READ_FIELDS, BookingSchema } from "@/modules/booking/domain/booking";
import { type BookingCatalogueItem, BookingCataloguePageSchema } from "@/modules/booking/domain/bookingCatalogue";
import {
  bookableItemDetailsHandlers,
  bookableItemsHandlers,
  bookableItemsOpenApi,
  sampleBookingEvents,
} from "@/modules/booking/pages/bookable-items/mocks/bookableItemsMocks";
import {
  busyBooking,
  collectionResponse,
  currentUser,
  otherBooking,
  ownBooking,
} from "@/modules/booking/pages/calendar/__tests__/calendarTestHarness";
import {
  calendarAvailabilityRow,
  rowAvailabilityIntervals,
} from "@/modules/booking/pages/calendar/calendarAvailability";
import { CALENDAR_BOOKING_FIELDS } from "@/modules/booking/pages/calendar/calendarEvents";
import { bookingsOpenApi } from "../my-bookings/mocks/bookingMocks";

export const availabilityBookingFields = BOOKING_READ_FIELDS;
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
  ...sampleBookingEvents,
  ...Array.from({ length: 10 }, (_, index) => ({
    ...sampleBookingEvents[0],
    id: 47 + index,
    start: "2026-08-17T12:30:00Z",
    end: "2026-08-17T14:00:00Z",
    purpose: `Concurrent booking ${index + 1}`,
  })),
  {
    ...sampleBookingEvents[0],
    id: 57,
    start: "2026-08-17T10:00:00Z",
    end: "2026-08-17T11:00:00Z",
    purpose: "Laser maintenance",
    bookedBy: null,
  },
].map((booking) => ({
  ...booking,
  kind: booking.id === 57 ? "MAINTENANCE" : "BOOKING",
  canEdit: booking.privacy === "full",
  canCancel: booking.privacy === "full",
  createdAt: "2026-08-01T09:00:00Z",
  updatedAt: "2026-08-01T09:00:00Z",
}));

const parsedAvailabilityBookings = v.parse(v.array(BookingSchema), availabilityBookings);

/** The server's quick-filter category for one catalogue item, from the client's own interval helpers. */
function catalogueAvailability(item: BookingCatalogueItem, url: URL) {
  const start = url.searchParams.get("availabilityStart") ?? "";
  const end = url.searchParams.get("availabilityEnd") ?? "";
  const row = calendarAvailabilityRow(item);
  if (!row) return "unavailable-today";
  const bounds = {
    date: start.slice(0, 10),
    timeZone: "UTC",
    start,
    end,
    elapsedMinutes: (Date.parse(end) - Date.parse(start)) / 60_000,
  };
  return classifyCurrentDayAvailability(
    rowAvailabilityIntervals(row, bounds, parsedAvailabilityBookings),
    new Date(start),
    new Date(end),
    new Date(url.searchParams.get("now") ?? start),
  );
}

/** Every item the plain catalogue handler matches for the request's item filters. */
async function catalogueCandidates(url: URL): Promise<BookingCatalogueItem[]> {
  const candidates = new URL(url);
  candidates.pathname = "/api/v2/booking-catalogue";
  for (const name of ["availability", "availabilityStart", "availabilityEnd", "now"]) {
    candidates.searchParams.delete(name);
  }
  candidates.searchParams.set("page", "1");
  candidates.searchParams.set("limit", "100");
  return v.parse(BookingCataloguePageSchema, await (await fetch(candidates)).json()).items;
}

export function resetBookingPageRequests(): void {
  bookingPageRequests.collectionQueries = [];
  bookingPageRequests.bookingRequests = 0;
  bookingPageRequests.bookingQuery = undefined;
  bookingPageRequests.calendarBookingRequests = [];
  bookingPageRequests.createdPayloads = [];
}

export function bookingPagesHandlers(): RequestHandler[] {
  return [
    http.get("/api/v2/openapi.json", () =>
      HttpResponse.json({
        ...bookableItemsOpenApi,
        paths: { ...bookableItemsOpenApi.paths, ...bookingsOpenApi.paths },
      }),
    ),
    oauthTokenHandler(true),
    http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
    // The server classifies availability; these apply it to the plain catalogue handler's matches.
    http.get("/api/v2/booking-catalogue/availability-counts", async ({ request }) => {
      const url = new URL(request.url);
      const categories = (await catalogueCandidates(url)).map((item) => catalogueAvailability(item, url));
      return HttpResponse.json({
        availableNow: categories.filter((category) => category === "available-now").length,
        freeLaterToday: categories.filter((category) => category === "free-later-today").length,
      });
    }),
    http.get("/api/v2/booking-catalogue", async ({ request }) => {
      const url = new URL(request.url);
      const availability = url.searchParams.get("availability");
      if (!availability) return undefined;
      const matching = (await catalogueCandidates(url)).filter(
        (item) => catalogueAvailability(item, url) === availability,
      );
      const page = Number(url.searchParams.get("page") ?? "1");
      const pageSize = Number(url.searchParams.get("limit") ?? "20");
      return HttpResponse.json({
        items: matching.slice((page - 1) * pageSize, page * pageSize),
        page,
        pageSize,
        total: matching.length,
        facets: { types: matching.length === 0 ? [] : ["INSTRUMENT"] },
      });
    }),
    ...bookableItemDetailsHandlers(),
    ...bookableItemsHandlers((request) => {
      bookingPageRequests.collectionQueries.push(decodeURIComponent(new URL(request.url).search));
    }),

    http.get("/api/v2/booking-calendar/events", ({ request }) => {
      const url = new URL(request.url);
      bookingPageRequests.calendarBookingRequests.push(url);
      const q = url.searchParams.get("q")?.toLowerCase();
      const where = url.searchParams.get("where") ?? "";
      const docs = [ownBooking, otherBooking, busyBooking].filter(
        (event) =>
          (!q ||
            [event.purpose, event.bookedBy, event.target?.value.name, event.target?.globalId].some((value) =>
              value?.toLowerCase().includes(q),
            )) &&
          (!where.includes("requesterId==1") || event.requesterId === 1),
      );
      return HttpResponse.json(collectionResponse(docs));
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
        cancellationReason: null,
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
