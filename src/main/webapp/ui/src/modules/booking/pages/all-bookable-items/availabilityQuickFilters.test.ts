import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { createElement, type ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";
import {
  type AllBookableItem,
  AvailabilityCandidateLimitError,
  deriveAvailabilityCandidateFilter,
  fetchAvailabilityCandidates,
  loadAvailabilityQuickIndex,
  resolveAvailabilityFilters,
  useAvailabilityQuickFilterIndex,
  withAvailability,
} from "./availabilityQuickFilters";

const candidate = (id: number, globalId: string, timezone: string): BookingConfiguration => ({
  id,
  configurationVersion: 0,
  target: {
    relationTo: "booking-instruments",
    value: { id, name: globalId, deleted: false },
    globalId,
  },
  enabled: true,
  state: "ACTIVE",
  timezone,
  slotGranularityMinutes: 5,
  openingStart: "00:00",
  openingEnd: "24:00",
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  effectiveRole: "Viewer",
  roleSources: [],
  capabilities: {
    canEditConfiguration: false,
    canViewAudit: false,
    canViewAccess: false,
    canManageAssignments: false,
    canManageOwners: false,
    canCreateBooking: false,
    canManageOwnBookings: false,
    canManageAllEvents: false,
    canCreateBlockout: false,
    canSubscribeCalendar: false,
    canLeaveConfiguration: false,
    canManageNotificationSubscription: false,
  },
});

const page = (docs: readonly BookingConfiguration[], pageNumber = 1, totalPages = 1, totalDocs = docs.length) => ({
  items: docs.map((doc) => ({
    ...doc,
    configurationId: doc.id,
    targetId: doc.target?.value.id,
    targetType: "INSTRUMENT",
    globalId: doc.target?.globalId,
    name: doc.target?.value.name,
    location: null,
  })),
  total: totalDocs,
  pageSize: totalPages > 1 && totalDocs < 100 ? 1 : 100,
  page: pageNumber,
  facets: { types: ["INSTRUMENT"] },
});

describe("availability quick filters", () => {
  it("derives a safe candidate predicate while preserving item-only grouping", () => {
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
    const availability: FilterExpression<AllBookableItem> = {
      kind: "comparison",
      field: "availability",
      operator: "equals",
      value: "available-now",
    };

    expect(deriveAvailabilityCandidateFilter({ kind: "and", children: [itemA, availability] })).toEqual(itemA);
    expect(deriveAvailabilityCandidateFilter({ kind: "or", children: [itemA, itemB] })).toEqual({
      kind: "or",
      children: [itemA, itemB],
    });
    expect(deriveAvailabilityCandidateFilter({ kind: "or", children: [itemA, availability] })).toBeNull();
    expect(
      deriveAvailabilityCandidateFilter({
        kind: "and",
        children: [{ kind: "or", children: [availability, itemA] }, itemB],
      }),
    ).toEqual(itemB);
  });

  it("retains duplicate item and ID rules when a quick filter is changed or removed", () => {
    const original: FilterExpression<AllBookableItem> = {
      kind: "and",
      children: [
        { kind: "comparison", field: "id", operator: "greaterThan", value: 5 },
        { kind: "comparison", field: "target", operator: "equals", value: "IN1" },
        { kind: "comparison", field: "target", operator: "equals", value: "IN2" },
      ],
    };
    const added = withAvailability(original, "available-now");
    expect(withAvailability(added, undefined)).toEqual(original);
    expect(withAvailability(withAvailability(added, "free-later-today"), undefined)).toEqual(original);
    expect(resolveAvailabilityFilters(added, new Map())).toEqual({
      kind: "and",
      children: [...original.children, { kind: "comparison", field: "id", operator: "equals", value: 0 }],
    });
  });

  it("treats a display window collapsed by DST as unavailable without requesting bookings", async () => {
    const result = await loadAvailabilityQuickIndex(
      [candidate(1, "IN1", "Europe/Berlin")],
      new Date("2026-03-29T00:00:00Z"),
      "Europe/Berlin",
      "02:30",
      "03:00",
      "token",
      new AbortController().signal,
    );
    expect(result.get("IN1")?.category).toBe("unavailable-today");
    expect(result.get("IN1")?.bounds.elapsedMinutes).toBe(0);
  });

  it("fetches every catalogue candidate page", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json(
          url.searchParams.get("page") === "1"
            ? page([candidate(1, "IN1", "UTC")], 1, 2, 2)
            : page([candidate(2, "IN2", "Europe/Berlin")], 2, 2, 2),
        );
      }),
    );

    const result = await fetchAvailabilityCandidates("token", new AbortController().signal);
    expect(result).toHaveLength(2);
    expect(requests.map((request) => request.searchParams.get("page"))).toEqual(["1", "2"]);
    expect(requests[0].searchParams.get("where")).toBeNull();
    expect(requests[0].searchParams.get("limit")).toBe("100");
  });

  it("pushes the safe candidate predicate down before applying the candidate limit", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(page([candidate(1, "IN1", "UTC")]));
      }),
    );

    await fetchAvailabilityCandidates("token", new AbortController().signal, "target.customFields.SF152==BSL-2");

    expect(requests[0].searchParams.get("where")).toBe("target.customFields.SF152==BSL-2");
  });

  it("preserves OR candidates before the filtered capacity limit", async () => {
    const rule = "target==IN1,target==IN2";
    const expected = rule;
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) =>
        HttpResponse.json(
          new URL(request.url).searchParams.get("where") === expected
            ? page([candidate(1, "IN1", "UTC"), candidate(2, "IN2", "UTC")])
            : page([], 1, 11, 1001),
        ),
      ),
    );
    await expect(fetchAvailabilityCandidates("token", new AbortController().signal, rule)).resolves.toHaveLength(2);
  });

  it("rejects candidate collections above the relationship-filter ceiling", async () => {
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(page([], 1, 11, 1001))));
    await expect(fetchAvailabilityCandidates("token", new AbortController().signal)).rejects.toBeInstanceOf(
      AvailabilityCandidateLimitError,
    );
  });

  it("applies catalogue search and types before the candidate capacity limit", async () => {
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const parameters = new URL(request.url).searchParams;
        return HttpResponse.json(
          parameters.get("q") === "microscope" && parameters.get("type") === "INSTRUMENT"
            ? page([candidate(1, "IN1", "UTC")])
            : page([], 1, 11, 1001),
        );
      }),
    );
    await expect(fetchAvailabilityCandidates("token", new AbortController().signal)).rejects.toBeInstanceOf(
      AvailabilityCandidateLimitError,
    );
    await expect(
      fetchAvailabilityCandidates("token", new AbortController().signal, undefined, {
        q: "microscope",
        types: ["INSTRUMENT"],
      }),
    ).resolves.toHaveLength(1);
  });

  it("classifies candidates using each configured time zone", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json({
          docs: [
            {
              id: 1,
              version: 0,
              target: candidate(1, "IN1", "UTC").target,
              timezone: "UTC",
              start: "2026-08-17T08:00:00Z",
              end: "2026-08-17T10:00:00Z",
              state: "CONFIRMED",
              privacy: "full",
              purpose: null,
              bookedBy: null,
              canEdit: false,
              canCancel: false,
              createdAt: "2026-08-17T08:00:00Z",
              updatedAt: "2026-08-17T08:00:00Z",
            },
          ],
          totalDocs: 1,
          totalPages: 1,
          page: 1,
          hasNextPage: false,
        }),
      ),
    );
    const result = await loadAvailabilityQuickIndex(
      [candidate(1, "IN1", "UTC"), candidate(2, "IN2", "America/Los_Angeles")],
      new Date("2026-08-17T09:00:00Z"),
      "UTC",
      "00:00",
      "24:00",
      "token",
      new AbortController().signal,
    );
    expect(result.get("IN1")?.category).toBe("free-later-today");
    expect(result.get("IN1")?.date).toBe("2026-08-17");
    expect(result.get("IN2")?.category).toBe("available-now");
    expect(result.get("IN2")?.date).toBe("2026-08-17");
  });

  it("distinguishes before opening, open now, and after closing", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json({ docs: [], totalDocs: 0, totalPages: 0, page: 1, hasNextPage: false }),
      ),
    );
    const restricted = {
      ...candidate(1, "IN1", "UTC"),
      openingStart: "08:00",
      openingEnd: "18:00",
    };

    for (const [time, expected] of [
      ["2026-08-17T07:00:00Z", "free-later-today"],
      ["2026-08-17T09:00:00Z", "available-now"],
      ["2026-08-17T19:00:00Z", "unavailable-today"],
    ] as const) {
      const result = await loadAvailabilityQuickIndex(
        [restricted],
        new Date(time),
        "UTC",
        "00:00",
        "24:00",
        "token",
        new AbortController().signal,
      );
      expect(result.get("IN1")?.category).toBe(expected);
    }
  });

  it("does not request data without a token and loads counts once authenticated", async () => {
    let candidateRequests = 0;
    let bookingRequests = 0;
    server.use(
      http.get("/api/v2/booking-catalogue", () => {
        candidateRequests += 1;
        return HttpResponse.json(page([candidate(1, "IN1", "UTC")]));
      }),
      http.get("/api/v2/bookings", () => {
        bookingRequests += 1;
        return HttpResponse.json({ docs: [], totalDocs: 0, totalPages: 0, page: 1, hasNextPage: false });
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children);
    const { result, rerender } = renderHook(
      ({ token }: { token: string }) =>
        useAvailabilityQuickFilterIndex(token, "UTC", "00:00", "24:00", () => new Date("2026-08-17T09:00:00Z")),
      {
        wrapper,
        initialProps: {
          token: "",
        },
      },
    );
    expect(result.current.isPending).toBe(false);
    expect(candidateRequests).toBe(0);
    rerender({ token: "token" });
    await waitFor(() => expect(result.current.data).toBeDefined());
    rerender({ token: "token" });
    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(candidateRequests).toBe(1);
    expect(bookingRequests).toBe(1);
  });

  it("refreshes bookings, but not fresh candidates, at the next minute", async () => {
    vi.useFakeTimers();
    vi.setSystemTime("2026-08-17T09:00:30Z");
    let candidateRequests = 0;
    let bookingRequests = 0;
    server.use(
      http.get("/api/v2/booking-catalogue", () => {
        candidateRequests += 1;
        return HttpResponse.json(page([candidate(1, "IN1", "UTC")]));
      }),
      http.get("/api/v2/bookings", () => {
        bookingRequests += 1;
        return HttpResponse.json({ docs: [], totalDocs: 0, totalPages: 0, page: 1, hasNextPage: false });
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children);
    const { result } = renderHook(() => useAvailabilityQuickFilterIndex("token"), { wrapper });
    await act(async () => vi.waitFor(() => expect(result.current.data).toBeDefined()));
    await act(() => vi.advanceTimersByTimeAsync(30_000));
    await act(async () => vi.waitFor(() => expect(bookingRequests).toBe(2)));
    expect(candidateRequests).toBe(1);
    vi.useRealTimers();
  });
});
