import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { cancelBooking, createBooking, fetchBooking, updateBooking } from "@/modules/booking/domain/booking";

const document = {
  id: 41,
  target: {
    relationTo: "instruments",
    value: { id: 12, name: "Scope", deleted: false },
    globalId: "IN12",
  },
  timezone: "Europe/Berlin",
  start: "2026-10-25T00:30:00Z",
  end: "2026-10-25T02:30:00Z",
  state: "CONFIRMED",
  privacy: "full",
  purpose: null,
  bookedBy: "Ada Lovelace (ada)",
  canEdit: true,
  createdAt: "2026-08-17T00:00:00Z",
  updatedAt: "2026-08-17T00:00:00Z",
};

describe("booking API", () => {
  it("uses strict read, POST, and PATCH contracts", async () => {
    const requests: Array<{ method: string; url: URL; body?: unknown }> = [];
    server.use(
      http.get("/api/v2/bookings/41", ({ request }) => {
        requests.push({ method: request.method, url: new URL(request.url) });
        return HttpResponse.json(document);
      }),
      http.post("/api/v2/bookings", async ({ request }) => {
        requests.push({ method: request.method, url: new URL(request.url), body: await request.json() });
        return HttpResponse.json(document);
      }),
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        requests.push({ method: request.method, url: new URL(request.url), body: await request.json() });
        return HttpResponse.json(document);
      }),
    );

    await fetchBooking(41, "token", new AbortController().signal);
    await createBooking(
      {
        target: { relationTo: "instruments", value: 12 },
        start: document.start,
        end: document.end,
        purpose: null,
      },
      "token",
    );
    await updateBooking(41, { start: document.start, end: document.end, purpose: "Plate" }, "token");

    expect(
      requests.map((request) => [request.method, request.url.pathname, request.url.searchParams.get("depth")]),
    ).toEqual([
      ["GET", "/api/v2/bookings/41", "1"],
      ["POST", "/api/v2/bookings", "1"],
      ["PATCH", "/api/v2/bookings/41", "1"],
    ]);
    expect(requests[1].body).toEqual({
      target: { relationTo: "instruments", value: 12 },
      start: document.start,
      end: document.end,
      purpose: null,
    });
    expect(requests[2].body).toEqual({ start: document.start, end: document.end, purpose: "Plate" });
    expect(requests[0].url.searchParams.get("fields[bookings]")).toBe(
      "id,target,timezone,start,end,state,purpose,bookedBy,privacy,canEdit,createdAt,updatedAt",
    );
  });

  it("rejects malformed success data and exposes stable problem codes", async () => {
    server.use(http.get("/api/v2/bookings/41", () => HttpResponse.json({ ...document, bookedBy: null })));
    await expect(fetchBooking(41, "token")).rejects.toThrow();

    server.use(
      http.get("/api/v2/bookings/41", () =>
        HttpResponse.json({ status: 409, code: "errors.api.v2.booking.overlap", detail: "Overlap" }, { status: 409 }),
      ),
    );
    await expect(fetchBooking(41, "token")).rejects.toEqual(
      expect.objectContaining({ status: 409, code: "errors.api.v2.booking.overlap" }),
    );
  });

  it("honors an aborted read", async () => {
    const controller = new AbortController();
    controller.abort();

    await expect(fetchBooking(41, "token", controller.signal)).rejects.toThrow();
  });

  it("cancels with the exact authenticated PATCH contract", async () => {
    let request: Request | undefined;
    server.use(
      http.patch("/api/v2/bookings/41", ({ request: nextRequest }) => {
        request = nextRequest;
        return HttpResponse.json({ ...document, state: "CANCELLED" });
      }),
    );

    const result = await cancelBooking(41, "token");

    expect(request?.method).toBe("PATCH");
    expect(new URL(request?.url ?? "http://localhost").searchParams.get("depth")).toBe("1");
    expect(await request?.json()).toEqual({ state: "CANCELLED" });
    expect(request?.headers.get("Authorization")).toBe("Bearer token");
    expect(request?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(result.state).toBe("CANCELLED");
  });

  it("preserves cancellation problem status and code", async () => {
    server.use(
      http.patch("/api/v2/bookings/41", () =>
        HttpResponse.json(
          { status: 409, code: "errors.api.v2.booking.state.transition", detail: "stale" },
          { status: 409 },
        ),
      ),
    );

    await expect(cancelBooking(41, "token")).rejects.toEqual(
      expect.objectContaining({ status: 409, code: "errors.api.v2.booking.state.transition" }),
    );
  });

  it.each([
    [400, "errors.api.v2.booking.window"],
    [409, "errors.api.v2.booking.target.unavailable"],
    [409, "errors.api.v2.booking.overlap"],
    [409, "errors.api.v2.booking.state.transition"],
    [403, "errors.api.v2.forbidden"],
  ])("preserves problem code %s", async (status, code) => {
    server.use(
      http.get("/api/v2/bookings/41", () => HttpResponse.json({ status, code, detail: "detail" }, { status })),
    );

    await expect(fetchBooking(41, "token")).rejects.toEqual(expect.objectContaining({ status, code }));
  });
});
