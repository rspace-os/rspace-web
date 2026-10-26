import { describe, expect, it } from "vitest";
import { todayAvailabilityWindow } from "../../pages/all-bookable-items/availabilityQuickFilters";
import { type CalendarAvailabilityRow, rowAvailabilityIntervals } from "../../pages/calendar/calendarAvailability";
import {
  type AvailabilityInterval,
  bookingConflicts,
  buildAvailabilitySegments,
  buildAvailabilitySlices,
  classifyCurrentDayAvailability,
} from "../availability";
import type { Booking } from "../booking";

const start = new Date("2026-08-17T00:00:00Z");
const end = new Date("2026-08-18T00:00:00Z");
const occupied = (from: string, to: string, kind: AvailabilityInterval["kind"] = "booking") => ({
  kind,
  startsAt: new Date(from),
  endsAt: new Date(to),
});

/**
 * The classification the server owns, computed with the client's own interval helpers. The mocks
 * use the same helpers, and `BookingCurrentAvailabilityTest` holds the server's copy of these cases.
 */
describe("the availability oracle", () => {
  const row = (overrides: Partial<CalendarAvailabilityRow> = {}): CalendarAvailabilityRow => ({
    globalId: "IN1",
    timezone: "UTC",
    openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),

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
    cancellationReason: null,
    kind,
    privacy: "busy",
    purpose: null,
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
    const restricted = row({
      openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "18:00" })),
    });
    expect(classify(restricted, "2026-08-17T07:00:00Z")).toBe("free-later-today");
    expect(classify(restricted, "2026-08-17T09:00:00Z")).toBe("available-now");
    expect(classify(restricted, "2026-08-17T19:00:00Z")).toBe("unavailable-today");
  });

  it("treats an item closed on the instrument's weekday as unavailable and follows its exception", () => {
    // 2026-08-17 is a Monday.
    expect(
      classify(
        row({
          openingHours: [
            { dayOfWeek: 2, start: "00:00", end: "24:00" },
            { dayOfWeek: 3, start: "00:00", end: "24:00" },
            { dayOfWeek: 4, start: "00:00", end: "24:00" },
            { dayOfWeek: 5, start: "00:00", end: "24:00" },
            { dayOfWeek: 6, start: "00:00", end: "24:00" },
            { dayOfWeek: 7, start: "00:00", end: "24:00" },
          ],
        }),
        "2026-08-17T09:00:00Z",
      ),
    ).toBe("unavailable-today");
    expect(
      classify(
        row({
          openingHours: [
            { dayOfWeek: 1, start: "12:00", end: "18:00" },
            { dayOfWeek: 2, start: "00:00", end: "24:00" },
            { dayOfWeek: 3, start: "00:00", end: "24:00" },
            { dayOfWeek: 4, start: "00:00", end: "24:00" },
            { dayOfWeek: 5, start: "00:00", end: "24:00" },
            { dayOfWeek: 6, start: "00:00", end: "24:00" },
            { dayOfWeek: 7, start: "00:00", end: "24:00" },
          ],
        }),
        "2026-08-17T09:00:00Z",
      ),
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

describe("availability", () => {
  it("deduplicates booking sources and excludes the booking being edited", () => {
    const source = (id: number) => ({
      id: `booking:${id}`,
      startsAt: new Date("2026-08-17T10:00:00Z"),
      endsAt: new Date("2026-08-17T11:00:00Z"),
      booking: {
        id,
        kind: "BOOKING" as const,
        privacy: "full" as const,
        purpose: `Purpose ${id}`,
        bookedBy: null,
      },
    });
    const intervals = [
      { kind: "booking" as const, startsAt: start, endsAt: end, source: source(42) },
      { kind: "booking" as const, startsAt: start, endsAt: end, source: source(42) },
      { kind: "booking" as const, startsAt: start, endsAt: end, source: source(43) },
    ];

    expect(bookingConflicts(intervals, "UTC", 42)).toEqual([
      expect.objectContaining({ id: 43, start: "2026-08-17T10:00:00.000Z", timezone: "UTC" }),
    ]);
  });

  it("marks conflicts where only the buffer, not the event itself, reaches the draft", () => {
    const source = (id: number, from: string, to: string) => ({
      id: `booking:${id}`,
      startsAt: new Date(from),
      endsAt: new Date(to),
      booking: { id, kind: "BOOKING" as const, privacy: "full" as const, purpose: null, bookedBy: null },
    });
    // Draft 11:00-12:00 with 15-minute buffers: #42 ends as the draft starts, #43 starts inside it.
    const intervals = [
      {
        kind: "booking" as const,
        startsAt: new Date("2026-08-17T11:00:00Z"),
        endsAt: new Date("2026-08-17T11:15:00Z"),
        source: source(42, "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z"),
      },
      {
        kind: "booking" as const,
        startsAt: new Date("2026-08-17T11:15:00Z"),
        endsAt: new Date("2026-08-17T12:00:00Z"),
        source: source(43, "2026-08-17T11:30:00Z", "2026-08-17T13:00:00Z"),
      },
    ];

    expect(bookingConflicts(intervals, "UTC")).toEqual([
      expect.objectContaining({ id: 42, bufferOnly: true, start: "2026-08-17T10:00:00.000Z" }),
      expect.objectContaining({ id: 43, bufferOnly: false }),
    ]);
  });

  it("classifies half-open current and future availability", () => {
    const intervals = [occupied("2026-08-17T08:00:00Z", "2026-08-17T10:00:00Z")];
    expect(classifyCurrentDayAvailability(intervals, start, end, new Date("2026-08-17T09:00:00Z"))).toBe(
      "free-later-today",
    );
    expect(classifyCurrentDayAvailability(intervals, start, end, new Date("2026-08-17T10:00:00Z"))).toBe(
      "available-now",
    );
  });

  it("does not claim future availability when occupied through day end", () => {
    const intervals = [occupied("2026-08-17T08:00:00Z", "2026-08-18T01:00:00Z")];
    expect(classifyCurrentDayAvailability(intervals, start, end, new Date("2026-08-17T09:00:00Z"))).toBe(
      "unavailable-today",
    );
  });

  it("classifies times before and after the displayed availability window", () => {
    const windowStart = new Date("2026-08-17T08:00:00Z");
    const windowEnd = new Date("2026-08-17T18:00:00Z");
    expect(classifyCurrentDayAvailability([], windowStart, windowEnd, new Date("2026-08-17T07:00:00Z"))).toBe(
      "free-later-today",
    );
    expect(
      classifyCurrentDayAvailability(
        [occupied("2026-08-17T08:00:00Z", "2026-08-17T18:00:00Z")],
        windowStart,
        windowEnd,
        new Date("2026-08-17T07:00:00Z"),
      ),
    ).toBe("unavailable-today");
    expect(classifyCurrentDayAvailability([], windowStart, windowEnd, windowEnd)).toBe("unavailable-today");
    expect(classifyCurrentDayAvailability([], windowStart, windowEnd, new Date("2026-08-17T19:00:00Z"))).toBe(
      "unavailable-today",
    );
  });

  it("retains every contributor when bookings overlap or touch", () => {
    const overlapping = [
      occupied("2026-08-17T08:00:00Z", "2026-08-17T10:00:00Z"),
      occupied("2026-08-17T09:00:00Z", "2026-08-17T11:00:00Z"),
    ];
    const touching = [
      occupied("2026-08-17T12:00:00Z", "2026-08-17T13:00:00Z"),
      occupied("2026-08-17T13:00:00Z", "2026-08-17T14:00:00Z"),
    ];

    expect(
      buildAvailabilitySlices(overlapping, start, end).find(({ state }) => state === "booking")?.intervals,
    ).toEqual(overlapping);
    expect(
      buildAvailabilitySlices(touching, start, end)
        .filter(({ state }) => state === "booking")
        .map(({ intervals }) => intervals),
    ).toEqual([touching]);
  });

  it("assigns contributors to booking, overlap, and blockout slices", () => {
    const booking = occupied("2026-08-17T08:00:00Z", "2026-08-17T10:00:00Z");
    const blockout = occupied("2026-08-17T09:00:00Z", "2026-08-17T11:00:00Z", "blockout");

    const slices = buildAvailabilitySlices([booking, blockout], start, end).filter(
      ({ state }) => state !== "available",
    );

    expect(slices.map(({ state }) => state)).toEqual(["booking", "overlap", "blockout"]);
    expect(slices.map(({ intervals }) => intervals)).toEqual([[booking], [booking, blockout], [blockout]]);
  });

  it("clips occupied bounds while preserving source metadata", () => {
    const source = {
      id: "booking:42",
      startsAt: new Date("2026-08-16T22:00:00Z"),
      endsAt: new Date("2026-08-17T02:00:00Z"),
    };
    const interval = { ...occupied("2026-08-16T23:00:00Z", "2026-08-17T01:00:00Z"), source };

    const bookingSlice = buildAvailabilitySlices([interval], start, end).find(({ state }) => state === "booking");

    expect(bookingSlice?.intervals).toEqual([
      {
        ...interval,
        startsAt: start,
        endsAt: new Date("2026-08-17T01:00:00Z"),
      },
    ]);
    expect(bookingSlice?.intervals[0].source).toBe(source);
  });

  it("does not attach contributors to available slices", () => {
    const slices = buildAvailabilitySlices([occupied("2026-08-17T08:00:00Z", "2026-08-17T09:00:00Z")], start, end);

    expect(slices.filter(({ state }) => state === "available").every(({ intervals }) => intervals.length === 0)).toBe(
      true,
    );
  });

  it("rejects invalid dates, periods, and intervals", () => {
    expect(() => buildAvailabilitySegments([], end, start)).toThrow(RangeError);
    expect(() =>
      buildAvailabilitySegments([occupied("2026-08-17T10:00:00Z", "2026-08-17T09:00:00Z")], start, end),
    ).toThrow(RangeError);
    expect(() => classifyCurrentDayAvailability([], start, end, new Date("invalid"))).toThrow(RangeError);
  });
});
