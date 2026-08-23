import { describe, expect, it } from "vitest";
import { type AvailabilityInterval, buildAvailabilitySegments, classifyCurrentDayAvailability } from "../availability";

const start = new Date("2026-08-17T00:00:00Z");
const end = new Date("2026-08-18T00:00:00Z");
const occupied = (from: string, to: string, kind: AvailabilityInterval["kind"] = "booking") => ({
  kind,
  startsAt: new Date(from),
  endsAt: new Date(to),
});

describe("availability", () => {
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

  it("treats booking, blockout, and overlap segments as occupied", () => {
    const segments = buildAvailabilitySegments(
      [
        occupied("2026-08-17T08:00:00Z", "2026-08-17T10:00:00Z"),
        occupied("2026-08-17T09:00:00Z", "2026-08-17T11:00:00Z", "blockout"),
      ],
      start,
      end,
    );
    expect(segments.filter(({ state }) => state !== "available").map(({ state }) => state)).toEqual([
      "booking",
      "overlap",
      "blockout",
    ]);
  });

  it("rejects invalid dates, periods, intervals, and an out-of-day current time", () => {
    expect(() => buildAvailabilitySegments([], end, start)).toThrow(RangeError);
    expect(() =>
      buildAvailabilitySegments([occupied("2026-08-17T10:00:00Z", "2026-08-17T09:00:00Z")], start, end),
    ).toThrow(RangeError);
    expect(() => classifyCurrentDayAvailability([], start, end, end)).toThrow("now must be within the day");
    expect(() => classifyCurrentDayAvailability([], start, end, new Date("invalid"))).toThrow(RangeError);
  });
});
