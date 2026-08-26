import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { loadCalendarEvents } from "../calendarEvents";
import { collectionResponse, otherBooking, ownBooking } from "./calendarTestHarness";

describe("calendar events", () => {
  it("loads every page for the selected calendar range with private-safe fields", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json(
          collectionResponse(url.searchParams.get("page") === "2" ? [otherBooking] : [ownBooking], {
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
      "start<2026-08-23T22:00:00Z;end>2026-08-16T22:00:00Z;state==CONFIRMED",
    );
    expect(requests[0].searchParams.get("fields[bookings]")).toBe(
      "id,target,requesterId,timezone,start,end,state,purpose,bookedBy,privacy,canEdit,createdAt,updatedAt",
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
