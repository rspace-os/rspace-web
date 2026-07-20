import { describe, expect, it } from "vitest";
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

  it("takes no parameters", () => {
    expect(today.params).toEqual([]);
  });

  it("returns today's local date as an ISO calendar date (YYYY-MM-DD)", () => {
    const result = String(today.fn());
    expect(result).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    // Matches the local date parts (not UTC), so it is the user's local "today".
    const now = new Date();
    const pad = (n: number) => String(n).padStart(2, "0");
    expect(result).toBe(`${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`);
  });
});
