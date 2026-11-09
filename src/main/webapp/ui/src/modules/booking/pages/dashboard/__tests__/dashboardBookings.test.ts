import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { BOOKING_READ_FIELDS, type BookingListDocument } from "@/modules/booking/domain/booking";
import {
  dashboardMonthInterval,
  fetchMonthlyDashboardBookings,
  fetchUpcomingDashboardBookings,
  groupDashboardBookingsByDay,
  TooManyDashboardBookingsError,
} from "../dashboardBookings";
import { calendarDateKey } from "../dashboardHelpers";
import { bookingTarget, collectionResponse, dashboardBookingFixture } from "../mocks/bookingDashboardMocks";

const testBookingOverrides: Partial<BookingListDocument> = {
  target: bookingTarget(12, "Instrument"),
  requester: { relationTo: "users", value: -1 },
  timezone: "Europe/Berlin",
};

describe("dashboard booking data", () => {
  it("queries upcoming bookings for negative requester IDs with a five-row limit", async () => {
    const requests: URL[] = [];
    const docs = [
      dashboardBookingFixture(5, 1, {
        ...testBookingOverrides,
        start: "2026-08-01T12:00:00Z",
        end: "2026-08-01T13:00:00Z",
      }),
      dashboardBookingFixture(2, 1, {
        ...testBookingOverrides,
        start: "2026-08-01T09:00:00Z",
        end: "2026-08-01T10:00:00Z",
      }),
      dashboardBookingFixture(1, 1, {
        ...testBookingOverrides,
        start: "2026-08-01T09:00:00Z",
        end: "2026-08-01T10:00:00Z",
      }),
      dashboardBookingFixture(3, 1, {
        ...testBookingOverrides,
        start: "2026-08-01T10:00:00Z",
        end: "2026-08-01T11:00:00Z",
      }),
      dashboardBookingFixture(4, 1, {
        ...testBookingOverrides,
        start: "2026-08-01T11:00:00Z",
        end: "2026-08-01T12:00:00Z",
      }),
      dashboardBookingFixture(6, 1, {
        ...testBookingOverrides,
        start: "2026-08-01T13:00:00Z",
        end: "2026-08-01T14:00:00Z",
      }),
    ];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json(collectionResponse(docs, { page: 1, hasNextPage: true }));
      }),
    );

    await expect(
      fetchUpcomingDashboardBookings({
        requesterId: -1,
        asOf: "2026-08-01T08:00:00Z",
      }),
    ).resolves.toEqual(
      docs.slice(0, 5).sort((left, right) => left.start.localeCompare(right.start) || left.id - right.id),
    );

    expect(requests).toHaveLength(1);
    expect(requests[0].searchParams.get("where")).toBe(
      "requester.value==-1;kind==BOOKING;state==CONFIRMED;end=gt=2026-08-01T08:00:00Z",
    );
    expect(requests[0].searchParams.get("limit")).toBe("5");
    expect(requests[0].searchParams.get("sort")).toBe("start,id");
    expect(requests[0].searchParams.get("depth")).toBe("1");
    expect(requests[0].searchParams.get("fields[bookings]")).toBe(`${BOOKING_READ_FIELDS},requester`);
  });

  it("loads monthly pages, deduplicates IDs, and sorts by start then ID", async () => {
    const first = dashboardBookingFixture(2, 2, {
      ...testBookingOverrides,
      start: "2026-08-02T10:00:00Z",
      end: "2026-08-02T11:00:00Z",
    });
    const duplicate = dashboardBookingFixture(2, 2, {
      ...testBookingOverrides,
      start: "2026-08-02T10:00:00Z",
      end: "2026-08-02T11:00:00Z",
    });
    const second = dashboardBookingFixture(1, 1, {
      ...testBookingOverrides,
      start: "2026-08-01T10:00:00Z",
      end: "2026-08-01T11:00:00Z",
    });
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json(
          requests.length === 1
            ? collectionResponse([first, duplicate], { page: 1, hasNextPage: true })
            : collectionResponse([second], { page: 1 }),
        );
      }),
    );

    await expect(
      fetchMonthlyDashboardBookings({
        requesterId: -1,
        start: "2026-07-27T22:00:00Z",
        end: "2026-09-06T22:00:00Z",
      }),
    ).resolves.toEqual([second, first]);

    expect(requests).toHaveLength(2);
    expect(requests[0].searchParams.get("where")).toBe(
      "requester.value==-1;kind==BOOKING;state==CONFIRMED;start=lt=2026-09-06T22:00:00Z;end=gt=2026-07-27T22:00:00Z",
    );
    expect(requests[0].searchParams.get("limit")).toBe("100");
    expect(requests[1].searchParams.get("page")).toBe("1");
    expect(requests[1].searchParams.get("where")).toContain(
      `;(start=gt=${first.start},(start==${first.start};id=gt=${first.id}))`,
    );
  });

  it.each([
    { rowsPerPage: 1, expectedRequests: 10 },
    { rowsPerPage: 501, expectedRequests: 1 },
  ])(
    "caps accumulated monthly rows and pages: $rowsPerPage rows per page",
    async ({ rowsPerPage, expectedRequests }) => {
      let requests = 0;
      let booking = dashboardBookingFixture(1, 1, testBookingOverrides);
      server.use(
        http.get("/api/v2/bookings", ({ request }) => {
          requests += 1;
          booking = { ...booking, id: requests };
          return HttpResponse.json(
            collectionResponse(
              Array.from({ length: rowsPerPage }, () => booking),
              {
                page: Number(new URL(request.url).searchParams.get("page")),
                hasNextPage: true,
              },
            ),
          );
        }),
      );

      await expect(
        fetchMonthlyDashboardBookings({
          requesterId: -1,
          start: "2026-07-27T22:00:00Z",
          end: "2026-09-06T22:00:00Z",
        }),
      ).rejects.toBeInstanceOf(TooManyDashboardBookingsError);
      expect(requests).toBe(expectedRequests);
    },
  );

  it("accepts exactly ten pages and 1,000 raw rows without totals", async () => {
    let requests = 0;
    let booking = dashboardBookingFixture(1, 1, testBookingOverrides);
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        requests += 1;
        const url = new URL(request.url);
        expect(url.searchParams.has("totals")).toBe(false);
        const page = requests;
        booking = { ...booking, id: page };
        return HttpResponse.json(
          collectionResponse(
            Array.from({ length: 100 }, () => booking),
            {
              page: 1,
              hasNextPage: page < 10,
            },
          ),
        );
      }),
    );

    await expect(
      fetchMonthlyDashboardBookings({
        requesterId: -1,
        start: "2026-07-27T22:00:00Z",
        end: "2026-09-06T22:00:00Z",
      }),
    ).resolves.toHaveLength(10);
    expect(requests).toBe(10);
  });

  it("rejects nonadvancing pagination and later-page failures", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json({
          ...collectionResponse(
            [
              dashboardBookingFixture(1, 1, {
                ...testBookingOverrides,
                start: "2026-08-01T10:00:00Z",
                end: "2026-08-01T11:00:00Z",
              }),
            ],
            { page: 0, hasNextPage: true },
          ),
        }),
      ),
    );
    await expect(
      fetchMonthlyDashboardBookings({
        requesterId: -1,
        start: "2026-07-27T22:00:00Z",
        end: "2026-09-06T22:00:00Z",
      }),
    ).rejects.toThrow("pagination did not advance");

    const skippedPageRequests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        skippedPageRequests.push(new URL(request.url));
        return HttpResponse.json({
          ...collectionResponse(
            [
              dashboardBookingFixture(1, 1, {
                ...testBookingOverrides,
                start: "2026-08-01T10:00:00Z",
                end: "2026-08-01T11:00:00Z",
              }),
            ],
            { page: 3, hasNextPage: true },
          ),
        });
      }),
    );
    await expect(
      fetchMonthlyDashboardBookings({
        requesterId: -1,
        start: "2026-07-27T22:00:00Z",
        end: "2026-09-06T22:00:00Z",
      }),
    ).rejects.toThrow("pagination did not advance");
    expect(skippedPageRequests).toHaveLength(1);

    server.use(
      http.get("/api/v2/bookings", ({ request }) =>
        !new URL(request.url).searchParams.get("where")?.includes("id=gt=")
          ? HttpResponse.json(
              collectionResponse(
                [
                  dashboardBookingFixture(1, 1, {
                    ...testBookingOverrides,
                    start: "2026-08-01T10:00:00Z",
                    end: "2026-08-01T11:00:00Z",
                  }),
                ],
                { page: 1, hasNextPage: true },
              ),
            )
          : new HttpResponse(null, { status: 503 }),
      ),
    );
    await expect(
      fetchMonthlyDashboardBookings({
        requesterId: -1,
        start: "2026-07-27T22:00:00Z",
        end: "2026-09-06T22:00:00Z",
      }),
    ).rejects.toMatchObject({ status: 503 });
  });

  it("passes abort through pagination and does not request a later page", async () => {
    const controller = new AbortController();
    const requests: RequestInit[] = [];
    const originalFetch = globalThis.fetch;
    const response = (docs: readonly BookingListDocument[], page: number) =>
      new Response(JSON.stringify(collectionResponse(docs, { page, hasNextPage: true })), {
        headers: { "Content-Type": "application/json" },
      });
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (_input, init) => {
      requests.push(init ?? {});
      controller.abort();
      return response(
        [
          dashboardBookingFixture(1, 1, {
            ...testBookingOverrides,
            start: "2026-08-01T10:00:00Z",
            end: "2026-08-01T11:00:00Z",
          }),
        ],
        1,
      );
    });

    try {
      await expect(
        fetchMonthlyDashboardBookings({
          requesterId: -1,
          start: "2026-07-27T22:00:00Z",
          end: "2026-09-06T22:00:00Z",

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

    const overnight = dashboardBookingFixture(1, 28, {
      ...testBookingOverrides,
      start: "2026-03-28T23:00:00Z",
      end: "2026-03-30T23:00:00Z",
    });
    const endsAtMidnight = dashboardBookingFixture(2, 29, {
      ...testBookingOverrides,
      start: "2026-03-29T08:00:00Z",
      end: "2026-03-29T22:00:00Z",
    });
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

it("continues after a removed row using the exact serialized start and id", async () => {
  const start = "2026-08-02T10:00:00.123456Z";
  const first = dashboardBookingFixture(2, 2, { ...testBookingOverrides, start });
  const remaining = dashboardBookingFixture(3, 2, { ...testBookingOverrides, start });
  let requests = 0;
  server.use(
    http.get("/api/v2/bookings", ({ request }) => {
      requests += 1;
      const params = new URL(request.url).searchParams;
      expect(params.get("page")).toBe("1");
      if (requests === 1) return HttpResponse.json(collectionResponse([first], { page: 1, hasNextPage: true }));
      expect(params.get("where")).toContain(`;(start=gt=${start},(start==${start};id=gt=2))`);
      // The first row was removed. An offset would skip the remaining row.
      return HttpResponse.json(collectionResponse([remaining], { page: 1 }));
    }),
  );
  expect(
    (
      await fetchMonthlyDashboardBookings({
        requesterId: -1,
        start: "2026-08-01T00:00:00Z",
        end: "2026-09-01T00:00:00Z",
      })
    ).map(({ id }) => id),
  ).toEqual([2, 3]);
  expect(requests).toBe(2);
});
