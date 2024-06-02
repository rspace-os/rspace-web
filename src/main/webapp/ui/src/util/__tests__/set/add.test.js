//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { arbRsSet } from "./helpers";

describe("add", () => {
  test("Result should be a subset of input set.", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), fc.anything()),
        ([set, addition]) => {
          expect(set.isSubsetOf(set.add(addition))).toBe(true);
        }
      )
    );
  });
});
