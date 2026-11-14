import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { createElement, type ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { BOOKING_READ_FIELDS } from "@/modules/booking/domain/booking";
import { displayInterval } from "@/modules/booking/domain/bookingTime";
import { invalidateListBookings } from "@/modules/common/api/v2/generated/bookings/bookings";
import type { BookingsRead } from "@/modules/common/api/v2/generated/models/bookingsRead";
import type { ListBookings200 } from "@/modules/common/api/v2/generated/models/listBookings200";
import { collectionResponse } from "../../mocks/bookingCalendarFixtures";
import { loadCalendarAvailability, useCalendarAvailability } from "../calendarAvailability";

const bookingResponse = (
  docs: readonly BookingsRead[],
  options?: Parameters<typeof collectionResponse>[1],
): ListBookings200 => ({ ...collectionResponse(docs, options), docs: [...docs] });

const booking = (id: number, target: string, start: string, end: string): BookingsRead => ({
  id,
  version: 0,
  target: {
    relationTo: "instruments",
    value: { id, name: target, deleted: false },
    globalId: target,
  },
  timezone: "Europe/Berlin",
  start,
  end,
  state: "CONFIRMED",
  kind: "BOOKING",
  purpose: `Purpose ${id}`,
  bookedBy: `User ${id}`,
  privacy: "full",
  canEdit: false,
  canCancel: false,
  createdAt: "2026-08-01T09:00:00Z",
  updatedAt: "2026-08-01T09:00:00Z",
});

const ALWAYS_OPEN = [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }));

const schedule = {
  openingHours: ALWAYS_OPEN,
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  allowDoubleBooking: false,
};

const sourced = (
  id: string,
  kind: "booking" | "blockout",
  startsAt: string,
  endsAt: string,
  sourceStartsAt = startsAt,
  sourceEndsAt = endsAt,
) => ({
  kind,
  startsAt: new Date(startsAt),
  endsAt: new Date(endsAt),
  source: {
    id,
    startsAt: new Date(sourceStartsAt),
    endsAt: new Date(sourceEndsAt),
    ...(kind === "booking"
      ? {
          booking: {
            id: Number(id.replace("booking:", "")),
            kind: "BOOKING",
            privacy: "full",
            purpose: `Purpose ${id.replace("booking:", "")}`,
            bookedBy: `User ${id.replace("booking:", "")}`,
            createdBy: undefined,
            instrumentTimeZone: "Europe/Berlin",
          },
        }
      : {}),
  },
});

const closureId = (globalId: string, startsAt: string, endsAt: string) =>
  `opening-hours:${globalId}:${new Date(startsAt).toISOString()}:${new Date(endsAt).toISOString()}`;

// Later pages always request page 1 and continue after the last row: `;(start=gt=S,(start==S;id=gt=ID))`.
const keysetAfterId = (request: Request) =>
  Number(/id=gt=(\d+)\)\)$/.exec(new URL(request.url).searchParams.get("where") ?? "")?.[1] ?? 0);

