import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { fetchBookingCatalogue } from "../bookingCatalogue";

describe("calendar catalogue", () => {
  it("sends the half-open window as start and end", async () => {
    server.use(
      http.get("/api/v2/booking-catalogue/calendar", ({ request }) => {
        const params = new URL(request.url).searchParams;
        if ([...params.keys()].some((key) => !["page", "limit", "totals", "start", "end"].includes(key))) {
          return new HttpResponse(null, { status: 400 });
        }
        expect(params.get("totals")).toBe("true");
        expect(params.get("start")).toBe("2026-10-30T00:00:00Z");
        expect(params.get("end")).toBe("2026-10-31T00:00:00Z");
        return HttpResponse.json({ items: [], page: 1, pageSize: 20, total: 0, facets: { types: [] } });
      }),
    );
    await expect(
      fetchBookingCatalogue({ start: "2026-10-30T00:00:00Z", end: "2026-10-31T00:00:00Z" }, "token"),
    ).resolves.toMatchObject({ items: [] });
  });
});
