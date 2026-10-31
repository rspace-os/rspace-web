import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { toTimelineEvent } from "@/modules/booking/components/toTimelineEvent";
import { fetchDayBookings } from "../fetchDayBookings";

const bounds = { start: "2026-09-25T00:00:00Z", end: "2026-09-26T00:00:00Z" };
const busy = {
  id: 1,
  version: 0,
  target: { relationTo: "instruments", globalId: "IN1", value: { id: 1, name: "Microscope", deleted: false } },
  timezone: "UTC",
  start: "2026-09-24T23:00:00Z",
  end: "2026-09-25T01:00:00Z",
  state: "CONFIRMED",
  kind: "BOOKING",
  privacy: "busy",
  purpose: null,
  bookedBy: null,
  canEdit: false,
  canCancel: false,
  createdAt: bounds.start,
  updatedAt: bounds.start,
};
const pageEnvelope = (docs: readonly unknown[], page = 1, hasNextPage = false, nextPage: number | null = null) => ({
  docs,
  page,
  hasNextPage,
  nextPage,
  limit: 100,
  pagingCounter: (page - 1) * 100 + 1,
  hasPrevPage: page > 1,
  prevPage: page > 1 ? page - 1 : null,
});
const load = () => fetchDayBookings(["IN1"], bounds, "test-token", new AbortController().signal);

describe("day schedule fetch", () => {
  it("uses overlap projection and includes every page without exposing busy details", async () => {
    const pages: string[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const params = new URL(request.url).searchParams;
        expect(params.get("where")).toBe(
          `target=in=(IN1);start=lt=${bounds.end};end=gt=${bounds.start};state==CONFIRMED`,
        );
        expect(params.get("depth")).toBe("1");
        expect(params.has("totals")).toBe(false);
        expect(params.get("fields[instruments]")).toBe("id,globalId,name,deleted");
        expect(request.headers.get("Authorization")).toBe("Bearer test-token");
        const page = Number(params.get("page"));
        pages.push(String(page));
        return HttpResponse.json(pageEnvelope([{ ...busy, id: page }], page, page === 1, page === 1 ? 3 : null));
      }),
    );
    const bookings = await load();
    expect(pages).toEqual(["1", "3"]);
    expect(bookings).toHaveLength(2);
    expect(toTimelineEvent(bookings[0], "2026-09-25", "UTC")).toEqual({
      id: "1",
      kind: "booking",
      privacy: "busy",
      startMinute: -60,
      endMinute: 60,
      startInstant: busy.start,
      endInstant: busy.end,
      instrumentTimeZone: "UTC",
    });
  });

  it("rejects the result cap instead of presenting a partial free schedule", async () => {
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"));
        return HttpResponse.json(
          pageEnvelope(
            Array.from({ length: 100 }, (_, index) => ({ ...busy, id: (page - 1) * 100 + index })),
            page,
            true,
            page + 1,
          ),
        );
      }),
    );
    await expect(load()).rejects.toThrow("1,000");
  });

  it("fails the whole schedule when a later page fails", async () => {
    server.use(
      http.get("/api/v2/bookings", ({ request }) =>
        new URL(request.url).searchParams.get("page") === "1"
          ? HttpResponse.json(pageEnvelope([busy], 1, true))
          : new HttpResponse(null, { status: 503 }),
      ),
    );
    await expect(load()).rejects.toThrow("503");
  });

  it("rejects repeated booking IDs across pages without totals", async () => {
    const pages: number[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"));
        pages.push(page);
        return HttpResponse.json(pageEnvelope([busy], page, page === 1));
      }),
    );

    await expect(load()).rejects.toThrow("changed during pagination");
    expect(pages).toEqual([1, 2]);
  });

  it("rejects a nextPage that repeats the current page", async () => {
    const nextPage = 1;
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(pageEnvelope([busy], 1, true, nextPage))));
    await expect(load()).rejects.toThrow("inconsistent pagination metadata");
  });
});
