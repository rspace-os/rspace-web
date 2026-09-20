import { HttpResponse, http, type RequestHandler } from "msw";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { currentUser } from "../../calendar/calendarFixtures";
import { bookingsOpenApi } from "../../my-bookings/mocks/bookingMocks";
import { inheritedBrowserBookingPreferences } from "../../preferences/bookingPreferencesFixtures";

const target = (id: number, name: string) => ({
  relationTo: "booking-instruments" as const,
  value: { id, name, deleted: false },
  globalId: `IN${id}`,
});

function timestamp(day: number, hour: number, minute = 0): string {
  return `2026-08-${String(day).padStart(2, "0")}T${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}:00Z`;
}

export function dashboardBooking(
  id: number,
  day: number,
  overrides: Partial<BookingListDocument> = {},
): BookingListDocument {
  const start = timestamp(day, 8 + (id % 8), (id % 2) * 30);
  const end = new Date(Date.parse(start) + 45 * 60 * 1000).toISOString();
  return {
    id,
    version: 0,
    target: target(id, `Instrument ${id}`),
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
    createdAt: start,
    updatedAt: start,
    ...overrides,
  };
}

export const dashboardBookings: readonly BookingListDocument[] = [
  ...Array.from({ length: 5 }, (_, index) => dashboardBooking(100 + index, 18)),
  ...Array.from({ length: 6 }, (_, index) => dashboardBooking(200 + index, 19)),
  ...Array.from({ length: 12 }, (_, index) => dashboardBooking(300 + index, 20)),
  ...Array.from({ length: 99 }, (_, index) => dashboardBooking(400 + index, 21)),
  ...Array.from({ length: 100 }, (_, index) => dashboardBooking(500 + index, 22)),
  dashboardBooking(700, 23, {
    target: target(700, "Private full booking"),
    purpose: "Private purpose",
  }),
  dashboardBooking(701, 23, {
    target: target(701, "Private busy booking"),
    privacy: "busy",
    purpose: null,
    bookedBy: null,
    canViewConfiguration: false,
  }),
];

export function dashboardCollectionResponse(
  docs: readonly BookingListDocument[],
  options: { page?: number; totalDocs?: number; totalPages?: number } = {},
) {
  const page = options.page ?? 1;
  const totalDocs = options.totalDocs ?? docs.length;
  const totalPages = options.totalPages ?? (totalDocs === 0 ? 0 : 1);
  return {
    docs,
    totalDocs,
    limit: page === 1 && totalPages === 1 && totalDocs <= 5 ? 5 : 100,
    page,
    pagingCounter: (page - 1) * 100 + 1,
    totalPages,
    hasPrevPage: page > 1,
    hasNextPage: page < totalPages,
    prevPage: page > 1 ? page - 1 : null,
    nextPage: page < totalPages ? page + 1 : null,
  };
}

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
        dashboardCollectionResponse(visible, {
          totalDocs: visible.length,
        }),
      );
    }),
    http.get("/api/v2/bookings/count", () => HttpResponse.json({ totalDocs: docs.length })),
  ];
}
