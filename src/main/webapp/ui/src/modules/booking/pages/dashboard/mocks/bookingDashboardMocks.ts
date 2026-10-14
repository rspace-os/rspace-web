import { HttpResponse, http, type RequestHandler } from "msw";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { bookingTarget, collectionResponse } from "../../mocks/bookingCalendarFixtures";
import { currentUser } from "../../mocks/currentUserFixture";
import { bookingsOpenApi } from "../../my-bookings/mocks/bookingMocks";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";

export { bookingTarget, collectionResponse };

function timestamp(day: number, hour: number, minute = 0): string {
  return `2026-08-${String(day).padStart(2, "0")}T${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}:00Z`;
}

export function dashboardBookingFixture(
  id: number,
  day: number,
  overrides: Partial<BookingListDocument> = {},
): BookingListDocument {
  const start = overrides.start ?? timestamp(day, 8 + (id % 8), (id % 2) * 30);
  const end = overrides.end ?? new Date(Date.parse(start) + 45 * 60 * 1000).toISOString();
  return {
    id,
    version: 0,
    target: bookingTarget(id, `Instrument ${id}`),
    requesterId: currentUser.id,
    canViewConfiguration: true,
    timezone: "UTC",
    start,
    end,
    state: "CONFIRMED",
    kind: "BOOKING",
    privacy: "full",
    purpose: `Purpose ${id}`,
    bookedBy: "Ada Lovelace (ada)",
    canEdit: false,
    canCancel: false,
    ...overrides,
    createdAt: overrides.createdAt ?? start,
    updatedAt: overrides.updatedAt ?? start,
    cancellationReason: overrides.cancellationReason ?? null,
  };
}

export const dashboardBookings: readonly BookingListDocument[] = [
  ...Array.from({ length: 5 }, (_, index) => dashboardBookingFixture(100 + index, 18)),
  ...Array.from({ length: 6 }, (_, index) => dashboardBookingFixture(200 + index, 19)),
  ...Array.from({ length: 12 }, (_, index) => dashboardBookingFixture(300 + index, 20)),
  ...Array.from({ length: 99 }, (_, index) => dashboardBookingFixture(400 + index, 21)),
  ...Array.from({ length: 100 }, (_, index) => dashboardBookingFixture(500 + index, 22)),
  dashboardBookingFixture(700, 23, {
    target: bookingTarget(700, "Private full booking"),
    purpose: "Private purpose",
  }),
  dashboardBookingFixture(701, 23, {
    target: bookingTarget(701, "Private busy booking"),
    privacy: "busy",
    purpose: null,
    bookedBy: null,
    canViewConfiguration: false,
  }),
];

export type DashboardBookingsError = "upcoming" | "monthly" | null;

export function bookingDashboardHandlers({
  docs = dashboardBookings,
  error = null,
  onRequest = () => undefined,
}: {
  docs?: readonly BookingListDocument[];
  error?: DashboardBookingsError;
  onRequest?: (url: URL) => void;
} = {}): RequestHandler[] {
  return [
    http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
    http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(inheritedBrowserBookingPreferences)),
    http.get("/api/v2/openapi.json", () => HttpResponse.json(bookingsOpenApi)),
    http.get("/api/v2/bookings", ({ request }) => {
      const url = new URL(request.url);
      onRequest(url);
      const isUpcoming = url.searchParams.get("limit") === "5";
      if (error === (isUpcoming ? "upcoming" : "monthly")) return new HttpResponse(null, { status: 503 });
      const visible = isUpcoming ? docs.slice(0, 5) : docs;
      return HttpResponse.json(
        collectionResponse(visible, {
          limit: isUpcoming ? 5 : 100,
          totalDocs: visible.length,
        }),
      );
    }),
    http.get("/api/v2/bookings/count", () => HttpResponse.json({ totalDocs: docs.length })),
  ];
}
