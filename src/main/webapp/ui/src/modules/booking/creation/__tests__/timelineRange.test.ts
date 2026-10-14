import { describe, expect, it, vi } from "vitest";
import type { IncrementUnit } from "@/modules/booking/domain/bookingIncrement";
import * as bookingOpeningHours from "@/modules/booking/domain/bookingOpeningHours";
import { ALL_ISO_WEEKDAYS, type OpeningHoursEntry } from "@/modules/booking/domain/bookingOpeningHours";
import {
  proposeDayRange,
  type TimelineContext,
  type TimelineRange,
  timelineAdjuster,
  timelinePolicy,
  timelineRangeToDraft,
  unrestrictedTimelinePolicy,
} from "../timelineRange";

function context(
  date: string,
  displayTimezone: string,
  timezone: string,
  slotGranularityMinutes: number,
  {
    incrementUnit = "MINUTES",
    alignToIncrement = true,
    openingHours = ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
    maxBookingDurationMinutes = 0,
  }: Partial<{
    incrementUnit: IncrementUnit;
    alignToIncrement: boolean;
    openingHours: OpeningHoursEntry[];
    maxBookingDurationMinutes: number;
  }> = {},
): TimelineContext {
  return {
    date,
    displayTimezone,
    policy: timelinePolicy({
      timezone,
      slotGranularityMinutes,
      incrementUnit,
      alignToIncrement,
      openingHours,

      maxBookingDurationMinutes,
    }),
  };
}

function adjuster(range: TimelineRange, timelineContext: TimelineContext) {
  const result = timelineAdjuster(range, timelineContext);
  if (!result) throw new Error("Expected an adjustable range");
  return result;
}

const utc15 = context("2026-08-17", "UTC", "UTC", 15);

