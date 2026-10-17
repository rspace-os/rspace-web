import { describe, expect, it, vi } from "vitest";
import { closestSchedulingTimelineSlot } from "../schedulingTimelineGrid";
import { adjustTimelineRange } from "../TimelineWindowEditor";
import type { TimelineMoveContext } from "../timelineMove";
import { moveTimelineRange } from "../timelineMove";
import * as zonedBookingWindowFields from "../ZonedBookingWindowFields";

function movementContext(
  date: string,
  displayTimezone: string,
  schedulingTimezone: string,
  slotGranularityMinutes: number,
  policy: Partial<TimelineMoveContext["policy"]> = {},
): TimelineMoveContext {
  return {
    date,
    displayTimezone,
    policy: {
      schedulingTimezone,
      slotGranularityMinutes,
      maxBookingDurationMinutes: 0,
      openingHours: [],
      enforceOpeningHours: false,
      ...policy,
    },
  };
}

describe("adjustTimelineRange", () => {
  it("moves a range without changing its duration", () => {
    expect(adjustTimelineRange({ startMinute: 60, endMinute: 120 }, "move", 30, 1440, 15)).toEqual({
      startMinute: 90,
      endMinute: 150,
    });
  });

  it("resizes either edge", () => {
    expect(adjustTimelineRange({ startMinute: 60, endMinute: 120 }, "start", 75, 1440, 15)).toEqual({
      startMinute: 75,
      endMinute: 120,
    });
    expect(adjustTimelineRange({ startMinute: 60, endMinute: 120 }, "end", 150, 1440, 15)).toEqual({
      startMinute: 60,
      endMinute: 150,
    });
  });

  it("keeps at least one increment between the edges", () => {
    expect(adjustTimelineRange({ startMinute: 60, endMinute: 120 }, "start", 120, 1440, 15)).toEqual({
      startMinute: 105,
      endMinute: 120,
    });
    expect(adjustTimelineRange({ startMinute: 60, endMinute: 120 }, "end", 60, 1440, 15)).toEqual({
      startMinute: 60,
      endMinute: 75,
    });
  });

  it("preserves an event extending past the right edge when moved", () => {
    expect(adjustTimelineRange({ startMinute: 1320, endMinute: 1500 }, "move", -15, 1440, 15)).toEqual({
      startMinute: 1305,
      endMinute: 1485,
    });
  });

  it("preserves an event extending past the left edge when moved", () => {
    expect(adjustTimelineRange({ startMinute: -60, endMinute: 120 }, "move", 15, 1440, 15)).toEqual({
      startMinute: -45,
      endMinute: 135,
    });
  });
});

