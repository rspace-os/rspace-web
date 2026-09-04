import { afterEach, describe, expect, it, test, vi } from "vitest";
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

describe("increment guards against values the endpoint always rejects", () => {
  // The backend accepts a Passage number only as a positive whole number, so a field that somehow
  // holds a fraction or a negative would have the wizard build a request its own endpoint refuses
  // every time, with no way for the user to see why (Copilot review, PR #1090).
  test.each([
    ["a fraction", "1.5"],
    ["a negative", "-3"],
    ["a value past safe integers", "9007199254740993"],
    ["not a number at all", "banana"],
    ["absent", undefined],
  ])("falls back to start for %s", (_label, current) => {
    expect(operationFunctions.increment.fn({ current, start: "1" })).toBe(1);
  });

  test("still counts on from a whole number", () => {
    expect(operationFunctions.increment.fn({ current: "4", start: "1" })).toBe(5);
  });

  test("counts on from zero, which is a legitimate prior value", () => {
    expect(operationFunctions.increment.fn({ current: "0", start: "1" })).toBe(1);
  });
});
