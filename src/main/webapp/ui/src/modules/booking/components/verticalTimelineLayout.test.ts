import { describe, expect, it } from "vitest";
import type { DayTimelineEvent } from "./DayTimelineEvent";
import { layoutVerticalTimelineEvents } from "./verticalTimelineLayout";

function event(id: string, startMinute: number, endMinute: number): DayTimelineEvent {
  return { id, kind: "booking", privacy: "busy", startMinute, endMinute };
}

describe("layoutVerticalTimelineEvents", () => {
  it("assigns deterministic lanes across connected overlaps and reuses them at half-open edges", () => {
    const positioned = layoutVerticalTimelineEvents(
      [event("c", 60, 75), event("b", 30, 90), event("a", 0, 60), event("d", 100, 120)],
      1440,
    );

    expect(positioned.map(({ event: item, lane, laneCount }) => [item.id, lane, laneCount])).toEqual([
      ["a", 0, 2],
      ["b", 1, 2],
      ["c", 0, 2],
      ["d", 0, 1],
    ]);
  });

  it("clips cross-day segments without changing original event intervals", () => {
    const before = event("before", -30, 30);
    const after = event("after", 1430, 1470);

    expect(layoutVerticalTimelineEvents([before, after], 1440)).toMatchObject([
      { event: before, startMinute: 0, endMinute: 30, continuesBefore: true, continuesAfter: false },
      { event: after, startMinute: 1430, endMinute: 1440, continuesBefore: false, continuesAfter: true },
    ]);
    expect(before).toMatchObject({ startMinute: -30, endMinute: 30 });
    expect(after).toMatchObject({ startMinute: 1430, endMinute: 1470 });
  });

  it("omits empty, invalid, and off-day intervals", () => {
    expect(
      layoutVerticalTimelineEvents(
        [event("empty", 30, 30), event("before", -60, 0), event("after", 1440, 1500), event("visible", 0, 15)],
        1440,
      ).map(({ event: item }) => item.id),
    ).toEqual(["visible"]);
  });
});
