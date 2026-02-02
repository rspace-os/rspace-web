import { describe, expect, it } from "vitest";
import { sum } from "../../iterators";

describe("sum", () => {
  it("Works on arrays", () => {
    expect(sum([1, 2, 3])).toBe(6);
  });

  it("Works on sets", () => {
    expect(sum(new Set([1, 2, 3]))).toBe(6);
  });
});


