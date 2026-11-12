import { describe, expect, it } from "vitest";
import { durationUnit, wholeMinutes } from "./DurationUnitInput";

describe("elapsed duration units", () => {
  it.each([
    [0, "HOURS", "HOURS"],
    [90, "DAYS", "MINUTES"],
    [120, "MINUTES", "HOURS"],
    [1440, "HOURS", "DAYS"],
  ] as const)("infers %i minutes with a %s zero fallback as %s", (minutes, fallback, expected) => {
    expect(durationUnit(minutes, fallback)).toBe(expected);
  });
  it("removes conversion noise while preserving fractional minutes for validation", () => {
    expect(wholeMinutes(0.35 * 1440)).toBe(504);
    expect(wholeMinutes(1.5 * 60)).toBe(90);
    expect(wholeMinutes(0.123 * 60)).toBe(7.38);
  });
});
