import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { createElement, type PropsWithChildren } from "react";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { getSearchCalendarEventsQueryKey } from "@/modules/common/api/v2/generated/booking-calendar/booking-calendar";
import { collectionResponse, otherBooking, ownBooking } from "../../mocks/bookingCalendarFixtures";
import { CALENDAR_BOOKING_FIELDS, loadCalendarEvents, useCalendarEvents } from "../calendarEvents";

function calendarBounds(firstDate: string, lastDate: string, timezone: string) {
  return {
    start: zonedDayBounds(firstDate, timezone).start,
    end: zonedDayBounds(lastDate, timezone).end,
  };
}

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
            : collectionResponse([], { totalDocs: 1001 }),
        );
      }),
    );
    await expect(
      loadCalendarEvents({
        ...calendarBounds("2026-08-17", "2026-08-23", "UTC"),

        signal: new AbortController().signal,
        scope: { where, q: "purpose-only" },
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
        if (
          url.searchParams.get("fields[instruments]") !== "id,globalId,name,deleted,parentContainer" ||
          url.searchParams.get("fields[containers]") !== "id,globalId,name"
        )
          return new HttpResponse(null, { status: 400 });
        const fields = url.searchParams.get("fields[bookings]")?.split(",") ?? [];
        const source = url.searchParams.get("page") === "2" ? otherBooking : ownBooking;
        const projectedBooking = Object.fromEntries(
          fields.flatMap((field) => (field in source ? [[field, source[field as keyof typeof source]]] : [])),
        );
        return HttpResponse.json(
          collectionResponse([projectedBooking], {
            page: Number(url.searchParams.get("page")),
            totalDocs: 2,
            limit: 1,
          }),
        );
      }),
    );

    await expect(
      loadCalendarEvents({
        ...calendarBounds("2026-08-17", "2026-08-23", "Europe/Berlin"),

        signal: new AbortController().signal,
      }),
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
      loadCalendarEvents({
        ...calendarBounds("2026-08-17", "2026-08-23", "Europe/Berlin"),

        signal: new AbortController().signal,
        targetGlobalIds: [],
      }),
    ).resolves.toEqual([]);

    await expect(
      loadCalendarEvents({
        ...calendarBounds("2026-08-17", "2026-08-23", "Europe/Berlin"),

        signal: new AbortController().signal,
      }),
    ).resolves.toEqual([ownBooking]);

    expect(requests).toBe(1);
  });

  it("rejects unsuccessful responses", async () => {
    server.use(http.get("/api/v2/booking-calendar/events", () => new HttpResponse(null, { status: 503 })));

    await expect(
      loadCalendarEvents({
        ...calendarBounds("2026-08-17", "2026-08-17", "UTC"),

        signal: new AbortController().signal,
      }),
    ).rejects.toMatchObject({ status: 503 });
  });

  it("rejects ranges containing more bookings than the calendar can safely render", async () => {
    let requests = 0;
    server.use(
      http.get("/api/v2/booking-calendar/events", () => {
        requests += 1;
        return HttpResponse.json(collectionResponse([ownBooking], { totalDocs: 1_001 }));
      }),
    );

    await expect(
      loadCalendarEvents({
        ...calendarBounds("2026-08-17", "2026-08-23", "UTC"),

        signal: new AbortController().signal,
      }),
    ).rejects.toThrow("Booking calendar exceeds 1,000 bookings");
    expect(requests).toBe(1);
  });
  it("filters booking requests to a non-empty target scope", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-calendar/events", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );

    await expect(
      loadCalendarEvents({
        ...calendarBounds("2026-08-17", "2026-08-23", "Europe/Berlin"),

        signal: new AbortController().signal,
        targetGlobalIds: ["IN123"],
      }),
    ).resolves.toEqual([ownBooking]);

    expect(requests).toHaveLength(1);
    expect(requests[0].searchParams.get("where")).toBe(
      "start=lt=2026-08-23T22:00:00Z;end=gt=2026-08-16T22:00:00Z;target=in=(IN123)",
    );
  });

  it.each(["empty", "unrestricted"])(
    "keeps scopes separate in a fresh shared cache when %s is queried first",
    async (first) => {
      const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
      const wrapper = ({ children }: PropsWithChildren) => createElement(QueryClientProvider, { client }, children);
      const requests: URL[] = [];
      server.use(
        http.get("/api/v2/booking-calendar/events", ({ request }) => {
          requests.push(new URL(request.url));
          return HttpResponse.json(collectionResponse([ownBooking]));
        }),
      );
      const scopes: (readonly string[] | undefined)[] = first === "empty" ? [[], undefined] : [undefined, []];
      try {
        for (const targetGlobalIds of scopes) {
          const query = renderHook(
            () =>
              useCalendarEvents({
                firstDate: "2026-08-17",
                lastDate: "2026-08-23",
                timezone: "Europe/Berlin",
                targetGlobalIds,
              }),
            { wrapper },
          );
          try {
            await waitFor(() => expect(query.result.current.isSuccess).toBe(true));
            expect(query.result.current.data).toEqual(targetGlobalIds ? [] : [ownBooking]);
          } finally {
            query.unmount();
          }
        }
        expect(requests).toHaveLength(1);
        expect(requests[0].searchParams.get("where")).not.toContain("target=in=");
        const prefix = getSearchCalendarEventsQueryKey();
        await client.invalidateQueries({ queryKey: prefix });
        const queries = client.getQueryCache().findAll({ queryKey: prefix });
        expect(queries).toHaveLength(2);
        for (const query of queries) expect(query.state.isInvalidated).toBe(true);
      } finally {
        client.clear();
      }
    },
  );
});