describe("timeline moves", () => {
  it("snaps an aligned start in the scheduling timezone and keeps the elapsed duration", () => {
    const range = { start: "2026-06-15T09:30:00Z", end: "2026-06-15T10:30:00Z" };
    const moves = adjuster(range, context("2026-06-15", "America/Los_Angeles", "Europe/Berlin", 15));

    // 09:22:30 is equidistant from 09:15 and 09:30, so the earlier start wins.
    expect(moves.adjust("move", "2026-06-15T09:22:30Z")).toEqual({
      start: "2026-06-15T09:15:00Z",
      end: "2026-06-15T10:15:00Z",
    });
  });

  it("aligns hourly starts to a scheduling zone with a fractional-hour offset", () => {
    const range = { start: "2026-08-17T09:15:00Z", end: "2026-08-17T10:15:00Z" };
    const moves = adjuster(range, context("2026-08-17", "UTC", "Asia/Kathmandu", 60));

    expect(moves.step("move", "next")).toEqual({ start: "2026-08-17T10:15:00Z", end: "2026-08-17T11:15:00Z" });
    expect(moves.adjust("move", "2026-08-17T09:55:00Z")).toEqual({
      start: "2026-08-17T10:15:00Z",
      end: "2026-08-17T11:15:00Z",
    });
  });

  it("moves an unaligned start by single minutes", () => {
    const range = { start: "2026-08-17T09:07:00Z", end: "2026-08-17T09:37:00Z" };
    const moves = adjuster(range, context("2026-08-17", "UTC", "UTC", 15, { alignToIncrement: false }));

    expect(moves.step("move", "next")).toEqual({ start: "2026-08-17T09:08:00Z", end: "2026-08-17T09:38:00Z" });
    expect(moves.adjust("move", "2026-08-17T11:03:20Z")).toEqual({
      start: "2026-08-17T11:03:00Z",
      end: "2026-08-17T11:33:00Z",
    });
  });

  it("keeps the true start when a move leaves part of the booking on the previous day", () => {
    const range = { start: "2026-08-17T01:00:00Z", end: "2026-08-17T03:00:00Z" };
    expect(adjuster(range, utc15).adjust("move", "2026-08-16T23:00:00Z")).toEqual({
      start: "2026-08-16T23:00:00Z",
      end: "2026-08-17T01:00:00Z",
    });
  });

  it("keeps a moved booking on the displayed day without shortening it", () => {
    const range = { start: "2026-08-17T21:00:00Z", end: "2026-08-17T23:00:00Z" };
    const moves = adjuster(range, utc15);

    expect(moves.step("move", "next")).toEqual({ start: "2026-08-17T21:15:00Z", end: "2026-08-17T23:15:00Z" });
    // Midnight would put the whole booking on the next day, so the nearest start still on this day wins.
    expect(moves.adjust("move", "2026-08-17T23:59:00Z")).toEqual({
      start: "2026-08-17T23:45:00Z",
      end: "2026-08-18T01:45:00Z",
    });
  });

  it("skips starts that would leave the opening hours", () => {
    const range = { start: "2026-08-17T10:00:00Z", end: "2026-08-17T11:00:00Z" };
    const moves = adjuster(
      range,
      context("2026-08-17", "UTC", "UTC", 60, {
        openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "09:00", end: "17:00" })),
      }),
    );

    expect(moves.adjust("move", "2026-08-17T18:00:00Z")).toEqual({
      start: "2026-08-17T16:00:00Z",
      end: "2026-08-17T17:00:00Z",
    });
  });

  it("keeps the calendar-day count when a Day booking moves across the spring change", () => {
    const range = { start: "2026-03-28T11:00:00Z", end: "2026-03-29T10:00:00Z" };
    const moves = adjuster(
      range,
      context("2026-03-28", "Europe/Berlin", "Europe/Berlin", 1440, { incrementUnit: "DAYS", alignToIncrement: false }),
    );

    // 13:00 on Saturday to 13:00 on Sunday: 23 elapsed hours, the same local time.
    expect(moves.adjust("move", "2026-03-28T12:00:00Z")).toEqual({
      start: "2026-03-28T12:00:00Z",
      end: "2026-03-29T11:00:00Z",
    });
  });

  it("does not move a Day booking to an end on a repeated local time", () => {
    const range = { start: "2026-10-23T00:30:00Z", end: "2026-10-24T00:30:00Z" };
    const moves = adjuster(
      range,
      context("2026-10-24", "Europe/Berlin", "Europe/Berlin", 1440, { incrementUnit: "DAYS", alignToIncrement: false }),
    );

    // Starts from 02:00 to 02:59 on 24 October would end on the repeated hour of 25 October.
    expect(moves.adjust("move", "2026-10-24T00:30:00Z")).toEqual({
      start: "2026-10-24T01:00:00Z",
      end: "2026-10-25T02:00:00Z",
    });
  });

  it("keeps a Day booking's chosen repeated end when the move does not change the start", () => {
    const range = { start: "2026-10-24T00:30:00Z", end: "2026-10-25T01:30:00Z" };
    const moves = adjuster(
      range,
      context("2026-10-25", "Europe/Berlin", "Europe/Berlin", 1440, { incrementUnit: "DAYS", alignToIncrement: false }),
    );

    expect(moves.adjust("move", range.start)).toEqual(range);
  });

  it("steps through both occurrences of the repeated hour", () => {
    const range = { start: "2026-10-25T00:45:00Z", end: "2026-10-25T01:15:00Z" };
    const moves = adjuster(range, context("2026-10-25", "Europe/Berlin", "Europe/Berlin", 15));

    expect(timelineRangeToDraft(range, "Europe/Berlin")).toMatchObject({
      startTime: "02:45",
      startOccurrence: "earlier",
      endTime: "02:15",
      endOccurrence: "later",
    });
    expect(moves.step("move", "next")).toEqual({ start: "2026-10-25T01:00:00Z", end: "2026-10-25T01:30:00Z" });
  });

  it("steps across the spring gap without inventing skipped times", () => {
    const range = { start: "2026-03-29T00:45:00Z", end: "2026-03-29T01:15:00Z" };
    const moves = adjuster(range, context("2026-03-29", "Europe/Berlin", "Europe/Berlin", 15));

    expect(moves.step("move", "next")).toEqual({ start: "2026-03-29T01:00:00Z", end: "2026-03-29T01:30:00Z" });
    expect(
      timelineRangeToDraft({ start: "2026-03-29T01:00:00Z", end: "2026-03-29T01:30:00Z" }, "Europe/Berlin"),
    ).toMatchObject({ startTime: "03:00", endTime: "03:30" });
  });
});

