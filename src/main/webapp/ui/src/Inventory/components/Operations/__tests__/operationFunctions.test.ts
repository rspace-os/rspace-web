import { afterEach, describe, expect, it, vi } from "vitest";
import { operationFunctions } from "../operationFunctions";

describe("operationFunctions.increment", () => {
  const { increment } = operationFunctions;

  it("declares its two parameters", () => {
    expect(increment.params).toEqual(["current", "start"]);
  });

  it("adds one to a numeric current", () => {
    expect(increment.fn({ current: 3, start: 1 })).toBe(4);
    expect(increment.fn({ current: "3", start: 1 })).toBe(4);
  });

  it("falls back to start when current is absent", () => {
    expect(increment.fn({ current: undefined, start: 1 })).toBe(1);
  });

  it("falls back to start when current is not a number", () => {
    expect(increment.fn({ current: "n/a", start: 7 })).toBe(7);
  });
});

describe("operationFunctions.today", () => {
  const { today } = operationFunctions;

  afterEach(() => vi.useRealTimers());

  it("takes no parameters", () => {
    expect(today.params).toEqual([]);
  });

  it("returns the local calendar date (YYYY-MM-DD), not the UTC date", () => {
    // Late local evening on a fixed day: in a negative-offset timezone the UTC clock has already
    // rolled to the next day, so a toISOString()-based (UTC) implementation would return "2026-03-15"
    // here and fail. The instant is built from local parts, so the expected local date is 2026-03-14
    // in every timezone. This is a hardcoded expectation (not recomputed from the impl's own logic),
    // so it actually pins the output rather than mirroring it.
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 14, 23, 30, 0));
    expect(String(today.fn())).toBe("2026-03-14");
  });
});
