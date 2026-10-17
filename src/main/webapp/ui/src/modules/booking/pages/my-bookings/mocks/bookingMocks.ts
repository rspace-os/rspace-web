import { HttpResponse, http, type RequestHandler } from "msw";
import { apiV2CollectionResponse } from "@/__tests__/mocks/apiV2CollectionMock";
import { apiV2OpenApi } from "@/__tests__/mocks/apiV2OpenApi";
import { bookingTarget } from "../../mocks/bookingCalendarFixtures";

export const bookingsOpenApi = apiV2OpenApi;

export const upcomingBooking = {
  id: 41,
  version: 0,
  requesterId: 84,
  kind: "BOOKING",
  target: bookingTarget(123, "Confocal microscope"),
  canViewConfiguration: true,
  timezone: "Pacific/Auckland",
  start: "2020-08-23T09:00:00Z",
  end: "2030-08-23T10:00:00Z",
  state: "CONFIRMED",
  purpose: "Scope training",
  bookedBy: "Test User (testuser)",
  privacy: "full",
  canEdit: true,
  canCancel: true,
  createdAt: "2020-08-01T09:00:00Z",
  updatedAt: "2020-08-01T09:00:00Z",
};

export const pastBooking = {
  id: 42,
  version: 0,
  requesterId: 84,
  kind: "BOOKING",
  target: bookingTarget(124, "Electron microscope"),
  canViewConfiguration: true,
  timezone: "UTC",
  start: "2020-08-23T09:00:00Z",
  end: "2020-08-23T10:00:00Z",
  state: "CONFIRMED",
  purpose: "Completed run",
  bookedBy: "Test User (testuser)",
  privacy: "full",
  canEdit: false,
  canCancel: false,
  createdAt: "2020-08-01T09:00:00Z",
  updatedAt: "2020-08-01T09:00:00Z",
};

export const roleLostBooking = {
  ...upcomingBooking,
  id: 43,
  target: null,
  timezone: null,
  canViewConfiguration: false,
  canEdit: false,
  canCancel: false,
  purpose: "Retained requester details",
};

function matchesEndFilter(document: unknown, where: string | null): boolean {
  const filter = where?.match(/end=(gt|le)=([^;)]+)/);
  if (!filter) return true;
  if (typeof document !== "object" || document === null || !("end" in document)) return false;
  const end = Date.parse(String(document.end));
  const boundary = Date.parse(filter[2]);
  return filter[1] === "gt" ? end > boundary : end <= boundary;
}

export function bookingHandlers(
  onListRequest: (url: URL) => void = () => undefined,
  onCountRequest: (url: URL) => void = () => undefined,
  docs?: readonly unknown[],
): RequestHandler[] {
  return [
    http.get("/api/v2/openapi.json", () => HttpResponse.json(bookingsOpenApi)),
    http.get("/api/v2/bookings", ({ request }) => {
      const url = new URL(request.url);
      onListRequest(url);
      const source = docs ?? [upcomingBooking, pastBooking];
      return apiV2CollectionResponse(
        "bookings",
        request,
        source.filter((document) => matchesEndFilter(document, url.searchParams.get("where"))),
      );
    }),
    http.get("/api/v2/bookings/count", ({ request }) => {
      onCountRequest(new URL(request.url));
      return HttpResponse.json({ totalDocs: 2 });
    }),
  ];
}
