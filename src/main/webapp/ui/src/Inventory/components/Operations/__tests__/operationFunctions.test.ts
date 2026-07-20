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
