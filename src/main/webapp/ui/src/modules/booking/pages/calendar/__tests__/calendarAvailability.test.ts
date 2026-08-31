import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { displayInterval } from "@/modules/booking/domain/bookingTime";
import { loadCalendarAvailability, loadDatedCalendarAvailability } from "../calendarAvailability";

function envelope(docs: unknown[], page = 1, totalPages = 1, totalDocs = docs.length) {
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

const booking = (id: number, target: string, start: string, end: string) => ({
  id,
  target: {
    relationTo: "booking-instruments",
    value: { id, name: target, deleted: false },
    globalId: target,
  },
  timezone: "Europe/Berlin",
  start,
  end,
  state: "CONFIRMED",
});

const schedule = {
  openingStart: "00:00",
  openingEnd: "24:00",
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
  },
});

const closureId = (globalId: string, startsAt: string, endsAt: string) =>
  `opening-hours:${globalId}:${new Date(startsAt).toISOString()}:${new Date(endsAt).toISOString()}`;

describe("calendar availability", () => {
  it("uses one minimal batched query and clips results to each row's zoned day", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(
          envelope([
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
    expect(requests[0].searchParams.get("fields[bookings]")).toBe("id,target,timezone,start,end,state,kind");
    expect(requests[0].searchParams.get("fields[bookings]")).not.toContain("purpose");
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
            ? envelope([booking(1, "IN1", "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z")], 1, 2, 2)
            : envelope([booking(2, "IN1", "2026-08-17T12:00:00Z", "2026-08-17T13:00:00Z")], 2, 2, 2),
        );
      }),
    );
    const rows = [{ globalId: "IN1", timezone: "UTC", ...schedule }];
    const result = await loadCalendarAvailability(rows, "2026-08-17", "token", new AbortController().signal);
    expect(pages).toEqual(["1", "2"]);
    expect(result.get("IN1")).toHaveLength(2);

    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(envelope([], 1, 11, 1001))));
    await expect(loadCalendarAvailability(rows, "2026-08-17", "token", new AbortController().signal)).rejects.toThrow(
      "exceeds 1,000",
    );
  });

  it("uses one envelope while clipping rows to different local dates", async () => {
    let requestUrl: URL | undefined;
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        requestUrl = new URL(request.url);
        return HttpResponse.json(
          envelope([
            booking(1, "IN1", "2026-08-17T22:00:00Z", "2026-08-18T02:00:00Z"),
            booking(2, "IN2", "2026-08-18T03:00:00Z", "2026-08-18T06:00:00Z"),
          ]),
        );
      }),
    );

    const result = await loadDatedCalendarAvailability(
      [
        { globalId: "IN1", timezone: "Asia/Tokyo", date: "2026-08-18", ...schedule },
        { globalId: "IN2", timezone: "America/Los_Angeles", date: "2026-08-17", ...schedule },
      ],
      "token",
      new AbortController().signal,
    );

    const where = requestUrl?.searchParams.get("where") ?? "";
    expect(where).toContain("start<2026-08-18T15:00:00Z");
    expect(where).toContain("end>2026-08-17T07:00:00Z");
    expect(result.get("IN1")).toEqual([
      sourced("booking:1", "booking", "2026-08-17T22:00:00Z", "2026-08-18T02:00:00Z"),
    ]);
    expect(result.get("IN2")).toEqual([
      sourced("booking:2", "booking", "2026-08-18T03:00:00Z", "2026-08-18T06:00:00Z"),
    ]);
  });

  it("generates and clips closures for every scheduling date overlapped by the display interval", async () => {
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(envelope([]))));
    const interval = displayInterval("2026-08-18", "Pacific/Auckland", "00:00", "24:00");

    const result = await loadCalendarAvailability(
      [
        {
          globalId: "IN1",
          timezone: "America/Los_Angeles",
          openingStart: "08:00",
          openingEnd: "18:00",
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
        closureId("IN1", "2026-08-17T07:00:00Z", "2026-08-17T15:00:00Z"),
        "blockout",
        "2026-08-17T12:00:00Z",
        "2026-08-17T15:00:00Z",
        "2026-08-17T07:00:00Z",
      ),
      sourced(
        closureId("IN1", "2026-08-18T01:00:00Z", "2026-08-18T07:00:00Z"),
        "blockout",
        "2026-08-18T01:00:00Z",
        "2026-08-18T07:00:00Z",
      ),
      sourced(
        closureId("IN1", "2026-08-18T07:00:00Z", "2026-08-18T15:00:00Z"),
        "blockout",
        "2026-08-18T07:00:00Z",
        "2026-08-18T12:00:00Z",
        "2026-08-18T07:00:00Z",
        "2026-08-18T15:00:00Z",
      ),
    ]);
  });

  it("marks closed and buffered periods unavailable while double-bookable events stay available", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json(envelope([booking(1, "IN1", "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z")])),
      ),
    );
    const restricted = {
      globalId: "IN1",
      timezone: "UTC",
      openingStart: "08:00",
      openingEnd: "18:00",
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
});
