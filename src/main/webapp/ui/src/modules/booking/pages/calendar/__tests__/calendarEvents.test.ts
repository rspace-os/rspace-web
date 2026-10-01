import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { createElement, type ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { CALENDAR_BOOKING_FIELDS, loadCalendarEvents, useCalendarEvents } from "../calendarEvents";
import { collectionResponse, otherBooking, ownBooking } from "./calendarTestHarness";

describe("calendar events", () => {
  it("sends complete item/event grouping and Search before the event limit", async () => {
    const where = "(target==IN1,target.customFields.SF152==BSL-2);kind==BOOKING";
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-calendar/events", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json(
          url.searchParams.get("where")?.endsWith(`;(${where})`)
            ? collectionResponse([ownBooking])
            : collectionResponse([], { totalDocs: 1001, totalPages: 11 }),
        );
      }),
    );
    await expect(
      loadCalendarEvents("2026-08-17", "2026-08-23", "UTC", "token", new AbortController().signal, undefined, {
        where,
        q: "purpose-only",
      }),
    ).resolves.toEqual([ownBooking]);
    expect(requests).toHaveLength(1);
    expect(requests[0].searchParams.get("q")).toBe("purpose-only");
    expect(requests[0].searchParams.get("start")).toBe("2026-08-17T00:00:00Z");
    expect(requests[0].searchParams.get("where")).not.toContain("state==CONFIRMED");
  });

  it("loads every page for the selected calendar range with private-safe fields", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-calendar/events", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        const fields = url.searchParams.get("fields[bookings]")?.split(",") ?? [];
        const source = url.searchParams.get("page") === "2" ? otherBooking : ownBooking;
        const projectedBooking = Object.fromEntries(
          fields.flatMap((field) => (field in source ? [[field, source[field as keyof typeof source]]] : [])),
        );
        return HttpResponse.json(
          collectionResponse([projectedBooking], {
            page: Number(url.searchParams.get("page")),
            totalDocs: 2,
            totalPages: 2,
          }),
        );
      }),
    );

    await expect(
      loadCalendarEvents("2026-08-17", "2026-08-23", "Europe/Berlin", "token", new AbortController().signal),
    ).resolves.toEqual([ownBooking, otherBooking]);

    expect(requests).toHaveLength(2);
    expect(requests[0].searchParams.get("where")).toBe("start=lt=2026-08-23T22:00:00Z;end=gt=2026-08-16T22:00:00Z");
    const selectedFields = requests[0].searchParams.get("fields[bookings]");
    expect(selectedFields).toBe(CALENDAR_BOOKING_FIELDS);
    expect(selectedFields?.split(",")).toContain("canViewConfiguration");
  });

  it("returns no events for an empty target scope while leaving undefined unrestricted", async () => {
    let requests = 0;
    server.use(
      http.get("/api/v2/booking-calendar/events", () => {
        requests += 1;
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );

    await expect(
      loadCalendarEvents("2026-08-17", "2026-08-23", "Europe/Berlin", "token", new AbortController().signal, []),
    ).resolves.toEqual([]);

    await expect(
      loadCalendarEvents("2026-08-17", "2026-08-23", "Europe/Berlin", "token", new AbortController().signal),
    ).resolves.toEqual([ownBooking]);

    expect(requests).toBe(1);
  });

  it("keeps calendar event queries separate for different authenticated callers", async () => {
    let requests = 0;
    server.use(
      http.get("/api/v2/booking-calendar/events", () => {
        requests += 1;
        return HttpResponse.json(collectionResponse([]));
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children);

    const firstCaller = renderHook(
      () => useCalendarEvents("2026-08-17", "2026-08-23", "UTC", "token", undefined, true, "caller-one"),
      { wrapper },
    );
    const secondCaller = renderHook(
      () => useCalendarEvents("2026-08-17", "2026-08-23", "UTC", "token", undefined, true, "caller-two"),
      { wrapper },
    );

    await waitFor(() => {
      expect(firstCaller.result.current.data).toEqual([]);
      expect(secondCaller.result.current.data).toEqual([]);
    });
    expect(requests).toBe(2);
  });

  it("rejects unsuccessful responses", async () => {
    server.use(http.get("/api/v2/booking-calendar/events", () => new HttpResponse(null, { status: 503 })));

    await expect(
      loadCalendarEvents("2026-08-17", "2026-08-17", "UTC", "token", new AbortController().signal),
    ).rejects.toThrow("Booking calendar request failed (503)");
  });

  it("rejects ranges containing more bookings than the calendar can safely render", async () => {
    let requests = 0;
    server.use(
      http.get("/api/v2/booking-calendar/events", () => {
        requests += 1;
        return HttpResponse.json(collectionResponse([ownBooking], { totalDocs: 1_001, totalPages: 11 }));
      }),
    );

    await expect(
      loadCalendarEvents("2026-08-17", "2026-08-23", "UTC", "token", new AbortController().signal),
    ).rejects.toThrow("Booking calendar exceeds 1,000 bookings");
    expect(requests).toBe(1);
  });
});
