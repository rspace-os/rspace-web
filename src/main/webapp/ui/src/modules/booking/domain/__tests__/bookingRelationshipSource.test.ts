import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { bookingInstrumentSource } from "../bookingRelationshipSource";

const signal = () => new AbortController().signal;
describe("booking relationship source", () => {
  it("resolves archived targets through configurations without using inventory access", async () => {
    server.use(
      http.get("/api/v2/booking-configurations", ({ request }) => {
        const params = new URL(request.url).searchParams;
        expect(params.get("where")).toBe("target==IN42");
        expect(params.get("depth")).toBe("1");
        return HttpResponse.json({
          docs: [{ target: { globalId: "IN42", value: { id: 42, name: "Archived centrifuge" } } }],
        });
      }),
    );
    await expect(bookingInstrumentSource.resolve?.("IN42", "token", signal())).resolves.toEqual({
      id: 42,
      globalId: "IN42",
      name: "Archived centrifuge",
    });
  });
  it("does not resolve historical targets when the configuration is unavailable", async () => {
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json({ docs: [] })));
    await expect(bookingInstrumentSource.resolve?.("IN42", "token", signal())).resolves.toBeNull();
  });
  it("returns unavailable rather than dropping an unresolvable saved ID", async () => {
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json({ docs: [] })),
      http.get("/api/v2/bookings", () => HttpResponse.json({ docs: [] })),
    );
    await expect(bookingInstrumentSource.resolve?.("IN42", "token", signal())).resolves.toBeNull();
    expect(bookingInstrumentSource.ownsValue("IN42")).toBe(true);
  });

  it("does not treat an unsafe numeric ID as a booking relationship", async () => {
    const request = vi.fn();
    server.use(
      http.get("/api/v2/booking-configurations", () => {
        request();
        return HttpResponse.json({ docs: [] });
      }),
      http.get("/api/v2/bookings", () => {
        request();
        return HttpResponse.json({ docs: [] });
      }),
    );

    const unsafe = "IN9007199254740992";
    expect(bookingInstrumentSource.ownsValue(unsafe)).toBe(false);
    await expect(bookingInstrumentSource.resolve?.(unsafe, "token", signal())).resolves.toBeNull();
    expect(request).not.toHaveBeenCalled();
  });
});
