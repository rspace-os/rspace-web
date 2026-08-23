import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { Booking } from "@/modules/booking/domain/booking";
import { adaptCalendarDetail, loadCalendarDetail } from "../calendarDetail";

const target = {
  relationTo: "instruments" as const,
  value: { id: 1, name: "Scope", deleted: false },
  globalId: "IN1",
};

describe("calendar detail adapter", () => {
  it("preserves full/busy privacy and cross-day continuation", () => {
    const bookings: Booking[] = [
      {
        id: 1,
        target,
        timezone: "Europe/Berlin",
        start: "2026-08-16T21:00:00Z",
        end: "2026-08-17T01:00:00Z",
        state: "CONFIRMED",
        privacy: "full",
        purpose: "Plate 4",
        bookedBy: "Ada Lovelace (ada)",
        canEdit: true,
        createdAt: "2026-08-01T00:00:00Z",
        updatedAt: "2026-08-01T00:00:00Z",
      },
      {
        id: 2,
        target,
        timezone: "Europe/Berlin",
        start: "2026-08-17T08:00:00Z",
        end: "2026-08-17T09:00:00Z",
        state: "CONFIRMED",
        privacy: "busy",
        purpose: null,
        bookedBy: null,
        canEdit: false,
        createdAt: "2026-08-01T00:00:00Z",
        updatedAt: "2026-08-01T00:00:00Z",
      },
    ];

    const result = adaptCalendarDetail(bookings, "2026-08-17", "Europe/Berlin");

    expect(result.timeline[0]).toMatchObject({
      privacy: "full",
      bookedBy: "Ada Lovelace (ada)",
      notes: "Plate 4",
      startMinute: -60,
      endMinute: 180,
    });
    expect(result.timeline[1]).toEqual({
      id: "2",
      kind: "booking",
      privacy: "busy",
      startMinute: 600,
      endMinute: 660,
    });
    expect(result.agenda[1]).not.toHaveProperty("bookedBy");
    expect(result.agenda[1]).not.toHaveProperty("purpose");
    expect(result.agenda[0].canEdit).toBe(true);
    expect(result.agenda[0].period).toMatch(/GMT\+2/);
  });

  it("requests the private-safe projection and loads every detail page", async () => {
    const pages: string[] = [];
    const full = {
      id: 1,
      target,
      timezone: "Europe/Berlin",
      start: "2026-08-17T08:00:00Z",
      end: "2026-08-17T09:00:00Z",
      state: "CONFIRMED",
      privacy: "full",
      purpose: null,
      bookedBy: "Ada Lovelace (ada)",
      canEdit: true,
    };
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        const page = url.searchParams.get("page") ?? "1";
        pages.push(page);
        expect(url.searchParams.get("fields[bookings]")).toBe(
          "id,target,timezone,start,end,state,privacy,purpose,bookedBy,canEdit",
        );
        return HttpResponse.json({
          docs: [{ ...full, id: Number(page) }],
          totalDocs: 2,
          totalPages: 2,
        });
      }),
    );

    const result = await loadCalendarDetail(
      "IN1",
      "2026-08-17",
      "Europe/Berlin",
      "token",
      new AbortController().signal,
    );

    expect(pages).toEqual(["1", "2"]);
    expect(result.map((booking) => booking.id)).toEqual([1, 2]);
  });

  it("rejects a booking in a different configuration timezone", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json({
          docs: [
            {
              id: 1,
              target,
              timezone: "UTC",
              start: "2026-08-17T08:00:00Z",
              end: "2026-08-17T09:00:00Z",
              state: "CONFIRMED",
              privacy: "busy",
              purpose: null,
              bookedBy: null,
              canEdit: false,
            },
          ],
          totalDocs: 1,
          totalPages: 1,
        }),
      ),
    );

    await expect(
      loadCalendarDetail("IN1", "2026-08-17", "Europe/Berlin", "token", new AbortController().signal),
    ).rejects.toThrow("differs");
  });
});
