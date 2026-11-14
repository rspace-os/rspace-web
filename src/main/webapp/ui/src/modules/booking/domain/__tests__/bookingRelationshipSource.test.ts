import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { BookingCatalogueManagerLocationPage } from "@/modules/common/api/v2/generated/models/bookingCatalogueManagerLocationPage";
import type { BookingCatalogueManagerPage } from "@/modules/common/api/v2/generated/models/bookingCatalogueManagerPage";
import type { ListBookingConfigurations200 } from "@/modules/common/api/v2/generated/models/listBookingConfigurations200";
import type { ListBookings200 } from "@/modules/common/api/v2/generated/models/listBookings200";
import {
  bookingInstrumentSource,
  bookingLocationSource,
  bookingRelationshipSources,
} from "../bookingRelationshipSource";

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

it("lists catalogue items before the user types", async () => {
  let requested: URL | undefined;
  server.use(
    http.get<never, never, BookingCatalogueManagerPage>("/api/v2/booking-catalogue", ({ request }) => {
      requested = new URL(request.url);
      return HttpResponse.json({ items: [], page: 1, pageSize: 20, total: 0, facets: { types: [] } });
    }),
  );

  expect(bookingInstrumentSource.browsable).toBe(true);
  await expect(bookingInstrumentSource.search("", signal())).resolves.toEqual([]);
  expect(requested?.searchParams.has("q")).toBe(false);
  expect(requested?.searchParams.has("totals")).toBe(false);
});

describe("booking location source", () => {
  it("uses a catalogue-scoped container source", () => {
    expect(bookingRelationshipSources.containers).toBe(bookingLocationSource);
    expect(bookingLocationSource.id).toBe("locations");
  });
  it("browses readable locations and filters a workbench by its Container ID", async () => {
    let requested: URL | undefined;
    server.use(
      http.get<never, never, BookingCatalogueManagerLocationPage>(
        "/api/v2/booking-catalogue/locations",
        ({ request }) => {
          requested = new URL(request.url);
          return HttpResponse.json({
            items: [
              { globalId: "IC12", name: "Cold room" },
              { globalId: "BE7", name: "WB ada" },
            ],
            page: 1,
            pageSize: 20,
            total: 2,
          });
        },
      ),
    );

    const options = (await bookingLocationSource.search("", signal())).map((document) =>
      bookingLocationSource.toOption(document, {}),
    );

    expect(bookingLocationSource.browsable).toBe(true);
    expect(requested?.searchParams.has("q")).toBe(false);
    expect(requested?.searchParams.has("totals")).toBe(false);
    expect(options.map(({ value, label }) => ({ value, label }))).toEqual([
      { value: "IC12", label: "Cold room" },
      { value: "IC7", label: "WB ada" },
    ]);
  });

  it("restores saved locations in one global ID batch, keyed by the filter value", async () => {
    let requested: URL | undefined;
    server.use(
      http.get<never, never, BookingCatalogueManagerLocationPage>(
        "/api/v2/booking-catalogue/locations",
        ({ request }) => {
          requested = new URL(request.url);
          return HttpResponse.json({
            items: [
              { globalId: "IC12", name: "Cold room" },
              { globalId: "BE7", name: "WB ada" },
            ],
            page: 1,
            pageSize: 2,
            total: 2,
          });
        },
      ),
    );

    await expect(bookingLocationSource.resolveMany?.(["IC12", "be7", "IC7"], signal())).resolves.toEqual({
      IC12: { globalId: "IC12", name: "Cold room" },
      IC7: { globalId: "BE7", name: "WB ada" },
    });
    expect(requested?.searchParams.getAll("globalId")).toEqual(["IC12", "IC7"]);
    expect(requested?.searchParams.get("limit")).toBe("2");
  });
});
