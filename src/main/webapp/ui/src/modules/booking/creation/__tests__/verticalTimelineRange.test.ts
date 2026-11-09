import { describe, expect, it } from "vitest";
import {
  adjustVerticalTimelineRange,
  canAdjustVerticalTimelineRange,
  stepVerticalTimelineRange,
  type VerticalTimelineRangeContext,
  verticalTimelineRangeToDraft,
} from "../verticalTimelineRange";

const utcContext: VerticalTimelineRangeContext = {
  date: "2026-08-17",
  displayTimezone: "UTC",
  schedulingTimezone: "UTC",
  slotGranularityMinutes: 15,
};

describe("vertical timeline range adjustment", () => {
  it("snaps in the scheduling timezone and preserves the elapsed duration on moves", () => {
    const context = {
      date: "2026-06-15",
      displayTimezone: "America/Los_Angeles",
      schedulingTimezone: "Europe/Berlin",
      slotGranularityMinutes: 15,
    };
    const range = { start: "2026-06-15T09:30:00Z", end: "2026-06-15T10:30:00Z" };

    expect(canAdjustVerticalTimelineRange(range, context)).toBe(true);
    expect(adjustVerticalTimelineRange(range, "move", "2026-06-15T09:22:30Z", context)).toEqual({
      start: "2026-06-15T09:15:00Z",
      end: "2026-06-15T10:15:00Z",
    });
    expect(adjustVerticalTimelineRange(range, "move", "2026-06-15T09:08:00Z", context)).toEqual({
      start: "2026-06-15T09:15:00Z",
      end: "2026-06-15T10:15:00Z",
    });
  });

  it("keeps the opposite endpoint fixed when resizing and never crosses it", () => {
    const range = { start: "2026-08-17T09:00:00Z", end: "2026-08-17T10:00:00Z" };

    expect(adjustVerticalTimelineRange(range, "end", "2026-08-17T10:09:00Z", utcContext)).toEqual({
      start: range.start,
      end: "2026-08-17T10:15:00Z",
    });
    expect(adjustVerticalTimelineRange(range, "start", "2026-08-17T09:52:00Z", utcContext)).toEqual({
      start: "2026-08-17T09:45:00Z",
      end: range.end,
    });
  });

  it("aligns hourly slots to a scheduling zone with a fractional-hour UTC offset", () => {
    const context = { ...utcContext, schedulingTimezone: "Asia/Kathmandu", slotGranularityMinutes: 60 };
    const range = { start: "2026-08-17T09:15:00Z", end: "2026-08-17T10:15:00Z" };

    expect(canAdjustVerticalTimelineRange(range, context)).toBe(true);
    expect(stepVerticalTimelineRange(range, "move", "next", context)).toEqual({
      start: "2026-08-17T10:15:00Z",
      end: "2026-08-17T11:15:00Z",
    });
    expect(adjustVerticalTimelineRange(range, "move", "2026-08-17T09:55:00Z", context)).toEqual({
      start: "2026-08-17T10:15:00Z",
      end: "2026-08-17T11:15:00Z",
    });
  });

  it("clamps a move at the start of the day without shortening the booking", () => {
    const range = { start: "2026-08-17T01:00:00Z", end: "2026-08-17T03:00:00Z" };
    expect(adjustVerticalTimelineRange(range, "move", "2026-08-16T23:00:00Z", utcContext)).toEqual({
      start: "2026-08-17T00:00:00Z",
      end: "2026-08-17T02:00:00Z",
    });
  });

  it("steps to the next valid slot and preserves duration at the day boundary", () => {
    const range = { start: "2026-08-17T21:00:00Z", end: "2026-08-17T23:00:00Z" };

    expect(stepVerticalTimelineRange(range, "move", "next", utcContext)).toEqual({
      start: "2026-08-17T21:15:00Z",
      end: "2026-08-17T23:15:00Z",
    });
    expect(adjustVerticalTimelineRange(range, "move", "2026-08-17T23:59:00Z", utcContext)).toEqual({
      start: "2026-08-17T22:00:00Z",
      end: "2026-08-18T00:00:00Z",
    });
    expect(verticalTimelineRangeToDraft({ start: "2026-08-17T23:00:00Z", end: "2026-08-18T00:00:00Z" }, "UTC")).toEqual(
      {
        startDate: "2026-08-17",
        startTime: "23:00",
        endDate: "2026-08-18",
        endTime: "00:00",
      },
    );
  });

  it("preserves both occurrences of the repeated hour through stepping and field conversion", () => {
    const context: VerticalTimelineRangeContext = {
      date: "2026-10-25",
      displayTimezone: "Europe/Berlin",
      schedulingTimezone: "Europe/Berlin",
      slotGranularityMinutes: 15,
    };
    const range = { start: "2026-10-25T00:45:00Z", end: "2026-10-25T01:15:00Z" };

    expect(canAdjustVerticalTimelineRange(range, context)).toBe(true);
    expect(verticalTimelineRangeToDraft(range, context.displayTimezone)).toMatchObject({
      startTime: "02:45",
      startOccurrence: "earlier",
      endTime: "02:15",
      endOccurrence: "later",
    });
    const next = stepVerticalTimelineRange(range, "move", "next", context);
    expect(next).toEqual({ start: "2026-10-25T01:00:00Z", end: "2026-10-25T01:30:00Z" });
    if (!next) throw new Error("Expected a valid next slot");
    expect(verticalTimelineRangeToDraft(next, context.displayTimezone)).toMatchObject({
      startTime: "02:00",
      startOccurrence: "later",
      endTime: "02:30",
      endOccurrence: "later",
    });
  });

  it("steps across a spring gap without inventing skipped wall-clock slots", () => {
    const context: VerticalTimelineRangeContext = {
      date: "2026-03-29",
      displayTimezone: "Europe/Berlin",
      schedulingTimezone: "Europe/Berlin",
      slotGranularityMinutes: 15,
    };
    const range = { start: "2026-03-29T00:45:00Z", end: "2026-03-29T01:15:00Z" };

    expect(stepVerticalTimelineRange(range, "move", "next", context)).toEqual({
      start: "2026-03-29T01:00:00Z",
      end: "2026-03-29T01:30:00Z",
    });
    expect(
      verticalTimelineRangeToDraft({ start: "2026-03-29T01:00:00Z", end: "2026-03-29T01:30:00Z" }, "Europe/Berlin"),
    ).toMatchObject({ startTime: "03:00", endTime: "03:30" });
  });

  it("disables cross-day and off-grid ranges without normalizing them", () => {
    const crossDay = { start: "2026-08-17T23:00:00Z", end: "2026-08-18T01:00:00Z" };
    const offGrid = { start: "2026-08-17T09:02:00Z", end: "2026-08-17T10:00:00Z" };

    expect(canAdjustVerticalTimelineRange(crossDay, utcContext)).toBe(false);
    expect(canAdjustVerticalTimelineRange(offGrid, utcContext)).toBe(false);
    expect(adjustVerticalTimelineRange(crossDay, "move", "2026-08-17T22:00:00Z", utcContext)).toBeUndefined();
    expect(stepVerticalTimelineRange(offGrid, "end", "next", utcContext)).toBeUndefined();
  });
});
