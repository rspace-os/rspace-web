import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import fc from "fast-check";
import { arbRsSet } from "./helpers";

describe("filter", () => {
  it("Set before is superset of set after filter i.e. size is less than or equal after", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), fc.func(fc.boolean())),
        ([set, func]) => {
          expect(set.isSupersetOf(set.filter((x) => func(x)))).toBe(true);
        }
      )
    );
  });
  it("Idempotence", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), fc.func(fc.boolean())),
        ([set, func]) => {
          expect(
            set
              .filter((x) => func(x))
              .filter((x) => func(x))
              .isSame(set.filter((x) => func(x)))
          ).toBe(true);
        }
      )
    );
  });
});


