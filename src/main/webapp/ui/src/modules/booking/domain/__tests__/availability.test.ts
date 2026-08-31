import { describe, expect, it } from "vitest";
import {
  type AvailabilityInterval,
  buildAvailabilitySegments,
  buildAvailabilitySlices,
  classifyCurrentDayAvailability,
} from "../availability";

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

  it("rejects invalid dates, periods, intervals, and an out-of-day current time", () => {
    expect(() => buildAvailabilitySegments([], end, start)).toThrow(RangeError);
    expect(() =>
      buildAvailabilitySegments([occupied("2026-08-17T10:00:00Z", "2026-08-17T09:00:00Z")], start, end),
    ).toThrow(RangeError);
    expect(() => classifyCurrentDayAvailability([], start, end, end)).toThrow("now must be within the day");
    expect(() => classifyCurrentDayAvailability([], start, end, new Date("invalid"))).toThrow(RangeError);
  });
});
