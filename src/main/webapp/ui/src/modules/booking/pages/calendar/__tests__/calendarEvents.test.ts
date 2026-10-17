import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { collectionResponse, otherBooking, ownBooking } from "../../mocks/bookingCalendarFixtures";
import { CALENDAR_BOOKING_FIELDS, loadCalendarEvents } from "../calendarEvents";

describe("calendar events", () => {
  it("loads every page for the selected calendar range with private-safe fields", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        const fields = url.searchParams.get("fields[bookings]")?.split(",") ?? [];
        const source = url.searchParams.get("page") === "2" ? otherBooking : ownBooking;
        const projectedBooking = Object.fromEntries(
          fields.flatMap((field) => (field in source ? [[field, source[field as keyof typeof source]]] : [])),
        );
        return HttpResponse.json(
          collectionResponse([projectedBooking], {
            page: Number(url.searchParams.get("page")),
            totalDocs: 2,
            totalPages: 2,
          }),
        );
      }),
    );

    await expect(
      loadCalendarEvents("2026-08-17", "2026-08-23", "Europe/Berlin", "token", new AbortController().signal),
    ).resolves.toEqual([ownBooking, otherBooking]);

    expect(requests).toHaveLength(2);
    expect(requests[0].searchParams.get("where")).toBe(
      "start=lt=2026-08-23T22:00:00Z;end=gt=2026-08-16T22:00:00Z;state==CONFIRMED",
    );
    const selectedFields = requests[0].searchParams.get("fields[bookings]");
    expect(selectedFields).toBe(CALENDAR_BOOKING_FIELDS);
    expect(selectedFields?.split(",")).toContain("canViewConfiguration");
  });

  it("returns no events without requesting bookings for an empty target scope", async () => {
    let requests = 0;
    server.use(
      http.get("/api/v2/bookings", () => {
        requests += 1;
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );

    await expect(
      loadCalendarEvents("2026-08-17", "2026-08-23", "Europe/Berlin", "token", new AbortController().signal, []),
    ).resolves.toEqual([]);

    expect(requests).toBe(0);
  });

  it("filters booking requests to a non-empty target scope", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );

    await expect(
      loadCalendarEvents("2026-08-17", "2026-08-23", "Europe/Berlin", "token", new AbortController().signal, ["IN123"]),
    ).resolves.toEqual([ownBooking]);

    expect(requests).toHaveLength(1);
    expect(requests[0].searchParams.get("where")).toBe(
      "start=lt=2026-08-23T22:00:00Z;end=gt=2026-08-16T22:00:00Z;state==CONFIRMED;target=in=(IN123)",
    );
  });

  it("rejects unsuccessful responses", async () => {
    server.use(http.get("/api/v2/bookings", () => new HttpResponse(null, { status: 503 })));

    await expect(
      loadCalendarEvents("2026-08-17", "2026-08-17", "UTC", "token", new AbortController().signal),
    ).rejects.toThrow("Booking calendar request failed (503)");
  });

  it("rejects ranges containing more bookings than the calendar can safely render", async () => {
    let requests = 0;
    server.use(
      http.get("/api/v2/bookings", () => {
        requests += 1;
        return HttpResponse.json(collectionResponse([ownBooking], { totalDocs: 1_001, totalPages: 11 }));
      }),
    );

    await expect(
      loadCalendarEvents("2026-08-17", "2026-08-23", "UTC", "token", new AbortController().signal),
    ).rejects.toThrow("Booking calendar exceeds 1,000 bookings");
    expect(requests).toBe(1);
  });
});