describe("timeline resizing", () => {
  it("keeps the other endpoint fixed and moves by whole increments", () => {
    const range = { start: "2026-08-17T09:00:00Z", end: "2026-08-17T10:00:00Z" };
    const resizing = adjuster(range, utc15);

    expect(resizing.adjust("end", "2026-08-17T10:09:00Z")).toEqual({ start: range.start, end: "2026-08-17T10:15:00Z" });
    expect(resizing.adjust("start", "2026-08-17T09:52:00Z")).toEqual({ start: "2026-08-17T09:45:00Z", end: range.end });
    expect(resizing.step("end", "previous")).toEqual({ start: range.start, end: "2026-08-17T09:45:00Z" });
    expect(resizing.adjust("start", range.end)).toEqual({ start: "2026-08-17T09:45:00Z", end: range.end });
    expect(resizing.adjust("end", range.start)).toEqual({ start: range.start, end: "2026-08-17T09:15:00Z" });
  });

  it("resizes an unaligned booking by duration multiples from the fixed endpoint", () => {
    const range = { start: "2026-08-17T09:07:00Z", end: "2026-08-17T09:24:00Z" };
    const resizing = adjuster(range, context("2026-08-17", "UTC", "UTC", 17, { alignToIncrement: false }));

    expect(resizing.adjust("end", "2026-08-17T10:00:00Z")).toEqual({ start: range.start, end: "2026-08-17T09:58:00Z" });
  });

  it("offers no Day resize without a distinct same-time endpoint", () => {
    const range = { start: "2026-03-30T10:00:00Z", end: "2026-04-03T10:00:00Z" };
    const resizing = adjuster(
      range,
      context("2026-03-30", "Europe/Berlin", "Europe/Berlin", 2880, { incrementUnit: "DAYS", alignToIncrement: false }),
    );

    expect(resizing.visible).toEqual({ start: true, end: false });
    expect(resizing.canResize("start")).toBe(false);
    // The pointer can only land on the current start.
    expect(resizing.adjust("start", "2026-03-30T08:00:00Z")).toEqual(range);
  });

  it("lets a Day end choose the other occurrence of a repeated time", () => {
    const range = { start: "2026-10-24T00:30:00Z", end: "2026-10-25T00:30:00Z" };
    const resizing = adjuster(
      range,
      context("2026-10-25", "Europe/Berlin", "Europe/Berlin", 1440, { incrementUnit: "DAYS", alignToIncrement: false }),
    );

    expect(resizing.canResize("end")).toBe(true);
    expect(resizing.adjust("end", "2026-10-25T01:20:00Z")).toEqual({ start: range.start, end: "2026-10-25T01:30:00Z" });
  });

  it("respects the item maximum", () => {
    const range = { start: "2026-08-17T09:00:00Z", end: "2026-08-17T10:00:00Z" };
    const resizing = adjuster(range, context("2026-08-17", "UTC", "UTC", 15, { maxBookingDurationMinutes: 60 }));

    expect(resizing.adjust("end", "2026-08-17T12:00:00Z")).toEqual(range);
    expect(resizing.step("end", "next")).toBeUndefined();
  });
});

