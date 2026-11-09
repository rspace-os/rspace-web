import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { toTimelineEvent } from "@/modules/booking/components/toTimelineEvent";
import type { BookingsRead } from "@/modules/common/api/v2/generated/models/bookingsRead";
import type { ListBookings200 } from "@/modules/common/api/v2/generated/models/listBookings200";
import { fetchDayBookings } from "../fetchDayBookings";

const bounds = { start: "2026-09-25T00:00:00Z", end: "2026-09-26T00:00:00Z" };
const busy: BookingsRead = {
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
  cancellationReason: null,
  bookedBy: null,
  canEdit: false,
  canCancel: false,
  createdAt: bounds.start,
  updatedAt: bounds.start,
};
const pageEnvelope = (docs: readonly BookingsRead[], page = 1, hasNextPage = false): ListBookings200 => ({
  docs: [...docs],
  page,
  hasNextPage,
  limit: 100,
});
const load = () => fetchDayBookings(["IN1"], bounds, new AbortController().signal);

describe("day schedule fetch", () => {
  it("uses overlap projection and includes every page without exposing busy details", async () => {
    const pages: string[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const params = new URL(request.url).searchParams;
        expect(params.get("where")).toContain(
          `target=in=(IN1);start=lt=${bounds.end};end=gt=${bounds.start};state==CONFIRMED`,
        );
        expect(params.get("depth")).toBe("1");
        expect(params.has("totals")).toBe(false);
        expect(params.get("fields[instruments]")).toBe("id,globalId,name,deleted");
        const page = pages.length + 1;
        expect(params.get("page")).toBe("1");
        if (page === 2)
          expect(params.get("where")).toContain(`;(start=gt=${busy.start},(start==${busy.start};id=gt=1))`);
        pages.push(String(page));
        return HttpResponse.json(pageEnvelope([{ ...busy, id: page }], 1, page === 1));
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
    let count = 0;
    server.use(
      http.get("/api/v2/bookings", () => {
        const page = ++count;
        return HttpResponse.json(
          pageEnvelope(
            Array.from({ length: 100 }, (_, index) => ({ ...busy, id: (page - 1) * 100 + index })),
            1,
            true,
          ),
        );
      }),
    );
    await expect(load()).rejects.toThrow("1,000");
  });

  it("fails the whole schedule when a later page fails", async () => {
    server.use(
      http.get("/api/v2/bookings", ({ request }) =>
        !new URL(request.url).searchParams.get("where")?.includes("id=gt=")
          ? HttpResponse.json(pageEnvelope([busy], 1, true))
          : new HttpResponse(null, { status: 503 }),
      ),
    );
    await expect(load()).rejects.toMatchObject({ status: 503 });
  });

  it("rejects repeated booking IDs across pages without totals", async () => {
    const pages: number[] = [];
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"));
        pages.push(page);
        return HttpResponse.json(pageEnvelope([busy], 1, pages.length === 1));
      }),
    );

    await expect(load()).rejects.toThrow("changed during pagination");
    expect(pages).toEqual([1, 1]);
  });

  it("rejects a page echo that differs from the request", async () => {
    server.use(http.get("/api/v2/bookings", () => HttpResponse.json(pageEnvelope([busy], 2, true))));
    await expect(load()).rejects.toThrow("inconsistent pagination metadata");
  });
});

it("does not skip the remaining tie after a row disappears between pages", async () => {
  const start = "2026-09-24T23:00:00.123456Z";
  let requests = 0;
  server.use(
    http.get("/api/v2/bookings", ({ request }) => {
      requests += 1;
      const params = new URL(request.url).searchParams;
      expect(params.get("page")).toBe("1");
      if (requests === 2) expect(params.get("where")).toContain(`;(start=gt=${start},(start==${start};id=gt=1))`);
      return HttpResponse.json(pageEnvelope([{ ...busy, start, id: requests }], 1, requests === 1));
    }),
  );
  expect((await load()).map(({ id }) => id)).toEqual([1, 2]);
  expect(requests).toBe(2);
});
