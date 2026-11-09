import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { ListBookingConfigurations200 } from "@/modules/common/api/v2/generated/models/listBookingConfigurations200";
import type { ListBookings200 } from "@/modules/common/api/v2/generated/models/listBookings200";
import { bookingInstrumentSource, bookingRelationshipSources } from "../bookingRelationshipSource";

const signal = () => new AbortController().signal;
describe("booking relationship source", () => {
  it("overrides the instruments picker with the booking items source", () => {
    expect(bookingRelationshipSources).toHaveProperty("instruments", bookingInstrumentSource);
    expect(bookingInstrumentSource.id).toBe("items");
  });
  it("resolves archived targets through configurations without using inventory access", async () => {
    server.use(
      http.get<never, never, ListBookingConfigurations200>("/api/v2/booking-configurations", ({ request }) => {
        const params = new URL(request.url).searchParams;
        expect(params.get("where")).toBe("target=in=(IN42)");
        expect(params.get("depth")).toBe("1");
        return HttpResponse.json({
          limit: 1,
          page: 1,
          docs: [
            {
              id: 7,
              target: {
                relationTo: "instruments",
                globalId: "IN42",
                value: { id: 42, name: "Archived centrifuge" },
              },
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
      http.get<never, never, ListBookingConfigurations200>("/api/v2/booking-configurations", () =>
        HttpResponse.json({ docs: [], page: 1, limit: 1 }),
      ),
      http.get<never, never, ListBookings200>("/api/v2/bookings", () =>
        HttpResponse.json({ docs: [], page: 1, limit: 1 }),
      ),
    );
    await expect(bookingInstrumentSource.resolveMany?.(["IN42"], signal())).resolves.toEqual({});
    expect(bookingInstrumentSource.ownsValue("IN42")).toBe(true);
  });

  it("does not treat an unsafe numeric ID as a booking relationship", async () => {
    const request = vi.fn();
    server.use(
      http.get<never, never, ListBookingConfigurations200>("/api/v2/booking-configurations", () => {
        request();
        return HttpResponse.json({ docs: [], page: 1, limit: 1 });
      }),
      http.get<never, never, ListBookings200>("/api/v2/bookings", () => {
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

it("restores archived targets in one bounded target=in request", async () => {
  let requested: URL | undefined;
  server.use(
    http.get<never, never, ListBookingConfigurations200>("/api/v2/booking-configurations", ({ request }) => {
      requested = new URL(request.url);
      return HttpResponse.json({
        page: 1,
        limit: 2,
        docs: [
          {
            id: 42,
            target: {
              relationTo: "instruments",
              globalId: "IN42",
              value: { id: 42, name: "Archived centrifuge" },
            },
          },
          {
            id: 43,
            target: {
              relationTo: "instruments",
              globalId: "IN43",
              value: { id: 43, name: "Archived microscope" },
            },
          },
        ],
      });
    }),
  );

  await expect(bookingInstrumentSource.resolveMany?.(["in0042", "IN43"], signal())).resolves.toEqual({
    IN42: { id: 42, name: "Archived centrifuge", globalId: "IN42" },
    IN43: { id: 43, name: "Archived microscope", globalId: "IN43" },
  });
  expect(bookingInstrumentSource.normalizeValue?.("in0042")).toBe("IN42");
  expect(requested?.searchParams.get("where")).toBe("target=in=(IN42,IN43)");
  expect(requested?.searchParams.get("limit")).toBe("2");
  expect(requested?.searchParams.get("depth")).toBe("1");
});
