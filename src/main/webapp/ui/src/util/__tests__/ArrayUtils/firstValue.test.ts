import { describe, expect, test } from "vitest";
import { firstValue } from "../../ArrayUtils";

describe("firstValue", () => {
  test("returns the first value from an iterable", () => {
    expect(firstValue(new Set(["alpha", "beta"]))).toBe("alpha");
  });

  test("returns undefined for an empty iterable", () => {
    expect(firstValue(new Set())).toBeUndefined();
  });
});