describe("calendar availability", () => {
  it("refreshes aggregate availability when the generated booking list prefix is invalidated", async () => {
    let requests = 0;
    server.use(
      http.get("/api/v2/bookings", () => {
        requests += 1;
        return HttpResponse.json(
          bookingResponse(requests === 1 ? [booking(1, "IN1", "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z")] : []),
        );
      }),
    );
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client }, children);
    const { result } = renderHook(
      () => useCalendarAvailability([{ globalId: "IN1", timezone: "UTC", ...schedule }], "2026-08-17"),
      { wrapper },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.get("IN1")?.some((entry) => entry.kind === "booking")).toBe(true);
    await invalidateListBookings(client);
    await waitFor(() => expect(result.current.data?.get("IN1")?.some((entry) => entry.kind === "booking")).toBe(false));
    expect(requests).toBe(2);
  });

  it("classifies maintenance as a blockout while retaining its details and buffers", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json(
          bookingResponse([
            { ...booking(1, "IN1", "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z"), kind: "MAINTENANCE" },
          ]),
        ),
      ),
    );
    const result = await loadCalendarAvailability(
      [{ globalId: "IN1", timezone: "UTC", ...schedule, bufferBeforeMinutes: 10, allowDoubleBooking: true }],
      "2026-08-17",

      new AbortController().signal,
    );
    expect(result.get("IN1")).toEqual([
      expect.objectContaining({
        kind: "blockout",
        startsAt: new Date("2026-08-17T09:50:00Z"),
        endsAt: new Date("2026-08-17T11:10:00Z"),
        source: expect.objectContaining({ booking: expect.objectContaining({ id: 1, kind: "MAINTENANCE" }) }),
      }),
    ]);
  });

  it("uses one detailed batched query and clips results to each row's zoned day", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(
          bookingResponse([
            booking(1, "IN1", "2026-03-28T22:00:00Z", "2026-03-29T01:00:00Z"),
            booking(2, "IN2", "2026-03-29T03:00:00Z", "2026-03-29T05:00:00Z"),
          ]),
        );
      }),
    );

    const result = await loadCalendarAvailability(
      [
        { globalId: "IN1", timezone: "Europe/Berlin", ...schedule },
        { globalId: "IN2", timezone: "America/New_York", ...schedule },
      ],
      "2026-03-29",

      new AbortController().signal,
    );

    expect(requests).toHaveLength(1);
    const where = requests[0].searchParams.get("where") ?? "";
    expect(where).toContain("target=in=(IN1,IN2)");
    expect(where).toContain("start=lt=2026-03-29T22:00:00Z");
    expect(where).toContain("end=gt=2026-03-28T23:00:00Z");
    expect(where).not.toContain("start<");
    expect(where).not.toContain("end>");
    expect(requests[0].searchParams.get("fields[bookings]")).toBe(BOOKING_READ_FIELDS);
    expect(requests[0].searchParams.has("totals")).toBe(false);
    expect(result.get("IN1")).toEqual([
      sourced("booking:1", "booking", "2026-03-28T23:00:00Z", "2026-03-29T01:00:00Z", "2026-03-28T22:00:00Z"),
    ]);
    expect(result.get("IN2")).toHaveLength(1);
    expect([...result.values()].flat().every((interval) => interval.kind === "booking")).toBe(true);
  });

  it("splits 1,000 target IDs within the generated URL budget", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(bookingResponse([]));
      }),
    );
    const rows = Array.from({ length: 1000 }, (_, index) => ({
      globalId: `IN${String(index).padStart(4, "0")}${"x".repeat(48)}`,
      timezone: "UTC",
      ...schedule,
    }));
    const result = await loadCalendarAvailability(rows, "2026-08-17", new AbortController().signal);
    expect(result.size).toBe(1000);
    expect(requests.length).toBeGreaterThan(2);
    for (const url of requests) {
      const targetList = url.searchParams.get("where")?.split("target=in=(")[1]?.split(")")[0] ?? "";
      const targetIds = targetList ? targetList.split(",") : [];
      expect(targetIds.length).toBeGreaterThan(0);
      expect(targetIds.length).toBeLessThanOrEqual(500);
      expect(new TextEncoder().encode(`${url.pathname}${url.search}`).length).toBeLessThanOrEqual(6000);
    }
  });

  it("fetches every page and fails explicitly above the cap", async () => {
    const cursors: number[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        cursors.push(keysetAfterId(request));
        return HttpResponse.json(
          cursors.length === 1
            ? bookingResponse(
                [
                  booking(1, "IN1", "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z"),
                  booking(2, "IN2", "2026-08-17T11:00:00Z", "2026-08-17T12:00:00Z"),
                ],
                { page: 1, limit: 2, totalDocs: 4 },
              )
            : bookingResponse([
                booking(3, "IN2", "2026-08-17T12:00:00Z", "2026-08-17T13:00:00Z"),
                booking(4, "IN1", "2026-08-17T13:00:00Z", "2026-08-17T14:00:00Z"),
              ]),
        );
      }),
    );
    const rows = [
      { globalId: "IN1", timezone: "UTC", ...schedule },
      { globalId: "IN2", timezone: "UTC", ...schedule },
    ];
    const result = await loadCalendarAvailability(rows, "2026-08-17", new AbortController().signal);
    expect(cursors).toEqual([0, 2]);
    expect(result.get("IN1")?.map(({ source }) => source.id)).toEqual(["booking:1", "booking:4"]);
    expect(result.get("IN2")?.map(({ source }) => source.id)).toEqual(["booking:2", "booking:3"]);

    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const afterId = keysetAfterId(request);
        return HttpResponse.json(
          bookingResponse(
            Array.from({ length: 100 }, (_, index) =>
              booking(afterId + index + 1, "IN1", "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z"),
            ),
            { totalDocs: 1001 - afterId },
          ),
        );
      }),
    );
    await expect(loadCalendarAvailability(rows, "2026-08-17", new AbortController().signal)).rejects.toThrow(
      "exceeds 1,000",
    );
  });

  it("accepts exactly 1,000 booking documents in one availability batch", async () => {
    const cursors: number[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const afterId = keysetAfterId(request);
        cursors.push(afterId);
        const docs = Array.from({ length: Math.min(100, 1000 - afterId) }, (_, index) =>
          booking(afterId + index + 1, "IN1", "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z"),
        );
        return HttpResponse.json(bookingResponse(docs, { totalDocs: 1000 - afterId }));
      }),
    );

    const result = await loadCalendarAvailability(
      [{ globalId: "IN1", timezone: "UTC", ...schedule }],
      "2026-08-17",

      new AbortController().signal,
    );

    expect(cursors).toEqual([0, 100, 200, 300, 400, 500, 600, 700, 800, 900]);
    expect(result.get("IN1")).toHaveLength(1000);
  });

  it("applies the 1,000-booking limit independently to each target batch", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        const where = url.searchParams.get("where") ?? "";
        const targetId = where.split("target=in=(")[1]?.split(",")[0] ?? "";
        const idOffset = targetId === "IN0500" ? 1000 : 0;
        const start = Math.max(0, keysetAfterId(request) - idOffset);
        const docs = Array.from({ length: 100 }, (_, index) =>
          booking(idOffset + start + index + 1, targetId, "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z"),
        );
        return HttpResponse.json(bookingResponse(docs, { totalDocs: 1000 - start }));
      }),
    );
    const rows = Array.from({ length: 1000 }, (_, index) => ({
      globalId: `IN${String(index).padStart(4, "0")}`,
      timezone: "UTC",
      ...schedule,
    }));

    const result = await loadCalendarAvailability(rows, "2026-08-17", new AbortController().signal);

    expect(requests).toHaveLength(20);
    expect(result.get("IN0000")).toHaveLength(1000);
    expect(result.get("IN0500")).toHaveLength(1000);
  });

  it("uses one display interval across rows with different local dates", async () => {
    let requestUrl: URL | undefined;
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        requestUrl = new URL(request.url);
        return HttpResponse.json(
          bookingResponse([
            booking(1, "IN1", "2026-08-17T22:00:00Z", "2026-08-18T02:00:00Z"),
            booking(2, "IN2", "2026-08-18T03:00:00Z", "2026-08-18T06:00:00Z"),
          ]),
        );
      }),
    );

    const interval = displayInterval("2026-08-18", "Asia/Tokyo", "00:00", "24:00");
    const result = await loadCalendarAvailability(
      [
        { globalId: "IN1", timezone: "Asia/Tokyo", ...schedule },
        { globalId: "IN2", timezone: "America/Los_Angeles", ...schedule },
      ],
      interval,

      new AbortController().signal,
    );

    const where = requestUrl?.searchParams.get("where") ?? "";
    expect(where).toContain("start=lt=2026-08-18T15:00:00Z");
    expect(where).toContain("end=gt=2026-08-17T15:00:00Z");
    expect(result.get("IN1")).toEqual([
      sourced("booking:1", "booking", "2026-08-17T22:00:00Z", "2026-08-18T02:00:00Z"),
    ]);
    expect(result.get("IN2")).toEqual([
      sourced("booking:2", "booking", "2026-08-18T03:00:00Z", "2026-08-18T06:00:00Z"),
    ]);
  });

  it("generates and clips closures for every scheduling date overlapped by the display interval", async () => {
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(bookingResponse([]))));
    const interval = displayInterval("2026-08-18", "Pacific/Auckland", "00:00", "24:00");

    const result = await loadCalendarAvailability(
      [
        {
          globalId: "IN1",
          timezone: "America/Los_Angeles",
          openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "18:00" })),
          bufferBeforeMinutes: 0,
          bufferAfterMinutes: 0,
          allowDoubleBooking: false,
        },
      ],
      interval,

      new AbortController().signal,
    );

    expect(interval).toMatchObject({ start: "2026-08-17T12:00:00Z", end: "2026-08-18T12:00:00Z" });
    expect(result.get("IN1")).toEqual([
      sourced(
        closureId("IN1", "2026-08-17T12:00:00Z", "2026-08-17T15:00:00Z"),
        "blockout",
        "2026-08-17T12:00:00Z",
        "2026-08-17T15:00:00Z",
      ),
      sourced(
        closureId("IN1", "2026-08-18T01:00:00Z", "2026-08-18T12:00:00Z"),
        "blockout",
        "2026-08-18T01:00:00Z",
        "2026-08-18T12:00:00Z",
      ),
    ]);
  });

  it("marks closed and buffered periods unavailable while double-bookable events stay available", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json(bookingResponse([booking(1, "IN1", "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z")])),
      ),
    );
    const restricted = {
      globalId: "IN1",
      timezone: "UTC",
      openingHours: [{ dayOfWeek: 1, start: "08:00", end: "18:00" }],
      bufferBeforeMinutes: 10,
      bufferAfterMinutes: 20,
      allowDoubleBooking: false,
    };

    const unavailable = await loadCalendarAvailability(
      [restricted],
      "2026-08-17",

      new AbortController().signal,
    );
    expect(unavailable.get("IN1")).toEqual([
      sourced(
        closureId("IN1", "2026-08-17T00:00:00Z", "2026-08-17T08:00:00Z"),
        "blockout",
        "2026-08-17T00:00:00Z",
        "2026-08-17T08:00:00Z",
      ),
      sourced(
        closureId("IN1", "2026-08-17T18:00:00Z", "2026-08-18T00:00:00Z"),
        "blockout",
        "2026-08-17T18:00:00Z",
        "2026-08-18T00:00:00Z",
      ),
      sourced(
        "booking:1",
        "booking",
        "2026-08-17T09:40:00Z",
        "2026-08-17T11:20:00Z",
        "2026-08-17T10:00:00Z",
        "2026-08-17T11:00:00Z",
      ),
    ]);

    const available = await loadCalendarAvailability(
      [{ ...restricted, allowDoubleBooking: true }],
      "2026-08-17",

      new AbortController().signal,
    );
    expect(available.get("IN1")?.filter(({ kind }) => kind === "booking")).toEqual([]);
  });

  it("uses the shared maximum gap in query bounds and allows its exact boundary", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(
          bookingResponse([
            booking(1, "IN1", "2026-08-17T09:00:00Z", "2026-08-17T09:40:00Z"),
            booking(2, "IN1", "2026-08-17T09:00:00Z", "2026-08-17T09:41:00Z"),
            booking(3, "IN1", "2026-08-17T11:19:00Z", "2026-08-17T12:00:00Z"),
            booking(4, "IN1", "2026-08-17T11:20:00Z", "2026-08-17T12:00:00Z"),
          ]),
        );
      }),
    );
    const result = await loadCalendarAvailability(
      [{ globalId: "IN1", timezone: "UTC", ...schedule, bufferBeforeMinutes: 10, bufferAfterMinutes: 20 }],
      displayInterval("2026-08-17", "UTC", "10:00", "11:00"),

      new AbortController().signal,
    );

    expect(requests).toHaveLength(1);
    expect(requests[0].searchParams.get("where")).toBe(
      "target=in=(IN1);start=lt=2026-08-17T11:20:00Z;end=gt=2026-08-17T09:40:00Z;state==CONFIRMED",
    );
    expect(result.get("IN1")).toEqual([
      sourced(
        "booking:2",
        "booking",
        "2026-08-17T10:00:00Z",
        "2026-08-17T10:01:00Z",
        "2026-08-17T09:00:00Z",
        "2026-08-17T09:41:00Z",
      ),
      sourced(
        "booking:3",
        "booking",
        "2026-08-17T10:59:00Z",
        "2026-08-17T11:00:00Z",
        "2026-08-17T11:19:00Z",
        "2026-08-17T12:00:00Z",
      ),
    ]);
  });

  it("honors cancellation before starting a batch", async () => {
    const controller = new AbortController();
    controller.abort();

    await expect(
      loadCalendarAvailability(
        [{ globalId: "IN1", timezone: "UTC", ...schedule }],
        "2026-08-17",

        controller.signal,
      ),
    ).rejects.toThrow();
  });

  it("rejects the complete availability result when a later target batch fails", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        if (url.searchParams.get("where")?.includes("IN0500")) {
          return HttpResponse.json({ message: "later batch failed" }, { status: 500 });
        }
        return HttpResponse.json(bookingResponse([]));
      }),
    );
    const rows = Array.from({ length: 501 }, (_, index) => ({
      globalId: `IN${String(index).padStart(4, "0")}`,
      timezone: "UTC",
      ...schedule,
    }));

    await expect(loadCalendarAvailability(rows, "2026-08-17", new AbortController().signal)).rejects.toMatchObject({
      status: 500,
    });
    expect(requests).toHaveLength(2);
  });

  it.each([414, 431])("splits a calendar target batch after HTTP %s", async (status) => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        const targetList = (url.searchParams.get("where") ?? "").split("target=in=(")[1]?.split(")")[0] ?? "";
        if (targetList.split(",").length > 1) return new HttpResponse(null, { status });
        return HttpResponse.json(bookingResponse([]));
      }),
    );
    const rows = ["IN1", "IN2"].map((globalId) => ({ globalId, timezone: "UTC", ...schedule }));

    const result = await loadCalendarAvailability(rows, "2026-08-17", new AbortController().signal);

    expect(requests).toHaveLength(3);
    expect(requests.map((url) => (url.searchParams.get("where") ?? "").split("target=in=(")[1]?.split(")")[0])).toEqual(
      ["IN1,IN2", "IN1", "IN2"],
    );
    expect(result.size).toBe(2);
  });

  it("rejects the complete availability result when a later target batch is aborted", async () => {
    const controller = new AbortController();
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        if (requests.length === 2) controller.abort();
        return HttpResponse.json(bookingResponse([]));
      }),
    );
    const rows = Array.from({ length: 501 }, (_, index) => ({
      globalId: `IN${String(index).padStart(4, "0")}`,
      timezone: "UTC",
      ...schedule,
    }));

    await expect(loadCalendarAvailability(rows, "2026-08-17", controller.signal)).rejects.toThrow();
    expect(requests).toHaveLength(2);
  });

  it.each([
    {
      date: "2026-08-17",
      closures: [
        ["2026-08-17T00:00:00Z", "2026-08-17T08:00:00Z"],
        ["2026-08-17T17:00:00Z", "2026-08-18T00:00:00Z"],
      ],
    },
    { date: "2026-08-18", closures: [["2026-08-18T00:00:00Z", "2026-08-18T10:00:00Z"]] },
    { date: "2026-08-19", closures: [["2026-08-19T00:00:00Z", "2026-08-20T00:00:00Z"]] },
  ])("uses the independent weekday schedule on $date", async ({ date, closures }) => {
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(bookingResponse([]))));
    const result = await loadCalendarAvailability(
      [
        {
          globalId: "IN1",
          timezone: "UTC",
          ...schedule,
          openingHours: [
            { dayOfWeek: 1, start: "08:00", end: "17:00" },
            { dayOfWeek: 2, start: "10:00", end: "24:00" },
          ],
        },
      ],
      date,

      new AbortController().signal,
    );
    expect(result.get("IN1")).toEqual(
      closures.map(([start, end]) => sourced(closureId("IN1", start, end), "blockout", start, end)),
    );
  });
});
