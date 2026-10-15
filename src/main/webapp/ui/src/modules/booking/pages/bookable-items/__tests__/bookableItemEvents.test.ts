import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { BOOKING_READ_FIELDS } from "@/modules/booking/domain/booking";
import { fetchBookableItemEvents } from "../bookableItemEvents";
import { bookableItemEventResponse, booking } from "./bookableItemEventFixtures";

describe("bookable item events", () => {
  it.each([
    ["upcoming", "end=gt=2026-08-24T12:00:00Z", "start,id"],
    ["past", "end=le=2026-08-24T12:00:00Z", "-end,-id"],
  ] as const)("queries %s events with stable pagination", async (period, boundary, sort) => {
    let request: Request | undefined;
    server.use(
      http.get("/api/v2/bookings", ({ request: received }) => {
        request = received;
        return HttpResponse.json(bookableItemEventResponse([booking], { page: 3 }));
      }),
    );

    await expect(
      fetchBookableItemEvents({
        globalId: "IN12",
        period,
        cutoff: "2026-08-24T12:00:00Z",
        page: 2,
        limit: 10,
        token: "token",
      }),
    ).resolves.toEqual(bookableItemEventResponse([booking], { page: 3 }));

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
      canCancel: false,
    };
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(bookableItemEventResponse([booking, busy]))));

    await expect(
      fetchBookableItemEvents({
        globalId: "IN12",
        period: "upcoming",
        cutoff: "2026-08-24T12:00:00Z",
        page: 0,
        limit: 10,
        token: "token",
      }),
    ).resolves.toEqual(bookableItemEventResponse([booking, busy]));
  });

  it("escapes the target global id", async () => {
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        expect(new URL(request.url).searchParams.get("where")).toContain('target=="IN1;state==CANCELLED"');
        return HttpResponse.json(
          bookableItemEventResponse([
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
      limit: 10,
      token: "token",
    });
  });

  it("rejects malformed, mismatched-target, and non-success responses", async () => {
    const input = {
      globalId: "IN12",
      period: "upcoming" as const,
      cutoff: "2026-08-24T12:00:00Z",
      page: 0,
      limit: 10,
      token: "token",
    };
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(bookableItemEventResponse([{ id: 1 }]))));
    await expect(fetchBookableItemEvents(input)).rejects.toThrow();

    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json(bookableItemEventResponse([{ ...booking, target: { ...booking.target, globalId: "IN99" } }])),
      ),
    );
    await expect(fetchBookableItemEvents(input)).rejects.toThrow("another target");

    server.use(http.get("/api/v2/bookings", () => new HttpResponse(null, { status: 403 })));
    await expect(fetchBookableItemEvents(input)).rejects.toThrow("status 403");
  });
});
