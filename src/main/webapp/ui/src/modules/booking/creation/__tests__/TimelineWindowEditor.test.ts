import { describe, expect, it } from "vitest";
import { closestSchedulingTimelineSlot } from "../schedulingTimelineGrid";
import { adjustTimelineRange } from "../TimelineWindowEditor";

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

  it("snaps an event extending past the right edge into the timeline when moved", () => {
    expect(adjustTimelineRange({ startMinute: 1320, endMinute: 1500 }, "move", -15, 1440, 15)).toEqual({
      startMinute: 1260,
      endMinute: 1440,
    });
  });

  it("snaps an event extending past the left edge into the timeline when moved", () => {
    expect(adjustTimelineRange({ startMinute: -60, endMinute: 120 }, "move", 15, 1440, 15)).toEqual({
      startMinute: 0,
      endMinute: 180,
    });
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
