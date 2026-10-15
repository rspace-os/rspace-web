import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { BOOKING_READ_FIELDS } from "@/modules/booking/domain/booking";
import { displayInterval } from "@/modules/booking/domain/bookingTime";
import { collectionResponse } from "../../mocks/bookingCalendarFixtures";
import { loadCalendarAvailability } from "../calendarAvailability";

const booking = (id: number, target: string, start: string, end: string) => ({
  id,
  version: 0,
  target: {
    relationTo: "booking-instruments",
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
          },
        }
      : {}),
  },
});

const closureId = (globalId: string, startsAt: string, endsAt: string) =>
  `opening-hours:${globalId}:${new Date(startsAt).toISOString()}:${new Date(endsAt).toISOString()}`;

describe("calendar availability", () => {
  it("classifies maintenance as a blockout while retaining its details and buffers", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json(
          collectionResponse([
            { ...booking(1, "IN1", "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z"), kind: "MAINTENANCE" },
          ]),
        ),
      ),
    );
    const result = await loadCalendarAvailability(
      [{ globalId: "IN1", timezone: "UTC", ...schedule, bufferBeforeMinutes: 10, allowDoubleBooking: true }],
      "2026-08-17",
      "token",
      new AbortController().signal,
    );
    expect(result.get("IN1")).toEqual([
      expect.objectContaining({
        kind: "blockout",
        startsAt: new Date("2026-08-17T09:50:00Z"),
        endsAt: new Date("2026-08-17T11:00:00Z"),
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
          collectionResponse([
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
      "token",
      new AbortController().signal,
    );

    expect(requests).toHaveLength(1);
    expect(requests[0].searchParams.get("where")).toContain("target=in=(IN1,IN2)");
    expect(requests[0].searchParams.get("fields[bookings]")).toBe(BOOKING_READ_FIELDS);
    expect(result.get("IN1")).toEqual([
      sourced("booking:1", "booking", "2026-03-28T23:00:00Z", "2026-03-29T01:00:00Z", "2026-03-28T22:00:00Z"),
    ]);
    expect(result.get("IN2")).toHaveLength(1);
    expect([...result.values()].flat().every((interval) => interval.kind === "booking")).toBe(true);
  });

  it("fetches every page and fails explicitly above the cap", async () => {
    const pages: string[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const page = new URL(request.url).searchParams.get("page") ?? "1";
        pages.push(page);
        return HttpResponse.json(
          page === "1"
            ? collectionResponse([booking(1, "IN1", "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z")], {
                page: 1,
                totalPages: 2,
                totalDocs: 2,
              })
            : collectionResponse([booking(2, "IN1", "2026-08-17T12:00:00Z", "2026-08-17T13:00:00Z")], {
                page: 2,
                totalPages: 2,
                totalDocs: 2,
              }),
        );
      }),
    );
    const rows = [{ globalId: "IN1", timezone: "UTC", ...schedule }];
    const result = await loadCalendarAvailability(rows, "2026-08-17", "token", new AbortController().signal);
    expect(pages).toEqual(["1", "2"]);
    expect(result.get("IN1")).toHaveLength(2);

    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json(collectionResponse([], { page: 1, totalPages: 11, totalDocs: 1001 })),
      ),
    );
    await expect(loadCalendarAvailability(rows, "2026-08-17", "token", new AbortController().signal)).rejects.toThrow(
      "exceeds 1,000",
    );
  });

  it("generates and clips closures for every scheduling date overlapped by the display interval", async () => {
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([]))));
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
      "token",
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
        HttpResponse.json(collectionResponse([booking(1, "IN1", "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z")])),
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
      "token",
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
        "2026-08-17T09:50:00Z",
        "2026-08-17T11:20:00Z",
        "2026-08-17T10:00:00Z",
        "2026-08-17T11:00:00Z",
      ),
    ]);

    const available = await loadCalendarAvailability(
      [{ ...restricted, allowDoubleBooking: true }],
      "2026-08-17",
      "token",
      new AbortController().signal,
    );
    expect(available.get("IN1")?.filter(({ kind }) => kind === "booking")).toEqual([]);
  });

  it("honors cancellation before starting a batch", async () => {
    const controller = new AbortController();
    controller.abort();

    await expect(
      loadCalendarAvailability(
        [{ globalId: "IN1", timezone: "UTC", ...schedule }],
        "2026-08-17",
        "token",
        controller.signal,
      ),
    ).rejects.toThrow();
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
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([]))));
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
      "token",
      new AbortController().signal,
    );
    expect(result.get("IN1")).toEqual(
      closures.map(([start, end]) => sourced(closureId("IN1", start, end), "blockout", start, end)),
    );
  });
});
