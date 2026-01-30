/*
 */
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import fc from "fast-check";
import { clamp } from "../../Util";

describe("clamp", () => {
  it("y <= clamp(x,y,z) <= z", () => {
    fc.assert(
      fc.property(fc.integer(), fc.integer(), fc.integer(), (x, min, max) => {
        fc.pre(min < max);
        expect(clamp(x, min, max)).toBeGreaterThanOrEqual(min);
        expect(clamp(x, min, max)).toBeLessThanOrEqual(max);
      })
    );
  });
});


