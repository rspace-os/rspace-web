//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import RsSet from "../../set";
import fc from "fast-check";
import { arbRsSet } from "./helpers";

describe("constructor", () => {
  describe("Should behave just like native set", () => {
    const expectSameSizeAsNativeSet = (data: ?Iterable<mixed>) => {
      expect(new RsSet(data).size).toEqual(new Set(data).size);
    };

    test("undefined", () => {
      expectSameSizeAsNativeSet();
    });
    test("null", () => {
      expectSameSizeAsNativeSet(null);
    });
    test("array", () => {
      fc.assert(
        fc.property(fc.array(fc.anything()), (array) => {
          expectSameSizeAsNativeSet(array);
        })
      );
    });
    test("set", () => {
      fc.assert(
        fc.property(
          fc.array(fc.anything()).map<Set<mixed>>((array) => new Set(array)),
          (set) => {
            expectSameSizeAsNativeSet(set);
          }
        )
      );
    });
    test("RsSet", () => {
      fc.assert(
        fc.property(arbRsSet(fc.anything()), (rsset) => {
          expectSameSizeAsNativeSet(rsset);
        })
      );
    });
  });
});
