import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import RsSet from "../../set";
import fc from "fast-check";
import { arbRsSet } from "./helpers";

describe("constructor", () => {
  describe("Should behave just like native set", () => {
    const expectSameSizeAsNativeSet = (data?: Iterable<unknown> | null) => {
      expect(new RsSet(data).size).toEqual(new Set(data).size);
    };

    it("undefined", () => {
      expectSameSizeAsNativeSet();
    });
    it("null", () => {
      expectSameSizeAsNativeSet(null);
    });
    it("array", () => {
      fc.assert(
        fc.property(fc.array(fc.anything()), (array) => {
          expectSameSizeAsNativeSet(array);
        })
      );
    });
    it("set", () => {
      fc.assert(
        fc.property(
          fc.array(fc.anything()).map((array) => new Set(array)),
          (set) => {
            expectSameSizeAsNativeSet(set);
          }
        )
      );
    });
    it("RsSet", () => {
      fc.assert(
        fc.property(arbRsSet(fc.anything()), (rsset) => {
          expectSameSizeAsNativeSet(rsset);
        })
      );
    });
  });
});


