import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { BOOKING_READ_FIELDS } from "@/modules/booking/domain/booking";
import { fetchBookableItemEvents } from "../bookableItemEvents";

const booking = {
  id: 41,
  target: {
    relationTo: "instruments",
    value: { id: 12, name: "Scope", deleted: false },
    globalId: "IN12",
  },
  timezone: "Europe/Berlin",
  start: "2026-08-25T09:00:00Z",
  end: "2026-08-25T10:00:00Z",
  state: "CONFIRMED",
  privacy: "full",
  purpose: null,
  bookedBy: "Ada Lovelace (ada)",
  canEdit: true,
  createdAt: "2026-08-17T00:00:00Z",
  updatedAt: "2026-08-17T00:00:00Z",
};

function envelope(docs: unknown[], page = 1) {
  return {
    docs,
    totalDocs: docs.length,
    limit: 10,
    page,
    pagingCounter: (page - 1) * 10 + 1,
    totalPages: docs.length === 0 ? 0 : page,
    hasPrevPage: page > 1,
    hasNextPage: false,
    prevPage: page > 1 ? page - 1 : null,
    nextPage: null,
  };
}

describe("bookable item events", () => {
  it.each([
    ["upcoming", "end=gt=2026-08-24T12:00:00Z", "start,id"],
    ["past", "end=le=2026-08-24T12:00:00Z", "-end,-id"],
  ] as const)("queries %s events with stable pagination", async (period, boundary, sort) => {
    let request: Request | undefined;
    server.use(
      http.get("/api/v2/bookings", ({ request: received }) => {
        request = received;
        return HttpResponse.json(envelope([booking], 3));
      }),
    );

    await expect(
      fetchBookableItemEvents({
        globalId: "IN12",
        period,
        cutoff: "2026-08-24T12:00:00Z",
        page: 2,
        token: "token",
      }),
    ).resolves.toEqual(envelope([booking], 3));

    const url = new URL(request?.url ?? "http://localhost");
    expect(url.searchParams.get("where")).toBe(`target==IN12;state==CONFIRMED;${boundary}`);
    expect(url.searchParams.get("sort")).toBe(sort);
    expect(url.searchParams.get("page")).toBe("3");
    expect(url.searchParams.get("limit")).toBe("10");
    expect(url.searchParams.get("depth")).toBe("1");
    expect(url.searchParams.get("fields[bookings]")).toBe(BOOKING_READ_FIELDS);
    expect(request?.headers.get("Authorization")).toBe("Bearer token");
  });

  it("accepts server-shaped full and busy documents", async () => {
    const busy = {
      ...booking,
      id: 42,
      privacy: "busy",
      purpose: null,
      bookedBy: null,
      canEdit: false,
    };
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(envelope([booking, busy]))));

    await expect(
      fetchBookableItemEvents({
        globalId: "IN12",
        period: "upcoming",
        cutoff: "2026-08-24T12:00:00Z",
        page: 0,
        token: "token",
      }),
    ).resolves.toEqual(envelope([booking, busy]));
  });

  it("escapes the target global id", async () => {
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        expect(new URL(request.url).searchParams.get("where")).toContain('target=="IN1;state==CANCELLED"');
        return HttpResponse.json(
          envelope([
            {
              ...booking,
              target: { ...booking.target, globalId: "IN1;state==CANCELLED" },
            },
          ]),
        );
      }),
    );

    await fetchBookableItemEvents({
      globalId: "IN1;state==CANCELLED",
      period: "past",
      cutoff: "2026-08-24T12:00:00Z",
      page: 0,
      token: "token",
    });
  });

  it("rejects malformed, mismatched-target, and non-success responses", async () => {
    const input = {
      globalId: "IN12",
      period: "upcoming" as const,
      cutoff: "2026-08-24T12:00:00Z",
      page: 0,
      token: "token",
    };
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(envelope([{ id: 1 }]))));
    await expect(fetchBookableItemEvents(input)).rejects.toThrow();

    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json(envelope([{ ...booking, target: { ...booking.target, globalId: "IN99" } }])),
      ),
    );
    await expect(fetchBookableItemEvents(input)).rejects.toThrow("another target");

    server.use(http.get("/api/v2/bookings", () => new HttpResponse(null, { status: 403 })));
    await expect(fetchBookableItemEvents(input)).rejects.toThrow("status 403");
  });
});
