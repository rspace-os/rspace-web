import { afterEach, describe, expect, it, test, vi } from "vitest";
import { operationFunctions } from "../operationFunctions";

describe("operationFunctions.today", () => {
  const { today } = operationFunctions;

  afterEach(() => vi.useRealTimers());

  it("returns the local calendar date (YYYY-MM-DD), not the UTC date", () => {
    // Late local evening: in a negative-offset timezone the UTC clock has already rolled to the
    // next day, so a toISOString()-based implementation would return "2026-03-15" here and fail.
    // The instant is built from local parts, so 2026-03-14 is the expectation in every timezone.
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 14, 23, 30, 0));
    expect(String(today.fn())).toBe("2026-03-14");
  });
});

describe("increment guards against values the endpoint always rejects", () => {
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
