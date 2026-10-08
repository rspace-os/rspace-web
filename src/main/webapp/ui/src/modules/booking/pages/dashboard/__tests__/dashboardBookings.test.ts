import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { BOOKING_READ_FIELDS } from "@/modules/booking/domain/booking";
import {
  calendarDateKey,
  dashboardMonthInterval,
  fetchMonthlyDashboardBookings,
  fetchUpcomingDashboardBookings,
  groupDashboardBookingsByDay,
  TooManyDashboardBookingsError,
} from "../dashboardBookings";

const target = {
  relationTo: "booking-instruments" as const,
  value: { id: 12, name: "Instrument", deleted: false },
  globalId: "IN12",
};

function booking(id: number, start: string, end: string, requesterId = -1): BookingListDocument {
  return {
    id,
    version: 0,
    target,
    requesterId,
    canViewConfiguration: true,
    timezone: "Europe/Berlin",
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
  };
}

function envelope(
  docs: readonly BookingListDocument[],
  page = 1,
  totalDocs = docs.length,
  totalPages = totalDocs === 0 ? 0 : 1,
): Record<string, unknown> {
  return {
    docs,
    totalDocs,
    limit: 100,
    page,
    pagingCounter: (page - 1) * 100 + 1,
    totalPages,
    hasPrevPage: page > 1,
    hasNextPage: page < totalPages,
    prevPage: page > 1 ? page - 1 : null,
    nextPage: page < totalPages ? page + 1 : null,
  };
}