describe("moveTimelineRange", () => {
  it("keeps the elapsed duration and resolves a left-edge grid tie to the earlier start", () => {
    expect(
      moveTimelineRange(
        { start: "2026-08-17T00:20:00Z", end: "2026-08-17T01:20:00Z" },
        { kind: "nearest", target: "2026-08-17T00:00:00Z" },
        movementContext("2026-08-17", "UTC", "Asia/Kathmandu", 10),
      ),
    ).toEqual({ start: "2026-08-16T23:55:00Z", end: "2026-08-17T00:55:00Z" });
  });

  it("finds the nearest valid start at the right display edge without losing the hidden end", () => {
    expect(
      moveTimelineRange(
        { start: "2026-08-17T23:45:00Z", end: "2026-08-18T00:45:00Z" },
        { kind: "nearest", target: "2026-08-18T00:00:00Z" },
        movementContext("2026-08-17", "UTC", "Asia/Kathmandu", 10),
      ),
    ).toEqual({ start: "2026-08-17T23:55:00Z", end: "2026-08-18T00:55:00Z" });
  });

  it("validates the whole interval against opening hours before choosing a candidate", () => {
    expect(
      moveTimelineRange(
        { start: "2026-08-17T09:00:00Z", end: "2026-08-17T09:30:00Z" },
        { kind: "nearest", target: "2026-08-17T16:45:00Z" },
        movementContext("2026-08-17", "UTC", "UTC", 15, {
          openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({
            dayOfWeek,
            start: "08:00",
            end: "17:00",
          })),
          enforceOpeningHours: true,
        }),
      ),
    ).toEqual({ start: "2026-08-17T16:30:00Z", end: "2026-08-17T17:00:00Z" });
  });

  it.each([
    ["pointer nearest", { kind: "nearest", target: "2026-08-17T09:00:00Z" }],
    ["keyboard step", { kind: "step", direction: "next" }],
  ] as const)("skips opening-hours validation for an over-limit %s movement", (_, request) => {
    const validateBookingWindow = vi.spyOn(zonedBookingWindowFields, "validateBookingWindow");
    try {
      expect(
        moveTimelineRange(
          { start: "2026-08-17T09:00:00Z", end: "2026-08-17T09:30:00Z" },
          request,
          movementContext("2026-08-17", "UTC", "UTC", 15, {
            maxBookingDurationMinutes: 15,
            openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({
              dayOfWeek,
              start: "08:00",
              end: "17:00",
            })),
            enforceOpeningHours: true,
          }),
        ),
      ).toBeUndefined();
      expect(validateBookingWindow).toHaveBeenCalledTimes(0);
    } finally {
      validateBookingWindow.mockRestore();
    }
  });

  it("rejects a move when no candidate satisfies the configured opening hours", () => {
    expect(
      moveTimelineRange(
        { start: "2026-08-17T09:00:00Z", end: "2026-08-17T09:30:00Z" },
        { kind: "step", direction: "next" },
        movementContext("2026-08-17", "UTC", "UTC", 15, {
          maxBookingDurationMinutes: 60,
          openingHours: [{ dayOfWeek: 1, start: "08:00", end: "09:00" }],
          enforceOpeningHours: true,
        }),
      ),
    ).toBeUndefined();
  });

  it("returns no step when every moved interval violates the maximum duration", () => {
    expect(
      moveTimelineRange(
        { start: "2026-08-17T09:00:00Z", end: "2026-08-17T09:30:00Z" },
        { kind: "step", direction: "next" },
        movementContext("2026-08-17", "UTC", "UTC", 15, { maxBookingDurationMinutes: 15 }),
      ),
    ).toBeUndefined();
  });

  it("allows a multi-day maintenance interval when the configured maximum is zero", () => {
    expect(
      moveTimelineRange(
        { start: "2026-08-17T09:00:00Z", end: "2026-08-19T09:00:00Z" },
        { kind: "nearest", target: "2026-08-17T10:00:00Z" },
        movementContext("2026-08-17", "UTC", "UTC", 15),
      ),
    ).toEqual({ start: "2026-08-17T10:00:00Z", end: "2026-08-19T10:00:00Z" });
  });
});

describe("closestSchedulingTimelineSlot", () => {
  it("snaps hourly Kathmandu slots to UTC :15", () => {
    expect(
      closestSchedulingTimelineSlot("2026-08-17T09:50:00Z", {
        date: "2026-08-17",
        displayTimezone: "UTC",
        schedulingTimezone: "Asia/Kathmandu",
        slotGranularityMinutes: 60,
      }),
    ).toBe("2026-08-17T10:15:00Z");
  });

  it("keeps ordinary UTC slots on the expected quarter-hour grid", () => {
    expect(
      closestSchedulingTimelineSlot("2026-08-17T10:07:00Z", {
        date: "2026-08-17",
        displayTimezone: "UTC",
        schedulingTimezone: "UTC",
        slotGranularityMinutes: 15,
      }),
    ).toBe("2026-08-17T10:00:00Z");
  });

  it("keeps the correct repeated-hour occurrence when snapping across a DST fall-back", () => {
    expect(
      closestSchedulingTimelineSlot("2026-10-25T00:52:00Z", {
        date: "2026-10-25",
        displayTimezone: "Europe/Berlin",
        schedulingTimezone: "Europe/Berlin",
        slotGranularityMinutes: 60,
      }),
    ).toBe("2026-10-25T01:00:00Z");
  });
});
