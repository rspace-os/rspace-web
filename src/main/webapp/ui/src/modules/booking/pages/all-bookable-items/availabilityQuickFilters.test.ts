import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { createElement, type ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { classifyCurrentDayAvailability } from "@/modules/booking/domain/availability";
import type { Booking } from "@/modules/booking/domain/booking";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import { type CalendarAvailabilityRow, rowAvailabilityIntervals } from "../calendar/calendarAvailability";
import {
  type AllBookableItem,
  keepAcrossMinutes,
  serverAvailabilityFilter,
  todayAvailabilityWindow,
  useAvailabilityCounts,
  withAvailability,
} from "./availabilityQuickFilters";

const itemA: FilterExpression<AllBookableItem> = {
  kind: "comparison",
  field: "target",
  operator: "equals",
  value: "IN1",
};
const itemB: FilterExpression<AllBookableItem> = {
  kind: "comparison",
  field: "target",
  operator: "equals",
  value: "IN2",
};
const availableNow: FilterExpression<AllBookableItem> = {
  kind: "comparison",
  field: "availability",
  operator: "equals",
  value: "available-now",
};
const freeLater: FilterExpression<AllBookableItem> = { ...availableNow, value: "free-later-today" };

describe("availability quick filters", () => {
  it("retains duplicate item and ID rules when a quick filter is changed or removed", () => {
    const original: FilterExpression<AllBookableItem> = {
      kind: "and",
      children: [{ kind: "comparison", field: "id", operator: "greaterThan", value: 5 }, itemA, itemB],
    };
    const added = withAvailability(original, "available-now");
    expect(withAvailability(added, undefined)).toEqual(original);
    expect(withAvailability(withAvailability(added, "free-later-today"), undefined)).toEqual(original);
  });

  it("sends a top-level availability rule as the catalogue parameter and keeps the item rules", () => {
    expect(serverAvailabilityFilter(null)).toEqual({ supported: true, where: null });
    expect(serverAvailabilityFilter(itemA)).toEqual({ supported: true, where: itemA });
    expect(serverAvailabilityFilter(availableNow)).toEqual({
      supported: true,
      where: null,
      availability: "available-now",
    });
    expect(
      serverAvailabilityFilter({
        kind: "and",
        children: [
          { kind: "or", children: [itemA, itemB] },
          { kind: "and", children: [freeLater, freeLater] },
        ],
      }),
    ).toEqual({ supported: true, where: { kind: "or", children: [itemA, itemB] }, availability: "free-later-today" });
  });

  it("matches nothing for contradictory categories and refuses availability inside an OR group", () => {
    expect(serverAvailabilityFilter({ kind: "and", children: [itemA, availableNow, freeLater] })).toEqual({
      supported: true,
      where: { kind: "and", children: [itemA, { kind: "comparison", field: "id", operator: "equals", value: 0 }] },
    });
    expect(serverAvailabilityFilter({ kind: "or", children: [itemA, availableNow] })).toEqual({ supported: false });
    expect(
      serverAvailabilityFilter({ kind: "and", children: [itemB, { kind: "or", children: [availableNow, itemA] }] }),
    ).toEqual({ supported: false });
  });

  it("describes today in the display time zone, whatever the page shows", () => {
    // 23:30Z on 2026-08-16 is already 2026-08-17 in Berlin.
    const today = todayAvailabilityWindow(new Date("2026-08-16T23:30:00Z"), "Europe/Berlin", "08:00", "18:00");
    expect(today).toMatchObject({
      date: "2026-08-17",
      start: "2026-08-17T06:00:00Z",
      end: "2026-08-17T16:00:00Z",
      now: "2026-08-16T23:30:00.000Z",
    });
  });

  it("collapses a display window that daylight saving skips", () => {
    const today = todayAvailabilityWindow(new Date("2026-03-29T00:00:00Z"), "Europe/Berlin", "02:30", "03:00");
    expect(today.bounds.elapsedMinutes).toBe(0);
    expect(today.start).toBe(today.end);
  });

  it("keeps a previous result only while the trailing minute alone changed", () => {
    const keep = keepAcrossMinutes(["counts", "q", "2026-08-17T09:01:00Z"]);
    expect(keep("previous", { queryKey: ["counts", "q", "2026-08-17T09:00:00Z"] })).toBe("previous");
    expect(keep("previous", { queryKey: ["counts", "other", "2026-08-17T09:00:00Z"] })).toBeUndefined();
    expect(keep("previous", undefined)).toBeUndefined();
  });

  it("reads both counts in one request, with the item rules and today's window, and no catalogue page", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue/availability-counts", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json({ availableNow: 3, freeLaterToday: 1 });
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children);
    const today = todayAvailabilityWindow(new Date("2026-08-17T09:00:00Z"), "UTC", "08:00", "18:00");

    const { result } = renderHook(
      () =>
        useAvailabilityCounts(
          "token",
          1,
          { q: "microscope", types: ["INSTRUMENT"], mine: true, where: "target.name=contains=scope" },
          today,
        ),
      { wrapper },
    );

    await waitFor(() => expect(result.current.data).toEqual({ availableNow: 3, freeLaterToday: 1 }));
    expect(requests).toHaveLength(1);
    const parameters = requests[0].searchParams;
    expect(Object.fromEntries(parameters)).toEqual({
      q: "microscope",
      where: "target.name=contains=scope",
      mine: "true",
      type: "INSTRUMENT",
      availabilityStart: "2026-08-17T08:00:00Z",
      availabilityEnd: "2026-08-17T18:00:00Z",
      now: "2026-08-17T09:00:00.000Z",
    });
  });

  it("does not request counts without a token", () => {
    let requests = 0;
    server.use(
      http.get("/api/v2/booking-catalogue/availability-counts", () => {
        requests += 1;
        return HttpResponse.json({ availableNow: 0, freeLaterToday: 0 });
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children);
    const today = todayAvailabilityWindow(new Date("2026-08-17T09:00:00Z"), "UTC", "00:00", "24:00");

    const { result } = renderHook(() => useAvailabilityCounts("", 1, {}, today), { wrapper });

    expect(result.current.fetchStatus).toBe("idle");
    expect(requests).toBe(0);
  });
});

/**
 * The classification the server now owns, computed with the client's own interval helpers. The mocks
 * use the same helpers, and `BookingCurrentAvailabilityTest` holds the server's copy of these cases.
 */
describe("the availability oracle", () => {
  const row = (overrides: Partial<CalendarAvailabilityRow> = {}): CalendarAvailabilityRow => ({
    globalId: "IN1",
    timezone: "UTC",
    openingStart: "00:00",
    openingEnd: "24:00",
    openDays: [1, 2, 3, 4, 5, 6, 7],
    openingExceptions: [],
    bufferBeforeMinutes: 0,
    bufferAfterMinutes: 0,
    allowDoubleBooking: false,
    ...overrides,
  });
  const booking = (start: string, end: string, kind: Booking["kind"] = "BOOKING"): Booking => ({
    id: 1,
    version: 0,
    target: { relationTo: "booking-instruments", value: { id: 1, name: "IN1", deleted: false }, globalId: "IN1" },
    timezone: "UTC",
    start,
    end,
    state: "CONFIRMED",
    kind,
    privacy: "busy",
    purpose: null,
    cancellationReason: null,
    bookedBy: null,
    canEdit: false,
    canCancel: false,
    createdAt: start,
    updatedAt: start,
  });
  const classify = (candidate: CalendarAvailabilityRow, now: string, bookings: readonly Booking[] = []) => {
    const today = todayAvailabilityWindow(new Date(now), "UTC", "00:00", "24:00");
    return classifyCurrentDayAvailability(
      rowAvailabilityIntervals(candidate, today.bounds, bookings),
      new Date(today.start),
      new Date(today.end),
      new Date(now),
    );
  };

  it("classifies each item in its own scheduling time zone", () => {
    const busy = [booking("2026-08-17T08:00:00Z", "2026-08-17T10:00:00Z")];
    expect(classify(row(), "2026-08-17T09:00:00Z", busy)).toBe("free-later-today");
    expect(classify(row({ globalId: "IN2", timezone: "America/Los_Angeles" }), "2026-08-17T09:00:00Z", busy)).toBe(
      "available-now",
    );
  });

  it("distinguishes before opening, open now, and after closing", () => {
    const restricted = row({ openingStart: "08:00", openingEnd: "18:00" });
    expect(classify(restricted, "2026-08-17T07:00:00Z")).toBe("free-later-today");
    expect(classify(restricted, "2026-08-17T09:00:00Z")).toBe("available-now");
    expect(classify(restricted, "2026-08-17T19:00:00Z")).toBe("unavailable-today");
  });

  it("treats an item closed on the instrument's weekday as unavailable and follows its exception", () => {
    // 2026-08-17 is a Monday.
    expect(classify(row({ openDays: [2, 3, 4, 5, 6, 7] }), "2026-08-17T09:00:00Z")).toBe("unavailable-today");
    expect(
      classify(row({ openingExceptions: [{ dayOfWeek: 1, start: "12:00", end: "18:00" }] }), "2026-08-17T09:00:00Z"),
    ).toBe("free-later-today");
  });

  it("counts maintenance on double-booked items and widens events by their buffers", () => {
    const shared = row({ allowDoubleBooking: true });
    expect(classify(shared, "2026-08-17T09:00:00Z", [booking("2026-08-17T08:00:00Z", "2026-08-17T10:00:00Z")])).toBe(
      "available-now",
    );
    expect(
      classify(shared, "2026-08-17T09:00:00Z", [
        booking("2026-08-17T08:00:00Z", "2026-08-17T10:00:00Z", "MAINTENANCE"),
      ]),
    ).toBe("free-later-today");
    expect(
      classify(row({ bufferBeforeMinutes: 30 }), "2026-08-17T09:00:00Z", [
        booking("2026-08-17T09:30:00Z", "2026-08-17T10:00:00Z"),
      ]),
    ).toBe("free-later-today");
  });
});