describe("hidden endpoints", () => {
  it("offers the next midnight for an end on a later day when the result is valid", () => {
    const range = { start: "2026-08-17T23:00:00Z", end: "2026-08-18T01:00:00Z" };
    const edges = adjuster(range, utc15);

    expect(edges.visible).toEqual({ start: true, end: false });
    expect(edges.hiddenEdge("start")).toBeUndefined();
    expect(edges.hiddenEdge("end")).toEqual({
      kind: "enabled",
      range: { start: "2026-08-17T23:00:00Z", end: "2026-08-18T00:00:00Z" },
    });
  });

  it("refuses a midnight that breaks a two-day increment", () => {
    const range = { start: "2026-03-30T10:00:00Z", end: "2026-04-03T10:00:00Z" };
    const edges = adjuster(
      range,
      context("2026-03-30", "Europe/Berlin", "Europe/Berlin", 2880, { incrementUnit: "DAYS", alignToIncrement: false }),
    );

    expect(edges.hiddenEdge("end")).toEqual({ kind: "disabled", reason: "duration" });
  });

  it("refuses a display-zone midnight that is not the scheduling zone's day boundary", () => {
    // Shown in UTC; Berlin midnight to midnight across the spring change starts at 23:00 UTC the day before.
    const range = { start: "2026-03-28T23:00:00Z", end: "2026-03-29T22:00:00Z" };
    const edges = adjuster(
      range,
      context("2026-03-29", "UTC", "Europe/Berlin", 1440, { incrementUnit: "DAYS", alignToIncrement: false }),
    );

    expect(edges.visible).toEqual({ start: false, end: true });
    expect(edges.hiddenEdge("start")).toEqual({ kind: "disabled", reason: "duration" });
  });

  it("names a skipped display-zone midnight instead of choosing a time", () => {
    // Santiago skips 00:00 to 01:00 on 6 September 2026.
    const range = { start: "2026-09-05T20:00:00Z", end: "2026-09-06T12:00:00Z" };
    const edges = adjuster(
      range,
      context("2026-09-06", "America/Santiago", "America/Santiago", 15, { alignToIncrement: false }),
    );

    expect(edges.hiddenEdge("start")).toEqual({ kind: "disabled", reason: "midnightSkipped" });
  });
});

describe("creation proposals", () => {
  it("starts an aligned proposal on the grid and rounds the length to whole increments", () => {
    expect(proposeDayRange({ startMinute: 547, endMinute: 548 }, "2026-08-17", "UTC", utc15.policy)).toEqual({
      startMinute: 540,
      endMinute: 555,
    });
  });

  it("keeps an unaligned start", () => {
    const policy = context("2026-08-17", "UTC", "UTC", 17, { alignToIncrement: false }).policy;
    expect(proposeDayRange({ startMinute: 547, endMinute: 600 }, "2026-08-17", "UTC", policy)).toEqual({
      startMinute: 547,
      endMinute: 598,
    });
  });

  it("offers no Day proposal whose end would need an occurrence choice", () => {
    const policy = context("2026-10-24", "Europe/Berlin", "Europe/Berlin", 1440, {
      incrementUnit: "DAYS",
      alignToIncrement: false,
    }).policy;
    expect(
      proposeDayRange({ startMinute: 150, endMinute: 151 }, "2026-10-24", "Europe/Berlin", policy),
    ).toBeUndefined();
  });

  it("extends a Day proposal to the same local time on a later date", () => {
    const policy = context("2026-03-28", "Europe/Berlin", "Europe/Berlin", 1440, {
      incrementUnit: "DAYS",
      alignToIncrement: false,
    }).policy;
    // Noon Saturday to noon Sunday in Berlin is 23 elapsed hours.
    expect(proposeDayRange({ startMinute: 720, endMinute: 721 }, "2026-03-28", "Europe/Berlin", policy)).toEqual({
      startMinute: 720,
      endMinute: 720 + 23 * 60,
    });
  });
});

