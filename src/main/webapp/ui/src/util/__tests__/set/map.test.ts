import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import fc from "fast-check";
import { arbRsSet } from "./helpers";

describe("map", () => {
  it("Size after must be less than or equal size before", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), fc.func(fc.anything())),
        ([set, func]) => {
          expect(set.map(func).size).toBeLessThanOrEqual(set.size);
        }
      )
    );
  });
});


