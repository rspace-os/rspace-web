import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { BookingCatalogueManagerPage } from "@/modules/common/api/v2/generated/models/bookingCatalogueManagerPage";
import { cataloguePage, confocalTarget } from "../../creation/__tests__/creationTestFixtures";
import { fetchBookingCatalogue, mapBookingCatalogueItem } from "../bookingCatalogue";

describe("calendar catalogue", () => {
  it("sends the half-open window as start and end", async () => {
    server.use(
      http.get("/api/v2/booking-catalogue/calendar", ({ request }) => {
        const params = new URL(request.url).searchParams;
        if ([...params.keys()].some((key) => !["page", "limit", "mine", "start", "end"].includes(key))) {
          return new HttpResponse(null, { status: 400 });
        }
        expect(params.has("totals")).toBe(false);
        expect(params.get("start")).toBe("2026-10-30T00:00:00Z");
        expect(params.get("end")).toBe("2026-10-31T00:00:00Z");
        return HttpResponse.json({ items: [], page: 1, pageSize: 20, total: 0, facets: { types: [] } });
      }),
    );
    await expect(
      fetchBookingCatalogue({ start: "2026-10-30T00:00:00Z", end: "2026-10-31T00:00:00Z" }),
    ).resolves.toMatchObject({ items: [] });
  });
});

it("serializes catalogue type and location filters as repeated keys", async () => {
  server.use(
    http.get<never, never, BookingCatalogueManagerPage>("/api/v2/booking-catalogue", ({ request }) => {
      const params = new URL(request.url).searchParams;
      expect(params.getAll("type")).toEqual(["INSTRUMENT", "INSTRUMENT"]);
      expect(params.getAll("location")).toEqual(["IC2", "BE3"]);
      return HttpResponse.json({ items: [], page: 1, pageSize: 20, total: 0, facets: { types: [] } });
    }),
  );
  await expect(
    fetchBookingCatalogue({ types: ["INSTRUMENT", "INSTRUMENT"], locations: ["IC2", "BE3"] }),
  ).resolves.toMatchObject({ items: [] });
});

it.each([{ slotGranularityMinutes: 0 }, { bufferBeforeMinutes: 10081 }])(
  "preserves catalogue scheduling policy checks for %o",
  (invalid) => {
    const item = cataloguePage([confocalTarget]).items[0];
    expect(mapBookingCatalogueItem(item)).toMatchObject({ globalId: confocalTarget.globalId });
    expect(() => mapBookingCatalogueItem({ ...item, ...invalid })).toThrow();
  },
);
