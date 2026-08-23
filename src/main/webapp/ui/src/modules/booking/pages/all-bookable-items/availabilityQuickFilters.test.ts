import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { createElement, type ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";
import {
  AvailabilityCandidateLimitError,
  fetchAvailabilityCandidates,
  loadAvailabilityQuickIndex,
  useAvailabilityQuickFilterIndex,
} from "./availabilityQuickFilters";

const candidate = (id: number, globalId: string, timezone: string): BookingConfiguration => ({
  id,
  target: {
    relationTo: "instruments",
    value: { id, name: globalId, deleted: false },
    globalId,
  },
  enabled: true,
  timezone,
});

const page = (docs: readonly BookingConfiguration[], pageNumber = 1, totalPages = 1, totalDocs = docs.length) => ({
  docs,
  totalDocs,
  totalPages,
  page: pageNumber,
});

describe("availability quick filters", () => {
  it("fetches every candidate page with the fixed filter and projection", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-configurations", ({ request }) => {
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
    expect(requests[0].searchParams.get("where")).toBe("enabled==true;target.deleted==false");
    expect(requests[0].searchParams.get("limit")).toBe("100");
    expect(requests[0].searchParams.get("depth")).toBe("1");
    expect(requests[0].searchParams.get("fields[booking-configurations]")).toBe("id,target,enabled,timezone");
  });

  it("rejects candidate collections above the relationship-filter ceiling", async () => {
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(page([], 1, 11, 1001))));
    await expect(fetchAvailabilityCandidates("token", new AbortController().signal)).rejects.toBeInstanceOf(
      AvailabilityCandidateLimitError,
    );
  });

  it("classifies candidates using each configured time zone", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json({
          docs: [
            {
              id: 1,
              target: candidate(1, "IN1", "UTC").target,
              timezone: "UTC",
              start: "2026-08-17T08:00:00Z",
              end: "2026-08-17T10:00:00Z",
              state: "CONFIRMED",
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
      "token",
      new AbortController().signal,
    );
    expect(result.get("IN1")?.category).toBe("free-later-today");
    expect(result.get("IN1")?.date).toBe("2026-08-17");
    expect(result.get("IN2")?.category).toBe("available-now");
    expect(result.get("IN2")?.date).toBe("2026-08-17");
  });

  it("does not request data while disabled and reuses the index when modes switch", async () => {
    let candidateRequests = 0;
    let bookingRequests = 0;
    server.use(
      http.get("/api/v2/booking-configurations", () => {
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
      ({ mode }: { mode: "available-now" | "free-later-today" | undefined }) =>
        useAvailabilityQuickFilterIndex(mode, "token", () => new Date("2026-08-17T09:00:00Z")),
      {
        wrapper,
        initialProps: {
          mode: undefined as "available-now" | "free-later-today" | undefined,
        },
      },
    );
    expect(result.current.isPending).toBe(false);
    expect(candidateRequests).toBe(0);
    rerender({ mode: "available-now" });
    await waitFor(() => expect(result.current.data).toBeDefined());
    rerender({ mode: "free-later-today" });
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
      http.get("/api/v2/booking-configurations", () => {
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
    const { result } = renderHook(() => useAvailabilityQuickFilterIndex("available-now", "token"), { wrapper });
    await act(async () => vi.waitFor(() => expect(result.current.data).toBeDefined()));
    await act(() => vi.advanceTimersByTimeAsync(30_000));
    await act(async () => vi.waitFor(() => expect(bookingRequests).toBe(2)));
    expect(candidateRequests).toBe(1);
    vi.useRealTimers();
  });
});
