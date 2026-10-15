import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { createElement, type ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
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
