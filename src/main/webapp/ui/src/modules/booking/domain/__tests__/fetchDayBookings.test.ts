import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { toTimelineEvent } from "@/modules/booking/components/toTimelineEvent";
import { fetchDayBookings } from "../fetchDayBookings";

const bounds = { start: "2026-09-25T00:00:00Z", end: "2026-09-26T00:00:00Z" };
const busy = {
  id: 1,
  version: 0,
  target: { relationTo: "booking-instruments", globalId: "IN1", value: { id: 1, name: "Microscope", deleted: false } },
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
        expect(request.headers.get("Authorization")).toBe("Bearer test-token");
        const page = Number(params.get("page"));
        pages.push(String(page));
        return HttpResponse.json({
          docs: [{ ...busy, id: page }],
          totalDocs: 2,
          totalPages: 2,
          page,
          hasNextPage: page === 1,
        });
      }),
    );
    const bookings = await load();
    expect(pages).toEqual(["1", "2"]);
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
      http.get("/api/v2/bookings", () =>
        HttpResponse.json({ docs: [], totalDocs: 1001, totalPages: 11, page: 1, hasNextPage: true }),
      ),
    );
    await expect(load()).rejects.toThrow("1,000");
  });

  it("fails the whole schedule when a later page fails", async () => {
    server.use(
      http.get("/api/v2/bookings", ({ request }) =>
        new URL(request.url).searchParams.get("page") === "1"
          ? HttpResponse.json({ docs: [busy], totalDocs: 2, totalPages: 2, page: 1, hasNextPage: true })
          : new HttpResponse(null, { status: 503 }),
      ),
    );
    await expect(load()).rejects.toThrow("503");
  });

  it("rejects a pagination envelope that changes while loading", async () => {
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"));
        return HttpResponse.json({
          docs: [busy],
          totalDocs: 2,
          totalPages: page === 1 ? 2 : 3,
          page,
          hasNextPage: true,
        });
      }),
    );
    await expect(load()).rejects.toThrow("changed during pagination");
  });
});