describe("preserved whole-window movement", () => {
  it.each([
    [
      "right",
      { start: "2026-08-17T22:00:00Z", end: "2026-08-18T01:00:00Z" },
      "2026-08-17T21:45:00Z",
      { start: "2026-08-17T21:45:00Z", end: "2026-08-18T00:45:00Z" },
    ],
    [
      "left",
      { start: "2026-08-16T23:00:00Z", end: "2026-08-17T02:00:00Z" },
      "2026-08-16T23:15:00Z",
      { start: "2026-08-16T23:15:00Z", end: "2026-08-17T02:15:00Z" },
    ],
  ] as const)("preserves an event extending past the %s edge when moved", (_, range, target, expected) => {
    expect(adjuster(range, utc15).adjust("move", target)).toEqual(expected);
  });

  it("keeps the elapsed duration and resolves a left-edge Kathmandu grid tie to the earlier start", () => {
    const range = { start: "2026-08-17T00:20:00Z", end: "2026-08-17T01:20:00Z" };
    expect(
      adjuster(range, context("2026-08-17", "UTC", "Asia/Kathmandu", 10)).adjust("move", "2026-08-17T00:00:00Z"),
    ).toEqual({ start: "2026-08-16T23:55:00Z", end: "2026-08-17T00:55:00Z" });
  });

  it("finds the nearest valid start at the right display edge without losing the hidden end", () => {
    const range = { start: "2026-08-17T23:45:00Z", end: "2026-08-18T00:45:00Z" };
    expect(
      adjuster(range, context("2026-08-17", "UTC", "Asia/Kathmandu", 10)).adjust("move", "2026-08-18T00:00:00Z"),
    ).toEqual({ start: "2026-08-17T23:55:00Z", end: "2026-08-18T00:55:00Z" });
  });

  it("validates the whole interval against opening hours before choosing a candidate", () => {
    const range = { start: "2026-08-17T09:00:00Z", end: "2026-08-17T09:30:00Z" };
    const moves = adjuster(
      range,
      context("2026-08-17", "UTC", "UTC", 15, {
        openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "17:00" })),
      }),
    );
    expect(moves.adjust("move", "2026-08-17T16:45:00Z")).toEqual({
      start: "2026-08-17T16:30:00Z",
      end: "2026-08-17T17:00:00Z",
    });
  });

  it.each(["pointer nearest", "keyboard step"] as const)(
    "skips opening-hours validation for an over-limit %s movement",
    (request) => {
      const coversInterval = vi.spyOn(bookingOpeningHours, "coversInterval");
      try {
        const range = { start: "2026-08-17T09:00:00Z", end: "2026-08-17T09:30:00Z" };
        const moves = adjuster(
          range,
          context("2026-08-17", "UTC", "UTC", 15, {
            maxBookingDurationMinutes: 15,
            openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "17:00" })),
          }),
        );
        expect(
          request === "pointer nearest" ? moves.adjust("move", range.start) : moves.step("move", "next"),
        ).toBeUndefined();
        expect(coversInterval).toHaveBeenCalledTimes(0);
      } finally {
        coversInterval.mockRestore();
      }
    },
  );

  it("rejects a move when no candidate satisfies the configured opening hours", () => {
    const range = { start: "2026-08-17T09:00:00Z", end: "2026-08-17T09:30:00Z" };
    const moves = adjuster(
      range,
      context("2026-08-17", "UTC", "UTC", 15, {
        maxBookingDurationMinutes: 60,
        openingHours: [{ dayOfWeek: 1, start: "08:00", end: "09:00" }],
      }),
    );
    expect(moves.step("move", "next")).toBeUndefined();
  });

  it("allows a multi-day maintenance interval when the configured maximum is zero", () => {
    const range = { start: "2026-08-17T09:00:00Z", end: "2026-08-19T09:00:00Z" };
    const moves = adjuster(range, {
      date: "2026-08-17",
      displayTimezone: "UTC",
      policy: unrestrictedTimelinePolicy("UTC"),
    });
    expect(moves.adjust("move", "2026-08-17T10:00:00Z")).toEqual({
      start: "2026-08-17T10:00:00Z",
      end: "2026-08-19T10:00:00Z",
    });
  });
});
