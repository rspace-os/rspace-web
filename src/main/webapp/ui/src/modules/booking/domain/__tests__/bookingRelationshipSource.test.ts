import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { bookingInstrumentSource, bookingRelationshipSources } from "../bookingRelationshipSource";

const signal = () => new AbortController().signal;
describe("booking relationship source", () => {
  it("overrides the instruments picker with the booking items source", () => {
    expect(bookingRelationshipSources).toHaveProperty("instruments", bookingInstrumentSource);
    expect(bookingInstrumentSource.id).toBe("items");
  });
  it("resolves archived targets through configurations without using inventory access", async () => {
    server.use(
      http.get("/api/v2/booking-configurations", ({ request }) => {
        const params = new URL(request.url).searchParams;
        expect(params.get("where")).toBe("target=in=(IN42)");
        expect(params.get("depth")).toBe("1");
        return HttpResponse.json({
          limit: 1,
          page: 1,
          docs: [
            {
              id: 7,
              target: { relationTo: "instruments", globalId: "IN42", value: { id: 42, name: "Archived centrifuge" } },
            },
          ],
        });
      }),
    );
    await expect(bookingInstrumentSource.resolveMany?.(["IN42"], signal())).resolves.toEqual({
      IN42: { id: 42, globalId: "IN42", name: "Archived centrifuge" },
    });
  });
  it("returns unavailable rather than dropping an unresolvable saved ID", async () => {
    server.use(
      http.get("/api/v2/booking-configurations", () => HttpResponse.json({ docs: [], page: 1, limit: 1 })),
      http.get("/api/v2/bookings", () => HttpResponse.json({ docs: [], page: 1, limit: 1 })),
    );
    await expect(bookingInstrumentSource.resolveMany?.(["IN42"], signal())).resolves.toEqual({});
    expect(bookingInstrumentSource.ownsValue("IN42")).toBe(true);
  });

  it("does not treat an unsafe numeric ID as a booking relationship", async () => {
    const request = vi.fn();
    server.use(
      http.get("/api/v2/booking-configurations", () => {
        request();
        return HttpResponse.json({ docs: [], page: 1, limit: 1 });
      }),
      http.get("/api/v2/bookings", () => {
        request();
        return HttpResponse.json({ docs: [], page: 1, limit: 1 });
      }),
    );

    const unsafe = "IN9007199254740992";
    expect(bookingInstrumentSource.ownsValue(unsafe)).toBe(false);
    await expect(bookingInstrumentSource.resolveMany?.([unsafe], signal())).resolves.toEqual({});
    expect(request).not.toHaveBeenCalled();
  });
});