describe("dashboard booking data", () => {
  it("queries upcoming bookings for negative requester IDs with a five-row limit", async () => {
    const requests: URL[] = [];
    const docs = [
      booking(5, "2026-08-01T12:00:00Z", "2026-08-01T13:00:00Z"),
      booking(2, "2026-08-01T09:00:00Z", "2026-08-01T10:00:00Z"),
      booking(1, "2026-08-01T09:00:00Z", "2026-08-01T10:00:00Z"),
      booking(3, "2026-08-01T10:00:00Z", "2026-08-01T11:00:00Z"),
      booking(4, "2026-08-01T11:00:00Z", "2026-08-01T12:00:00Z"),
      booking(6, "2026-08-01T13:00:00Z", "2026-08-01T14:00:00Z"),
    ];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json(envelope(docs, 1, docs.length, 2));
      }),
    );

    await expect(
      fetchUpcomingDashboardBookings({
        requesterId: -1,
        asOf: "2026-08-01T08:00:00Z",
        token: "token",
      }),
    ).resolves.toEqual(
      docs.slice(0, 5).sort((left, right) => left.start.localeCompare(right.start) || left.id - right.id),
    );

    expect(requests).toHaveLength(1);
    expect(requests[0].searchParams.get("where")).toBe(
      "requesterId==-1;kind==BOOKING;state==CONFIRMED;end=gt=2026-08-01T08:00:00Z",
    );
    expect(requests[0].searchParams.get("limit")).toBe("5");
    expect(requests[0].searchParams.get("sort")).toBe("start,id");
    expect(requests[0].searchParams.get("depth")).toBe("1");
    expect(requests[0].searchParams.get("fields[bookings]")).toBe(`${BOOKING_READ_FIELDS},requesterId`);
  });

  it("loads monthly pages, deduplicates IDs, and sorts by start then ID", async () => {
    const first = booking(2, "2026-08-02T10:00:00Z", "2026-08-02T11:00:00Z");
    const duplicate = booking(2, "2026-08-02T10:00:00Z", "2026-08-02T11:00:00Z");
    const second = booking(1, "2026-08-01T10:00:00Z", "2026-08-01T11:00:00Z");
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json(
          Number(url.searchParams.get("page")) === 1
            ? envelope([first, duplicate], 1, 3, 2)
            : envelope([second], 2, 3, 2),
        );
      }),
    );

    await expect(
      fetchMonthlyDashboardBookings({
        requesterId: -1,
        start: "2026-07-27T22:00:00Z",
        end: "2026-09-06T22:00:00Z",
        token: "token",
      }),
    ).resolves.toEqual([second, first]);

    expect(requests).toHaveLength(2);
    expect(requests[0].searchParams.get("where")).toBe(
      "requesterId==-1;kind==BOOKING;state==CONFIRMED;start=lt=2026-09-06T22:00:00Z;end=gt=2026-07-27T22:00:00Z",
    );
    expect(requests[0].searchParams.get("limit")).toBe("100");
    expect(requests[1].searchParams.get("page")).toBe("2");
  });

  it("stops immediately when monthly metadata exceeds the safe bound", async () => {
    let requests = 0;
    server.use(
      http.get("/api/v2/bookings", () => {
        requests += 1;
        return HttpResponse.json(envelope([booking(1, "2026-08-01T10:00:00Z", "2026-08-01T11:00:00Z")], 1, 1_001, 11));
      }),
    );

    await expect(
      fetchMonthlyDashboardBookings({
        requesterId: -1,
        start: "2026-07-27T22:00:00Z",
        end: "2026-09-06T22:00:00Z",
        token: "token",
      }),
    ).rejects.toBeInstanceOf(TooManyDashboardBookingsError);
    expect(requests).toBe(1);
  });

  it("rejects nonadvancing pagination and later-page failures", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json({
          ...envelope([booking(1, "2026-08-01T10:00:00Z", "2026-08-01T11:00:00Z")], 1, 2, 2),
          nextPage: 1,
        }),
      ),
    );
    await expect(
      fetchMonthlyDashboardBookings({
        requesterId: -1,
        start: "2026-07-27T22:00:00Z",
        end: "2026-09-06T22:00:00Z",
        token: "token",
      }),
    ).rejects.toThrow("pagination did not advance");

    const skippedPageRequests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        skippedPageRequests.push(new URL(request.url));
        return HttpResponse.json({
          ...envelope([booking(1, "2026-08-01T10:00:00Z", "2026-08-01T11:00:00Z")], 1, 3, 3),
          nextPage: 3,
        });
      }),
    );
    await expect(
      fetchMonthlyDashboardBookings({
        requesterId: -1,
        start: "2026-07-27T22:00:00Z",
        end: "2026-09-06T22:00:00Z",
        token: "token",
      }),
    ).rejects.toThrow("pagination did not advance");
    expect(skippedPageRequests).toHaveLength(1);

    server.use(
      http.get("/api/v2/bookings", ({ request }) =>
        Number(new URL(request.url).searchParams.get("page")) === 1
          ? HttpResponse.json(envelope([booking(1, "2026-08-01T10:00:00Z", "2026-08-01T11:00:00Z")], 1, 2, 2))
          : new HttpResponse(null, { status: 503 }),
      ),
    );
    await expect(
      fetchMonthlyDashboardBookings({
        requesterId: -1,
        start: "2026-07-27T22:00:00Z",
        end: "2026-09-06T22:00:00Z",
        token: "token",
      }),
    ).rejects.toThrow("Dashboard bookings request failed (503)");
  });

  it("passes abort through pagination and does not request a later page", async () => {
    const controller = new AbortController();
    const requests: RequestInit[] = [];
    const originalFetch = globalThis.fetch;
    const response = (docs: readonly BookingListDocument[], page: number) =>
      new Response(JSON.stringify(envelope(docs, page, 2, 2)), {
        headers: { "Content-Type": "application/json" },
      });
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (_input, init) => {
      requests.push(init ?? {});
      controller.abort();
      return response([booking(1, "2026-08-01T10:00:00Z", "2026-08-01T11:00:00Z")], 1);
    });

    try {
      await expect(
        fetchMonthlyDashboardBookings({
          requesterId: -1,
          start: "2026-07-27T22:00:00Z",
          end: "2026-09-06T22:00:00Z",
          token: "token",
          signal: controller.signal,
        }),
      ).rejects.toThrow();
    } finally {
      fetchMock.mockRestore();
      expect(globalThis.fetch).toBe(originalFetch);
    }

    expect(requests).toHaveLength(1);
    expect(requests[0].signal).toBe(controller.signal);
  });

  it("keeps month boundaries and grouping in the requested timezone", () => {
    const interval = dashboardMonthInterval(new Date(2026, 7, 15), "Europe/Berlin");
    expect(interval.dates).toHaveLength(42);
    expect(interval.dates[0]).toBe("2026-07-27");
    expect(interval.dates.at(-1)).toBe("2026-09-06");
    expect(interval.start).toBe("2026-07-26T22:00:00Z");
    expect(interval.end).toBe("2026-09-06T22:00:00Z");

    const autumn = dashboardMonthInterval(new Date(2026, 9, 15), "Europe/Berlin");
    expect(autumn.dates[0]).toBe("2026-09-28");
    expect(autumn.dates.at(-1)).toBe("2026-11-08");
    expect(autumn.start).toBe("2026-09-27T22:00:00Z");
    expect(autumn.end).toBe("2026-11-08T23:00:00Z");

    const yearBoundary = dashboardMonthInterval(new Date(2027, 0, 15), "Europe/Berlin");
    expect(yearBoundary.dates[0]).toBe("2026-12-28");
    expect(yearBoundary.dates.at(-1)).toBe("2027-02-07");

    const overnight = booking(1, "2026-03-28T23:00:00Z", "2026-03-30T23:00:00Z");
    const endsAtMidnight = booking(2, "2026-03-29T08:00:00Z", "2026-03-29T22:00:00Z");
    const grouped = groupDashboardBookingsByDay(
      [overnight, endsAtMidnight],
      ["2026-03-29", "2026-03-30"],
      "Europe/Berlin",
    );
    expect(grouped.get("2026-03-29")?.map(({ id }) => id)).toEqual([1, 2]);
    expect(grouped.get("2026-03-30")?.map(({ id }) => id)).toEqual([1]);
    expect(calendarDateKey(new Date(2026, 7, 3, 23, 30))).toBe("2026-08-03");
  });
});
