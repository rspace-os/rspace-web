import { describe, expect, it } from "vitest";
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
